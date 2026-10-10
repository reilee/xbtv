import SwiftUI
import AVKit
import Observation
import XBTVCatalog

@MainActor @Observable final class PlaybackSession {
    let player = AVPlayer()
    private(set) var error: String?
    private(set) var isLoading = true
    private(set) var ended = false
    private var statusObserver: NSKeyValueObservation?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var failureObserver: NSObjectProtocol?
    private var recordProgress: ((Double, Double) -> Void)?
    private var didStart = false
    private var resumed = false
    private var startSeconds: Double = 0

    func load(episode: Episode, resumeSeconds: Double, client: CatalogClient,
              record: @escaping (Double, Double) -> Void) async {
        stop(); error = nil; isLoading = true; ended = false
        startSeconds = resumeSeconds; recordProgress = record
        do {
            let url = try await client.stream(episode); try Task.checkCancellation()
            let item = AVPlayerItem(url: url)
            statusObserver = item.observe(\.status, options: [.initial, .new]) { [weak self] item, _ in
                Task { @MainActor [weak self] in self?.statusChanged(item) }
            }
            endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard let self, self.player.currentItem === item else { return }; self.save(); self.ended = true
                }
            }
            failureObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemFailedToPlayToEndTime, object: item, queue: .main) { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard let self, self.player.currentItem === item else { return }; self.fail()
                }
            }
            player.replaceCurrentItem(with: item)
            timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 5, preferredTimescale: 600), queue: .main) { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard let self, self.player.currentItem === item else { return }
                    if self.player.currentTime().seconds > 0 { self.didStart = true; self.save() }
                }
            }
            player.play()
        } catch {
            guard !Task.isCancelled else { return }
            self.error = error.localizedDescription; isLoading = false
        }
    }
    private func statusChanged(_ item: AVPlayerItem) {
        guard player.currentItem === item else { return }
        switch item.status {
        case .readyToPlay:
            isLoading = false
            if !resumed {
                resumed = true
                let duration = item.duration.seconds
                if startSeconds > 0, duration.isFinite, startSeconds < duration - 30 {
                    player.seek(to: CMTime(seconds: startSeconds, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
                }
            }
        case .failed: fail()
        default: break
        }
    }
    private func fail() {
        save(); player.pause(); isLoading = false
        error = "此线路暂时无法播放。可重试，或返回详情页选择其他线路。"
    }
    func save() {
        guard let item = player.currentItem else { return }
        let seconds = player.currentTime().seconds
        guard didStart || (seconds.isFinite && seconds > 0.5 && item.status == .readyToPlay) else { return }
        recordProgress?(player.currentTime().seconds, item.duration.seconds)
    }
    func stop() {
        save(); player.pause()
        statusObserver?.invalidate(); statusObserver = nil
        if let timeObserver { player.removeTimeObserver(timeObserver) }; timeObserver = nil
        for observer in [endObserver, failureObserver].compactMap({ $0 }) { NotificationCenter.default.removeObserver(observer) }
        endObserver = nil; failureObserver = nil
        player.replaceCurrentItem(with: nil); didStart = false; resumed = false; recordProgress = nil
    }
}

struct PlaybackView: View {
    let request: PlaybackRequest
    let client: CatalogClient
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @Environment(LibraryStore.self) private var library
    @State private var session = PlaybackSession()
    @State private var episode: Episode
    @State private var resumeSeconds: Double
    @State private var reload = 0
    @State private var nextEpisodeCountdown: Int?
    @State private var autoAdvanceCancelled = false
    init(request: PlaybackRequest, client: CatalogClient) {
        self.request = request; self.client = client
        // Intentional one-time seeds: each full-screen presentation is a new playback request.
        _episode = State(initialValue: request.episode); _resumeSeconds = State(initialValue: request.resumeSeconds)
    }
    private var nextEpisode: Episode? {
        guard let index = request.line.episodes.firstIndex(of: episode), index + 1 < request.line.episodes.count else { return nil }
        return request.line.episodes[index + 1]
    }
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if session.error == nil && !session.ended {
                NativePlayer(player: session.player, title: request.detail.video.title, episodeTitle: episode.title,
                         episodes: request.line.episodes, selectedEpisode: episode,
                         selectEpisode: switchEpisode, exit: { dismiss() })
                .ignoresSafeArea()
            }
            if session.isLoading {
                ProgressView("正在准备 \(episode.title)…").padding(40).background(.regularMaterial, in: .rect(cornerRadius: 20))
                    .onExitCommand { dismiss() }
            }
            if let error = session.error {
                VStack(spacing: 20) {
                    MessageView(title: "播放失败", message: error) { reload += 1 }
                    Button("返回选集") { cancelAutoAdvance(); dismiss() }
                }.padding(48).background(.regularMaterial)
                    .onExitCommand { dismiss() }
            } else if session.ended {
                VStack(spacing: 24) {
                    Text("本集播放结束").font(.title2)
                    if let nextEpisode {
                        if let nextEpisodeCountdown {
                            Text("\(nextEpisodeCountdown) 秒后播放 · \(nextEpisode.title)")
                                .font(.headline).monospacedDigit()
                            Button("取消自动播放") { cancelAutoAdvance() }
                        }
                        Button("立即播放下一集 · \(nextEpisode.title)") { switchEpisode(nextEpisode) }
                    }
                    Button("重新播放") { cancelAutoAdvance(); resumeSeconds = 0; reload += 1 }
                    Button("返回选集") { cancelAutoAdvance(); dismiss() }
                }.padding(56).background(.regularMaterial, in: .rect(cornerRadius: 24))
                    .onExitCommand { cancelAutoAdvance(); dismiss() }
                    .task(id: episode.id) {
                        guard let next = nextEpisode, scenePhase == .active else { return }
                        autoAdvanceCancelled = false
                        nextEpisodeCountdown = 5
                        for remaining in stride(from: 4, through: 0, by: -1) {
                            do { try await Task.sleep(for: .seconds(1)) }
                            catch { return }
                            guard !Task.isCancelled, !autoAdvanceCancelled,
                                  session.ended, scenePhase == .active else { return }
                            if remaining == 0 { switchEpisode(next) }
                            else { nextEpisodeCountdown = remaining }
                        }
                    }
            }
        }
        .task(id: "\(episode.id)-\(reload)") {
            let playingEpisode = episode
            await session.load(episode: playingEpisode, resumeSeconds: resumeSeconds, client: client) { seconds, duration in
                library.record(WatchRecord(video: request.detail.video, episode: playingEpisode,
                                           lineID: request.line.id, lineTitle: request.line.title, seconds: seconds, duration: duration))
            }
        }
        .onDisappear { cancelAutoAdvance(); session.stop() }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { cancelAutoAdvance(); session.save(); session.player.pause() }
        }
    }
    private func cancelAutoAdvance() {
        autoAdvanceCancelled = true
        nextEpisodeCountdown = nil
    }
    private func switchEpisode(_ next: Episode) {
        cancelAutoAdvance(); session.stop(); resumeSeconds = 0; episode = next
    }
}

/// AVKit owns Siri Remote transport, scrubbing, subtitles, audio tracks and AirPlay.
struct NativePlayer: UIViewControllerRepresentable {
    let player: AVPlayer
    let title: String
    let episodeTitle: String
    let episodes: [Episode]
    let selectedEpisode: Episode
    let selectEpisode: (Episode) -> Void
    let exit: () -> Void

    final class Coordinator: NSObject, AVPlayerViewControllerDelegate {
        var exit: () -> Void
        var picker: EpisodePickerHostingController?
        let presentation = EpisodePickerPresentation()
        init(exit: @escaping () -> Void) { self.exit = exit }
        func playerViewControllerShouldDismiss(_ playerViewController: AVPlayerViewController) -> Bool {
            exit()
            return false // SwiftUI owns the full-screen presentation.
        }
    }
    func makeCoordinator() -> Coordinator { Coordinator(exit: exit) }
    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        controller.player = player; controller.showsPlaybackControls = true
        controller.delegate = context.coordinator
        return controller
    }
    func updateUIViewController(_ controller: AVPlayerViewController, context: Context) {
        context.coordinator.exit = exit
        if controller.player !== player { controller.player = player }
        let pickerView = PlaybackEpisodePicker(episodes: episodes, selectedEpisode: selectedEpisode, presentation: context.coordinator.presentation) { [weak controller] value in
            controller?.customOverlayViewController?.dismiss(animated: true)
            selectEpisode(value)
        }
        if let picker = context.coordinator.picker {
            picker.rootView = pickerView
        } else {
            let picker = EpisodePickerHostingController(rootView: pickerView)
            picker.title = "选集"
            picker.preferredContentSize = CGSize(width: 1920, height: 360)
            picker.view.backgroundColor = .clear
            context.coordinator.picker = picker
            // AVKit owns the upward remote gesture and the overlay Back behavior.
            controller.customOverlayViewController = picker
        }
        if let item = player.currentItem {
            let metadata = AVMutableMetadataItem()
            metadata.identifier = .commonIdentifierTitle
            metadata.value = "\(title) · \(episodeTitle)" as NSString
            metadata.extendedLanguageTag = "zh-Hans"
            item.externalMetadata = [metadata]
        }
        // Transport controls disappear with AVKit's UI; contextual actions stay on screen.
        controller.contextualActions = []
        var navigation: [UIMenuElement] = []
        if let index = episodes.firstIndex(of: selectedEpisode) {
            if index > 0 {
                let previous = episodes[index - 1]
                navigation.append(UIAction(title: "上一集", image: UIImage(systemName: "backward.end.fill")) { _ in selectEpisode(previous) })
            }
            if index + 1 < episodes.count {
                let next = episodes[index + 1]
                navigation.append(UIAction(title: "下一集", image: UIImage(systemName: "forward.end.fill")) { _ in selectEpisode(next) })
            }
        }
        // Native menus do not expose a scroll-to-row API. Put the current episode
        // first while keeping all other episodes in their original order.
        let menuEpisodes = [selectedEpisode] + episodes.filter { $0.id != selectedEpisode.id }
        navigation.append(UIMenu(title: "选集", image: UIImage(systemName: "list.bullet"), options: .singleSelection, children: menuEpisodes.map { value in
            UIAction(title: value.id == selectedEpisode.id ? "正在播放 · \(value.title)" : value.title, state: value.id == selectedEpisode.id ? .on : .off) { _ in selectEpisode(value) }
        }))
        controller.transportBarCustomMenuItems = navigation
    }
    static func dismantleUIViewController(_ controller: AVPlayerViewController, coordinator: Coordinator) {
        controller.delegate = nil
        controller.customOverlayViewController = nil
        coordinator.picker = nil
        controller.player = nil
    }
}


@MainActor @Observable final class EpisodePickerPresentation {
    var revision = 0
}

final class EpisodePickerHostingController: UIHostingController<PlaybackEpisodePicker> {
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        // AVKit retains this controller between presentations.
        rootView.presentation.revision += 1
    }
}

/// System-presented slide-up panel; selecting an episode keeps the playback session open.
struct PlaybackEpisodePicker: View {
    let episodes: [Episode]
    let selectedEpisode: Episode
    let presentation: EpisodePickerPresentation
    let select: (Episode) -> Void
    @FocusState private var focusedID: String?
    var body: some View {
        VStack {
            Spacer()
            VStack(alignment: .leading, spacing: 24) {
                Text("选集").font(.title2).bold()
                ScrollViewReader { proxy in
                    ScrollView(.horizontal) {
                        HStack(spacing: 24) {
                            ForEach(episodes) { episode in
                                Button { select(episode) } label: {
                                    VStack(spacing: 8) {
                                        Text(episode.title).font(.system(size: 27, weight: .medium))
                                            .lineLimit(2).minimumScaleFactor(0.85)
                                        Text(episode.id == selectedEpisode.id ? "正在播放" : "播放此集")
                                            .font(.system(size: 23)).foregroundStyle(.secondary)
                                    }.frame(width: 220, height: 96)
                                }
                                .buttonStyle(.bordered)
                                .focused($focusedID, equals: episode.id)
                                .accessibilityAddTraits(episode.id == selectedEpisode.id ? .isSelected : [])
                                .id(episode.id)
                            }
                        }.padding(.vertical, 24).padding(.horizontal, 12)
                    }
                    .frame(height: 180)
                    .defaultFocus($focusedID, selectedEpisode.id)
                    .task(id: "\(selectedEpisode.id)-\(presentation.revision)") {
                        // The presentation revision changes after AVKit finishes showing the panel.
                        focusedID = nil
                        await Task.yield()
                        guard !Task.isCancelled else { return }
                        proxy.scrollTo(selectedEpisode.id, anchor: .center)
                        focusedID = selectedEpisode.id
                    }
                }
            }
            .padding(48)
            .background(.regularMaterial, in: .rect(cornerRadius: 24))
        }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
            .padding(48)
    }
}

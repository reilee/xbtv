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
                         next: nextEpisode.map { next in { switchEpisode(next) } })
                .ignoresSafeArea()
            }
            if session.isLoading {
                ProgressView("正在准备 \(episode.title)…").padding(40).background(.regularMaterial, in: .rect(cornerRadius: 20))
            }
            if let error = session.error {
                VStack(spacing: 20) {
                    MessageView(title: "播放失败", message: error) { reload += 1 }
                    Button("返回选集") { dismiss() }
                }.padding(48).background(.regularMaterial)
            } else if session.ended {
                VStack(spacing: 24) {
                    Text("本集播放结束").font(.title2)
                    if let nextEpisode { Button("播放下一集 · \(nextEpisode.title)") { switchEpisode(nextEpisode) } }
                    Button("重新播放") { resumeSeconds = 0; reload += 1 }
                    Button("返回选集") { dismiss() }
                }.padding(56).background(.regularMaterial, in: .rect(cornerRadius: 24))
            }
        }
        .task(id: "\(episode.id)-\(reload)") {
            let playingEpisode = episode
            await session.load(episode: playingEpisode, resumeSeconds: resumeSeconds, client: client) { seconds, duration in
                library.record(WatchRecord(video: request.detail.video, episode: playingEpisode,
                                           lineID: request.line.id, lineTitle: request.line.title, seconds: seconds, duration: duration))
            }
        }
        .onExitCommand { dismiss() }
        .onDisappear { session.stop() }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { session.save(); session.player.pause() }
        }
    }
    private func switchEpisode(_ next: Episode) { session.stop(); resumeSeconds = 0; episode = next }
}

/// AVKit owns Siri Remote transport, scrubbing, subtitles, audio tracks and AirPlay.
struct NativePlayer: UIViewControllerRepresentable {
    let player: AVPlayer
    let title: String
    let episodeTitle: String
    let next: (() -> Void)?
    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        controller.player = player; controller.showsPlaybackControls = true
        return controller
    }
    func updateUIViewController(_ controller: AVPlayerViewController, context: Context) {
        if controller.player !== player { controller.player = player }
        if let item = player.currentItem {
            let metadata = AVMutableMetadataItem()
            metadata.identifier = .commonIdentifierTitle
            metadata.value = "\(title) · \(episodeTitle)" as NSString
            metadata.extendedLanguageTag = "zh-Hans"
            item.externalMetadata = [metadata]
        }
        controller.contextualActions = next.map { action in
            [UIAction(title: "下一集", image: UIImage(systemName: "forward.end.fill")) { _ in action() }]
        } ?? []
    }
    static func dismantleUIViewController(_ controller: AVPlayerViewController, coordinator: ()) {
        controller.player = nil
    }
}

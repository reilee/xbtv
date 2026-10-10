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
        var picker: PlaybackEpisodePickerController?
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
        let picker: PlaybackEpisodePickerController
        if let existing = context.coordinator.picker {
            picker = existing
        } else {
            picker = PlaybackEpisodePickerController()
            context.coordinator.picker = picker
            controller.customOverlayViewController = picker
        }
        picker.configure(episodes: episodes, selectedID: selectedEpisode.id) { [weak controller] value in
            controller?.customOverlayViewController?.dismiss(animated: true)
            selectEpisode(value)
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
        // A native action sheet exposes preferredAction; UIMenu has no public
        // initial-focus API. Keep the episode order and focus the checked action.
        navigation.append(UIAction(title: "选集", image: UIImage(systemName: "list.bullet")) { [weak controller] _ in
            guard let controller else { return }
            let sheet = UIAlertController(title: "选集", message: nil, preferredStyle: .actionSheet)
            for value in episodes {
                let current = value.id == selectedEpisode.id
                let action = UIAlertAction(title: current ? "✓  \(value.title)" : value.title, style: .default) { _ in
                    selectEpisode(value)
                }
                sheet.addAction(action)
                if current { sheet.preferredAction = action }
            }
            sheet.addAction(UIAlertAction(title: "取消", style: .cancel))
            controller.present(sheet, animated: true)
        })
        controller.transportBarCustomMenuItems = navigation
    }
    static func dismantleUIViewController(_ controller: AVPlayerViewController, coordinator: Coordinator) {
        controller.delegate = nil
        controller.customOverlayViewController = nil
        coordinator.picker = nil
        controller.player = nil
    }
}


/// AVKit keeps this panel alive. Align and request native focus on every appearance.
final class PlaybackEpisodePickerController: UIViewController, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout {
    private var episodes: [Episode] = []
    private var selectedID = ""
    private var select: ((Episode) -> Void)?
    private var needsAlignment = true
    private let layout = UICollectionViewFlowLayout()
    private lazy var collection = UICollectionView(frame: .zero, collectionViewLayout: layout)

    private var currentIndex: IndexPath? {
        episodes.firstIndex { $0.id == selectedID }.map { IndexPath(item: $0, section: 0) }
    }
    func configure(episodes: [Episode], selectedID: String, select: @escaping (Episode) -> Void) {
        let changed = self.episodes != episodes || self.selectedID != selectedID
        self.episodes = episodes; self.selectedID = selectedID; self.select = select
        if changed {
            needsAlignment = true
            if isViewLoaded { collection.reloadData(); view.setNeedsLayout() }
        }
    }
    override func viewDidLoad() {
        super.viewDidLoad()
        title = "选集"
        preferredContentSize = CGSize(width: 1920, height: 360)
        view.backgroundColor = .clear
        let panel = UIVisualEffectView(effect: UIBlurEffect(style: .regular))
        panel.layer.cornerRadius = 24; panel.clipsToBounds = true
        let heading = UILabel()
        heading.text = "选集"; heading.font = .preferredFont(forTextStyle: .title2)
        layout.scrollDirection = .horizontal
        layout.itemSize = CGSize(width: 220, height: 112)
        layout.minimumLineSpacing = 24
        collection.backgroundColor = .clear
        collection.dataSource = self; collection.delegate = self
        collection.remembersLastFocusedIndexPath = false
        collection.showsHorizontalScrollIndicator = false
        collection.register(PlaybackEpisodeCell.self, forCellWithReuseIdentifier: "episode")
        view.addSubview(panel); panel.contentView.addSubview(heading); panel.contentView.addSubview(collection)
        for child in [panel, heading, collection] { child.translatesAutoresizingMaskIntoConstraints = false }
        NSLayoutConstraint.activate([
            panel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 48),
            panel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -48),
            panel.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -48),
            panel.heightAnchor.constraint(equalToConstant: 280),
            heading.leadingAnchor.constraint(equalTo: panel.contentView.leadingAnchor, constant: 48),
            heading.topAnchor.constraint(equalTo: panel.contentView.topAnchor, constant: 24),
            collection.leadingAnchor.constraint(equalTo: panel.contentView.leadingAnchor),
            collection.trailingAnchor.constraint(equalTo: panel.contentView.trailingAnchor),
            collection.topAnchor.constraint(equalTo: heading.bottomAnchor, constant: 8),
            collection.bottomAnchor.constraint(equalTo: panel.contentView.bottomAnchor, constant: -16)
        ])
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        needsAlignment = true
        collection.reloadData()
        view.setNeedsLayout()
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let inset = max(24, (collection.bounds.width - layout.itemSize.width) / 2)
        if layout.sectionInset.left != inset {
            layout.sectionInset = UIEdgeInsets(top: 24, left: inset, bottom: 24, right: inset)
            layout.invalidateLayout()
        }
        if needsAlignment, collection.bounds.width > 0, let currentIndex {
            collection.layoutIfNeeded()
            collection.scrollToItem(at: currentIndex, at: .centeredHorizontally, animated: false)
            needsAlignment = false
        }
    }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        view.layoutIfNeeded()
        if let currentIndex {
            collection.scrollToItem(at: currentIndex, at: .centeredHorizontally, animated: false)
            collection.layoutIfNeeded()
            collection.selectItem(at: currentIndex, animated: false, scrollPosition: [])
            if let cell = collection.cellForItem(at: currentIndex), let focusSystem = UIFocusSystem.focusSystem(for: cell) {
                focusSystem.requestFocusUpdate(to: cell)
                focusSystem.updateFocusIfNeeded()
            }
        }
        setNeedsFocusUpdate(); updateFocusIfNeeded()
    }
    override var preferredFocusEnvironments: [UIFocusEnvironment] {
        if let currentIndex, let cell = collection.cellForItem(at: currentIndex) { return [cell] }
        return [collection]
    }
    func indexPathForPreferredFocusedView(in collectionView: UICollectionView) -> IndexPath? { currentIndex }
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int { episodes.count }
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "episode", for: indexPath) as! PlaybackEpisodeCell
        let episode = episodes[indexPath.item]
        cell.configure(title: episode.title, current: episode.id == selectedID)
        return cell
    }
    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        select?(episodes[indexPath.item])
    }
}

final class PlaybackEpisodeCell: UICollectionViewCell {
    private let titleLabel = UILabel()
    private let statusLabel = UILabel()
    private var current = false
    override var canBecomeFocused: Bool { true }
    override init(frame: CGRect) {
        super.init(frame: frame)
        contentView.layer.cornerRadius = 16
        titleLabel.font = .systemFont(ofSize: 27, weight: .medium)
        titleLabel.numberOfLines = 2; titleLabel.textAlignment = .center
        titleLabel.adjustsFontSizeToFitWidth = true; titleLabel.minimumScaleFactor = 0.85
        statusLabel.font = .systemFont(ofSize: 23); statusLabel.textAlignment = .center
        let stack = UIStackView(arrangedSubviews: [titleLabel, statusLabel])
        stack.axis = .vertical; stack.spacing = 8
        contentView.addSubview(stack); stack.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 12),
            stack.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -12),
            stack.centerYAnchor.constraint(equalTo: contentView.centerYAnchor)
        ])
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func configure(title: String, current: Bool) {
        titleLabel.text = title; self.current = current
        statusLabel.text = current ? "✓ 正在播放" : "播放此集"
        accessibilityLabel = title; accessibilityValue = current ? "正在播放" : nil
        accessibilityTraits = current ? [.button, .selected] : .button
        updateAppearance()
    }
    override func didUpdateFocus(in context: UIFocusUpdateContext, with coordinator: UIFocusAnimationCoordinator) {
        super.didUpdateFocus(in: context, with: coordinator)
        coordinator.addCoordinatedAnimations({ self.updateAppearance() }, completion: nil)
    }
    private func updateAppearance() {
        contentView.backgroundColor = isFocused ? .white : (current ? UIColor.systemBlue.withAlphaComponent(0.4) : UIColor.white.withAlphaComponent(0.12))
        contentView.layer.borderWidth = current ? 3 : 0
        contentView.layer.borderColor = UIColor.systemBlue.cgColor
        titleLabel.textColor = isFocused ? .black : .white
        statusLabel.textColor = isFocused ? .darkGray : .lightGray
        transform = isFocused ? CGAffineTransform(scaleX: 1.06, y: 1.06) : .identity
    }
}

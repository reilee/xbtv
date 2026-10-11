import SwiftUI
import XBTVCatalog

struct PlaybackRequest: Identifiable {
    let id = UUID()
    let detail: VideoDetail
    let line: PlayLine
    let episode: Episode
    let resumeSeconds: Double
}

struct DetailView: View {
    let video: Video
    let client: CatalogClient
    @Environment(LibraryStore.self) private var library
    @State private var detail: VideoDetail?
    @State private var selectedLineID: String?
    @State private var error: String?
    @State private var reload = 0
    @State private var playback: PlaybackRequest?
    @FocusState private var focusedAction: String?
    @FocusState private var focusedEpisode: String?
    @Namespace private var detailFocus
    private var selectedLine: PlayLine? {
        detail?.lines.first { $0.id == selectedLineID } ?? detail?.lines.first
    }
    private var resumable: (PlayLine, Episode, WatchRecord)? {
        guard let record = library.record(for: video), let detail,
              let line = detail.lines.first(where: { $0.id == record.lineID }),
              let episode = line.episodes.first(where: { $0.pageURL == record.episode.pageURL }) else { return nil }
        return (line, episode, record)
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 42) {
                if let detail {
                    HStack(alignment: .top, spacing: 56) {
                        Poster(url: detail.video.coverURL ?? video.coverURL, width: 270)
                        VStack(alignment: .leading, spacing: 24) {
                            Text(detail.video.title).font(.largeTitle).bold()
                            Text([detail.video.year, detail.area].filter { !$0.isEmpty }.joined(separator: " · "))
                                .foregroundStyle(.secondary)
                            if !detail.director.isEmpty { Text("导演：\(detail.director)").font(.callout).foregroundStyle(.secondary) }
                            if !detail.actors.isEmpty { Text("主演：\(detail.actors)").font(.callout).foregroundStyle(.secondary).lineLimit(2) }
                            if !detail.summary.isEmpty {
                                Text(detail.summary).font(.body).foregroundStyle(.secondary)
                            }
                            HStack(spacing: 24) {
                                if let (line, episode, record) = resumable {
                                    Button { play(detail, line: line, episode: episode, seconds: record.resumeSeconds) } label: {
                                        Label("继续观看 · \(episode.title)", systemImage: "play.fill")
                                    }.buttonStyle(.borderedProminent)
                                        .focused($focusedAction, equals: "resume")
                                        .prefersDefaultFocus(true, in: detailFocus)
                                }
                                if let line = selectedLine, let episode = line.episodes.first {
                                    Button { play(detail, line: line, episode: episode, seconds: 0) } label: {
                                        Label(resumable == nil ? "播放" : "从头播放", systemImage: "play.fill")
                                    }.buttonStyle(.bordered)
                                        .focused($focusedAction, equals: "play")
                                        .prefersDefaultFocus(resumable == nil, in: detailFocus)
                                }
                            }.focusSection()
                        }.frame(maxWidth: .infinity, alignment: .leading)
                    }
                    if detail.lines.isEmpty {
                        MessageView(title: "暂无可播放剧集", message: "请稍后重试。", symbol: "film") { reload += 1 }
                    } else {
                        Text("播放线路").font(.title2).bold()
                        HStack(spacing: 24) {
                            ForEach(detail.lines) { line in
                                Button(line.title) { selectedLineID = line.id }
                                    .buttonStyle(.bordered)
                                    .tint(selectedLine?.id == line.id ? .white : .gray)
                                    .accessibilityAddTraits(selectedLine?.id == line.id ? .isSelected : [])
                            }
                        }.focusSection()
                        if let line = selectedLine {
                            Text("剧集 · \(line.episodes.count) 集").font(.title2).bold()
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 220), spacing: 24)], spacing: 30) {
                                ForEach(line.episodes) { episode in
                                    Button { play(detail, line: line, episode: episode, seconds: 0) } label: {
                                        Text(episode.title).font(.system(size: 27, weight: .medium))
                                            .lineLimit(2).minimumScaleFactor(0.85)
                                            .frame(maxWidth: .infinity, minHeight: 64)
                                            .padding(.horizontal, 20).padding(.vertical, 12)
                                    }
                                        .frame(maxWidth: .infinity).buttonStyle(.bordered)
                                        .focused($focusedEpisode, equals: episode.id)
                                        .accessibilityHint("播放\(line.title)，\(episode.title)")
                                }
                            }.focusSection()
                        }
                    }
                } else if let error {
                    MessageView(title: "详情加载失败", message: error) { reload += 1 }
                } else {
                    ProgressView("正在加载详情…").frame(maxWidth: .infinity, minHeight: 500)
                }
            }.padding(64).focusScope(detailFocus)
        }
        .task(id: reload) { await load() }
        .fullScreenCover(item: $playback, onDismiss: restoreEpisodeFocus) { request in
            PlaybackView(request: request, client: client)
        }
    }
    @MainActor private func load() async {
        error = nil
        do {
            let result = try await client.detail(video); try Task.checkCancellation()
            detail = result
            selectedLineID = result.lines.first { $0.id == library.record(for: video)?.lineID }?.id ?? result.lines.first?.id
        } catch {
            guard !Task.isCancelled else { return }; self.error = error.localizedDescription
        }
    }
    private func play(_ detail: VideoDetail, line: PlayLine, episode: Episode, seconds: Double) {
        selectedLineID = line.id
        focusedEpisode = episode.id
        playback = PlaybackRequest(detail: detail, line: line, episode: episode, resumeSeconds: seconds)
    }
    private func restoreEpisodeFocus() {
        guard let episodeID = playback?.episode.id else { return }
        DispatchQueue.main.async {
            focusedEpisode = episodeID
        }
    }
}

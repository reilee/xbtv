import SwiftUI
import XBTVCatalog

struct BrowseView: View {
    let client: CatalogClient
    @State private var category = Category.all[0]
    @State private var videos: [Video] = []
    @State private var nextURL: URL?
    @State private var isLoading = false
    @State private var error: String?
    @State private var reload = 0
    @State private var pageTask: Task<Void, Never>?
    @State private var loadedRequest: String?
    @FocusState private var focusedControl: String?
    @State private var appendedVideoID: String?
    private var categoryFocusID: String { "category-\(category.id)" }
    var body: some View {
        ScrollViewReader { proxy in
        ScrollView {
            VStack(alignment: .leading, spacing: 36) {
                HStack(alignment: .firstTextBaseline) {
                    Text("小宝影院").font(.largeTitle).bold()
                    Spacer()
                    Text("发现你的下一部好戏").font(.headline).foregroundStyle(.secondary)
                }
                HStack(spacing: 22) {
                    ForEach(Category.all) { value in
                        Button(value.title) { category = value }
                            .buttonStyle(.bordered)
                            .focused($focusedControl, equals: "category-\(value.id)")
                            .id("category-\(value.id)")
                            .tint(category == value ? .white : .gray)
                            .accessibilityAddTraits(category == value ? .isSelected : [])
                    }
                }.focusSection()
                Text(category.title).font(.title2).bold()
                if isLoading && videos.isEmpty {
                    ProgressView("正在加载影片…").frame(maxWidth: .infinity, minHeight: 360)
                }
                VideoGrid(videos: videos, client: client, focus: $focusedControl)
                    .onExitCommand { focusedControl = categoryFocusID }
                if let error {
                    MessageView(title: "加载失败", message: error) {
                        if videos.isEmpty { reload += 1 } else { loadMore() }
                    }
                } else if nextURL != nil {
                    Button(isLoading ? "正在加载…" : "加载更多") { loadMore() }
                        .frame(maxWidth: .infinity)
                        .focused($focusedControl, equals: "load-more")
                        .onExitCommand { focusedControl = categoryFocusID }
                }
            }.padding(.horizontal, 64).padding(.vertical, 48)
        }
        .onChange(of: appendedVideoID) { _, id in
            guard let id else { return }
            proxy.scrollTo(id, anchor: .center)
            Task { @MainActor in
                await Task.yield()
                guard appendedVideoID == id else { return }
                focusedControl = id
            }
        }
        .onChange(of: focusedControl) { _, id in
            if id == categoryFocusID { proxy.scrollTo(categoryFocusID, anchor: .top) }
        }
        }
        .navigationDestination(for: Video.self) { video in DetailView(video: video, client: client) }
        .task(id: "\(category.id)-\(reload)") { await loadFirst() }
        .onDisappear { pageTask?.cancel(); isLoading = false }
    }
    @MainActor private func loadFirst() async {
        let requestID = "\(category.id)-\(reload)"
        if loadedRequest == requestID, !videos.isEmpty { return }
        pageTask?.cancel(); appendedVideoID = nil; videos = []; nextURL = nil; error = nil; isLoading = true
        do {
            let result = try await client.category(category)
            try Task.checkCancellation()
            videos = result.videos; nextURL = result.nextURL; isLoading = false; loadedRequest = requestID
        } catch {
            guard !Task.isCancelled else { return }
            self.error = error.localizedDescription; isLoading = false
        }
    }
    @MainActor private func loadMore() {
        guard let nextURL, !isLoading else { return }
        error = nil; isLoading = true
        let requestedCategory = category
        pageTask = Task {
            do {
                let result = try await client.category(requestedCategory, nextURL: nextURL)
                try Task.checkCancellation()
                guard category == requestedCategory else { return }
                let existing = Set(videos.map(\.id))
                let added = result.videos.filter { !existing.contains($0.id) }
                videos.append(contentsOf: added)
                // Move to the start of the new page, not the relocated pagination button.
                if focusedControl == "load-more", let first = added.first { appendedVideoID = first.id }
                self.nextURL = result.nextURL; isLoading = false
            } catch {
                guard !Task.isCancelled else { return }
                self.error = error.localizedDescription; isLoading = false
            }
        }
    }
}

struct SearchView: View {
    let client: CatalogClient
    @State private var query = ""
    @State private var submittedQuery = ""
    @State private var videos: [Video] = []
    @State private var isLoading = false
    @State private var error: String?
    @State private var reload = 0
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 36) {
                if submittedQuery.isEmpty {
                    MessageView(title: "找一部想看的影片", message: "输入影片名称或关键词，完成后按搜索。中文输入可使用 iPhone 的 Apple TV 键盘。", symbol: "magnifyingglass")
                } else if isLoading {
                    ProgressView("正在搜索…").frame(maxWidth: .infinity, minHeight: 360)
                } else if let error {
                    MessageView(title: "搜索失败", message: error) { reload += 1 }
                } else if videos.isEmpty {
                    MessageView(title: "没有找到相关影片", message: "试试其他名称或更短的关键词。", symbol: "magnifyingglass")
                } else {
                    Text("搜索结果").font(.title2).bold()
                    VideoGrid(videos: videos, client: client)
                }
            }.padding(64)
        }
        .searchable(text: $query, prompt: "影片名称")
        .navigationDestination(for: Video.self) { video in DetailView(video: video, client: client) }
        .onSubmit(of: .search) {
            let value = query.trimmingCharacters(in: .whitespacesAndNewlines)
            if submittedQuery == value { reload += 1 } else { submittedQuery = value }
        }
        .task(id: "\(submittedQuery)-\(reload)") { await search() }
    }
    @MainActor private func search() async {
        let keyword = submittedQuery
        guard !keyword.isEmpty else { videos = []; error = nil; isLoading = false; return }
        error = nil
        isLoading = true
        do {
            let result = try await client.search(keyword)
            try Task.checkCancellation(); videos = result.videos; isLoading = false
        } catch {
            guard !Task.isCancelled else { return }
            self.error = error.localizedDescription; isLoading = false
        }
    }
}

struct HistoryView: View {
    let client: CatalogClient
    @Environment(LibraryStore.self) private var library
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 36) {
                Text("最近观看").font(.largeTitle).bold()
                if let error = library.storageError {
                    MessageView(title: "观看记录不可用", message: error)
                } else if library.history.records.isEmpty {
                    MessageView(title: "从一部好戏开始", message: "播放过的影片会出现在这里，并记住集数与进度。", symbol: "clock")
                } else {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 290), spacing: 36)], spacing: 44) {
                        ForEach(library.history.records) { record in
                            NavigationLink(value: record.video) {
                                VideoCard(video: record.video, subtitle: "\(record.episode.title) · \(Int(record.resumeSeconds / 60)) 分钟")
                            }
                            .buttonStyle(.card)
                            .contextMenu { Button("移除观看记录", role: .destructive) { library.remove(record.id) } }
                        }
                    }.focusSection()
                }
            }.padding(64)
        }
        .navigationDestination(for: Video.self) { video in DetailView(video: video, client: client) }
    }
}

struct SettingsView: View {
    @Environment(LibraryStore.self) private var library
    @State private var confirmClear = false
    var body: some View {
        Form {
            Section("观看记录") {
                Text("保存最近 12 部影片的集数与播放进度，仅存储在此 Apple TV 上。")
                Button("清除观看记录", role: .destructive) { confirmClear = true }
            }
            Section("关于小宝影院") {
                Text("Apple TV 版 · 1.0")
                Text("内容来自 www.xiaobaotv.com，网站的可用性与播放线路可能变化。")
                Text("使用遥控器方向键浏览，按选择键打开，按返回键回到上一页。播放中使用系统控件暂停、快进和选取字幕。")
            }
        }
        .navigationTitle("设置")
        .confirmationDialog("清除所有观看记录？", isPresented: $confirmClear, titleVisibility: .visible) {
            Button("清除", role: .destructive) { library.clear() }
            Button("取消", role: .cancel) { }
        } message: { Text("此操作会删除此设备保存的集数与进度。") }
    }
}

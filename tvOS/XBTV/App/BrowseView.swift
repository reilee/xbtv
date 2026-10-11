import SwiftUI
import Observation
import XBTVCatalog

struct BrowseView: View {
    let client: CatalogClient
    private let category: XBTVCatalog.Category

    init(client: CatalogClient, initialCategory: XBTVCatalog.Category = XBTVCatalog.Category.all[0]) {
        self.client = client
        category = initialCategory
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 42) {
                ForEach(CatalogRailKind.allCases) { kind in
                    CatalogRailView(client: client, category: category, kind: kind)
                }
            }
            .padding(.horizontal, 64)
            .padding(.vertical, 48)
        }
        .navigationDestination(for: Video.self) { video in
            DetailView(video: video, client: client)
        }
    }
}

private enum CatalogRailKind: String, CaseIterable, Identifiable {
    case latest
    case monthlyPopular
    case allTime

    var id: String { rawValue }
    var title: String {
        switch self {
        case .latest: "最新"
        case .monthlyPopular: "本月热门"
        case .allTime: "总榜单"
        }
    }

    func path(for category: XBTVCatalog.Category) -> String {
        switch self {
        case .latest: category.path
        case .monthlyPopular, .allTime: "/vod/show/by/hits/id/\(category.id).html"
        }
    }
}

private struct CatalogRailView: View {
    let client: CatalogClient
    let category: XBTVCatalog.Category
    let kind: CatalogRailKind
    @State private var videos: [Video] = []
    @State private var nextURL: URL?
    @State private var isLoading = false
    @State private var error: String?
    @State private var pageTask: Task<Void, Never>?

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text(kind.title).font(.title2.weight(.semibold))
            ScrollView(.horizontal) {
                LazyHStack(alignment: .top, spacing: 34) {
                    ForEach(Array(videos.enumerated()), id: \.element.id) { index, video in
                        NavigationLink(value: video) {
                            VideoCard(video: video, rank: kind == .allTime ? index + 1 : nil)
                        }
                        .buttonStyle(.card)
                        .accessibilityHint("查看详情和剧集")
                        .onAppear {
                            if index >= max(videos.count - 5, 0) { loadMore() }
                        }
                    }
                    if videos.isEmpty && isLoading {
                        ProgressView().frame(width: 250, height: 375)
                    } else if videos.isEmpty, let error {
                        VStack(spacing: 18) {
                            Text(error).font(.callout).foregroundStyle(.secondary).lineLimit(2)
                            Button("重试") { Task { await loadFirst() } }.buttonStyle(.bordered)
                        }
                        .frame(width: 300, height: 375)
                    } else if isLoading {
                        ProgressView().frame(width: 250, height: 375)
                    } else if let error {
                        Button("加载失败，重试") { loadMore() }
                            .buttonStyle(.bordered)
                            .frame(width: 250, height: 375)
                            .accessibilityHint(error)
                    }
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 22)
            }
            .focusSection()
        }
        .task { await loadFirst() }
        .onDisappear { pageTask?.cancel() }
    }

    @MainActor private func loadFirst() async {
        guard videos.isEmpty, !isLoading else { return }
        isLoading = true; error = nil
        do {
            let page = try await client.category(category, filterPath: kind.path(for: category), allowsEmpty: true)
            try Task.checkCancellation()
            videos = page.videos; nextURL = page.nextURL; isLoading = false
        } catch {
            guard !Task.isCancelled else { return }
            self.error = error.localizedDescription; isLoading = false
        }
    }

    @MainActor private func loadMore() {
        guard let nextURL, !isLoading else { return }
        isLoading = true; error = nil
        pageTask?.cancel()
        pageTask = Task {
            do {
                let page = try await client.category(category, nextURL: nextURL, allowsEmpty: true)
                try Task.checkCancellation()
                let existing = Set(videos.map(\.id))
                videos.append(contentsOf: page.videos.filter { !existing.contains($0.id) })
                self.nextURL = page.nextURL; isLoading = false
            } catch {
                guard !Task.isCancelled else { return }
                self.error = error.localizedDescription; isLoading = false
            }
        }
    }
}

/// Keep UIKit's remote keyboard session independent of SwiftUI result rendering.
struct SearchView: UIViewControllerRepresentable {
    let client: CatalogClient
    @Environment(LibraryStore.self) private var library

    @MainActor @Observable final class Submission {
        enum Mode: Equatable {
            case keyword
            case filters(category: XBTVCatalog.Category, path: String)
        }
        var query = ""
        var revision = 0
        var mode: Mode = .keyword
        func submit(_ text: String) {
            query = text.trimmingCharacters(in: .whitespacesAndNewlines)
            mode = .keyword
            revision += 1
        }
        func applyFilters(category: XBTVCatalog.Category, path: String) {
            mode = .filters(category: category, path: path)
            revision += 1
        }
    }
    final class Coordinator: NSObject, UISearchBarDelegate, UISearchResultsUpdating {
        let submission = Submission()
        weak var searchController: UISearchController?
        var pendingSearch: Task<Void, Never>?

        func submitCurrent(force: Bool = true) {
            pendingSearch?.cancel()
            guard let bar = searchController?.searchBar else { return }
            let keyword = (bar.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            if force || keyword != submission.query { submission.submit(keyword) }
        }
        func searchBarSearchButtonClicked(_ searchBar: UISearchBar) { submitCurrent() }
        func searchBarTextDidEndEditing(_ searchBar: UISearchBar) { submitCurrent(force: false) }
        func updateSearchResults(for searchController: UISearchController) {
            // Remote keyboards don't always send searchButtonClicked on tvOS.
            // Wait for text changes to settle without writing back into UIKit or rebuilding the input.
            pendingSearch?.cancel()
            pendingSearch = Task { @MainActor [weak self] in
                do { try await Task.sleep(for: .milliseconds(800)) } catch { return }
                guard !Task.isCancelled else { return }
                self?.submitCurrent(force: false)
            }
        }
    }
    func makeCoordinator() -> Coordinator { Coordinator() }
    func makeUIViewController(context: Context) -> UISearchContainerViewController {
        let results = UIHostingController(rootView:
            SearchResultsView(client: client, submission: context.coordinator.submission, submit: { [weak coordinator = context.coordinator] in coordinator?.submitCurrent() })
                .environment(library).preferredColorScheme(.dark).tint(.white))
        let search = UISearchController(searchResultsController: results)
        context.coordinator.searchController = search
        search.searchBar.placeholder = "影片名称"
        search.searchBar.searchBarStyle = .minimal
        search.searchBar.delegate = context.coordinator
        search.searchResultsUpdater = context.coordinator
        let container = UISearchContainerViewController(searchController: search)
        // The tvOS keyboard places its language-switch hint at the trailing edge.
        // Reserve extra room so the localized hint stays inside the visible safe area.
        container.additionalSafeAreaInsets = UIEdgeInsets(top: 0, left: 0, bottom: 0, right: 72)
        return container
    }
    func updateUIViewController(_ controller: UISearchContainerViewController, context: Context) {
        // The native controller and its text input remain stable for the whole tab lifetime.
    }
    static func dismantleUIViewController(_ controller: UISearchContainerViewController, coordinator: Coordinator) {
        coordinator.pendingSearch?.cancel()
        controller.searchController.searchBar.delegate = nil
        controller.searchController.searchResultsUpdater = nil
    }
}

private struct SearchResultsView: View {
    let client: CatalogClient
    let submission: SearchView.Submission
    let submit: () -> Void
    @State private var filters = SearchFilterSelection()
    @State private var videos: [Video] = []
    @State private var nextURL: URL?
    @State private var isLoading = false
    @State private var error: String?
    @FocusState private var searchButtonFocused: Bool
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 36) {
                    SearchFilterPanel(filters: $filters, searchButtonFocused: $searchButtonFocused)
                    Button("搜索") {
                        if submission.query.isEmpty, let category = filters.category, let path = filters.path {
                            submission.applyFilters(category: category, path: path)
                        } else {
                            submit()
                        }
                    }
                        .focused($searchButtonFocused)
                        .buttonStyle(.bordered)
                        .accessibilityHint("搜索当前输入的影片名称")
                    if submission.query.isEmpty {
                        if case .filters = submission.mode {
                            if isLoading {
                                ProgressView("正在筛选…").frame(maxWidth: .infinity, minHeight: 360)
                            } else if let error {
                                MessageView(title: "筛选失败", message: error) { submission.revision += 1 }
                            } else if videos.isEmpty {
                                MessageView(title: "没有符合条件的影片", message: "调整筛选条件后重试。", symbol: "line.3.horizontal.decrease.circle")
                            } else {
                                Text("筛选结果").font(.title2).bold()
                                VideoGrid(videos: videos, client: client)
                            }
                        } else {
                            MessageView(title: "找一部想看的影片", message: "输入影片名称或关键词，停止输入后自动搜索；也可向下选择大类和筛选条件。中文输入可使用 iPhone 的 Apple TV 键盘。", symbol: "magnifyingglass")
                        }
                    } else if isLoading {
                        ProgressView(submission.mode.isFilterMode ? "正在筛选…" : "正在搜索…")
                            .frame(maxWidth: .infinity, minHeight: 360)
                    } else if let error {
                        MessageView(title: "搜索失败", message: error) { submission.revision += 1 }
                    } else if videos.isEmpty {
                        MessageView(title: "没有找到相关影片", message: "试试其他名称或更短的关键词。", symbol: "magnifyingglass")
                    } else {
                        Text("搜索结果").font(.title2).bold()
                        VideoGrid(videos: videos, client: client)
                    }
                    if nextURL != nil {
                        Button(isLoading ? "正在加载…" : "加载更多", action: loadMore)
                            .buttonStyle(.bordered)
                            .disabled(isLoading)
                    }
                }.padding(64)
            }
            .navigationDestination(for: Video.self) { video in DetailView(video: video, client: client) }
            .task(id: "\(submission.query)-\(submission.revision)-\(modeKey)") { await search() }
        }
    }
    private var modeKey: String {
        switch submission.mode {
        case .keyword: "keyword"
        case .filters(let category, let path): "\(category.id):\(path)"
        }
    }
    @MainActor private func search() async {
        let keyword = submission.query
        videos = []; nextURL = nil
        if case .filters(let category, let path) = submission.mode {
            error = nil; isLoading = true
            do {
                let result = try await client.category(category, filterPath: path, allowsEmpty: true)
                try Task.checkCancellation()
                videos = result.videos; nextURL = result.nextURL; isLoading = false
            } catch {
                guard !Task.isCancelled else { return }
                self.error = error.localizedDescription; isLoading = false
            }
            return
        }
        guard !keyword.isEmpty else { videos = []; error = nil; isLoading = false; return }
        error = nil; isLoading = true
        do {
            let result = try await client.search(keyword)
            try Task.checkCancellation()
            videos = result.videos; nextURL = result.nextURL; isLoading = false
        } catch {
            guard !Task.isCancelled else { return }
            self.error = error.localizedDescription; isLoading = false
        }
    }
    @MainActor private func loadMore() {
        guard let nextURL, !isLoading else { return }
        isLoading = true; error = nil
        let category: XBTVCatalog.Category
        if case .filters(let selected, _) = submission.mode { category = selected }
        else { category = XBTVCatalog.Category.all[0] }
        Task {
            do {
                let page = try await client.category(category, nextURL: nextURL)
                try Task.checkCancellation()
                let existing = Set(videos.map(\.id))
                videos.append(contentsOf: page.videos.filter { !existing.contains($0.id) })
                self.nextURL = page.nextURL; isLoading = false
            } catch {
                guard !Task.isCancelled else { return }
                self.error = error.localizedDescription; isLoading = false
            }
        }
    }
}

private extension SearchView.Submission.Mode {
    var isFilterMode: Bool {
        if case .filters = self { return true }
        return false
    }
}

private struct SearchFilterOption: Identifiable, Hashable {
    let title: String
    let value: String
    var id: String { value }
}

private struct SearchFilterSelection {
    var category: XBTVCatalog.Category?
    var typeID = ""
    var plot = ""
    var area = ""
    var year = ""
    var language = ""
    var sort = "time"

    mutating func select(_ value: XBTVCatalog.Category) {
        category = value
        typeID = value.id
        plot = ""; area = ""; year = ""; language = ""; sort = "time"
    }

    var path: String? {
        guard let category else { return nil }
        var parts = ["/vod/show"]
        if !plot.isEmpty { parts.append("class/\(encode(plot))") }
        if !area.isEmpty { parts.append("area/\(encode(area))") }
        if sort != "time" { parts.append("by/\(sort)") }
        parts.append("id/\(typeID.isEmpty ? category.id : typeID)")
        if !year.isEmpty { parts.append("year/\(year)") }
        if !language.isEmpty { parts.append("lang/\(encode(language))") }
        parts[parts.count - 1] += ".html"
        return parts.joined(separator: "/")
    }

    private func encode(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? value
    }

    var selectedCount: Int {
        (typeID.isEmpty || typeID == category?.id ? 0 : 1) +
        (plot.isEmpty ? 0 : 1) + (area.isEmpty ? 0 : 1) +
        (year.isEmpty ? 0 : 1) + (language.isEmpty ? 0 : 1) + (sort == "time" ? 0 : 1)
    }

    var types: [SearchFilterOption] {
        guard let category else { return [] }
        let values: [(String, String)]
        switch category.id {
        case "1": values = [("全部", "1"), ("动作片", "101"), ("喜剧片", "102"), ("爱情片", "103"), ("科幻片", "104"), ("剧情片", "105"), ("悬疑片", "106"), ("惊悚片", "107"), ("恐怖片", "108"), ("犯罪片", "109"), ("冒险片", "110"), ("奇幻片", "111"), ("灾难片", "112"), ("战争片", "113"), ("动画电影片", "114"), ("歌舞片", "115"), ("网络电影片", "116"), ("同性片", "117"), ("经典片", "118"), ("其它片", "119"), ("记录片", "120")]
        case "2": values = [("全部", "2"), ("国产剧", "201"), ("港台剧", "202"), ("台湾剧", "203"), ("日韩剧", "204"), ("日本剧", "205"), ("欧美剧", "206"), ("泰国剧", "207"), ("海外剧", "208"), ("新马泰剧", "209"), ("其他剧", "210")]
        case "3": values = [("全部", "3"), ("国产动漫", "301"), ("日韩动漫", "302"), ("港台动漫", "303"), ("欧美动漫", "304"), ("其它动漫", "305")]
        case "4": values = [("全部", "4"), ("大陆综艺", "401"), ("日韩综艺", "402"), ("港台综艺", "403"), ("欧美综艺", "404"), ("新马泰综艺", "405"), ("其它综艺", "406")]
        default: values = [("全部", category.id)]
        }
        return values.map { SearchFilterOption(title: $0.0, value: $0.1) }
    }

    var plots: [SearchFilterOption] {
        let values: [String]
        switch category?.id {
        case "1": values = ["爱情", "古装", "动作", "伦理", "悬疑", "犯罪", "谍战", "历史", "喜剧", "奇幻", "科幻", "家庭", "青春", "剧情", "冒险", "纪录", "动画", "人物", "文化", "其他"]
        case "2": values = ["爱情", "古装", "悬疑", "都市", "喜剧", "战争", "剧情", "青春", "历史", "网剧", "奇幻", "冒险", "励志", "犯罪", "商战", "恐怖", "穿越", "农村", "人物", "商业", "生活", "其他"]
        case "3": values = ["冒险", "战斗", "搞笑", "经典", "科幻", "玄幻", "魔幻", "武侠", "恋爱", "推理", "日常", "校园", "悬疑", "真人", "历史", "竞技", "其他"]
        case "4": values = ["游戏", "脱口秀", "音乐", "情感", "生活", "职场", "真人秀", "搞笑", "公益", "艺术", "访谈", "益智", "体育", "少儿", "时尚", "人物", "其他"]
        case "11": values = ["古装", "复仇", "强者", "悬疑", "甜宠", "神豪", "穿越", "虐恋", "逆袭", "重生", "萌宝"]
        default: values = []
        }
        return [SearchFilterOption(title: "全部", value: "")] + values.map { SearchFilterOption(title: $0, value: $0) }
    }

    static let areas = ["中国大陆", "中国香港", "中国台湾", "欧美", "韩国", "日本", "泰国", "新加坡", "马来西亚", "印度", "英国", "法国", "加拿大", "西班牙", "俄罗斯", "其它"].map { SearchFilterOption(title: $0, value: $0) }
    static let languages = ["汉语普通话", "英语", "粤语", "韩语", "日语", "法语", "德语", "西班牙语", "意大利语", "泰语", "其它"].map { SearchFilterOption(title: $0, value: $0) }
    static let sorts = [SearchFilterOption(title: "时间", value: "time"), SearchFilterOption(title: "人气", value: "hits"), SearchFilterOption(title: "评分", value: "score")]
    var years: [SearchFilterOption] {
        [SearchFilterOption(title: "全部", value: "")] + (2000...Calendar.current.component(.year, from: .now)).reversed().map { SearchFilterOption(title: String($0), value: String($0)) }
    }
}

private struct SearchFilterPanel: View {
    @Binding var filters: SearchFilterSelection
    var searchButtonFocused: FocusState<Bool>.Binding
    private let categories = XBTVCatalog.Category.all

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 18) {
                Label("筛选", systemImage: "line.3.horizontal.decrease.circle").font(.title3.weight(.semibold))
                if filters.selectedCount > 0 { Text("已选 \(filters.selectedCount) 项").font(.callout).foregroundStyle(.secondary) }
                Spacer()
                if filters.category != nil {
                    Button("重置") { filters = SearchFilterSelection() }.buttonStyle(.bordered)
                }
            }
            filterRow("大类", options: categories.map { SearchFilterOption(title: $0.title, value: $0.id) }, value: Binding(
                get: { filters.category?.id ?? "" },
                set: { id in if let category = categories.first(where: { $0.id == id }) { filters.select(category) } }
            ))
            if filters.category != nil {
                if filters.types.count > 1 { filterRow("类型", options: filters.types, value: $filters.typeID) }
                filterRow("剧情", options: filters.plots, value: $filters.plot)
                filterRow("地区", options: [SearchFilterOption(title: "全部", value: "")] + SearchFilterSelection.areas, value: $filters.area)
                filterRow("年份", options: filters.years, value: $filters.year)
                filterRow("语言", options: [SearchFilterOption(title: "全部", value: "")] + SearchFilterSelection.languages, value: $filters.language)
                filterRow("排序", options: SearchFilterSelection.sorts, value: $filters.sort, focusSearchOnDown: searchButtonFocused)
            }
        }
        .padding(24)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24))
    }

    private func filterRow(
        _ title: String,
        options: [SearchFilterOption],
        value: Binding<String>,
        focusSearchOnDown: FocusState<Bool>.Binding? = nil
    ) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Text(title)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .frame(width: 76, alignment: .leading)
            ScrollView(.horizontal) {
                HStack(spacing: 14) {
                    ForEach(options) { option in
                        Button(option.title) { value.wrappedValue = option.value }
                            .buttonStyle(.bordered)
                            .tint(value.wrappedValue == option.value ? .white : .gray)
                            .accessibilityAddTraits(value.wrappedValue == option.value ? .isSelected : [])
                            .onMoveCommand { direction in
                                if direction == .down { focusSearchOnDown?.wrappedValue = true }
                            }
                    }
                }
                .padding(.vertical, 10)
            }
            .frame(height: 58)
            .contentMargins(.vertical, 2)
            .focusSection()
        }
        .focusSection()
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

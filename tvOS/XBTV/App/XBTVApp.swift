import SwiftUI
import Observation
import XBTVCatalog

@main struct XBTVApp: App {
    @State private var library = LibraryStore()
    private let client = CatalogClient()
    var body: some Scene {
        WindowGroup {
            RootView(client: client)
                .environment(library)
                .preferredColorScheme(.dark)
                .tint(.white)
        }
    }
}

@MainActor @Observable final class LibraryStore {
    private(set) var history: WatchHistory
    var storageError: String?
    private let defaults: UserDefaults
    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: "watchHistory.v1") {
            do { history = WatchHistory(records: try JSONDecoder().decode([WatchRecord].self, from: data)) }
            catch { history = WatchHistory(); storageError = "观看记录无法读取，原始数据已保留。" }
        } else { history = WatchHistory() }
    }
    func record(_ value: WatchRecord) {
        guard storageError == nil else { return }
        history.upsert(value); persist()
    }
    func remove(_ id: String) { history.remove(id: id); persist() }
    func clear() { history = WatchHistory(); storageError = nil; persist() }
    private func persist() {
        do { defaults.set(try JSONEncoder().encode(history.records), forKey: "watchHistory.v1") }
        catch { storageError = "观看记录保存失败。" }
    }
    func record(for video: Video) -> WatchRecord? { history.records.first { $0.id == video.id } }
}

struct RootView: View {
    let client: CatalogClient
    var body: some View {
        TabView {
            Tab("浏览", systemImage: "square.grid.2x2") {
                NavigationStack { BrowseView(client: client) }
            }
            Tab("搜索", systemImage: "magnifyingglass", role: .search) {
                NavigationStack { SearchView(client: client) }
            }
            Tab("最近观看", systemImage: "clock") {
                NavigationStack { HistoryView(client: client) }
            }
            Tab("设置", systemImage: "gearshape") {
                NavigationStack { SettingsView() }
            }
        }
    }
}

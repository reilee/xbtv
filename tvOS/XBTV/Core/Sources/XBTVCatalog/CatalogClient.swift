import Foundation

public actor CatalogClient {
    public let parser: CatalogParser
    private let session: URLSession
    public init(baseURL: URL = URL(string: "https://www.xiaobaotv.com")!, session: URLSession = .shared) {
        parser = CatalogParser(baseURL: baseURL); self.session = session
    }
    public func category(_ category: Category, nextURL: URL? = nil) async throws -> CatalogPage {
        guard let pageURL = nextURL ?? parser.url(category.path) else { throw CatalogError.invalidURL }
        let page = try parser.listing(await fetch(pageURL), pageURL: pageURL)
        guard !page.videos.isEmpty else { throw CatalogError.changedPage }
        return page
    }
    public func search(_ keyword: String) async throws -> CatalogPage {
        guard !keyword.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return CatalogPage(videos: [], nextURL: nil)
        }
        guard var components = URLComponents(url: parser.baseURL.appendingPathComponent("search.html"), resolvingAgainstBaseURL: false) else { throw CatalogError.invalidURL }
        components.queryItems = [URLQueryItem(name: "wd", value: keyword)]
        guard let pageURL = components.url else { throw CatalogError.invalidURL }
        return try parser.listing(await fetch(pageURL), pageURL: pageURL)
    }
    public func detail(_ video: Video) async throws -> VideoDetail {
        try parser.detail(await fetch(video.detailURL), pageURL: video.detailURL)
    }
    public func stream(_ episode: Episode) async throws -> URL {
        // Signed streams may expire: resolve again on each playback/retry.
        try parser.stream(await fetch(episode.pageURL))
    }
    private func fetch(_ url: URL) async throws -> String {
        guard url.host == parser.baseURL.host, url.scheme == "https" else { throw CatalogError.invalidURL }
        var request = URLRequest(url: url, timeoutInterval: 20)
        request.setValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64)", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await session.data(for: request)
        try Task.checkCancellation()
        guard let response = response as? HTTPURLResponse else { throw CatalogError.invalidHTML }
        guard (200...299).contains(response.statusCode) else { throw CatalogError.http(response.statusCode) }
        guard let html = String(data: data, encoding: .utf8), !html.isEmpty else { throw CatalogError.invalidHTML }
        return html
    }
}

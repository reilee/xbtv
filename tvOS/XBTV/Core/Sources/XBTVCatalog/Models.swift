import Foundation

public struct Video: Identifiable, Hashable, Codable, Sendable {
    public let id: String
    public let title: String
    public let coverURL: URL?
    public let year: String
    public let status: String
    public let detailURL: URL
    public init(id: String, title: String, coverURL: URL?, year: String, status: String, detailURL: URL) {
        self.id = id; self.title = title; self.coverURL = coverURL
        self.year = year; self.status = status; self.detailURL = detailURL
    }
}
public struct Category: Identifiable, Hashable, Sendable {
    public let id: String
    public let title: String
    public var symbol: String {
        switch id {
        case "1": "film"
        case "2": "tv.fill"
        case "3": "music.mic"
        case "4": "sparkles.tv"
        case "5": "play.rectangle.on.rectangle"
        default: "square.grid.2x2"
        }
    }
    public var path: String { "/movie/type/\(id).html" }
    public static let all: [Category] = [
        .init(id: "1", title: "电影"), .init(id: "2", title: "电视剧"),
        .init(id: "3", title: "综艺"), .init(id: "4", title: "动漫"), .init(id: "5", title: "短剧")
    ]
}
public struct Episode: Identifiable, Hashable, Codable, Sendable {
    public var id: String { pageURL.absoluteString }
    public let title: String
    public let pageURL: URL
    public init(title: String, pageURL: URL) { self.title = title; self.pageURL = pageURL }
}
public struct PlayLine: Identifiable, Hashable, Sendable {
    public let id: String
    public let title: String
    public let episodes: [Episode]
}
public struct VideoDetail: Sendable {
    public let video: Video
    public let area: String
    public let actors: String
    public let director: String
    public let summary: String
    public let lines: [PlayLine]
}
public struct CatalogPage: Sendable {
    public let videos: [Video]
    public let nextURL: URL?
}
public struct CategoryHomeRails: Sendable {
    public let monthlyPopular: CatalogPage
    public let allTime: CatalogPage
}
public enum CatalogError: LocalizedError, Sendable {
    case invalidURL, http(Int), invalidHTML, changedPage, unsupportedStream
    public var errorDescription: String? {
        switch self {
        case .invalidURL: "网站地址无效。"
        case .http(let code): "网站请求失败（\(code)），请稍后重试。"
        case .invalidHTML: "无法读取网站页面。"
        case .changedPage: "网站页面结构可能已变化，暂时无法读取内容。"
        case .unsupportedStream: "此线路没有可直接播放的 HLS 地址，请返回详情页选择其他线路。"
        }
    }
}

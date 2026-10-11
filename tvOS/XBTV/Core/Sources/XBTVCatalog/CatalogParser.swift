import Foundation
import SwiftSoup

/// DOM parsing mirrors the Android Jsoup selectors; JavaScript is never executed.
public struct CatalogParser: Sendable {
    public let baseURL: URL
    public init(baseURL: URL = URL(string: "https://www.xiaobaotv.tv")!) { self.baseURL = baseURL }

    public func url(_ raw: String, relativeTo page: URL? = nil) -> URL? {
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, let result = URL(string: value, relativeTo: page ?? baseURL)?.absoluteURL,
              ["https", "http"].contains(result.scheme?.lowercased() ?? ""), result.host != nil else { return nil }
        return result
    }

    public func listing(_ html: String, pageURL: URL) throws -> CatalogPage {
        let doc = try SwiftSoup.parse(html, pageURL.absoluteString)
        var videos: [Video] = []
        var seen = Set<String>()
        let items = try doc.select("#searchList > li, .myui-vodlist__box, .myui-vodlist__item, .vodlist__thumb")
        for item in items.array() {
            guard let link = try item.select("a[href*='/movie/detail/'], a[href*='/vod/detail/']").first(),
                  let detail = url(try link.attr("href"), relativeTo: pageURL) else { continue }
            let id = detail.deletingPathExtension().lastPathComponent
            guard !id.isEmpty, seen.insert(id).inserted else { continue }
            var title = try link.attr("title")
            if title.isEmpty { title = try item.select("h4.title a").text() }
            if title.isEmpty { title = try link.text() }
            guard !title.isEmpty else { continue }
            videos.append(Video(id: id, title: title, coverURL: try cover(link, page: pageURL),
                                year: try item.select(".pic-tag .tag").text(),
                                status: try item.select(".pic-text").text(), detailURL: detail))
        }
        var next: URL?
        for link in try doc.select(".myui-page a, a[rel=next]").array() {
            let title = try link.text()
            let relation = try link.attr("rel")
            if title.contains("下一页") || relation == "next" {
                if let candidate = url(try link.attr("href"), relativeTo: pageURL),
                   candidate.host == baseURL.host, candidate != pageURL { next = candidate; break }
            }
        }
        // Search may legitimately be empty; callers distinguish that from a broken category page.
        return CatalogPage(videos: videos, nextURL: next)
    }

    public func homepageRails(_ html: String, pageURL: URL, category: Category) throws -> CategoryHomeRails {
        let doc = try SwiftSoup.parse(html, pageURL.absoluteString)
        let hotTitles = ["本月热门\(category.title)", "最热\(category.title)"]
        let monthly = try homepageSection(doc, titles: hotTitles, listSelector: "ul.myui-vodlist__text", pageURL: pageURL)
        let allTime = try homepageSection(doc, titles: ["\(category.title)总榜单"], listSelector: "ul.myui-vodlist__text", pageURL: pageURL)
        return CategoryHomeRails(monthlyPopular: monthly, allTime: allTime)
    }

    private func homepageSection(_ doc: Document, titles: [String], listSelector: String, pageURL: URL) throws -> CatalogPage {
        guard let heading = try doc.select("h3.title").array().first(where: { titles.contains(try $0.text()) }) else {
            return CatalogPage(videos: [], nextURL: nil)
        }

        var moreURL: URL?
        if let header = heading.parent(), let link = try header.select("a.more[href]").first() {
            moreURL = url(try link.attr("href"), relativeTo: pageURL)
        }

        var ancestor = heading.parent()
        var list: Element?
        while let current = ancestor {
            if let candidate = try current.select(listSelector).first() {
                list = candidate
                break
            }
            ancestor = current.parent()
        }
        guard let list else { return CatalogPage(videos: [], nextURL: moreURL) }

        var videos: [Video] = []
        var seen = Set<String>()
        for link in try list.select("a[href*='/movie/detail/'], a[href*='/vod/detail/']").array() {
            guard let detailURL = url(try link.attr("href"), relativeTo: pageURL), detailURL.host == baseURL.host else { continue }
            let id = detailURL.deletingPathExtension().lastPathComponent
            guard !id.isEmpty, seen.insert(id).inserted else { continue }
            let title = (try link.attr("title")).trimmingCharacters(in: .whitespacesAndNewlines)
            guard !title.isEmpty else { continue }

            var card: Element? = link
            for _ in 0..<6 {
                guard let current = card, current.parent() != nil else { break }
                if current.tagName() == "li" || current.hasClass("myui-vodlist__box") { break }
                card = current.parent()
            }
            let poster = try card?.select(".myui-vodlist__thumb").first()
            videos.append(Video(
                id: id,
                title: title,
                coverURL: try poster.map { try cover($0, page: pageURL) } ?? nil,
                year: try card?.select(".pic-tag .tag").text() ?? "",
                status: try card?.select(".pic-text").text() ?? "",
                detailURL: detailURL
            ))
        }
        return CatalogPage(videos: videos, nextURL: moreURL)
    }

    public func detail(_ html: String, pageURL: URL) throws -> VideoDetail {
        let doc = try SwiftSoup.parse(html, pageURL.absoluteString)
        let title = try doc.select(".myui-content__detail .title").text()
        guard !title.isEmpty else { throw CatalogError.changedPage }
        let thumb = try doc.select(".myui-content__thumb img").first() ?? doc.select(".myui-content__thumb a").first()
        var fields: [String: String] = [:]
        for element in try doc.select(".myui-content__detail .data").array() {
            for child in element.children().array() where child.tagName() == "span" {
                let label = try child.text().replacingOccurrences(of: "：", with: "").replacingOccurrences(of: ":", with: "")
                guard ["年份", "地区", "主演", "导演"].contains(label) else { continue }
                var values: [String] = []
                var sibling = try child.nextElementSibling()
                while let current = sibling, current.tagName() != "span" {
                    let text = try current.text(); if !text.isEmpty { values.append(text) }
                    sibling = try current.nextElementSibling()
                }
                fields[label] = values.joined(separator: " / ")
            }
        }
        var lines: [PlayLine] = []
        let tabs = try doc.select(".nav-tabs a[href^='#']").array()
        let panes = try doc.select(".tab-content > div").array()
        if !panes.isEmpty {
            for pane in panes {
                let paneID = pane.id()
                let episodes = try playEpisodes(in: pane, pageURL: pageURL)
                guard !episodes.isEmpty else { continue }
                let tab = try tabs.first { try $0.attr("href") == "#\(paneID)" }
                let name = try tab?.text() ?? "线路 \(lines.count + 1)"
                lines.append(PlayLine(id: paneID.isEmpty ? episodes[0].id : paneID, title: name, episodes: episodes))
            }
        } else {
            // xiaobaotv.tv lists each source as a separate panel rather than tab panes.
            for (index, list) in try doc.select("ul.myui-content__list.sort-list").array().enumerated() {
                let episodes = try playEpisodes(in: list, pageURL: pageURL)
                guard !episodes.isEmpty else { continue }
                let panel = list.parent()?.parent()
                let name = try panel?.select(".myui-panel__head h3.title").first()?.text() ?? "线路 \(index + 1)"
                lines.append(PlayLine(id: "line-\(index + 1)", title: name, episodes: episodes))
            }
        }
        let video = Video(id: pageURL.deletingPathExtension().lastPathComponent, title: title,
                          coverURL: try thumb.flatMap { try cover($0, page: pageURL) }, year: fields["年份"] ?? "",
                          status: try doc.select(".myui-content__detail .pic-text").text(), detailURL: pageURL)
        return VideoDetail(video: video, area: fields["地区"] ?? "", actors: fields["主演"] ?? "",
                           director: fields["导演"] ?? "", summary: try doc.select(".content .data, .myui-content__desc .data").first()?.text() ?? "", lines: lines)
    }

    private func playEpisodes(in container: Element, pageURL: URL) throws -> [Episode] {
        var episodes: [Episode] = []
        var seen = Set<String>()
        for link in try container.select("a[href*='/movie/play/'], a[href*='/vod/play/']").array() {
            guard let page = url(try link.attr("href"), relativeTo: pageURL), seen.insert(page.absoluteString).inserted else { continue }
            let title = try link.text()
            guard !title.isEmpty else { continue }
            episodes.append(Episode(title: title, pageURL: page))
        }
        return episodes
    }

    public func stream(_ html: String) throws -> URL {
        // Match only the playback configuration, not unrelated ads or preview URLs.
        let pattern = #"(?:var\s+)?player_[A-Za-z0-9_]+\s*=\s*(\{)"#
        let regex = try NSRegularExpression(pattern: pattern)
        let range = NSRange(html.startIndex..., in: html)
        for match in regex.matches(in: html, range: range) {
            guard let r = Range(match.range(at: 1), in: html),
                  let object = jsonObject(in: html, from: r.lowerBound),
                  let data = object.data(using: .utf8),
                  let config = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  var raw = config["url"] as? String else { continue }
            let encrypt = (config["encrypt"] as? NSNumber)?.intValue ?? Int(config["encrypt"] as? String ?? "0") ?? 0
            if encrypt == 2 {
                guard let bytes = Data(base64Encoded: raw), let decoded = String(data: bytes, encoding: .utf8) else { continue }
                raw = decoded
            } else if encrypt != 0 && encrypt != 1 { continue }
            if encrypt == 1 || encrypt == 2 { raw = raw.removingPercentEncoding ?? raw }
            guard let result = url(raw), result.path.lowercased().hasSuffix(".m3u8") else { continue }
            return result
        }
        throw CatalogError.unsupportedStream
    }

    /// Scan balanced braces without mistaking a brace inside a quoted URL for structure.
    private func jsonObject(in text: String, from start: String.Index) -> String? {
        var depth = 0
        var quoted = false
        var escaped = false
        for index in text[start...].indices {
            let character = text[index]
            if quoted {
                if escaped { escaped = false }
                else if character == "\\" { escaped = true }
                else if character == "\"" { quoted = false }
            } else {
                if character == "\"" { quoted = true }
                else if character == "{" { depth += 1 }
                else if character == "}" {
                    depth -= 1
                    if depth == 0 { return String(text[start...index]) }
                }
            }
        }
        return nil
    }

    private func cover(_ element: Element, page: URL) throws -> URL? {
        for attr in ["data-original", "data-src", "src"] {
            if let result = url(try element.attr(attr), relativeTo: page) { return result }
        }
        let style = try element.attr("style")
        let regex = try NSRegularExpression(pattern: #"url\(\s*["']?([^"')]+)["']?\s*\)"#, options: .caseInsensitive)
        if let match = regex.firstMatch(in: style, range: NSRange(style.startIndex..., in: style)),
           let range = Range(match.range(at: 1), in: style) { return url(String(style[range]), relativeTo: page) }
        return nil
    }
}

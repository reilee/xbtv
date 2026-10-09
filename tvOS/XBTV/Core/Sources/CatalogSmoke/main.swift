import Foundation
import XBTVCatalog

@main struct Smoke {
    static func main() async throws {
        let client = CatalogClient()
        let page = try await client.category(Category.all[0])
        guard let video = page.videos.first else { fatalError("No catalog items") }
        guard let next = page.nextURL else { fatalError("Pagination missing") }
        let secondPage = try await client.category(Category.all[0], nextURL: next)
        guard !secondPage.videos.isEmpty else { fatalError("Empty next page") }
        print("PASS: live pagination")
        let detail = try await client.detail(video)
        guard detail.video.coverURL != nil, !detail.summary.isEmpty, !detail.video.year.isEmpty else { fatalError("Detail metadata missing") }
        guard let episode = detail.lines.first?.episodes.first else { fatalError("No episodes") }
        let stream = try await client.stream(episode)
        let (data, response) = try await URLSession.shared.data(from: stream)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200,
              let manifest = String(data: data, encoding: .utf8), manifest.hasPrefix("#EXTM3U") else {
            fatalError("Stream did not return an HLS manifest")
        }
        // Do not log signed stream URLs.
        print("PASS: \(page.videos.count) titles, \(detail.lines.count) lines, \(episode.title), valid HLS manifest")
        let search = try await client.search(video.title)
        guard search.videos.contains(where: { $0.id == video.id }) else { fatalError("Search missed title") }
        print("PASS: live search")
    }
}

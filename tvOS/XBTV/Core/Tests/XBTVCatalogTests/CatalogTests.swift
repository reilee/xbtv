import Foundation
import XCTest
@testable import XBTVCatalog

final class CatalogTests: XCTestCase {
    let parser = CatalogParser()
    let base = URL(string: "https://www.xiaobaotv.com/vod/type/1.html")!
    func testListingDeduplicatesAndNormalizesImagesAndPagination() throws {
        let html = """
        <div class="myui-vodlist__box"><a href="/vod/detail/12.html" title="测试 &amp; 电影" data-original="//img.example.com/a.webp"><span class="pic-tag"><span class="tag">2026</span></span><span class="pic-text">HD</span></a></div>
        <div class="myui-vodlist__box"><a href="/vod/detail/12.html" title="duplicate"></a></div>
        <div class="myui-vodlist__box"><a href="/vod/detail/13.html" title="第二部" style="background-image: url('/cover.jpg')"></a></div>
        <ul class="myui-page"><li><a href="/vod/type/1-2.html">下一页</a></li></ul>
        """
        let result = try parser.listing(html, pageURL: base)
        XCTAssertEqual(result.videos.count, 2)
        XCTAssertEqual(result.videos[0].title, "测试 & 电影")
        XCTAssertEqual(result.videos[0].coverURL?.absoluteString, "https://img.example.com/a.webp")
        XCTAssertEqual(result.videos[1].coverURL?.path, "/cover.jpg")
        XCTAssertEqual(result.nextURL?.path, "/vod/type/1-2.html")
    }
    func testSearchLayoutAndEmptyCover() throws {
        let html = """
        <ul id="searchList"><li><div class="thumb"><a href="/vod/detail/9.html"></a></div><h4 class="title"><a>搜索结果</a></h4></li></ul>
        """
        let page = try parser.listing(html, pageURL: base)
        XCTAssertEqual(page.videos.first?.title, "搜索结果")
        XCTAssertNil(page.videos.first?.coverURL)
    }
    func testDetailMatchesTabByIDAndSeparatesMetadata() throws {
        let html = """
        <div class="myui-content__thumb"><a><img data-original="/cover.webp"></a></div><div class="myui-content__detail"><h1 class="title">节目</h1><p class="data"><span>地区：</span><a>日本</a><span class="split-line"></span><span>年份：</span><a>2026</a></p><p class="data"><span>主演：</span><a>甲</a><a>乙</a></p></div>
        <div class="content"><span class="sketch">简略</span><span class="data">完整剧情简介</span></div>
        <ul class="nav-tabs"><li><a href="#line2">备用</a></li><li><a href="#line1">主线</a></li></ul>
        <div class="tab-content"><div id="line1"><li><a href="/vod/play/9-1-1.html">第1集</a></li></div><div id="line2"><li><a href="/vod/play/9-2-1.html">第1集</a></li></div></div>
        """
        let detail = try parser.detail(html, pageURL: URL(string: "https://www.xiaobaotv.com/vod/detail/9.html")!)
        XCTAssertEqual(detail.video.coverURL?.path, "/cover.webp")
        XCTAssertEqual(detail.video.year, "2026"); XCTAssertEqual(detail.area, "日本")
        XCTAssertEqual(detail.summary, "完整剧情简介")
        XCTAssertEqual(detail.actors, "甲 / 乙"); XCTAssertEqual(detail.lines[0].title, "主线")
        XCTAssertEqual(detail.lines[1].title, "备用")
    }
    func testEscapedSignedHLSAndNestedPlayerObject() throws {
        let html = #"<script>var player_aaaa={"vod_data":{"name":"test"},"encrypt":0,"url":"https:\/\/cdn.example.com\/index.m3u8?token=a%2Fb&x=1","url_next":"https://wrong.example.com/next.m3u8"};</script>"#
        let url = try parser.stream(html)
        XCTAssertEqual(url.host, "cdn.example.com")
        XCTAssertEqual(url.query, "token=a%2Fb&x=1")
    }
    func testMultilineConfigurationAndQuotedBraces() throws {
        let html = """
        <script>
        var player_aaaa = {
            "vod_data": {"name": "A } B"},
            "encrypt": 0,
            "url": "https://cdn.example.com/index.m3u8?token=abc"
        }; window.extra = true;
        </script>
        """
        XCTAssertEqual(try parser.stream(html).host, "cdn.example.com")
    }
    func testEncodedStreams() throws {
        let raw = "https://cdn.example.com/index.m3u8?token=abc"
        let b64 = Data(raw.utf8).base64EncodedString()
        XCTAssertEqual(try parser.stream("<script>var player_aaaa={\"encrypt\":2,\"url\":\"\(b64)\"}</script>").absoluteString, raw)
        XCTAssertEqual(try parser.stream(#"<script>var player_aaaa={"encrypt":1,"url":"https%3A%2F%2Fcdn.example.com%2Findex.m3u8%3Ftoken%3Dabc"}</script>"#).absoluteString, raw)
    }
    func testRejectsUnrelatedStreamsAndNonHTTPURLs() {
        XCTAssertThrowsError(try parser.stream(#"<script>var ad={"url":"https://ad.example.com/index.m3u8"}</script>"#))
        XCTAssertThrowsError(try parser.stream(#"<script>var player_aaaa={"url":"https://example.com/parser?url=index.m3u8"}</script>"#))
        XCTAssertNil(parser.url("javascript:alert(1)")); XCTAssertNil(parser.url(""))
        XCTAssertThrowsError(try parser.detail("<html>blocked</html>", pageURL: base))
    }
    func testHistoryBoundDeduplicationAndResume() throws {
        var history = WatchHistory()
        let ep = Episode(title: "第1集", pageURL: base)
        for i in 0..<15 {
            let video = Video(id: "\(i)", title: "\(i)", coverURL: nil, year: "", status: "", detailURL: base)
            history.upsert(WatchRecord(video: video, episode: ep, lineID: "a", lineTitle: "主线", seconds: 100, duration: 1000))
        }
        XCTAssertEqual(history.records.count, 12)
        let latest = history.records[0]
        XCTAssertEqual(latest.resumeSeconds, 100)
        history.upsert(WatchRecord(video: latest.video, episode: ep, lineID: "a", lineTitle: "主线", seconds: 990, duration: 1000))
        XCTAssertEqual(history.records.count, 12); XCTAssertEqual(history.records[0].resumeSeconds, 0)
        let restored = try JSONDecoder().decode([WatchRecord].self, from: JSONEncoder().encode(history.records))
        XCTAssertEqual(WatchHistory(records: restored).records.count, 12)
    }
}

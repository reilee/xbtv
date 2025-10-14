package com.xiaobaotv.app.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import com.xiaobaotv.app.model.*
import java.util.concurrent.TimeUnit

class XiaoBaoTVParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://www.xiaobaotv.com"

    // 获取分类列表
    suspend fun getCategories(): List<Category> = withContext(Dispatchers.IO) {
        listOf(
            Category("1", "电影", "/vod/type/1.html"),
            Category("2", "电视剧", "/vod/type/2.html"),
            Category("3", "动漫", "/vod/type/3.html"),
            Category("4", "综艺", "/vod/type/4.html"),
            Category("11", "短剧", "/vod/type/11.html")
        )
    }

    // 获取视频列表
    suspend fun getVideoList(categoryUrl: String, page: Int = 1): List<Video> =
        withContext(Dispatchers.IO) {
            val url = if (page == 1) {
                "$baseUrl$categoryUrl"
            } else {
                "$baseUrl${categoryUrl.replace(".html", "-$page.html")}"
            }

            val doc = fetchDocument(url)
            parseVideoList(doc)
        }

    // 搜索视频
    suspend fun searchVideos(keyword: String): List<Video> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/search.html?wd=$keyword"
        val doc = fetchDocument(url)
        parseVideoList(doc)
    }

    // 获取视频详情
    suspend fun getVideoDetail(detailUrl: String): VideoDetail =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl$detailUrl"
            val doc = fetchDocument(url)
            parseVideoDetail(doc)
        }

    // 获取播放地址
    suspend fun getPlayUrl(playUrl: String): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl$playUrl"
        val html = fetchHtml(url)

        // 提取 player_aaaa 变量中的 url
        val regex = Regex("\"url\"\\s*:\\s*\"(https?:\\\\/\\\\/[^\"']*?\\.m3u8)\"")
        val match = regex.find(html)
        match?.groupValues?.get(1)?.replace("\\/", "/") ?: ""
    }

    // 解析视频列表
    private fun parseVideoList(doc: Document): List<Video> {
        return doc.select(".myui-vodlist__box").map { element ->
            val link = element.selectFirst("a")
            val title = link?.attr("title") ?: ""
            val coverUrl = link?.attr("data-original") ?: ""
            val detailUrl = link?.attr("href") ?: ""
            val year = element.selectFirst(".pic-tag .tag")?.text() ?: ""
            val status = element.selectFirst(".pic-text")?.text() ?: ""

            val id = detailUrl.substringAfter("/vod/detail/")
                .substringBefore(".html")

            Video(id, title, coverUrl, year, status, detailUrl)
        }
    }

    // 解析视频详情
    private fun parseVideoDetail(doc: Document): VideoDetail {
        val title = doc.selectFirst(".myui-content__detail .title")?.text() ?: ""
        val coverUrl = doc.selectFirst(".myui-content__thumb img")
            ?.attr("data-original") ?: ""

        val dataElements = doc.select(".myui-content__detail .data")
        var year = ""
        var area = ""
        var actors = ""
        var director = ""

        dataElements.forEach { element ->
            val text = element.text()
            when {
                text.contains("年份：") -> year = element.select("a").text()
                text.contains("地区：") -> area = element.select("a").text()
                text.contains("主演：") -> actors = element.select("a")
                    .joinToString(", ") { it.text() }
                text.contains("导演：") -> director = element.select("a")
                    .joinToString(", ") { it.text() }
            }
        }

        val description = doc.selectFirst(".content .data p")?.text() ?: ""

        // 解析播放线路
        val playLines = mutableListOf<PlayLine>()
        doc.select(".tab-content > div").forEachIndexed { index, lineDiv ->
            val lineName = doc.select(".nav-tabs li").getOrNull(index)
                ?.selectFirst("a")?.text() ?: "线路${index + 1}"

            val episodes = lineDiv.select("li a").map { episodeLink ->
                Episode(
                    name = episodeLink.text(),
                    playUrl = episodeLink.attr("href")
                )
            }

            if (episodes.isNotEmpty()) {
                playLines.add(PlayLine(lineName, episodes))
            }
        }

        val id = doc.location().substringAfter("/vod/detail/")
            .substringBefore(".html")

        return VideoDetail(
            id, title, coverUrl, year, area, actors, director,
            description, playLines
        )
    }

    private fun fetchDocument(url: String): Document {
        val html = fetchHtml(url)
        return Jsoup.parse(html)
    }

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("请求失败: ${response.code}")
            return response.body?.string() ?: ""
        }
    }
}
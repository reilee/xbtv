package com.xiaobaotv.app.network

import android.util.Log
import com.xiaobaotv.app.model.Category
import com.xiaobaotv.app.model.Episode
import com.xiaobaotv.app.model.PlayLine
import com.xiaobaotv.app.model.Video
import com.xiaobaotv.app.model.VideoDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 小宝影院 HTML 页面解析器，负责抓取分类、视频列表、详情及播放地址
 */
class XiaoBaoTVParser {
    companion object {
        /** 匹配 CSS background-image 中的 url */
        private val STYLE_URL_REGEX = Regex("background-image\\s*:\\s*url\\([\"']?(.*?)[\"']?\\)", RegexOption.IGNORE_CASE)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://www.xiaobaotv.com"

    /**
     * 获取静态分类列表
     */
    suspend fun getCategories(): List<Category> = withContext(Dispatchers.IO) {
        listOf(
            Category("1", "电影", "/vod/type/1.html"),
            Category("2", "电视剧", "/vod/type/2.html"),
            Category("3", "动漫", "/vod/type/3.html"),
            Category("4", "综艺", "/vod/type/4.html"),
            Category("11", "短剧", "/vod/type/11.html")
        )
    }

    /**
     * 根据分类 URL 和页码获取视频列表
     * @param categoryUrl 分类路径
     * @param page 页码
     */
    suspend fun getVideoList(categoryUrl: String, page: Int = 1): List<Video> =
        withContext(Dispatchers.IO) {
            val url = if (page == 1) {
                normalizeUrl(categoryUrl)
            } else {
                normalizeUrl(categoryUrl.replace(".html", "-$page.html"))
            }

            val doc = fetchDocument(url)
            parseVideoList(doc)
        }

    /**
     * 搜索视频
     * @param keyword 搜索关键词
     */
    suspend fun searchVideos(keyword: String): List<Video> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        val encoded = try { URLEncoder.encode(keyword, "UTF-8") } catch (_: Exception) { keyword }
        val url = "$baseUrl/search.html?wd=$encoded"
        val doc = fetchDocument(url)
        
        // 1. 优先尝试解析搜索专用列表
        val searchList = parseSearchList(doc)
        if (searchList.isNotEmpty()) return@withContext searchList
        
        // 2. 尝试解析普通视频网格
        val list = parseVideoList(doc)
        if (list.isNotEmpty()) return@withContext list
        
        // 3. 最后尝试兜底解析方案
        parseVideoListFallback(doc)
    }

    /**
     * 获取视频详情信息
     * @param detailUrl 详情页 URL
     */
    suspend fun getVideoDetail(detailUrl: String): VideoDetail =
        withContext(Dispatchers.IO) {
            val url = normalizeUrl(detailUrl)
            val doc = fetchDocument(url)
            parseVideoDetail(doc, url)
        }

    /**
     * 解析播放页面以获取真实的 m3u8 播放地址
     * @param playUrl 播放页面 URL
     */
    suspend fun getPlayUrl(playUrl: String): String = withContext(Dispatchers.IO) {
        val url = normalizeUrl(playUrl)
        val html = fetchHtml(url)

        // 提取 player_aaaa 脚本变量中的 URL (通常是经过转义的 m3u8 地址)
        val regex = Regex("\"url\"\\s*:\\s*\"(https?:\\\\/\\\\/[^\"']*?\\.m3u8)\"")
        val match = regex.find(html)
        match?.groupValues?.get(1)?.replace("\\/", "/") ?: ""
    }

    /**
     * 解析标准的视频列表项 (.myui-vodlist__box)
     */
    private fun parseVideoList(doc: Document): List<Video> {
        return doc.select(".myui-vodlist__box").mapNotNull { element ->
            val link = element.selectFirst("a") ?: return@mapNotNull null
            val title = link.attr("title").ifBlank { link.text() }
            var coverUrl = link.attr("data-original")
            if (coverUrl.isBlank()) {
                val style = link.attr("style")
                STYLE_URL_REGEX.find(style)?.let { coverUrl = it.groupValues[1] }
            }
            val detailUrl = link.attr("href")
            if (!detailUrl.contains("/vod/detail/")) return@mapNotNull null
            val id = detailUrl.substringAfter("/vod/detail/").substringBefore(".html")
            if (id.isBlank()) return@mapNotNull null
            
            val year = element.selectFirst(".pic-tag .tag")?.text() ?: ""
            val status = element.selectFirst(".pic-text")?.text() ?: ""
            Video(id, title, normalizeUrl(coverUrl), year, status, detailUrl)
        }
    }

    /**
     * 解析搜索结果特有的列表结构 (ul#searchList)
     */
    private fun parseSearchList(doc: Document): List<Video> {
        val listRoot = doc.selectFirst("ul#searchList") ?: return emptyList()
        return listRoot.select("> li").mapNotNull { li ->
            val a = li.selectFirst("div.thumb a.myui-vodlist__thumb") ?: return@mapNotNull null
            val detailUrl = a.attr("href")
            if (!detailUrl.contains("/vod/detail/")) return@mapNotNull null
            val id = detailUrl.substringAfter("/vod/detail/").substringBefore(".html")
            if (id.isBlank()) return@mapNotNull null

            val title = li.selectFirst("h4.title a")?.text()?.trim().takeUnless { it.isNullOrBlank() } ?: a.attr("title")
            var coverUrl = a.attr("data-original")
            if (coverUrl.isBlank()) {
                val style = a.attr("style")
                STYLE_URL_REGEX.find(style)?.let { coverUrl = it.groupValues[1] }
            }
            val year = a.selectFirst(".pic-tag .tag")?.text() ?: ""
            val status = a.selectFirst(".pic-text")?.text() ?: ""
            Video(id, title ?: "", normalizeUrl(coverUrl), year, status, detailUrl)
        }
    }

    /**
     * 兜底解析方案，尝试更多可能的 CSS 选择器
     */
    private fun parseVideoListFallback(doc: Document): List<Video> {
        return doc.select(".myui-vodlist__item, .vodlist__thumb").mapNotNull { element ->
            val link = element.selectFirst("a") ?: return@mapNotNull null
            val title = link.attr("title").ifBlank { link.text() }
            var coverUrl = link.attr("data-original").ifBlank { link.attr("src") }
            if (coverUrl.isBlank()) {
                val style = link.attr("style")
                STYLE_URL_REGEX.find(style)?.let { coverUrl = it.groupValues[1] }
            }
            val detailUrl = link.attr("href")
            if (!detailUrl.contains("/vod/detail/")) return@mapNotNull null
            val id = detailUrl.substringAfter("/vod/detail/").substringBefore(".html")
            if (id.isBlank()) return@mapNotNull null

            val year = element.selectFirst(".pic-tag, .tag, .note")?.text() ?: ""
            val status = element.selectFirst(".pic-text, .status, .note")?.text() ?: ""
            Video(id, title, normalizeUrl(coverUrl), year, status, detailUrl)
        }
    }

    /**
     * 解析视频详情页 Document
     */
    private fun parseVideoDetail(doc: Document, originalUrl: String? = null): VideoDetail {
        val title = doc.selectFirst(".myui-content__detail .title")?.text() ?: ""
        val coverUrl = normalizeUrl(doc.selectFirst(".myui-content__thumb img")
            ?.attr("data-original") ?: "")

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

        val urlForId = originalUrl ?: doc.location()
        val id = urlForId.substringAfter("/vod/detail/")
            .substringBefore(".html")

        return VideoDetail(
            id, title, coverUrl, year, area, actors, director,
            description, playLines
        )
    }

    /** 获取指定 URL 的 Jsoup Document */
    private fun fetchDocument(url: String): Document {
        val html = fetchHtml(url)
        return Jsoup.parse(html, url)
    }

    /** 执行 HTTP 请求获取 HTML 字符串 */
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

    /**
     * 规范化 URL，补全域名和协议
     */
    private fun normalizeUrl(raw: String): String {
        if (raw.isBlank()) return raw
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> baseUrl + raw
            else -> "$baseUrl/$raw"
        }
    }
}

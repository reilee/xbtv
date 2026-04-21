package com.xiaobaotv.app.data

import android.content.SharedPreferences
import com.xiaobaotv.app.model.RecentWatchItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * 观看记录持久化仓库类。
 * 使用 SharedPreferences 存储观看记录，数据格式为 JSON 字符串。
 * 限制最大存储数量为 12 条，且每个视频（通过 detailUrl 标识）仅保留一条记录。
 */
class RecentWatchRepository(
    private val preferences: SharedPreferences
) {
    companion object {
        private const val KEY_RECENT_WATCH = "recent_watch_v1"
        private const val MAX_ITEMS = 12
    }

    /**
     * 从持久化层加载所有观看记录。
     * @return 观看记录列表，如果加载失败或无记录则返回空列表。
     */
    @Synchronized
    fun load(): List<RecentWatchItem> {
        val raw = preferences.getString(KEY_RECENT_WATCH, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    add(obj.toRecentWatchItem())
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 更新或插入一条观看记录。
     * 如果已存在相同视频的记录，则先删除旧记录。新记录会被插入到列表最前端。
     * @param item 新的观看记录项
     * @return 更新后的完整记录列表（已截断至最大限制）
     */
    @Synchronized
    fun upsert(item: RecentWatchItem): List<RecentWatchItem> {
        val current = load().toMutableList()
        // 确保同一个视频只有一条记录，避免详情页入口重复
        current.removeAll { it.detailUrl == item.detailUrl }
        current.add(0, item)
        val trimmed = current.take(MAX_ITEMS)
        saveInternal(trimmed)
        return trimmed
    }

    /**
     * 全量替换当前的观看记录。
     * @param items 新的记录列表
     */
    @Synchronized
    fun replaceAll(items: List<RecentWatchItem>) {
        saveInternal(items.take(MAX_ITEMS))
    }

    /**
     * 将列表序列化并存入 SharedPreferences。
     */
    private fun saveInternal(items: List<RecentWatchItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(item.toJson())
        }
        preferences.edit().putString(KEY_RECENT_WATCH, array.toString()).apply()
    }

    /**
     * JSON 对象转 RecentWatchItem 数据模型。
     */
    private fun JSONObject.toRecentWatchItem(): RecentWatchItem = RecentWatchItem(
        detailUrl = optString("detailUrl"),
        title = optString("title"),
        coverUrl = optString("coverUrl"),
        year = optString("year"),
        status = optString("status"),
        playUrl = optString("playUrl"),
        episodeName = optString("episodeName"),
        episodeIndex = optInt("episodeIndex", 0),
        lineName = optString("lineName"),
        updatedAt = optLong("updatedAt", 0L)
    )

    /**
     * RecentWatchItem 模型转 JSON 对象。
     */
    private fun RecentWatchItem.toJson(): JSONObject = JSONObject()
        .put("detailUrl", detailUrl)
        .put("title", title)
        .put("coverUrl", coverUrl)
        .put("year", year)
        .put("status", status)
        .put("playUrl", playUrl)
        .put("episodeName", episodeName)
        .put("episodeIndex", episodeIndex)
        .put("lineName", lineName)
        .put("updatedAt", updatedAt)
}

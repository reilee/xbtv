package com.xiaobaotv.app.model

/**
 * 代表一个视频分类
 * @param id 分类ID
 * @param name 分类名称
 * @param url 分类对应的URL路径
 */
data class Category(
    val id: String,
    val name: String,
    val url: String
)

/**
 * 代表视频简要信息，通常用于列表显示
 * @param id 视频唯一标识
 * @param title 视频标题
 * @param coverUrl 封面图地址
 * @param year 发行年份
 * @param status 更新状态或备注
 * @param detailUrl 详情页相对路径
 */
data class Video(
    val id: String,
    val title: String,
    val coverUrl: String,
    val year: String,
    val status: String,
    val detailUrl: String
)

/**
 * 视频详细信息
 * @param id 视频唯一标识
 * @param title 视频标题
 * @param coverUrl 封面图地址
 * @param year 发行年份
 * @param area 地区
 * @param actors 主演
 * @param director 导演
 * @param description 简介
 * @param playLines 播放线路列表
 */
data class VideoDetail(
    val id: String,
    val title: String,
    val coverUrl: String,
    val year: String,
    val area: String,
    val actors: String,
    val director: String,
    val description: String,
    val playLines: List<PlayLine>
)

/**
 * 播放线路
 * @param name 线路名称（如：小宝云播）
 * @param episodes 剧集列表
 */
data class PlayLine(
    val name: String,
    val episodes: List<Episode>
)

/**
 * 单集信息
 * @param name 集数名称
 * @param playUrl 播放页相对路径
 */
data class Episode(
    val name: String,
    val playUrl: String
)

/**
 * 最近观看记录项
 * @param detailUrl 详情页URL
 * @param title 标题
 * @param coverUrl 封面图
 * @param year 年份
 * @param status 状态
 * @param playUrl 播放页URL
 * @param episodeName 当前播放集数名称
 * @param episodeIndex 当前播放集数索引
 * @param lineName 线路名称
 * @param updatedAt 最后更新时间戳
 */
data class RecentWatchItem(
    val detailUrl: String,
    val title: String,
    val coverUrl: String,
    val year: String,
    val status: String,
    val playUrl: String,
    val episodeName: String,
    val episodeIndex: Int,
    val lineName: String,
    val updatedAt: Long
) {
    /**
     * 将观看记录转换为Video对象，以便在列表中展示
     */
    fun toVideo(): Video = Video(
        id = detailUrl.substringAfterLast('/').substringBefore(".html").ifBlank { detailUrl },
        title = title,
        coverUrl = coverUrl,
        year = year,
        status = buildString {
            if (lineName.isNotBlank()) append(lineName)
            if (episodeName.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(episodeName)
            }
            if (isEmpty()) append(status)
        },
        detailUrl = detailUrl
    )
}

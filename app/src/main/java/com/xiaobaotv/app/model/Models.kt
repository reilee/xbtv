package com.xiaobaotv.app.model

data class Category(
    val id: String,
    val name: String,
    val url: String
)

data class Video(
    val id: String,
    val title: String,
    val coverUrl: String,
    val year: String,
    val status: String,
    val detailUrl: String
)

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

data class PlayLine(
    val name: String,
    val episodes: List<Episode>
)

data class Episode(
    val name: String,
    val playUrl: String
)
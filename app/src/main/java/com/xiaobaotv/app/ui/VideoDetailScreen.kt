package com.xiaobaotv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xiaobaotv.app.model.VideoDetail
import com.xiaobaotv.app.model.RecentWatchItem
import com.xiaobaotv.app.model.PlayLine

/**
 * 视频详情页面，展示视频元数据并提供选集功能。
 * 
 * @param videoDetail 视频详细信息数据对象
 * @param resumePlayUrl 可选的续播地址，用于自动定位当前播放的线路和集数
 * @param onPlayClick 点击播放时的回调，传递包含上下文信息的 RecentWatchItem
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun VideoDetailScreen(
    videoDetail: VideoDetail,
    resumePlayUrl: String? = null,
    onPlayClick: (RecentWatchItem) -> Unit
) {
    // 根据续播地址定位初始选中的播放线路
    val initialLineIndex = remember(videoDetail.id, resumePlayUrl) {
        val matchedLineIndex = resumePlayUrl?.let { playUrl ->
            videoDetail.playLines.indexOfFirst { line -> line.episodes.any { it.playUrl == playUrl } }
        } ?: -1
        matchedLineIndex.takeIf { it >= 0 } ?: 0
    }

    // 当前选中的播放线路索引
    var selectedLineIndex by remember(videoDetail.id, resumePlayUrl) {
        mutableStateOf(initialLineIndex)
    }
    
    val lines = videoDetail.playLines
    if (selectedLineIndex >= lines.size && lines.isNotEmpty()) {
        selectedLineIndex = 0
    }

    // 根据续播地址定位初始选中的集数索引
    val targetEpisodeIndex = remember(videoDetail.id, selectedLineIndex, resumePlayUrl) {
        val episodes = lines.getOrNull(selectedLineIndex)?.episodes.orEmpty()
        val matchedIndex = resumePlayUrl?.let { playUrl ->
            episodes.indexOfFirst { it.playUrl == playUrl }
        } ?: -1
        matchedIndex.takeIf { it >= 0 } ?: 0
    }

    // 焦点请求器：用于自动聚焦到当前选中的集数
    val targetEpisodeFocusRequester = remember { FocusRequester() }

    // 当详情数据或线路切换时，自动将焦点移动到选中的集数，提升 TV 操作体验
    LaunchedEffect(videoDetail.id, selectedLineIndex) {
        if (lines.isNotEmpty()) {
            val episodes = lines.getOrNull(selectedLineIndex)?.episodes.orEmpty()
            if (episodes.isNotEmpty()) {
                kotlinx.coroutines.yield()
                runCatching { targetEpisodeFocusRequester.requestFocus() }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) // 使用更深的背景色
            .padding(horizontal = 48.dp, vertical = 32.dp)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. 顶部元数据区域：封面 + 标题 + 简介
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Card(
                    modifier = Modifier
                        .width(180.dp)
                        .height(250.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                ) {
                    AsyncImage(
                        model = videoDetail.coverUrl,
                        contentDescription = videoDetail.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = videoDetail.title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${videoDetail.year} · ${videoDetail.area} · ${videoDetail.director}",
                        fontSize = 14.sp,
                        color = Color(0xFFBBBBBB)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "主演: ${videoDetail.actors}",
                        fontSize = 14.sp,
                        color = Color(0xFF999999),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("简介:", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = videoDetail.description,
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // 2. 线路切换 Tab 区域
        if (lines.isNotEmpty()) {
            item {
                TabRow(
                    selectedTabIndex = selectedLineIndex,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    divider = {}, // 移除默认分割线
                    indicator = { tabPositions ->
                        if (tabPositions.isNotEmpty()) {
                            TabRowDefaults.Indicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[selectedLineIndex])
                                    .height(3.dp),
                                color = Color(0xFFFF6B6B)
                            )
                        }
                    }
                ) {
                    lines.forEachIndexed { index, playLine ->
                        val selected = index == selectedLineIndex
                        Tab(
                            selected = selected,
                            onClick = { selectedLineIndex = index },
                            modifier = Modifier.focusable(),
                            text = {
                                Text(
                                    text = playLine.name,
                                    fontSize = 16.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else Color(0xFF999999)
                                )
                            }
                        )
                    }
                }
            }

            // 3. 集数列表区域：自适应多行展示
            item {
                val currentLine = lines.getOrNull(selectedLineIndex)
                val episodes = currentLine?.episodes.orEmpty()
                
                if (episodes.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        episodes.forEachIndexed { index, ep ->
                            val isTarget = index == targetEpisodeIndex
                            
                            Box(modifier = Modifier.widthIn(min = 80.dp)) {
                                EpisodeButton(
                                    episode = ep,
                                    focusRequester = if (isTarget) targetEpisodeFocusRequester else null,
                                    onPlayClick = { _ ->
                                        onPlayClick(
                                            RecentWatchItem(
                                                detailUrl = "/vod/detail/${videoDetail.id}.html",
                                                title = videoDetail.title,
                                                coverUrl = videoDetail.coverUrl,
                                                year = videoDetail.year,
                                                status = "${currentLine?.name} · ${ep.name}",
                                                playUrl = ep.playUrl,
                                                episodeName = ep.name,
                                                episodeIndex = index,
                                                lineName = currentLine?.name ?: "",
                                                updatedAt = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "暂无剧集",
                        color = Color.Gray,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                    )
                }
            }
        }
    }
}

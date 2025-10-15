package com.xiaobaotv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xiaobaotv.app.model.VideoDetail
import com.xiaobaotv.app.ui.common.consumePointerEvents

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoDetailScreen(
    videoDetail: VideoDetail,
    onPlayClick: (String) -> Unit
) {
    var selectedLineIndex by remember { mutableStateOf(0) }
    val lines = videoDetail.playLines
    if (selectedLineIndex >= lines.size) selectedLineIndex = 0

    // 焦点请求器：始终用于当前线路第一集
    val firstEpisodeFocusRequester = remember { FocusRequester() }

    // 当详情 / 线路 / 当前选中线路变化时，请求该线路第一集焦点
    LaunchedEffect(videoDetail, lines, selectedLineIndex) {
        if (lines.isNotEmpty()) {
            val episodes = lines.getOrNull(selectedLineIndex)?.episodes.orEmpty()
            if (episodes.isNotEmpty()) {
                kotlinx.coroutines.yield()
                runCatching { firstEpisodeFocusRequester.requestFocus() }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .padding(24.dp)
            .consumePointerEvents(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Card(
                    modifier = Modifier
                        .width(150.dp)
                        .height(210.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                ) {
                    AsyncImage(
                        model = videoDetail.coverUrl,
                        contentDescription = videoDetail.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(videoDetail.title, fontSize = 20.sp, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text("${videoDetail.year} · ${videoDetail.area}", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(6.dp))
                    Text("导演: ${videoDetail.director}", fontSize = 10.sp, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text("主演: ${videoDetail.actors}", fontSize = 10.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Text("简介:", fontSize = 12.sp, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = videoDetail.description,
                        fontSize = 10.sp,
                        color = Color.LightGray,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 12.sp
                    )
                }
            }
        }
        if (lines.isNotEmpty()) {
            item {
                TabRow(
                    selectedTabIndex = selectedLineIndex,
                    containerColor = Color(0xFF222222),
                    contentColor = Color.White,
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
                            text = {
                                Text(
                                    playLine.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (selected) Color.White else Color(0xFFBBBBBB)
                                )
                            }
                        )
                    }
                }
            }
            item {
                val episodes = lines[selectedLineIndex].episodes
                if (episodes.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        episodes.forEachIndexed { idx, ep ->
                            val requester = if (idx == 0) firstEpisodeFocusRequester else null
                            EpisodeButton(
                                episode = ep,
                                onPlayClick = onPlayClick,
                                focusRequester = requester
                            )
                        }
                    }
                } else {
                    Text(
                        text = "暂无剧集",
                        color = Color.Gray,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

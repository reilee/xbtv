package com.xiaobaotv.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.xiaobaotv.app.model.Category
import com.xiaobaotv.app.model.Episode
import com.xiaobaotv.app.model.Video
import com.xiaobaotv.app.ui.common.tvActivate

/**
 * 分类卡片组件。
 * 
 * @param category 分类数据
 * @param onClick 点击或确认后的动作
 * @param width 宽度
 * @param height 高度
 * @param focusRequester 焦点控制
 */
@Composable
fun CategoryCard(
    category: Category,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    // 聚焦时的缩放动画，增强 TV 视觉交互感
    val scale by animateFloatAsState(if (focused) 1.25f else 1f, tween(150), label = "categoryScale")
    
    Card(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .width(width)
            .height(height)
            .onFocusChanged { focused = it.isFocused }
            .tvActivate { onClick() }
            .clickable { onClick() }
            .focusable(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (focused) Color(0xFFFF6B6B) else Color(0xFF333333)
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(
                text = category.name,
                fontSize = 15.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 视频封面卡片组件，主要用于首页列表和搜索结果。
 * 
 * @param video 视频简要信息
 * @param onClick 点击后的回调
 * @param width 固定宽度
 * @param focusRequester 焦点控制
 */
@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    width: Dp,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.15f else 1f, tween(150), label = "videoScale")
    
    Card(
        modifier = Modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .width(width)
            .onFocusChanged { focused = it.isFocused }
            .tvActivate { onClick() }
            .clickable { onClick() }
            .focusable(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(Modifier.padding(6.dp)) {
            AsyncImage(
                model = video.coverUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f) // 常见的电影海报比例
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A2A2A))
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = video.title,
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${video.year} · ${video.status}",
                fontSize = 11.sp,
                color = Color.Gray,
                maxLines = 1
            )
        }
    }
}

/**
 * 选集按钮组件。
 * 
 * @param episode 剧集信息
 * @param onPlayClick 播放点击回调
 * @param focusRequester 焦点控制
 */
@Composable
fun EpisodeButton(
    episode: Episode,
    onPlayClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.2f else 1f, tween(150), label = "episodeScale")
    
    val finalModifier = modifier
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer(scaleX = scale, scaleY = scale)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { focused = it.isFocused }
        .tvActivate { onPlayClick(episode.playUrl) }
        .focusable()

    Button(
        onClick = { onPlayClick(episode.playUrl) },
        modifier = finalModifier,
        shape = CommonButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) Color(0xFFFF6B6B) else Color(0xFF3A3A3A)
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = episode.name,
            maxLines = 1,
            fontSize = 13.sp,
            color = Color.White
        )
    }
}

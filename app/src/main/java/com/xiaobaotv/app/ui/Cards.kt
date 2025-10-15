package com.xiaobaotv.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xiaobaotv.app.model.Category
import com.xiaobaotv.app.model.Episode
import com.xiaobaotv.app.model.Video
import com.xiaobaotv.app.ui.common.consumePointerEvents
import com.xiaobaotv.app.ui.common.tvActivate

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
    val shape = RoundedCornerShape(10.dp)
    Card(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .width(width)
            .height(height)
            .padding(vertical = 4.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvActivate { onClick() }
            .consumePointerEvents() // 屏蔽鼠标
            .focusable(),
        shape = shape,
        border = if (focused) BorderStroke(1.dp, Color(0xFFEB9100)) else null,
        colors = CardDefaults.cardColors(containerColor = if (focused) Color(0xFF303030) else Color.Transparent)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(
                text = category.name,
                fontSize = 16.sp,
                color = Color.White,
                maxLines = 2
            )
        }
    }
}

@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    width: Dp
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Card(
        modifier = Modifier
            .width(width)
            .onFocusChanged { focused = it.isFocused }
            .tvActivate { onClick() }
            .consumePointerEvents()
            .focusable(),
        shape = shape,
        border = if (focused) BorderStroke(1.dp, Color(0xFFEB9100)) else null,
        colors = CardDefaults.cardColors(containerColor = if (focused) Color(0xFF303030) else Color.Transparent)
    ) {
        Column(Modifier.padding(8.dp)) {
            AsyncImage(
                model = video.coverUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
            )
            Spacer(Modifier.height(8.dp))
            Text(video.title, fontSize = 14.sp, color = Color.White, maxLines = 2)
            Text("${video.year} · ${video.status}", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
fun EpisodeButton(
    episode: Episode,
    onPlayClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val finalModifier = modifier
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { focused = it.isFocused }
        .tvActivate { onPlayClick(episode.playUrl) }
        // 移除 consumePointerEvents 以允许鼠标/触摸点击
        .focusable()
    Button(
        onClick = { onPlayClick(episode.playUrl) },
        modifier = finalModifier,
        shape = CommonButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) Color(0xFFFF6B6B) else Color(0xFF3A3A3A)
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    ) { Text(episode.name, maxLines = 1, fontSize = 12.sp) }
}

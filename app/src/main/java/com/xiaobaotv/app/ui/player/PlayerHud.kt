package com.xiaobaotv.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.xiaobaotv.app.ui.common.TimeFormatter
import androidx.compose.ui.focus.FocusRequester

/**
 * 播放器简易 HUD (Heads-Up Display)，用于在快进/快退时显示进度条。
 * 
 * @param positionMs 当前播放位置（毫秒）
 * @param durationMs 视频总时长（毫秒）
 * @param visible 是否可见
 */
@Composable
fun BoxScope.PlayerHud(
    positionMs: Long,
    durationMs: Long,
    visible: Boolean,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp)
                .alpha(0.85f)
        ) {
            val posText = TimeFormatter.format(positionMs)
            val durText = TimeFormatter.format(durationMs)
            
            // 进度条
            LinearProgressIndicator(
                progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = Color(0xFFFF6B6B),
                trackColor = Color.Gray.copy(alpha = 0.3f)
            )
            
            Spacer(Modifier.height(4.dp))
            
            // 时间文本
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(posText, color = Color.White, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                Text(durText, color = Color.White, fontSize = MaterialTheme.typography.labelSmall.fontSize)
            }
        }
    }
}

/**
 * 播放器交互式进度控制面板。
 * 当用户按下方向键下时显示，允许用户通过焦点进行更精细的进度查看。
 * 
 * @param positionMs 当前播放位置
 * @param durationMs 视频总时长
 * @param visible 是否可见
 * @param focused 是否已获得焦点
 * @param focusRequester 焦点请求器
 * @param onFocusedChanged 焦点变化回调
 * @param modifier 修饰符
 */
@Composable
fun BoxScope.PlayerSeekControls(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    visible: Boolean,
    focused: Boolean,
    focusRequester: FocusRequester,
    onFocusedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        val shape = RoundedCornerShape(12.dp)
        Column(
            modifier = modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp)
                .background(Color.Black.copy(alpha = 0.6f), shape)
                .then(
                    if (focused) Modifier.border(2.dp, Color(0xFFFF6B6B).copy(alpha = 0.8f), shape) 
                    else Modifier
                )
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusedChanged(it.isFocused) }
                .focusable()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )

                Column(modifier = Modifier.weight(1f)) {
                    val posText = TimeFormatter.format(positionMs)
                    val durText = TimeFormatter.format(durationMs)
                    
                    // 交互式进度条（颜色更亮以示区别）
                    LinearProgressIndicator(
                        progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = Color(0xFFFF6B6B),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // 时间文本
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(posText, color = Color.White, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                        Text(durText, color = Color.White, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                    }
                }
            }
        }
    }
}

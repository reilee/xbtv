package com.xiaobaotv.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xiaobaotv.app.ui.common.TimeFormatter

@Composable
fun BoxScope.PlayerHud(
    positionMs: Long,
    durationMs: Long,
    visible: Boolean,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp)
                .alpha(0.85f)
        ) {
            val posText = TimeFormatter.format(positionMs)
            val durText = TimeFormatter.format(durationMs)
            LinearProgressIndicator(
                progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(posText, color = Color.White, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                Text(durText, color = Color.White, fontSize = MaterialTheme.typography.labelSmall.fontSize)
            }
        }
    }
}

package com.xiaobaotv.app.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 仅针对 TV 遥控（Enter / DPAD_CENTER）触发激活动作；不提供鼠标点击。
 */
fun Modifier.tvActivate(onActivate: () -> Unit): Modifier =
    this.onKeyEvent { evt ->
        if (evt.type == KeyEventType.KeyUp && (evt.key == Key.Enter || evt.key == Key.DirectionCenter)) {
            onActivate(); true
        } else false
    }

/**
 * 屏蔽所有指针事件（鼠标 / 触摸），保证只能通过遥控器操作。
 */
fun Modifier.consumePointerEvents(): Modifier =
    this.pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent() } } }

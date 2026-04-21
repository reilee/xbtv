package com.xiaobaotv.app.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 专为 TV 遥控器设计的交互扩展。
 * 仅在按下 Enter 或 方向键确认键（DPAD_CENTER）时触发回调。
 * 
 * @param onActivate 激活后的动作回调
 */
fun Modifier.tvActivate(onActivate: () -> Unit): Modifier =
    this.onKeyEvent { evt ->
        if (evt.type == KeyEventType.KeyUp && (evt.key == Key.Enter || evt.key == Key.DirectionCenter)) {
            onActivate(); true
        } else false
    }

/**
 * 屏蔽所有指针事件（鼠标、点击、触摸等）。
 * 在 Android TV 应用中，通常需要屏蔽这类事件以确保纯遥控器焦点的交互逻辑不被干扰。
 */
fun Modifier.consumePointerEvents(): Modifier =
    this.pointerInput(Unit) { 
        awaitPointerEventScope { 
            while (true) { 
                awaitPointerEvent() 
            } 
        } 
    }

package com.xiaobaotv.app.ui.common

object UiConstants {
    const val SEEK_STEP_MS = 10_000L
    const val HUD_PROGRESS_REFRESH_MS = 300L
    const val PLAYER_ICON_SIZE_DP = 72 // 现已不再使用播放/暂停图标，但保留以备后续需要
    const val GRID_COLUMNS = 6
    const val CARD_CORNER_DP = 10
    // 新增：快进/快退 触发后 HUD（进度条+时间）显示的时长
    const val SEEK_HUD_VISIBLE_MS = 2_000L
}

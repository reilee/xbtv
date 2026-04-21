package com.xiaobaotv.app.ui.common

/**
 * 界面显示相关的全局常量。
 */
object UiConstants {
    /** 播放器快进/快退的默认步进时长（10秒） */
    const val SEEK_STEP_MS = 10_000L
    
    /** 播放器进度条刷新的间隔时长 */
    const val HUD_PROGRESS_REFRESH_MS = 300L
    
    /** 首页推荐列表每行的列数 */
    const val GRID_COLUMNS = 6
    
    /** 卡片的默认圆角半径 */
    const val CARD_CORNER_DP = 10
    
    /** 快进/快退触发后 HUD（进度条+时间提示）显示的维持时长 */
    const val SEEK_HUD_VISIBLE_MS = 2_000L
}

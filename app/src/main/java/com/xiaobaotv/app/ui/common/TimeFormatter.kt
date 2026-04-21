package com.xiaobaotv.app.ui.common

import java.util.Locale

/**
 * 时间格式化工具类，将毫秒转换为 00:00 或 0:00:00 格式。
 */
object TimeFormatter {
    private val locale = Locale.US
    
    /**
     * 将时长（毫秒）格式化为字符串。
     * @param ms 毫秒数
     * @return 格式化后的时间字符串，如 "05:30" 或 "1:12:45"
     */
    fun format(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(locale, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(locale, "%02d:%02d", m, s)
        }
    }
}

package com.xiaobaotv.app.ui.common

import java.util.Locale

object TimeFormatter {
    private val locale = Locale.US
    fun format(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(locale, "%d:%02d:%02d", h, m, s) else String.format(locale, "%02d:%02d", m, s)
    }
}

package com.example.lxmusic.util

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * 高刷新率支持（对齐 NeriPlayer 的 PreferredRefreshRate）。
 */
fun Activity.applyPreferredHighRefreshRate(enabled: Boolean) {
    val display = window.decorView.display ?:
        (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
    if (display == null) return
    val preferredRefreshRate = if (enabled) {
        display.supportedModes.orEmpty()
            .mapNotNull { it.refreshRate.takeIf { rate -> rate.isFinite() && rate > 0f } }
            .maxOrNull()
            ?: display.refreshRate
    } else {
        0f
    }
    val attributes = window.attributes
    if (attributes.preferredRefreshRate == preferredRefreshRate) return
    attributes.preferredRefreshRate = preferredRefreshRate
    window.attributes = attributes
}

package com.ganfan.junshi.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

object StatusBarInsets {

    fun applyTopPadding(view: View) {
        val baseTop = view.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val statusBarHeight = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = statusBarHeight + baseTop)
            windowInsets
        }
        ViewCompat.requestApplyInsets(view)
    }
}

fun View.applyStatusBarTopPadding() {
    StatusBarInsets.applyTopPadding(this)
}

package com.rainnya.chat.ui.util

import android.graphics.Rect
import android.os.Build
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime

/**
 * Returns the current IME (keyboard) height in pixels.
 *
 * - API 30+ : `WindowInsets.ime` — native, reactive
 * - API 21-29: `ViewTreeObserver` + `getWindowVisibleDisplayFrame` with dynamic baseline
 */
@Composable
fun rememberImeHeightPx(): Int {
    val density = LocalDensity.current
    val view = LocalView.current

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return WindowInsets.ime.getBottom(density)
    }

    var imeHeight by remember { mutableIntStateOf(0) }
    var baselinePx by remember { mutableIntStateOf(-1) }

    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            view.getWindowVisibleDisplayFrame(rect)
            val spaceBelow = view.rootView.height - rect.bottom
            if (baselinePx < 0) baselinePx = spaceBelow
            val keyboard = (spaceBelow - baselinePx).coerceAtLeast(0)
            imeHeight = keyboard
            if (keyboard == 0) baselinePx = spaceBelow
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }

    return imeHeight
}

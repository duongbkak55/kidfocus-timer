package com.kidfocus.timer.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Keeps the current timer screen visible only while its countdown is running. */
@Composable
fun KeepScreenOnWhileRunning(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val window = view.context.findActivity()?.window
        // Android can retain the window flag after the Compose view clears its own flag.
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view.keepScreenOn = enabled
        onDispose {
            view.keepScreenOn = false
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

package com.kidfocus.timer.childlog

import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kidfocus.timer.ui.screens.ChildQuickLogScreen
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.ChildQuickLogViewModel
import com.kidfocus.timer.ui.viewmodel.ScheduleAccessViewModel
import dagger.hilt.android.AndroidEntryPoint

/** Isolated entry point: no NavHost, parent routes, or saved draft. */
@AndroidEntryPoint
class LockScreenLogActivity : ComponentActivity() {
    private val childLog: ChildQuickLogViewModel by viewModels()
    private val access: ScheduleAccessViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            KidFocusTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ChildQuickLogScreen(childLog, access, lockScreen = true,
                        onBack = { finish() }, onFinished = { finish() })
                }
            }
        }
    }

    override fun onDestroy() { childLog.clearDraft(); super.onDestroy() }
}

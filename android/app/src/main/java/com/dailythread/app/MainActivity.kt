package com.dailythread.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.dailythread.app.ui.DailyThreadRoot
import com.dailythread.app.ui.theme.DailyThreadTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as DailyThreadApp
        setContent {
            DailyThreadTheme {
                DailyThreadRoot(app)
            }
        }
    }
}

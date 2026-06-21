package com.rainnya.chat

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.rainnya.chat.data.settings.AppSettings
import com.rainnya.chat.ui.chat.ChatViewModel
import com.rainnya.chat.ui.navigation.AppNavigation
import com.rainnya.chat.ui.theme.RainnyaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("RainnyaFatal", "CRASH on thread ${thread.name}", throwable)
        }

        val settings = AppSettings(applicationContext)
        val chatViewModel = ViewModelProvider(this)[ChatViewModel::class.java]
        lifecycle.addObserver(chatViewModel)
        setContent {
            RainnyaTheme {
                AppNavigation(settings = settings)
            }
        }
    }
}

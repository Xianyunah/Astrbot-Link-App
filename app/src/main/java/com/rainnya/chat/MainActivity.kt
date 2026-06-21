package com.rainnya.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rainnya.chat.data.settings.AppSettings
import com.rainnya.chat.ui.navigation.AppNavigation
import com.rainnya.chat.ui.theme.RainnyaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settings = AppSettings(applicationContext)
        setContent {
            RainnyaTheme {
                AppNavigation(settings = settings)
            }
        }
    }
}

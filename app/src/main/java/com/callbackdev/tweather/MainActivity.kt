package com.callbackdev.tweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.callbackdev.tweather.ui.navigation.TweatherApp
import com.callbackdev.tweather.ui.theme.TweatherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TweatherTheme {
                TweatherApp()
            }
        }
    }
}

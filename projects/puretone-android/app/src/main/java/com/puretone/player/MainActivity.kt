package com.puretone.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.puretone.player.ui.PureToneApp
import com.puretone.player.ui.theme.PureToneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PureToneTheme {
                PureToneApp()
            }
        }
    }
}

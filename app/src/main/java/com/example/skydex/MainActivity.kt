package com.example.skydex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.skydex.ui.theme.SkyDexTheme
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkyDexTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BuildHomeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

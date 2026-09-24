package com.iudigital.radio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.iudigital.radio.ui.theme.IUDigitalRadioTheme
import com.iudigital.radio.ui.screen.RadioScreen


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IUDigitalRadioTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RadioScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

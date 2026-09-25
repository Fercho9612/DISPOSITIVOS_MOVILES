package com.iudigital.radio.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun VolumeControlCard(
    currentVolume: Int,
    maxVolume: Int,
    onVolumeChanged: (Int) -> Unit,
    onVolumeIconClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F222A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ícono interactivo para el Mute
            IconButton(
                onClick = { onVolumeIconClick?.invoke() },
                enabled = onVolumeIconClick != null,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (currentVolume == 0) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.AutoMirrored.Filled.VolumeDown
                    },
                    contentDescription = if (currentVolume == 0) "Silenciado" else "Volumen Bajo",
                    tint = if (currentVolume == 0) Color.Red else Color.White
                )
            }

            Slider(
                value = currentVolume.toFloat(),
                onValueChange = { onVolumeChanged(it.toInt()) },
                valueRange = 0f..maxVolume.toFloat(),
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF2A75FF),
                    activeTrackColor = Color(0xFF2A75FF),
                    inactiveTrackColor = Color.DarkGray
                )
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Volumen Alto",
                tint = Color.White
            )
        }
    }
}
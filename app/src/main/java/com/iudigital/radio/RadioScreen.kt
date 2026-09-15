package com.iudigital.radio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream

// ==========================================
// 1. MODELO DE DATOS Y LISTA DE EMISORAS
// ==========================================
data class Station(
    val id: Int,
    val name: String,
    val genre: String,
    val streamUrl: String,
    val imageUrl: String
)

val stationList = listOf(
    Station(
        id = 1,
        name = "Radio IU Digital",
        genre = "Emisora Institucional",
        streamUrl = "https://stream.zeno.fm/f3wvbb1f018uv",
        imageUrl = "https://picsum.photos/seed/iudigital/300/300"
    ),
    Station(
        id = 2,
        name = "Caracol Radio",
        genre = "Noticias y Deportes",
        streamUrl = "https://26283.live.streamtheworld.com/CARACOL_RADIOAAC.aac",
        imageUrl = "https://picsum.photos/seed/caracol/300/300"
    ),
    Station(
        id = 3,
        name = "RCN Radio",
        genre = "Hablada / General",
        streamUrl = "https://26683.live.streamtheworld.com/RCN_RADIOAAC.aac",
        imageUrl = "https://picsum.photos/seed/rcn/300/300"
    ),
    Station(
        id = 4,
        name = "Radiónica",
        genre = "Música Alternativa",
        streamUrl = "https://rtvc-radionica.solumedia.com.ar/stream",
        imageUrl = "https://picsum.photos/seed/radionica/300/300"
    ),
    Station(
        id = 5,
        name = "W Radio",
        genre = "Noticias y Entrevistas",
        streamUrl = "https://20853.live.streamtheworld.com/WRADIO_COLOMBIAAAC.aac",
        imageUrl = "https://picsum.photos/seed/wradio/300/300"
    )
)

// ==========================================
// 2. PANTALLA PRINCIPAL (UI Y LÓGICA)
// ==========================================
@Composable
fun RadioScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    var isPlaying by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.8f) }
    var selectedStation by remember { mutableStateOf(stationList[0]) }

    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        profileBitmap = loadProfileImage(context)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            profileBitmap = bitmap
            saveProfileImage(context, bitmap)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            cameraLauncher.launch()
        } else {
            Toast.makeText(context, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    fun openCamera() {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Inicialización segura de ExoPlayer para el modo Design/Preview
    val exoPlayer = remember(context) {
        if (!isPreview) {
            ExoPlayer.Builder(context).build()
        } else {
            null
        }
    }

    // Cambio de canal en vivo
    LaunchedEffect(selectedStation, isPlaying) {
        if (!isPreview && exoPlayer != null) {
            val mediaItem = MediaItem.fromUri(selectedStation.streamUrl)
            exoPlayer.stop()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()

            if (isPlaying) {
                exoPlayer.play()
            }
        }
    }

    LaunchedEffect(volume) {
        if (!isPreview && exoPlayer != null) {
            exoPlayer.volume = volume
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!isPreview && exoPlayer != null) {
                exoPlayer.release()
            }
        }
    }

    fun triggerVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- CABECERA ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Radio Player",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isPlaying) Color.Red else Color.Gray)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isPlaying) "EN VIVO" else "OFFLINE",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { openCamera() },
                    contentAlignment = Alignment.Center
                ) {
                    if (profileBitmap != null) {
                        Image(
                            bitmap = profileBitmap!!.asImageBitmap(),
                            contentDescription = "Foto de perfil",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Tomar foto de perfil",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // --- CARÁTULA DINÁMICA DE LA EMISORA SELECCIONADA ---
            Card(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(24.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = selectedStation.imageUrl,
                        contentDescription = "Logo Emisora",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = Color.White.copy(alpha = 0.3f)
                    )
                }
            }

            // --- INFORMACIÓN DINÁMICA DE LA EMISORA ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = selectedStation.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = selectedStation.genre,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // --- LISTA DESLIZABLE DE EMISORAS ---
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Seleccionar Emisora:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(stationList) { station ->
                        val isSelected = station.id == selectedStation.id
                        Card(
                            modifier = Modifier
                                .width(130.dp)
                                .clickable {
                                    triggerVibration()
                                    selectedStation = station
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (isSelected) 6.dp else 2.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = station.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                                Text(
                                    text = station.genre,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // --- CONTROLES DE REPRODUCCIÓN Y VOLUMEN ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = {
                        triggerVibration()
                        if (!isPreview && exoPlayer != null) {
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        }
                        isPlaying = !isPlaying
                    },
                    modifier = Modifier
                        .size(68.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                        contentDescription = "Volumen bajo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volumen alto",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// PERSISTENCIA DE IMAGEN LOCAL
private fun saveProfileImage(context: Context, bitmap: Bitmap) {
    try {
        val filename = "profile_picture.jpg"
        val file = File(context.filesDir, filename)
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        stream.flush()
        stream.close()

        val prefs = context.getSharedPreferences("radio_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("profile_image_path", file.absolutePath).apply()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun loadProfileImage(context: Context): Bitmap? {
    val prefs = context.getSharedPreferences("radio_prefs", Context.MODE_PRIVATE)
    val path = prefs.getString("profile_image_path", null) ?: return null
    val file = File(path)
    return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
}

@Preview(showBackground = true)
@Composable
fun RadioScreenPreview() {
    RadioScreen()
}
package com.iudigital.radio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream

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
        name = "Radio IU Digital (Test)",
        genre = "Emisora Institucional",
        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
        imageUrl = "https://picsum.photos/seed/iudigital/300/300"
    ),
    Station(
        id = 2,
        name = "Caracol Radio",
        genre = "Noticias y Deportes",
        streamUrl = "https://playerservices.streamtheworld.com/api/livestream-redirect/CARACOL_RADIOAAC.aac",
        imageUrl = "https://picsum.photos/seed/caracol/300/300"
    ),
    Station(
        id = 3,
        name = "La FM Bogotá",
        genre = "Noticias y Música",
        streamUrl = "https://mdstrm.com/audio/632c9b23d1dcd7027f32f7fe/live.m3u8",
        imageUrl = "https://picsum.photos/seed/lafm/300/300"
    ),
    Station(
        id = 4,
        name = "Radiónica",
        genre = "Música Alternativa",
        streamUrl = "http://shoutcast.rtvc.gov.co:8010/;",
        imageUrl = "https://picsum.photos/seed/radionica/300/300"
    ),
    Station(
        id = 5,
        name = "W Radio",
        genre = "Noticias y Entrevistas",
        streamUrl = "https://playerservices.streamtheworld.com/api/livestream-redirect/WRADIO.mp3",
        imageUrl = "https://picsum.photos/seed/wradio/300/300"
    )
)

@Composable
fun RadioScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun triggerVibration() {
        // Mensaje visual para confirmar que la función sí se está llamando
        Toast.makeText(context, "Ejecutando vibración...", Toast.LENGTH_SHORT).show()

        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Aumentamos de 50 a 150 milisegundos
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(150)
            }
        }
    }

    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var volume by rememberSaveable { mutableFloatStateOf(0.8f) }
    var isMuted by rememberSaveable { mutableStateOf(false) }
    var previousVolume by rememberSaveable { mutableFloatStateOf(0.8f) }
    var selectedStationId by rememberSaveable { mutableIntStateOf(stationList.first().id) }

    val selectedStation = remember(selectedStationId) {
        stationList.find { it.id == selectedStationId } ?: stationList.first()
    }

    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) { profileBitmap = loadProfileImage(context) }

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
        if (isGranted) cameraLauncher.launch(null) else Toast.makeText(context, "Permiso denegado", Toast.LENGTH_SHORT).show()
    }

    fun openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(null)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val exoPlayer = remember(context) {
        if (!isPreview) {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0")
                .setAllowCrossProtocolRedirects(true)

            val player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
                .build()

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            player.setAudioAttributes(audioAttributes, true)

            player.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("RadioApp", "Error ExoPlayer: ${error.message}")
                }
            })
            player
        } else null
    }

    LaunchedEffect(selectedStationId) {
        if (!isPreview && exoPlayer != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(selectedStation.streamUrl))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = isPlaying
        }
    }

    LaunchedEffect(isPlaying) { if (!isPreview && exoPlayer != null) exoPlayer.playWhenReady = isPlaying }
    LaunchedEffect(volume) { if (!isPreview && exoPlayer != null) exoPlayer.volume = volume }
    DisposableEffect(Unit) { onDispose { if (!isPreview && exoPlayer != null) exoPlayer.release() } }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isLandscape) {
            // DISEÑO HORIZONTAL
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Columna Izquierda: Controles y Perfil
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isPlaying) Color.Red else Color.Gray)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isPlaying) "EN VIVO" else "OFFLINE",
                                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { openCamera() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (profileBitmap != null) {
                                Image(bitmap = profileBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(
                            modifier = Modifier.size(100.dp).clip(RoundedCornerShape(16.dp)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            AsyncImage(model = selectedStation.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Column {
                            Text(text = selectedStation.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = selectedStation.genre, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { triggerVibration(); if (isMuted) { volume = previousVolume; isMuted = false } else { previousVolume = volume; volume = 0f; isMuted = true } },
                                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            ) {
                                Icon(if (isMuted || volume == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            IconButton(
                                onClick = { triggerVibration(); isPlaying = !isPlaying },
                                modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                            ) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Fila del Slider añadida
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(
                                value = volume, onValueChange = { volume = it; isMuted = (it == 0f) },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                            )
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Columna Derecha: Lista de Emisoras
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(text = "Catálogo de Emisoras:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                        items(stationList) { station ->
                            val isSelected = station.id == selectedStation.id
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { triggerVibration(); selectedStationId = station.id; isPlaying = true },
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Radio, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(text = station.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(text = station.genre, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // DISEÑO VERTICAL (ORIGINAL)
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Radio Player", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (isPlaying) Color.Red else Color.Gray).padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = if (isPlaying) "EN VIVO" else "OFFLINE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).clickable { openCamera() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (profileBitmap != null) {
                            Image(bitmap = profileBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
                        }
                    }
                }

                Card(modifier = Modifier.size(150.dp).clip(RoundedCornerShape(20.dp)), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
                    AsyncImage(model = selectedStation.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = selectedStation.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(text = selectedStation.genre, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)) {
                    Text(text = "Catálogo de Emisoras:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                        items(stationList) { station ->
                            val isSelected = station.id == selectedStation.id
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { triggerVibration(); selectedStationId = station.id; isPlaying = true },
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Radio, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = station.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(text = station.genre, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { triggerVibration(); if (isMuted) { volume = previousVolume; isMuted = false } else { previousVolume = volume; volume = 0f; isMuted = true } },
                            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                        ) {
                            Icon(if (isMuted || volume == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        IconButton(
                            onClick = { triggerVibration(); isPlaying = !isPlaying },
                            modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = volume, onValueChange = { volume = it; isMuted = (it == 0f) },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                        )
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun saveProfileImage(context: Context, bitmap: Bitmap) {
    try {
        val file = File(context.filesDir, "profile_picture.jpg")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        stream.flush()
        stream.close()
        context.getSharedPreferences("radio_prefs", Context.MODE_PRIVATE).edit().putString("profile_image_path", file.absolutePath).apply()
    } catch (e: Exception) { e.printStackTrace() }
}

private fun loadProfileImage(context: Context): Bitmap? {
    val path = context.getSharedPreferences("radio_prefs", Context.MODE_PRIVATE).getString("profile_image_path", null) ?: return null
    val file = File(path)
    return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
}

@Preview(showBackground = true)
@Composable
fun RadioScreenPreview() { RadioScreen() }
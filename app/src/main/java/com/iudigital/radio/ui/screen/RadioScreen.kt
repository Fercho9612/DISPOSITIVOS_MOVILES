package com.iudigital.radio.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.iudigital.radio.data.model.CategoryTab
import com.iudigital.radio.data.model.Station
import com.iudigital.radio.data.remote.RetrofitClient
import com.iudigital.radio.ui.components.ActiveStationCard
import com.iudigital.radio.ui.components.CategorySelector
import com.iudigital.radio.ui.components.GenreFilterChips
import com.iudigital.radio.ui.components.StationGridItem
import com.iudigital.radio.ui.components.VolumeControlCard
import com.iudigital.radio.utils.saveBitmapToGallery
import com.iudigital.radio.utils.triggerVibration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


// ==========================================
// 3. PANTALLA PRINCIPAL
// ==========================================

@OptIn(UnstableApi::class)
@Composable
fun RadioScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var shouldResumeOnReturn by remember { mutableStateOf(false) }

    // Control de volumen del sistema
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var currentVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
// Estado para la pestaña seleccionada (Colombia / Internacional)
    var selectedTab by remember { mutableStateOf(CategoryTab.COLOMBIA) }

    var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var activeStation by remember { mutableStateOf<Station?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var selectedGenre by remember { mutableStateOf("Todos") }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    var isLoading by remember { mutableStateOf(true) }

    //  Estado para almacenar el texto que escribe el usuario
    var searchQuery by remember { mutableStateOf("") }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

///  FILTRADO COMBINADO (Por Género / Favoritos Y Nombre)
    val filteredStations = remember(stations, selectedGenre, searchQuery, favorites) {
        stations.filter { station ->
            val matchesGenre = when (selectedGenre) {
                "Favoritos" -> favorites.contains(station.id)
                "Todos" -> true
                else -> station.genre?.contains(selectedGenre, ignoreCase = true) ?: false
            }

            val matchesSearch = searchQuery.isEmpty() ||
                    station.name.contains(searchQuery, ignoreCase = true)

            matchesGenre && matchesSearch
        }
    }

    //  Declaramos los permisos requeridos según la versión de Android
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else {
            arrayOf(Manifest.permission.CAMERA)
        }
    }

//  Launcher de la cámara (Usa saveBitmapToGallery cuando el usuario captura la foto)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            triggerVibration(context, 100)
            saveBitmapToGallery(context, bitmap)
        }
    }

    // Launcher para solicitar permisos
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val storageGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: true

        if (cameraGranted && storageGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(
                context,
                "Se requieren permisos de cámara y almacenamiento",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ExoPlayer
    val exoPlayer = remember(context) {
        val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
            setDefaultRequestProperties(mapOf("User-Agent" to "Mozilla/5.0"))
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // El parámetro 'false' evita que ExoPlayer gestione y pause automáticamente el audio al perder el foco momentáneo de la cámara
        player.setAudioAttributes(audioAttributes, false)

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("RadioApp", "Error ExoPlayer: ${error.message}")
                isPlaying = false
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        })
        player
    }

    // Escuchador del ciclo de vida para reanudar al regresar de la cámara
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (shouldResumeOnReturn && activeStation != null && !exoPlayer.isPlaying) {
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    // Variables de caché en memoria para no repetir peticiones a la API
    var colombiaStationsCache by remember { mutableStateOf<List<Station>>(emptyList()) }
    var internationalStationsCache by remember { mutableStateOf<List<Station>>(emptyList()) }

    // Función para cargar emisoras según la pestaña seleccionada
    // Función optimizada con caché y corrutinas en Dispatchers.IO
    fun loadStations(tab: CategoryTab) {
        coroutineScope.launch(Dispatchers.IO) {
            // 1. Si ya tenemos datos en memoria, los mostramos AL INSTANTE sin pantalla negra
            val cachedList = when (tab) {
                CategoryTab.COLOMBIA -> colombiaStationsCache
                CategoryTab.INTERNATIONAL -> internationalStationsCache
            }

            if (cachedList.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    stations = cachedList
                    isLoading = false
                }
                return@launch
            }

            // 2. Si no hay caché, mostramos el cargador solo la primera vez
            withContext(Dispatchers.Main) { isLoading = true }

            try {
                // Petición a la red en hilo de E/S
                val result = when (tab) {
                    CategoryTab.COLOMBIA -> RetrofitClient.apiService.getStationsByCountry()
                    CategoryTab.INTERNATIONAL -> RetrofitClient.apiService.getSpanishStations()
                }

                // Guardamos en caché y actualizamos la interfaz
                withContext(Dispatchers.Main) {
                    if (tab == CategoryTab.COLOMBIA) {
                        colombiaStationsCache = result
                    } else {
                        internationalStationsCache = result
                    }
                    stations = result
                }
            } catch (e: Exception) {
                Log.e("RadioApp", "Error al cargar emisoras: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

     // Cambiamos (Unit) por (selectedTab) para que reaccione si cambias de pestaña
    LaunchedEffect(selectedTab) {
        loadStations(selectedTab)
    }

    fun playStation(station: Station) {
        triggerVibration(context, 40)
        activeStation = station
        exoPlayer.stop()
        exoPlayer.setMediaItem(MediaItem.fromUri(station.streamUrl))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun filterByGenre(genre: String) {
        triggerVibration(context, 30)
        selectedGenre = genre
    }

// USO DE LAZY-COLUMN PARA PERMITIR DESPLAZAMIENTO EN MODO HORIZONTAL / ROTACIÓN
    // Componente reutilizable para los controles del encabezado
    @Composable
    fun HeaderControls() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "IU Digital Radio",
                color = Color.White,
                fontSize = if (isLandscape) 16.sp else 20.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = {
                    triggerVibration(context, 50)
                    val hasCameraPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    val hasStoragePermission = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                    } else true

                    if (hasCameraPermission && hasStoragePermission) {
                        cameraLauncher.launch(null)
                    } else {
                        permissionsLauncher.launch(requiredPermissions)
                    }
                },
                modifier = Modifier
                    .background(Color(0xFF2196F3), CircleShape)
                    .size(if (isLandscape) 34.dp else 40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Tomar Foto",
                    tint = Color.White,
                    modifier = Modifier.size(if (isLandscape) 18.dp else 22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))

        CategorySelector(
            selectedTab = selectedTab,
            onTabSelected = { newTab ->
                if (selectedTab != newTab) {
                    triggerVibration(context, 30)
                    selectedTab = newTab
                    selectedGenre = "Todos"
                }
            }
        )
        Spacer(modifier = Modifier.height(2.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar emisora...", fontSize = 11.sp) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Buscar", modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Limpiar",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp, vertical = 2.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))

        activeStation?.let { station ->
            ActiveStationCard(
                station = station,
                isPlaying = isPlaying,
                isFavorite = favorites.contains(station.id),
                onPlayPauseClick = {
                    triggerVibration(context, 50)
                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                onFavoriteClick = {
                    triggerVibration(context, 50)
                    favorites = if (favorites.contains(station.id)) {
                        favorites - station.id
                    } else {
                        favorites + station.id
                    }
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

    // Estado para guardar el volumen previo al silenciar
    var previousVolume by remember { mutableIntStateOf(currentVolume) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        VolumeControlCard(
            currentVolume = currentVolume,
            maxVolume = maxVolume,
            onVolumeChanged = { newVolume ->
                currentVolume = newVolume
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
            },
            onVolumeIconClick = {
                triggerVibration(context, 30)
                if (currentVolume > 0) {
                    previousVolume = currentVolume
                    currentVolume = 0
                } else {
                    currentVolume = if (previousVolume > 0) previousVolume else maxVolume / 2
                }
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
            }
        )
    }
        Spacer(modifier = Modifier.height(2.dp))

        GenreFilterChips(
            selectedGenre = selectedGenre,
            onGenreSelected = { genre -> filterByGenre(genre) }
        )
    }

    // Componente reutilizable para la lista de emisoras
    @Composable
    fun StationsGridContent(columns: Int) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF2A75FF))
                    }
                }
            } else if (filteredStations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedGenre == "Favoritos") "No tienes emisoras en favoritos" else "No se encontraron emisoras",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                val chunkedStations = filteredStations.chunked(columns)
                items(items = chunkedStations) { rowStations ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (station in rowStations) {
                            Box(modifier = Modifier.weight(1f)) {
                                StationGridItem(
                                    station = station,
                                    isActive = activeStation?.id == station.id,
                                    onClick = { playStation(station) }
                                )
                            }
                        }
                        repeat(columns - rowStations.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    // RENDERS SEGÚN ORIENTACIÓN
    if (isLandscape) {
        // MODO HORIZONTAL: División en dos columnas
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
            ) {
                HeaderControls()
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
            ) {
                StationsGridContent(columns = 2)
            }
        }
    } else {
        // MODO VERTICAL: Columna apilada
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(top = 8.dp)
        ) {
            HeaderControls()
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.weight(1f)) {
                StationsGridContent(columns = 3)
            }
        }
    }
}
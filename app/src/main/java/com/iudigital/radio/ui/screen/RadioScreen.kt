package com.iudigital.radio.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    // Función para cargar emisoras según la pestaña seleccionada
    fun loadStations(tab: CategoryTab) {
        coroutineScope.launch {
            try {
                isLoading = true
                stations = withContext(Dispatchers.IO) {
                    when (tab) {
                        CategoryTab.COLOMBIA -> RetrofitClient.apiService.getStationsByCountry()
                        CategoryTab.INTERNATIONAL -> RetrofitClient.apiService.getSpanishStations()
                    }
                }
            } catch (e: Exception) {
                Log.e("RadioApp", "Error al cargar emisoras: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    /*LaunchedEffect(Unit) {
        try {
            isLoading = true
            stations = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.getStationsByCountry()
            }
        } catch (e: Exception) {
            Log.e("RadioApp", "Error: ${e.message}")
        } finally {
            isLoading = false
        }
    }*/
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

        if (genre == "Favoritos") return

        coroutineScope.launch {
            try {
                isLoading = true
                stations = withContext(Dispatchers.IO) {
                    if (genre == "Todos") {
                        RetrofitClient.apiService.getStationsByCountry()
                    } else {
                        RetrofitClient.apiService.searchStationsByTag(tag = genre.lowercase())
                    }
                }
            } catch (e: Exception) {
                Log.e("RadioApp", "Error al filtrar: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

// USO DE LAZY-COLUMN PARA PERMITIR DESPLAZAMIENTO EN MODO HORIZONTAL / ROTACIÓN
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(top = 8.dp)
    ) {
        // Encabezado con Cámara
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IU Digital Radio",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = {
                        triggerVibration(context, 50)

                        // Verificación directa e implícita de los permisos
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
                            // Solicita permisos si falta cualquiera de los dos
                            permissionsLauncher.launch(requiredPermissions)
                        }
                    },
                    modifier = Modifier
                        .background(Color(0xFF2196F3), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Tomar Foto",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Selector de Pestañas (Colombia / Internacional)
        item {
            CategorySelector(
                selectedTab = selectedTab,
                onTabSelected = { newTab ->
                    if (selectedTab != newTab) {
                        triggerVibration(context, 30)
                        selectedTab = newTab
                        selectedGenre = "Todos" // Reinicia el filtro de género al cambiar de pestaña
                    }
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        //  BUSCADOR DE EMISORAS
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar emisora...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Buscar")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        //  Tarjeta de Emisora Activa
        item {
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
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

         // 2. Control de Volumen
        item {
            VolumeControlCard(
                currentVolume = currentVolume,
                maxVolume = maxVolume,
                onVolumeChanged = { newVolume ->
                    currentVolume = newVolume
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Filtros por Género + Favoritos
        item {
            GenreFilterChips(
                selectedGenre = selectedGenre,
                onGenreSelected = { genre -> filterByGenre(genre) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 4. Grilla de Emisoras
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
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
                        .padding(32.dp),
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
            // Se organiza la lista en filas de a 3 columnas para permitir scroll continuo
            val chunkedStations = filteredStations.chunked(3)
            items(
                items = chunkedStations
            ) { rowStations: List<Station> ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    repeat(3 - rowStations.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun CategorySelector(
    selectedTab: CategoryTab,
    onTabSelected: (CategoryTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CategoryTab.entries.forEach { tab ->
            FilterChip(
                selected = (selectedTab == tab),
                onClick = { onTabSelected(tab) },
                label = { Text(tab.title) },
                leadingIcon = {
                    if (selectedTab == tab) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    }
}

/*package com.iudigital.radio.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.iudigital.radio.ui.components.ActiveStationCard
import com.iudigital.radio.ui.components.GenreFilterChips
import com.iudigital.radio.ui.components.StationGridItem
import com.iudigital.radio.ui.components.VolumeControlCard
import com.iudigital.radio.utils.saveBitmapToGallery
import com.iudigital.radio.utils.triggerVibration

// ==========================================
// 3. PANTALLA PRINCIPAL
// ==========================================

@OptIn(UnstableApi::class)
@Composable
fun RadioScreen(
    modifier: Modifier = Modifier,
    viewModel: RadioViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var shouldResumeOnReturn by remember { mutableStateOf(false) }

    // Control de volumen del sistema
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var currentVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    // Obtenemos los estados desde el ViewModel
    val selectedTab = viewModel.selectedTab
    val stations = viewModel.stations
    val isLoading = viewModel.isLoading

    var activeStation by remember { mutableStateOf<Station?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var selectedGenre by remember { mutableStateOf("Todos") }
    var favorites by remember { mutableStateOf(setOf<String>()) }

    // Estado para almacenar el texto que escribe el usuario
    var searchQuery by remember { mutableStateOf("") }

    /// FILTRADO COMBINADO (Por Género / Favoritos Y Nombre)
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

    // Declaramos los permisos requeridos según la versión de Android
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

    // Launcher de la cámara
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

        if (genre == "Favoritos") return
        viewModel.filterByGenre(genre)
    }

    // USO DE LAZY-COLUMN PARA PERMITIR DESPLAZAMIENTO
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(top = 8.dp)
    ) {
        // Encabezado con Cámara
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IU Digital Radio",
                    color = Color.White,
                    fontSize = 20.sp,
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
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Tomar Foto",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Selector de Pestañas (Colombia / Internacional)
        item {
            CategorySelector(
                selectedTab = selectedTab,
                onTabSelected = { newTab ->
                    if (selectedTab != newTab) {
                        triggerVibration(context, 30)
                        selectedGenre = "Todos"
                        viewModel.selectTab(newTab)
                    }
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // BUSCADOR DE EMISORAS
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar emisora...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Buscar")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Tarjeta de Emisora Activa
        item {
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
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Control de Volumen
        item {
            VolumeControlCard(
                currentVolume = currentVolume,
                maxVolume = maxVolume,
                onVolumeChanged = { newVolume ->
                    currentVolume = newVolume
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Filtros por Género + Favoritos
        item {
            GenreFilterChips(
                selectedGenre = selectedGenre,
                onGenreSelected = { genre -> filterByGenre(genre) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Grilla de Emisoras
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
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
                        .padding(32.dp),
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
            val chunkedStations = filteredStations.chunked(3)
            items(
                items = chunkedStations
            ) { rowStations: List<Station> ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    repeat(3 - rowStations.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun CategorySelector(
    selectedTab: CategoryTab,
    onTabSelected: (CategoryTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CategoryTab.entries.forEach { tab ->
            FilterChip(
                selected = (selectedTab == tab),
                onClick = { onTabSelected(tab) },
                label = { Text(tab.title) },
                leadingIcon = {
                    if (selectedTab == tab) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    }
}*/




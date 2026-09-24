package com.iudigital.radio.ui.screen

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iudigital.radio.data.model.CategoryTab
import com.iudigital.radio.data.model.Station
import com.iudigital.radio.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RadioViewModel : ViewModel() {

    var selectedTab by mutableStateOf(CategoryTab.COLOMBIA)
        private set

    var stations by mutableStateOf<List<Station>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    init {
        loadStations(selectedTab)
    }

    fun selectTab(tab: CategoryTab) {
        selectedTab = tab
        loadStations(tab)
    }

    fun loadStations(tab: CategoryTab) {
        viewModelScope.launch {
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

    fun filterByGenre(genre: String) {
        viewModelScope.launch {
            try {
                isLoading = true
                stations = withContext(Dispatchers.IO) {
                    if (genre == "Todos") {
                        when (selectedTab) {
                            CategoryTab.COLOMBIA -> RetrofitClient.apiService.getStationsByCountry()
                            CategoryTab.INTERNATIONAL -> RetrofitClient.apiService.getSpanishStations()
                        }
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
}
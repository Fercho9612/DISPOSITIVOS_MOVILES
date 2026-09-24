package com.iudigital.radio.data.remote

import com.iudigital.radio.data.model.Station
import retrofit2.http.GET
import retrofit2.http.Query

interface RadioApiService {

    // 1. Emisoras de Colombia activas ordenadas por popularidad (Sin límite estricto)
    @GET("json/stations/bycountry/Colombia")
    suspend fun getStationsByCountry(
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true,
        @Query("hidebroken") hideBroken: Boolean = true // <-- Solo emisoras en línea y funcionando
    ): List<Station>

    // 2. Búsqueda por género o etiqueta en Colombia (Solo activas)
    @GET("json/stations/search")
    suspend fun searchStationsByTag(
        @Query("tag") tag: String,
        @Query("countrycode") countryCode: String = "CO",
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true,
        @Query("hidebroken") hideBroken: Boolean = true
    ): List<Station>

    // 3. Búsqueda por nombre de emisora, ciudad o género (Solo activas)
    @GET("json/stations/search")
    suspend fun searchStationsByName(
        @Query("name") name: String,
        @Query("countrycode") countryCode: String = "CO",
        @Query("hidebroken") hideBroken: Boolean = true
    ): List<Station>

    // 4. Emisoras globales en español activas (De Colombia y el mundo)
    @GET("json/stations/bylanguage/spanish")
    suspend fun getSpanishStations(
        @Query("order") order: String = "clickcount",
        @Query("reverse") reverse: Boolean = true,
        @Query("hidebroken") hideBroken: Boolean = true
    ): List<Station>
}
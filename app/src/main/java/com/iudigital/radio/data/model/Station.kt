package com.iudigital.radio.data.model

import com.google.gson.annotations.SerializedName

data class Station(
    @SerializedName("stationuuid") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("url_resolved") val streamUrl: String, // url_resolved es más fiable que 'url'
    @SerializedName("favicon") val logoUrl: String?,
    @SerializedName("tags") val genre: String?,
    @SerializedName("country") val country: String?,
    @SerializedName("votes") val votes: Int = 0
)

enum class CategoryTab(val title: String) {
    COLOMBIA("Colombia"),
    INTERNATIONAL("Internacional")
}
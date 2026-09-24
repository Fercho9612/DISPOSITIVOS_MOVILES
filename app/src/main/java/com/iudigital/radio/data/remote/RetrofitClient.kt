package com.iudigital.radio.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://de1.api.radio-browser.info/"

    private val okHttpClient = OkHttpClient.Builder()
        // Aumentamos a 30s por si la lista completa tarda más en descargar
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val requestWithUserAgent = originalRequest.newBuilder()
                .header("User-Agent", "IUDigitalRadioApp/1.0 (com.iudigital.radio)")
                .build()
            chain.proceed(requestWithUserAgent)
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            // Nota: Si son muchas emisoras, BODY puede saturar el Logcat.
            // Puedes cambiarlo a Level.BASIC en producción.
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val apiService: RadioApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RadioApiService::class.java)
    }
}

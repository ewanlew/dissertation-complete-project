package com.ewan.wallscheduler.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = Credentials.DB_URL // base url for the backend api

    private val retrofit by lazy {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // logs request and response bodies
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logger) // adds logging to http client
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL) // sets backend base url
            .client(client) // attaches custom client with logging
            .addConverterFactory(GsonConverterFactory.create()) // uses gson to parse json
            .build()
    }

    val api: RoomBookingApi by lazy {
        retrofit.create(RoomBookingApi::class.java) // creates api instance from the interface
    }
}

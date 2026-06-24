package com.securepass.vision.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL_PRINCIPAL = "https://6a138bb56c7db8aac0532024.mockapi.io/api/v1/"

    private const val BASE_URL_EVENTOS = "https://6a399e6464a2d82692243396.mockapi.io/api/v1/"
    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL_PRINCIPAL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(ApiService::class.java)
    }

    val eventsInstance : ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL_EVENTOS)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(ApiService::class.java)
    }
}

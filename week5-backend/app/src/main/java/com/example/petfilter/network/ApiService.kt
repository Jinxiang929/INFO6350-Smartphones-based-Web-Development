package com.example.petfilter.network

import retrofit2.http.POST

data class GenerateResponse(
    val message: String
)

interface ApiService {

    @POST("generate")
    suspend fun generate(): GenerateResponse
}
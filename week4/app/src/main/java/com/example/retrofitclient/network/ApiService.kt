package com.example.retrofitclient.network

import com.example.retrofitclient.model.ChatRequest
import com.example.retrofitclient.model.ChatResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("api/chat")
    suspend fun sendMessage(
        @Body request: ChatRequest
    ): ChatResponse
}
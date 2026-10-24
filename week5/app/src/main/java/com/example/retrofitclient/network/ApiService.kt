
package com.example.retrofitclient.network

import com.example.retrofitclient.model.ChatRequest
import com.example.retrofitclient.model.ChatResponse
import com.example.retrofitclient.model.AuthRequest
import com.example.retrofitclient.model.AuthResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("api/chat")
    suspend fun sendMessage(
        @Body request: ChatRequest
    ): ChatResponse

    @POST("api/auth/google")
    suspend fun googleLogin(
        @Body request: AuthRequest
    ): AuthResponse
}

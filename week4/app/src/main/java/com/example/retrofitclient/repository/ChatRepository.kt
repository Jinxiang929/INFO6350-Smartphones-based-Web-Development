package com.example.retrofitclient.repository

import com.example.retrofitclient.model.ChatRequest
import com.example.retrofitclient.model.ChatResponse
import com.example.retrofitclient.network.RetrofitClient

class ChatRepository {

    suspend fun sendMessage(prompt: String): ChatResponse {
        val request = ChatRequest(prompt = prompt)

        return RetrofitClient.apiService.sendMessage(request)
    }
}
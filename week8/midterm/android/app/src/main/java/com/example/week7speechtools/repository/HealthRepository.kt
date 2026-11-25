package com.example.week7speechtools.repository

import com.example.week7speechtools.model.HealthResponse
import com.example.week7speechtools.network.ApiService

class HealthRepository(
    private val apiService: ApiService
) {

    suspend fun checkHealth(): HealthResponse {
        return apiService.getHealth()
    }
}
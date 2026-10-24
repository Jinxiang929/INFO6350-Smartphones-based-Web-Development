
package com.example.googlesheetsapp.network

import com.example.googlesheetsapp.model.AuthRequest
import com.example.googlesheetsapp.model.AuthResponse
import com.example.googlesheetsapp.model.SubmitRequest
import com.example.googlesheetsapp.model.SubmitResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {

    @POST("api/auth/google")
    suspend fun authenticateGoogle(
        @Body request: AuthRequest
    ): AuthResponse

    @POST("api/submit")
    suspend fun submitData(
        @Header("Authorization") authorization: String,
        @Body request: SubmitRequest
    ): SubmitResponse
}

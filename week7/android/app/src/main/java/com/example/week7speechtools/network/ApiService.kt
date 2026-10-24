package com.example.week7speechtools.network

import com.example.week7speechtools.model.GoogleSignInRequest
import com.example.week7speechtools.model.GoogleSignInResponse
import com.example.week7speechtools.model.HealthResponse
import com.example.week7speechtools.model.LogoutResponse
import com.example.week7speechtools.model.MeResponse
import com.example.week7speechtools.model.SaveRecordRequest
import com.example.week7speechtools.model.SaveRecordResponse
import com.example.week7speechtools.model.SynthesisRequest
import com.example.week7speechtools.model.TranscriptionResponse
import com.example.week7speechtools.model.TranslationRequest
import com.example.week7speechtools.model.TranslationResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    @GET("api/health")
    suspend fun getHealth(): HealthResponse

    @POST("api/auth/google")
    suspend fun signInWithGoogle(
        @Body request: GoogleSignInRequest
    ): GoogleSignInResponse

    @GET("api/auth/me")
    suspend fun getCurrentUser(
        @Header("Authorization") authorization: String
    ): MeResponse

    @POST("api/auth/logout")
    suspend fun logout(
        @Header("Authorization") authorization: String
    ): LogoutResponse

    @Multipart
    @POST("api/speech/transcribe")
    suspend fun transcribeAudio(
        @Header("Authorization") authorization: String,
        @Part audio: MultipartBody.Part
    ): TranscriptionResponse

    @POST("api/translate")
    suspend fun translate(
        @Header("Authorization") authorization: String,
        @Body request: TranslationRequest
    ): TranslationResponse

    @POST("api/speech/synthesize")
    suspend fun synthesizeSpeech(
        @Header("Authorization") authorization: String,
        @Body request: SynthesisRequest
    ): ResponseBody

    @POST("api/submit")
    suspend fun saveRecord(
        @Header("Authorization") authorization: String,
        @Body request: SaveRecordRequest
    ): SaveRecordResponse
}
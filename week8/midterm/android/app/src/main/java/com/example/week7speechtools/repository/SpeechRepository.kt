package com.example.week7speechtools.repository

import com.example.week7speechtools.auth.SessionStore
import com.example.week7speechtools.model.SynthesisRequest
import com.example.week7speechtools.network.ApiService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

class SpeechRepository(
    private val apiService: ApiService,
    private val cacheDir: File
) {

    suspend fun transcribe(file: File): String {
        require(file.exists() && file.length() > 0L) {
            "The recording is empty"
        }

        require(file.length() < 19_000_000L) {
            "The recording is too large"
        }

        val requestBody = file.asRequestBody(
            "audio/mp4".toMediaType()
        )

        val audioPart = MultipartBody.Part.createFormData(
            "audio",
            file.name,
            requestBody
        )

        val response = apiService.transcribeAudio(
            authorization = authorizationHeader(),
            audio = audioPart
        )

        val text = response.text

        check(response.success && !text.isNullOrBlank()) {
            "The backend returned an empty transcription"
        }

        return text.trim()
    }

    suspend fun synthesize(text: String): File {
        val input = text.trim()

        require(input.isNotBlank() && input.length <= 2000) {
            "Text must contain between 1 and 2000 characters"
        }

        val response = apiService.synthesizeSpeech(
            authorization = authorizationHeader(),
            request = SynthesisRequest(text = input)
        )

        var outputFile: File? = null

        try {
            return withContext(Dispatchers.IO) {
                response.use { body ->
                    check(body.contentType()?.type == "audio") {
                        "The backend returned an unexpected audio response"
                    }

                    val file = File.createTempFile(
                        "tts_",
                        ".mp3",
                        cacheDir
                    )

                    outputFile = file

                    file.outputStream().use { output ->
                        body.byteStream().use { inputStream ->
                            inputStream.copyTo(output)
                        }
                    }

                    check(file.length() > 0L) {
                        "The backend returned empty audio"
                    }

                    file
                }
            }
        } catch (error: Exception) {
            response.close()
            outputFile?.delete()
            throw error
        }
    }

    private fun authorizationHeader(): String {
        val token = SessionStore.getAccessToken()

        check(!token.isNullOrBlank()) {
            "Sign in before using the speech tools"
        }

        return "Bearer $token"
    }
}
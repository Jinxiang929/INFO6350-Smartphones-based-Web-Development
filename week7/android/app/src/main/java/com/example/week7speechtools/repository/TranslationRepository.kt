package com.example.week7speechtools.repository

import com.example.week7speechtools.auth.SessionStore
import com.example.week7speechtools.model.TranslationLanguage
import com.example.week7speechtools.model.TranslationRequest
import com.example.week7speechtools.model.TranslationResult
import com.example.week7speechtools.network.ApiService

class TranslationRepository(
    private val apiService: ApiService
) {

    suspend fun translate(
        text: String,
        targetLanguage: TranslationLanguage
    ): TranslationResult {
        val input = text.trim()

        require(input.isNotBlank() && input.length <= 2000) {
            "Text must contain between 1 and 2000 characters"
        }

        val token = SessionStore.getAccessToken()

        check(!token.isNullOrBlank()) {
            "Sign in before translating text"
        }

        val response = apiService.translate(
            authorization = "Bearer $token",
            request = TranslationRequest(
                text = input,
                targetLanguage = targetLanguage.code
            )
        )

        val translatedText = response.translatedText

        check(
            response.success &&
                    response.sourceText == input &&
                    response.sourceLanguage == "auto" &&
                    response.targetLanguage == targetLanguage.code &&
                    !translatedText.isNullOrBlank()
        ) {
            "The backend returned an invalid translation"
        }

        return TranslationResult(
            sourceText = input,
            translatedText = translatedText.trim(),
            sourceLanguage = "auto",
            targetLanguage = targetLanguage.code
        )
    }
}
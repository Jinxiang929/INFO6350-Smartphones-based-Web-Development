package com.example.week7speechtools.repository

import com.example.week7speechtools.auth.SessionStore
import com.example.week7speechtools.model.SaveRecordRequest
import com.example.week7speechtools.model.SpeechRecord
import com.example.week7speechtools.network.ApiService

class SheetsRepository(
    private val apiService: ApiService
) {

    suspend fun save(record: SpeechRecord) {
        val token = SessionStore.getAccessToken()

        check(!token.isNullOrBlank()) {
            "Sign in before saving records"
        }

        val response = apiService.saveRecord(
            authorization = "Bearer $token",
            request = SaveRecordRequest(
                recordId = record.recordId,
                operation = record.operation,
                text = record.text,
                translatedText = record.translatedText,
                targetLanguage = record.targetLanguage,
                sourceLanguage = record.sourceLanguage
            )
        )

        check(
            response.success &&
                    response.recordId == record.recordId
        ) {
            "The backend did not confirm the saved record"
        }
    }
}
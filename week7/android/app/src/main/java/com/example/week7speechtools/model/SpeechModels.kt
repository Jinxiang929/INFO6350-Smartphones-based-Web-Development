package com.example.week7speechtools.model

import com.google.gson.annotations.SerializedName

data class TranscriptionResponse(
    val success: Boolean = false,
    val text: String? = null
)

data class SynthesisRequest(
    val text: String
)

data class LogoutResponse(
    val success: Boolean = false,
    val message: String? = null
)

data class ApiErrorResponse(
    val error: String? = null
)

data class SaveRecordRequest(
    @SerializedName("record_id")
    val recordId: String,

    val operation: String,

    val text: String,

    @SerializedName("translated_text")
    val translatedText: String? = null,

    @SerializedName("target_language")
    val targetLanguage: String? = null,

    @SerializedName("source_language")
    val sourceLanguage: String? = null
)

data class SaveRecordResponse(
    val success: Boolean = false,

    @SerializedName("record_id")
    val recordId: String? = null,

    val message: String? = null
)

enum class SaveStatus {
    PENDING,
    SAVING,
    SAVED,
    FAILED
}

data class SpeechRecord(
    val recordId: String,
    val operation: String,
    val text: String,
    val translatedText: String? = null,
    val targetLanguage: String? = null,
    val sourceLanguage: String? = null,
    val status: SaveStatus = SaveStatus.PENDING,
    val error: String? = null
)
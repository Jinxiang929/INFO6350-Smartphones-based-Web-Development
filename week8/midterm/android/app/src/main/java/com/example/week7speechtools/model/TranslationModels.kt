package com.example.week7speechtools.model

import com.google.gson.annotations.SerializedName

enum class TranslationLanguage(
    val code: String,
    val displayName: String
) {
    ENGLISH("en", "English"),
    CHINESE("zh", "Simplified Chinese"),
    SPANISH("es", "Spanish"),
    FRENCH("fr", "French"),
    GERMAN("de", "German"),
    JAPANESE("ja", "Japanese"),
    KOREAN("ko", "Korean"),
    ITALIAN("it", "Italian"),
    PORTUGUESE("pt", "Portuguese"),
    RUSSIAN("ru", "Russian"),
    ARABIC("ar", "Arabic"),
    HINDI("hi", "Hindi")
}

data class TranslationRequest(
    val text: String,

    @SerializedName("target_language")
    val targetLanguage: String,

    @SerializedName("source_language")
    val sourceLanguage: String = "auto"
)

data class TranslationResponse(
    val success: Boolean = false,

    @SerializedName("source_text")
    val sourceText: String? = null,

    @SerializedName("translated_text")
    val translatedText: String? = null,

    @SerializedName("source_language")
    val sourceLanguage: String? = null,

    @SerializedName("target_language")
    val targetLanguage: String? = null
)

data class TranslationResult(
    val sourceText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String
)
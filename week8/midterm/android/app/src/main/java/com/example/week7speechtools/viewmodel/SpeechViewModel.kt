package com.example.week7speechtools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.week7speechtools.audio.AudioRecorder
import com.example.week7speechtools.audio.SpeechPlayer
import com.example.week7speechtools.model.ApiErrorResponse
import com.example.week7speechtools.model.SaveStatus
import com.example.week7speechtools.model.SpeechRecord
import com.example.week7speechtools.model.TranslationLanguage
import com.example.week7speechtools.network.RetrofitClient
import com.example.week7speechtools.repository.SheetsRepository
import com.example.week7speechtools.repository.SpeechRepository
import com.example.week7speechtools.repository.TranslationRepository
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class SpeechUiState(
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val transcript: String = "",
    val transcriptRecord: SpeechRecord? = null,
    val message: String = "Tap Start Recording and speak.",
    val isError: Boolean = false,

    val ttsText: String = "",
    val targetLanguage: TranslationLanguage =
        TranslationLanguage.SPANISH,
    val isTranslating: Boolean = false,
    val translatedText: String = "",
    val translationRecord: SpeechRecord? = null,
    val translationMessage: String =
        "Enter text and choose a target language.",
    val translationIsError: Boolean = false,

    val isGenerating: Boolean = false,
    val isPreparing: Boolean = false,
    val isPlaying: Boolean = false,
    val hasAudio: Boolean = false,
    val ttsMessage: String =
        "Translate text before generating speech.",
    val ttsIsError: Boolean = false,

    val sessionExpired: Boolean = false
) {
    val isSaving: Boolean
        get() = transcriptRecord?.status == SaveStatus.SAVING ||
                translationRecord?.status == SaveStatus.SAVING

    val isBusy: Boolean
        get() = isRecording ||
                isTranscribing ||
                isTranslating ||
                isGenerating ||
                isPreparing ||
                isPlaying ||
                isSaving
}

class SpeechViewModel(
    private val recorder: AudioRecorder,
    private val player: SpeechPlayer,
    private val speechRepository: SpeechRepository,
    private val translationRepository: TranslationRepository,
    private val sheetsRepository: SheetsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpeechUiState())

    val uiState: StateFlow<SpeechUiState> =
        _uiState.asStateFlow()

    private var autoStopJob: Job? = null
    private var transcriptionJob: Job? = null
    private var translationJob: Job? = null
    private var generationJob: Job? = null
    private var saveJob: Job? = null

    private var audioFile: File? = null

    private fun blocked(): Boolean {
        return _uiState.value.isBusy ||
                _uiState.value.sessionExpired
    }

    fun startRecording() {
        if (blocked()) {
            return
        }

        try {
            recorder.start()

            _uiState.value = _uiState.value.copy(
                isRecording = true,
                transcript = "",
                transcriptRecord = null,
                message = "Recording... Tap Stop Recording when finished.",
                isError = false
            )

            autoStopJob = viewModelScope.launch {
                delay(60_000L)
                stopAndTranscribe()
            }
        } catch (error: Exception) {
            recorder.cancel()
            showError(
                "Unable to start recording. Check microphone access."
            )
        }
    }

    fun stopAndTranscribe() {
        if (!_uiState.value.isRecording) {
            return
        }

        autoStopJob?.cancel()
        autoStopJob = null

        val file = try {
            recorder.stop()
        } catch (error: Exception) {
            recorder.cancel()

            _uiState.value = _uiState.value.copy(
                isRecording = false,
                message = "Record for at least one second and try again.",
                isError = true
            )

            return
        }

        _uiState.value = _uiState.value.copy(
            isRecording = false,
            isTranscribing = true,
            message = "Transcribing your recording...",
            isError = false
        )

        transcriptionJob = viewModelScope.launch {
            try {
                val text = speechRepository.transcribe(file)

                // Keep transcription separate from translation.
                _uiState.value = _uiState.value.copy(
                    isTranscribing = false,
                    transcript = text,
                    transcriptRecord = SpeechRecord(
                        recordId = UUID.randomUUID().toString(),
                        operation = "speech_to_text",
                        text = text
                    ),
                    message = "Transcription complete. The text has not been saved.",
                    isError = false
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTranscribing = false,
                    message = errorMessage(
                        error,
                        "Transcription failed."
                    ),
                    isError = true,
                    sessionExpired = isUnauthorized(error)
                )
            } finally {
                file.delete()
            }
        }
    }

    fun updateTtsText(text: String) {
        if (
            blocked() ||
            text.length > 2000 ||
            text == _uiState.value.ttsText
        ) {
            return
        }

        clearTranslation()

        _uiState.value = _uiState.value.copy(
            ttsText = text,
            translationMessage =
                "Choose a target language and tap Translate."
        )
    }

    fun selectTargetLanguage(language: TranslationLanguage) {
        if (
            blocked() ||
            language == _uiState.value.targetLanguage
        ) {
            return
        }

        clearTranslation()

        _uiState.value = _uiState.value.copy(
            targetLanguage = language,
            translationMessage =
                "Target language changed. Tap Translate."
        )
    }

    fun translateText() {
        if (blocked()) {
            return
        }

        val text = _uiState.value.ttsText.trim()
        val language = _uiState.value.targetLanguage

        if (text.isBlank() || text.length > 2000) {
            showTranslationError(
                "Enter between 1 and 2000 characters."
            )
            return
        }

        clearTranslation()

        _uiState.value = _uiState.value.copy(
            isTranslating = true,
            translationMessage = "Translating...",
            translationIsError = false
        )

        translationJob = viewModelScope.launch {
            try {
                val result = translationRepository.translate(
                    text = text,
                    targetLanguage = language
                )

                val record = SpeechRecord(
                    recordId = UUID.randomUUID().toString(),
                    operation = "translation",
                    text = result.sourceText,
                    translatedText = result.translatedText,
                    targetLanguage = result.targetLanguage,
                    sourceLanguage = result.sourceLanguage
                )

                _uiState.value = _uiState.value.copy(
                    isTranslating = false,
                    ttsText = result.sourceText,
                    translatedText = result.translatedText,
                    translationRecord = record,
                    translationMessage =
                        "Translation complete. The result has not been saved.",
                    translationIsError = false,
                    ttsMessage = if (
                        result.translatedText.length <= 2000
                    ) {
                        "Tap Generate Speech to hear the translation."
                    } else {
                        "The translation exceeds the 2000-character audio limit. Use shorter original text."
                    },
                    ttsIsError = false
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTranslating = false,
                    translationMessage = errorMessage(
                        error,
                        "Translation failed."
                    ),
                    translationIsError = true,
                    sessionExpired = isUnauthorized(error)
                )
            }
        }
    }

    fun generateAndPlay() {
        if (blocked()) {
            return
        }

        val text = _uiState.value
            .translationRecord
            ?.translatedText
            .orEmpty()

        if (text.isBlank() || text.length > 2000) {
            showTtsError(
                "Translate text first. Audio supports up to 2000 characters."
            )
            return
        }

        deleteAudio()

        _uiState.value = _uiState.value.copy(
            isGenerating = true,
            hasAudio = false,
            ttsMessage = "Generating speech from the translation...",
            ttsIsError = false
        )

        generationJob = viewModelScope.launch {
            try {
                val file = speechRepository.synthesize(text)
                audioFile = file

                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    hasAudio = true
                )

                // Generating audio does not create or save another record.
                startPlayback(file)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    ttsMessage = errorMessage(
                        error,
                        "Speech generation failed."
                    ),
                    ttsIsError = true,
                    sessionExpired = isUnauthorized(error)
                )
            }
        }
    }

    fun replay() {
        if (blocked()) {
            return
        }

        val file = audioFile

        if (file == null || !file.exists()) {
            showTtsError("Generate speech before replaying it.")
            return
        }

        startPlayback(file)
    }

    private fun startPlayback(file: File) {
        _uiState.value = _uiState.value.copy(
            isPreparing = true,
            isPlaying = false,
            ttsMessage = "Preparing audio...",
            ttsIsError = false
        )

        player.play(
            file = file,
            onStarted = {
                _uiState.value = _uiState.value.copy(
                    isPreparing = false,
                    isPlaying = true,
                    ttsMessage = "Playing the translated text."
                )
            },
            onEnded = {
                _uiState.value = _uiState.value.copy(
                    isPreparing = false,
                    isPlaying = false,
                    ttsMessage = "Playback ended. Tap Replay to listen again."
                )
            },
            onError = {
                _uiState.value = _uiState.value.copy(
                    isPreparing = false,
                    isPlaying = false,
                    ttsMessage = "Unable to play audio. You can try Replay.",
                    ttsIsError = true
                )
            }
        )
    }

    fun stopAudio() {
        if (
            !_uiState.value.isPlaying &&
            !_uiState.value.isPreparing
        ) {
            return
        }

        player.stop()

        _uiState.value = _uiState.value.copy(
            isPreparing = false,
            isPlaying = false,
            ttsMessage = "Playback stopped.",
            ttsIsError = false
        )
    }

    private fun clearTranslation() {
        deleteAudio()

        _uiState.value = _uiState.value.copy(
            translatedText = "",
            translationRecord = null,
            translationMessage = "",
            translationIsError = false,
            hasAudio = false,
            isPreparing = false,
            isPlaying = false,
            ttsMessage = "Translate text before generating speech.",
            ttsIsError = false
        )
    }

    fun retrySave(recordId: String) {
        if (blocked()) {
            return
        }

        val state = _uiState.value

        val record = listOfNotNull(
            state.transcriptRecord,
            state.translationRecord
        ).firstOrNull {
            it.recordId == recordId
        } ?: return

        if (
            record.status == SaveStatus.SAVING ||
            record.status == SaveStatus.SAVED
        ) {
            return
        }

        updateRecord(recordId, SaveStatus.SAVING)

        saveJob = viewModelScope.launch {
            try {
                // Reuse the same immutable record when retrying.
                sheetsRepository.save(record)
                updateRecord(recordId, SaveStatus.SAVED)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                updateRecord(
                    recordId = recordId,
                    status = SaveStatus.FAILED,
                    error = errorMessage(
                        error,
                        "Unable to save to Google Sheets."
                    )
                )

                if (isUnauthorized(error)) {
                    _uiState.value = _uiState.value.copy(
                        sessionExpired = true
                    )
                }
            }
        }
    }

    private fun updateRecord(
        recordId: String,
        status: SaveStatus,
        error: String? = null
    ) {
        val state = _uiState.value

        fun updated(record: SpeechRecord?): SpeechRecord? {
            return if (record?.recordId == recordId) {
                record.copy(
                    status = status,
                    error = error
                )
            } else {
                record
            }
        }

        _uiState.value = state.copy(
            transcriptRecord = updated(state.transcriptRecord),
            translationRecord = updated(state.translationRecord)
        )
    }

    fun showSaveError(recordId: String, message: String) {
        updateRecord(
            recordId = recordId,
            status = SaveStatus.FAILED,
            error = message
        )
    }

    fun cancelRecording() {
        if (!_uiState.value.isRecording) {
            return
        }

        autoStopJob?.cancel()
        autoStopJob = null

        recorder.cancel()

        _uiState.value = _uiState.value.copy(
            isRecording = false,
            message = "Recording cancelled because the app left the foreground.",
            isError = false
        )
    }

    fun onBackground() {
        cancelRecording()
        stopAudio()

        if (_uiState.value.isGenerating) {
            generationJob?.cancel()
            generationJob = null

            _uiState.value = _uiState.value.copy(
                isGenerating = false,
                ttsMessage = "Speech generation cancelled.",
                ttsIsError = false
            )
        }
    }

    fun showError(message: String) {
        _uiState.value = _uiState.value.copy(
            message = message,
            isError = true
        )
    }

    fun showTranslationError(message: String) {
        _uiState.value = _uiState.value.copy(
            translationMessage = message,
            translationIsError = true
        )
    }

    fun showTtsError(message: String) {
        _uiState.value = _uiState.value.copy(
            ttsMessage = message,
            ttsIsError = true
        )
    }

    fun reset() {
        autoStopJob?.cancel()
        transcriptionJob?.cancel()
        translationJob?.cancel()
        generationJob?.cancel()
        saveJob?.cancel()

        autoStopJob = null
        transcriptionJob = null
        translationJob = null
        generationJob = null
        saveJob = null

        recorder.cancel()
        deleteAudio()

        _uiState.value = SpeechUiState()
    }

    private fun deleteAudio() {
        player.stop()
        audioFile?.delete()
        audioFile = null
    }

    private fun isUnauthorized(error: Exception): Boolean {
        return error is HttpException && error.code() == 401
    }

    private fun errorMessage(
        error: Exception,
        fallback: String
    ): String {
        return when (error) {
            is HttpException -> {
                if (error.code() == 401) {
                    "Your session expired. Please sign in again."
                } else {
                    val serverMessage = runCatching {
                        Gson().fromJson(
                            error.response()?.errorBody()?.string(),
                            ApiErrorResponse::class.java
                        )?.error
                    }.getOrNull()

                    serverMessage?.takeIf {
                        it.isNotBlank()
                    } ?: "$fallback HTTP ${error.code()}."
                }
            }

            is IOException -> {
                "Unable to connect. Check the backend and network connection."
            }

            is IllegalArgumentException,
            is IllegalStateException -> {
                error.message?.takeIf {
                    it.isNotBlank()
                } ?: fallback
            }

            else -> fallback
        }
    }

    override fun onCleared() {
        reset()
        super.onCleared()
    }

    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                val apiService = RetrofitClient.apiService

                SpeechViewModel(
                    recorder = AudioRecorder(application),
                    player = SpeechPlayer(application),
                    speechRepository = SpeechRepository(
                        apiService = apiService,
                        cacheDir = application.cacheDir
                    ),
                    translationRepository =
                        TranslationRepository(apiService),
                    sheetsRepository =
                        SheetsRepository(apiService)
                )
            }
        }
    }
}
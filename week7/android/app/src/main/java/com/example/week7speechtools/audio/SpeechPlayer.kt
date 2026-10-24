package com.example.week7speechtools.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import java.io.File

class SpeechPlayer(context: Context) {

    private val audioManager = context.applicationContext
        .getSystemService(AudioManager::class.java)

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    fun play(
        file: File,
        onStarted: () -> Unit,
        onEnded: () -> Unit,
        onError: () -> Unit
    ) {
        stop()

        val currentPlayer = MediaPlayer()
        player = currentPlayer

        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val request = AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { change ->
                    val lostFocus =
                        change == AudioManager.AUDIOFOCUS_LOSS ||
                                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK

                    if (lostFocus && player === currentPlayer) {
                        stop()
                        onEnded()
                    }
                }
                .build()

            focusRequest = request

            if (
                audioManager.requestAudioFocus(request) !=
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            ) {
                stop()
                onError()
                return
            }

            currentPlayer.setAudioAttributes(attributes)
            currentPlayer.setDataSource(file.absolutePath)

            currentPlayer.setOnPreparedListener { preparedPlayer ->
                if (player === preparedPlayer) {
                    try {
                        preparedPlayer.start()
                        onStarted()
                    } catch (error: Exception) {
                        stop()
                        onError()
                    }
                }
            }

            currentPlayer.setOnCompletionListener { completedPlayer ->
                if (player === completedPlayer) {
                    stop()
                    onEnded()
                }
            }

            currentPlayer.setOnErrorListener { failedPlayer, _, _ ->
                if (player === failedPlayer) {
                    stop()
                    onError()
                }

                true
            }

            currentPlayer.prepareAsync()
        } catch (error: Exception) {
            stop()
            onError()
        }
    }

    fun stop() {
        val currentPlayer = player
        player = null

        runCatching { currentPlayer?.release() }

        val currentRequest = focusRequest
        focusRequest = null

        if (currentRequest != null) {
            runCatching {
                audioManager.abandonAudioFocusRequest(currentRequest)
            }
        }
    }
}
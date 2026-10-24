package com.example.week7speechtools.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException

class AudioRecorder(context: Context) {

    private val appContext = context.applicationContext

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0L

    @SuppressLint("MissingPermission")
    fun start() {
        check(recorder == null) {
            "A recording is already active"
        }

        val permissionGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            throw SecurityException("Microphone permission is required")
        }

        val file = File.createTempFile(
            "speech_",
            ".m4a",
            appContext.cacheDir
        )

        val activeRecorder = createRecorder()

        try {
            activeRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            activeRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            activeRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            activeRecorder.setAudioChannels(1)
            activeRecorder.setAudioSamplingRate(44_100)
            activeRecorder.setAudioEncodingBitRate(128_000)
            activeRecorder.setOutputFile(file.absolutePath)
            activeRecorder.prepare()
            activeRecorder.start()

            recorder = activeRecorder
            outputFile = file
            startedAt = SystemClock.elapsedRealtime()
        } catch (error: Exception) {
            runCatching { activeRecorder.release() }
            file.delete()
            throw error
        }
    }

    fun stop(): File {
        val activeRecorder = checkNotNull(recorder) {
            "No recording is active"
        }

        val file = checkNotNull(outputFile)

        try {
            if (SystemClock.elapsedRealtime() - startedAt < 1_000L) {
                throw IOException("Record for at least one second")
            }

            activeRecorder.stop()

            if (!file.exists() || file.length() == 0L) {
                throw IOException("The recording is empty")
            }

            return file
        } catch (error: Exception) {
            file.delete()
            throw error
        } finally {
            runCatching { activeRecorder.release() }
            recorder = null
            outputFile = null
        }
    }

    fun cancel() {
        recorder?.let { activeRecorder ->
            runCatching { activeRecorder.stop() }
            runCatching { activeRecorder.release() }
        }

        recorder = null

        outputFile?.delete()
        outputFile = null
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(appContext)
        } else {
            MediaRecorder()
        }
    }
}
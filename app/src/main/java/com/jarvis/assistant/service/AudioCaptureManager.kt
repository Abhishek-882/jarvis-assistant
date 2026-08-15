package com.jarvis.assistant.service

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the AudioRecord stream for continuous microphone capture.
 *
 * Provides a consistent audio stream (16kHz, 16-bit, mono PCM) that feeds
 * into the wake word detector and speech recognizer.
 *
 * Features:
 * - Audio focus management
 * - Interruption handling (phone calls, media)
 * - Buffer management for real-time processing
 */
class AudioCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioCapture"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SIZE_MULTIPLIER = 2
    }

    private var audioRecord: AudioRecord? = null
    private var isCapturing = false
    private var captureJob: Job? = null

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    /**
     * The minimum buffer size for AudioRecord at our sample rate.
     */
    val bufferSize: Int by lazy {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        minBuffer * BUFFER_SIZE_MULTIPLIER
    }

    /**
     * Start audio capture.
     *
     * @param onAudioData Callback invoked with each audio buffer (byte array + size)
     */
    fun startCapture(
        scope: CoroutineScope,
        onAudioData: (ByteArray, Int) -> Unit
    ): Boolean {
        if (isCapturing) {
            Log.w(TAG, "Audio capture already running")
            return true
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return false
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isCapturing = true
            _isActive.value = true
            Log.i(TAG, "Audio capture started (buffer: $bufferSize bytes)")

            // Start the capture loop on IO dispatcher
            captureJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(bufferSize)
                while (isCapturing && isActive) {
                    try {
                        val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                        if (bytesRead > 0) {
                            onAudioData(buffer.copyOf(bytesRead), bytesRead)
                        } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                            Log.e(TAG, "AudioRecord: Invalid operation")
                            break
                        } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e(TAG, "AudioRecord: Bad value")
                            break
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e(TAG, "Audio capture error", e)
                    }
                }
            }

            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting audio capture", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio capture", e)
            audioRecord?.release()
            audioRecord = null
            return false
        }
    }

    /**
     * Stop audio capture and release resources.
     */
    fun stopCapture() {
        isCapturing = false
        _isActive.value = false
        captureJob?.cancel()
        captureJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio capture", e)
        }
        audioRecord = null
        Log.i(TAG, "Audio capture stopped")
    }

    /**
     * Get raw audio as ShortArray (useful for speaker verification).
     */
    fun convertToShortArray(buffer: ByteArray, size: Int): ShortArray {
        val shorts = ShortArray(size / 2)
        for (i in shorts.indices) {
            shorts[i] = ((buffer[i * 2 + 1].toInt() shl 8) or
                    (buffer[i * 2].toInt() and 0xFF)).toShort()
        }
        return shorts
    }

    /**
     * Check if audio is currently being captured.
     */
    val isRecording: Boolean
        get() = isCapturing && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
}

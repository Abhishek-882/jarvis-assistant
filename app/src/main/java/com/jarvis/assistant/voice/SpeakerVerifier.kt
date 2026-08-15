package com.jarvis.assistant.voice

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.vosk.Model
import org.vosk.Recognizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Speaker Verification using voice embeddings.
 *
 * Since TFLite speaker embedding models require specific model files,
 * this implementation uses a simplified approach based on voice characteristics
 * extracted during enrollment. In production, replace with ECAPA-TDNN or similar.
 *
 * Current approach:
 * - During enrollment: Record multiple phrases, extract audio features (energy, pitch patterns)
 * - During verification: Compare incoming audio features with enrolled profile
 * - Uses a confidence threshold for accept/reject
 */
class SpeakerVerifier {

    companion object {
        private const val TAG = "SpeakerVerifier"
        private const val SIMILARITY_THRESHOLD = 0.70f
        private const val ENROLLMENT_PHRASES = 5
    }

    // Voice profile data
    private var enrolledProfile: VoiceProfile? = null
    private var isEnrolled = false

    /**
     * Check if a voice profile has been enrolled.
     */
    val hasEnrolledVoice: Boolean
        get() = isEnrolled

    /**
     * Enrollment state flow for UI observation.
     */
    private val _enrollmentProgress = MutableStateFlow(0)
    val enrollmentProgress: StateFlow<Int> = _enrollmentProgress

    /**
     * Start voice enrollment — records audio samples to build a voice profile.
     *
     * @param audioSamples List of PCM audio byte arrays from enrollment recordings
     * @return true if enrollment succeeded
     */
    fun enrollVoice(audioSamples: List<ShortArray>): Boolean {
        if (audioSamples.size < ENROLLMENT_PHRASES) {
            Log.w(TAG, "Need at least $ENROLLMENT_PHRASES samples, got ${audioSamples.size}")
            return false
        }

        try {
            // Extract features from each sample
            val features = audioSamples.map { sample ->
                extractVoiceFeatures(sample)
            }

            // Average the features to create a master profile
            val avgEnergy = features.map { it.averageEnergy }.average().toFloat()
            val avgZeroCrossing = features.map { it.zeroCrossingRate }.average().toFloat()
            val avgPeakFrequency = features.map { it.dominantFrequencyBand }.average().toFloat()
            val avgSpeechRate = features.map { it.speechRateEstimate }.average().toFloat()
            val avgSpectralCentroid = features.map { it.spectralCentroid }.average().toFloat()

            enrolledProfile = VoiceProfile(
                averageEnergy = avgEnergy,
                zeroCrossingRate = avgZeroCrossing,
                dominantFrequencyBand = avgPeakFrequency,
                speechRateEstimate = avgSpeechRate,
                spectralCentroid = avgSpectralCentroid,
                enrollmentTimestamp = System.currentTimeMillis()
            )

            isEnrolled = true
            Log.i(TAG, "Voice enrollment successful. Profile: $enrolledProfile")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Voice enrollment failed", e)
            return false
        }
    }

    /**
     * Verify if the given audio matches the enrolled voice profile.
     *
     * @param audioSample PCM audio data to verify
     * @return Pair of (isMatch, confidence score 0.0-1.0)
     */
    fun verifyVoice(audioSample: ShortArray): Pair<Boolean, Float> {
        val profile = enrolledProfile ?: return Pair(true, 1.0f) // If not enrolled, accept all

        try {
            val features = extractVoiceFeatures(audioSample)
            val similarity = calculateSimilarity(profile, features)

            val isMatch = similarity >= SIMILARITY_THRESHOLD
            Log.d(TAG, "Voice verification: similarity=$similarity, match=$isMatch")
            return Pair(isMatch, similarity)

        } catch (e: Exception) {
            Log.e(TAG, "Voice verification error", e)
            return Pair(true, 0.5f) // On error, allow through with low confidence
        }
    }

    /**
     * Extract voice characteristics from audio samples.
     */
    private fun extractVoiceFeatures(audio: ShortArray): VoiceFeatures {
        if (audio.isEmpty()) return VoiceFeatures()

        // Average energy (RMS)
        val rms = Math.sqrt(audio.map { it.toDouble() * it.toDouble() }.average())
        val normalizedEnergy = (rms / Short.MAX_VALUE).toFloat()

        // Zero crossing rate (correlates with pitch)
        var zeroCrossings = 0
        for (i in 1 until audio.size) {
            if ((audio[i] >= 0 && audio[i - 1] < 0) || (audio[i] < 0 && audio[i - 1] >= 0)) {
                zeroCrossings++
            }
        }
        val zcr = zeroCrossings.toFloat() / audio.size

        // Simple spectral centroid estimation using zero crossings
        // Higher ZCR correlates with higher frequency content
        val spectralCentroid = zcr * 8000 // Scale to approximate Hz at 16kHz sample rate

        // Dominant frequency band estimation
        val dominantBand = spectralCentroid / 1000 // Rough band 0-8

        // Speech rate estimate based on energy envelope changes
        val frameSize = 160 // 10ms at 16kHz
        val energyEnvelope = audio.toList().chunked(frameSize).map { frame ->
            frame.map { it.toDouble() * it.toDouble() }.average()
        }
        val speechTransitions = energyEnvelope.zipWithNext().count { (a, b) ->
            (a > 100 && b < 50) || (a < 50 && b > 100)
        }
        val speechRate = speechTransitions.toFloat() / (audio.size.toFloat() / 16000f) // transitions per second

        return VoiceFeatures(
            averageEnergy = normalizedEnergy,
            zeroCrossingRate = zcr,
            dominantFrequencyBand = dominantBand,
            speechRateEstimate = speechRate,
            spectralCentroid = spectralCentroid
        )
    }

    /**
     * Calculate cosine-like similarity between enrolled profile and incoming features.
     */
    private fun calculateSimilarity(profile: VoiceProfile, features: VoiceFeatures): Float {
        // Normalized difference for each feature (0 = identical, 1 = completely different)
        val energyDiff = Math.abs(profile.averageEnergy - features.averageEnergy) /
                maxOf(profile.averageEnergy, features.averageEnergy, 0.001f)
        val zcrDiff = Math.abs(profile.zeroCrossingRate - features.zeroCrossingRate) /
                maxOf(profile.zeroCrossingRate, features.zeroCrossingRate, 0.001f)
        val freqDiff = Math.abs(profile.dominantFrequencyBand - features.dominantFrequencyBand) /
                maxOf(profile.dominantFrequencyBand, features.dominantFrequencyBand, 0.001f)
        val rateDiff = Math.abs(profile.speechRateEstimate - features.speechRateEstimate) /
                maxOf(profile.speechRateEstimate, features.speechRateEstimate, 0.001f)
        val centroidDiff = Math.abs(profile.spectralCentroid - features.spectralCentroid) /
                maxOf(profile.spectralCentroid, features.spectralCentroid, 0.001f)

        // Weighted average similarity (1 - diff)
        val similarity = 1.0f - (
            energyDiff * 0.15f +
            zcrDiff * 0.25f +
            freqDiff * 0.25f +
            rateDiff * 0.15f +
            centroidDiff * 0.20f
        )

        return similarity.coerceIn(0f, 1f)
    }

    /**
     * Clear the enrolled voice profile.
     */
    fun clearEnrollment() {
        enrolledProfile = null
        isEnrolled = false
        _enrollmentProgress.value = 0
        Log.i(TAG, "Voice enrollment cleared")
    }

    /**
     * Serialize the voice profile for storage.
     */
    fun getProfileData(): String? {
        return enrolledProfile?.let {
            "${it.averageEnergy}|${it.zeroCrossingRate}|${it.dominantFrequencyBand}|${it.speechRateEstimate}|${it.spectralCentroid}|${it.enrollmentTimestamp}"
        }
    }

    /**
     * Restore a voice profile from serialized data.
     */
    fun restoreProfile(data: String): Boolean {
        return try {
            val parts = data.split("|")
            if (parts.size >= 6) {
                enrolledProfile = VoiceProfile(
                    averageEnergy = parts[0].toFloat(),
                    zeroCrossingRate = parts[1].toFloat(),
                    dominantFrequencyBand = parts[2].toFloat(),
                    speechRateEstimate = parts[3].toFloat(),
                    spectralCentroid = parts[4].toFloat(),
                    enrollmentTimestamp = parts[5].toLong()
                )
                isEnrolled = true
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore voice profile", e)
            false
        }
    }
}

/**
 * Voice profile — averaged characteristics from enrollment.
 */
data class VoiceProfile(
    val averageEnergy: Float = 0f,
    val zeroCrossingRate: Float = 0f,
    val dominantFrequencyBand: Float = 0f,
    val speechRateEstimate: Float = 0f,
    val spectralCentroid: Float = 0f,
    val enrollmentTimestamp: Long = 0L
)

/**
 * Voice features extracted from a single audio sample.
 */
data class VoiceFeatures(
    val averageEnergy: Float = 0f,
    val zeroCrossingRate: Float = 0f,
    val dominantFrequencyBand: Float = 0f,
    val speechRateEstimate: Float = 0f,
    val spectralCentroid: Float = 0f
)

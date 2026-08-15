package com.jarvis.assistant.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.vosk.Model
import org.vosk.Recognizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Speech recognizer using Vosk for offline speech-to-text.
 *
 * Two modes:
 * 1. Wake word mode: Grammar-restricted recognition, only listens for "jarvis"
 * 2. Full recognition mode: Full vocabulary STT for command processing
 *
 * Auto-Download Feature:
 * If the model is not bundled in assets, it automatically downloads and extracts
 * the lightweight model directly to internal storage. No manual file management needed!
 */
class SpeechRecognizerManager(private val context: Context) {

    companion object {
        private const val TAG = "JarvisSTT"
        private const val SAMPLE_RATE = 16000f
        private const val WAKE_WORD = "jarvis"
        private const val WAKE_WORD_CONFIDENCE = 0.80f
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    }

    private var model: Model? = null
    private var wakeWordRecognizer: Recognizer? = null
    private var commandRecognizer: Recognizer? = null
    private var isModelLoaded = false

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Initialize the Vosk model.
     * Checks internal storage, then assets, and finally auto-downloads if missing.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Initializing Vosk model...")

            val modelDir = File(context.filesDir, "vosk-model")

            // Check if model directory is already valid (has am, conf, graph, etc.)
            if (!isValidModelDir(modelDir)) {
                Log.i(TAG, "Model not ready in internal storage. Checking assets...")
                val extractedFromAssets = tryExtractModelFromAssets(modelDir)
                
                if (!extractedFromAssets || !isValidModelDir(modelDir)) {
                    Log.i(TAG, "Model not found in assets. Auto-downloading model in background...")
                    val downloaded = downloadAndExtractModel(modelDir)
                    if (!downloaded) {
                        Log.e(TAG, "Failed to auto-download Vosk model")
                    }
                }
            }

            if (isValidModelDir(modelDir)) {
                model = Model(modelDir.absolutePath)
                isModelLoaded = true
                Log.i(TAG, "Vosk model loaded successfully from ${modelDir.absolutePath}")
                true
            } else {
                Log.e(TAG, "No valid Vosk model found.")
                isModelLoaded = false
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Vosk model", e)
            isModelLoaded = false
            false
        }
    }

    /**
     * Checks if the directory contains a valid Vosk model.
     */
    private fun isValidModelDir(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        val files = dir.list() ?: return false
        return files.contains("am") || files.contains("conf") || files.contains("graph") ||
                files.any { File(dir, it).isDirectory && isValidModelDir(File(dir, it)) }
    }

    /**
     * Download and extract the Vosk model zip automatically.
     */
    private fun downloadAndExtractModel(targetDir: File): Boolean {
        return try {
            val zipFile = File(context.cacheDir, "vosk_temp.zip")
            Log.i(TAG, "Downloading Vosk model from $MODEL_URL...")

            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
                return false
            }

            connection.inputStream.use { input ->
                FileOutputStream(zipFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.i(TAG, "Download complete. Extracting model...")
            targetDir.mkdirs()

            // Extract zip
            ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    // Strip the root folder name from the zip (e.g. "vosk-model-small-en-us-0.15/...")
                    val pathParts = entry.name.split("/")
                    val relativePath = if (pathParts.size > 1) {
                        pathParts.drop(1).joinToString("/")
                    } else {
                        entry.name
                    }

                    if (relativePath.isNotBlank()) {
                        val outFile = File(targetDir, relativePath)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                zipIn.copyTo(out)
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            // Cleanup zip
            zipFile.delete()
            Log.i(TAG, "Vosk model auto-downloaded and extracted successfully!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading or extracting model", e)
            false
        }
    }

    /**
     * Create a wake word recognizer that only listens for "jarvis".
     */
    fun createWakeWordRecognizer(): Recognizer? {
        if (!isModelLoaded || model == null) return null

        return try {
            val grammar = """["jarvis", "hey jarvis", "[unk]"]"""
            val recognizer = Recognizer(model, SAMPLE_RATE, grammar)
            recognizer.setWords(true)
            wakeWordRecognizer = recognizer
            Log.i(TAG, "Wake word recognizer created")
            recognizer
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create wake word recognizer", e)
            null
        }
    }

    /**
     * Create a full vocabulary recognizer for command processing.
     */
    fun createCommandRecognizer(): Recognizer? {
        if (!isModelLoaded || model == null) return null

        return try {
            val recognizer = Recognizer(model, SAMPLE_RATE)
            recognizer.setWords(true)
            commandRecognizer = recognizer
            Log.i(TAG, "Command recognizer created")
            recognizer
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create command recognizer", e)
            null
        }
    }

    /**
     * Process audio buffer for wake word detection.
     */
    fun processWakeWord(buffer: ByteArray, size: Int): WakeWordResult {
        val recognizer = wakeWordRecognizer ?: return WakeWordResult(false)

        return try {
            if (recognizer.acceptWaveForm(buffer, size)) {
                val result = recognizer.result
                val parsed = parseResult(result)
                val text = parsed.first.lowercase().trim()
                val confidence = parsed.second

                if (text.contains(WAKE_WORD) && confidence >= WAKE_WORD_CONFIDENCE) {
                    return WakeWordResult(detected = true, confidence = confidence)
                }
            } else {
                val partial = recognizer.partialResult
                val partialText = parsePartialResult(partial).lowercase().trim()
                if (partialText.contains(WAKE_WORD)) {
                    return WakeWordResult(detected = true, confidence = 0.85f, isPartial = true)
                }
            }
            WakeWordResult(false)
        } catch (e: Exception) {
            WakeWordResult(false)
        }
    }

    /**
     * Process audio for command recognition.
     */
    fun processCommand(buffer: ByteArray, size: Int): CommandResult {
        val recognizer = commandRecognizer ?: return CommandResult("", false)

        return try {
            if (recognizer.acceptWaveForm(buffer, size)) {
                val result = recognizer.result
                val parsed = parseResult(result)
                CommandResult(
                    text = parsed.first.trim(),
                    isFinal = true,
                    confidence = parsed.second
                )
            } else {
                val partial = recognizer.partialResult
                val partialText = parsePartialResult(partial)
                CommandResult(
                    text = partialText.trim(),
                    isFinal = false,
                    confidence = 0.5f
                )
            }
        } catch (e: Exception) {
            CommandResult("", false)
        }
    }

    fun getFinalCommandResult(): String {
        val recognizer = commandRecognizer ?: return ""
        return try {
            val result = recognizer.finalResult
            parseResult(result).first.trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun resetCommandRecognizer() {
        commandRecognizer?.reset()
    }

    private fun parseResult(jsonStr: String): Pair<String, Float> {
        return try {
            val obj = json.parseToJsonElement(jsonStr).jsonObject
            val text = obj["text"]?.jsonPrimitive?.content ?: ""
            Pair(text, if (text.isNotBlank()) 0.9f else 0f)
        } catch (e: Exception) {
            Pair("", 0f)
        }
    }

    private fun parsePartialResult(jsonStr: String): String {
        return try {
            val obj = json.parseToJsonElement(jsonStr).jsonObject
            obj["partial"]?.jsonPrimitive?.content ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Extract model from assets if available.
     */
    private fun tryExtractModelFromAssets(targetDir: File): Boolean {
        return try {
            val assetManager = context.assets
            val modelFiles = assetManager.list("model") ?: return false
            if (modelFiles.isEmpty()) return false

            targetDir.mkdirs()
            for (fileName in modelFiles) {
                val assetPath = "model/$fileName"
                val targetFile = File(targetDir, fileName)
                val subFiles = assetManager.list(assetPath)

                if (subFiles != null && subFiles.isNotEmpty()) {
                    extractAssetDir(assetPath, targetFile)
                } else {
                    assetManager.open(assetPath).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun extractAssetDir(assetPath: String, targetDir: File) {
        targetDir.mkdirs()
        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: return

        for (fileName in files) {
            val subAssetPath = "$assetPath/$fileName"
            val targetFile = File(targetDir, fileName)
            val subFiles = assetManager.list(subAssetPath)

            if (subFiles != null && subFiles.isNotEmpty()) {
                extractAssetDir(subAssetPath, targetFile)
            } else {
                assetManager.open(subAssetPath).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    fun shutdown() {
        wakeWordRecognizer?.close()
        commandRecognizer?.close()
        model?.close()
        wakeWordRecognizer = null
        commandRecognizer = null
        model = null
        isModelLoaded = false
        Log.i(TAG, "Vosk speech recognizer shutdown")
    }
}

data class WakeWordResult(
    val detected: Boolean,
    val confidence: Float = 0f,
    val isPartial: Boolean = false
)

data class CommandResult(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float = 0f
)

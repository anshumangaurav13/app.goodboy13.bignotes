package app.goodboy13.bignotes.core.search.recognition

import android.util.Log
import app.goodboy13.bignotes.core.search.model.ModelDownloadStatus
import app.goodboy13.bignotes.core.search.model.StrokeCluster
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Manages the on-device ML Kit Digital Ink Recognition model lifecycle and stroke conversion.
 * Once the initial ~20MB language model is downloaded, recognition is completely offline with zero latency.
 */
class DigitalInkRecognitionEngine(
    private val languageTag: String = "en-US"
) {
    private val TAG = "DigitalInkEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val remoteModelManager = RemoteModelManager.getInstance()

    private val modelIdentifier: DigitalInkRecognitionModelIdentifier? by lazy {
        DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
            ?: DigitalInkRecognitionModelIdentifier.fromLanguageTag("en")
    }

    private val model: DigitalInkRecognitionModel? by lazy {
        modelIdentifier?.let { id ->
            DigitalInkRecognitionModel.builder(id).build()
        }
    }

    private var recognizer: DigitalInkRecognizer? = null

    private val _downloadStatus = MutableStateFlow<ModelDownloadStatus>(ModelDownloadStatus.NotDownloaded)
    val downloadStatus: StateFlow<ModelDownloadStatus> = _downloadStatus.asStateFlow()

    init {
        scope.launch {
            checkAndInitModel()
        }
    }

    suspend fun checkAndInitModel(): Boolean {
        val targetModel = model ?: run {
            _downloadStatus.value = ModelDownloadStatus.Failed("Language $languageTag not supported")
            return false
        }

        return try {
            val isDownloaded = isModelDownloadedInternal(targetModel)
            if (isDownloaded) {
                initRecognizer(targetModel)
                _downloadStatus.value = ModelDownloadStatus.Ready
                true
            } else {
                _downloadStatus.value = ModelDownloadStatus.NotDownloaded
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking model status", e)
            _downloadStatus.value = ModelDownloadStatus.Failed(e.message ?: "Unknown error")
            false
        }
    }

    fun downloadModelAsync(onComplete: ((Boolean) -> Unit)? = null) {
        val targetModel = model ?: run {
            _downloadStatus.value = ModelDownloadStatus.Failed("Language $languageTag not supported")
            onComplete?.invoke(false)
            return
        }

        _downloadStatus.value = ModelDownloadStatus.Downloading
        scope.launch {
            try {
                val conditions = DownloadConditions.Builder().build()
                val success = suspendCancellableCoroutine<Boolean> { cont ->
                    remoteModelManager.download(targetModel, conditions)
                        .addOnSuccessListener {
                            cont.resume(true)
                        }
                        .addOnFailureListener { err ->
                            Log.e(TAG, "Model download failed", err)
                            cont.resume(false)
                        }
                }

                if (success) {
                    initRecognizer(targetModel)
                    _downloadStatus.value = ModelDownloadStatus.Ready
                    onComplete?.invoke(true)
                } else {
                    _downloadStatus.value = ModelDownloadStatus.Failed("Download failed")
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception downloading model", e)
                _downloadStatus.value = ModelDownloadStatus.Failed(e.message ?: "Download exception")
                onComplete?.invoke(false)
            }
        }
    }

    private fun initRecognizer(targetModel: DigitalInkRecognitionModel) {
        try {
            recognizer?.close()
            val options = DigitalInkRecognizerOptions.builder(targetModel).build()
            recognizer = DigitalInkRecognition.getClient(options)
            Log.i(TAG, "DigitalInkRecognizer initialized successfully for $languageTag")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DigitalInkRecognizer", e)
        }
    }

    private suspend fun isModelDownloadedInternal(targetModel: DigitalInkRecognitionModel): Boolean =
        suspendCancellableCoroutine { cont ->
            remoteModelManager.isModelDownloaded(targetModel)
                .addOnSuccessListener { downloaded ->
                    cont.resume(downloaded == true)
                }
                .addOnFailureListener { err ->
                    Log.w(TAG, "Error checking if model is downloaded", err)
                    cont.resume(false)
                }
        }

    /**
     * Converts a stroke cluster into an ML Kit Ink object and performs on-device handwriting recognition.
     * Returns the highest-confidence recognized text, or null if recognition fails or model is not ready.
     */
    suspend fun recognizeCluster(cluster: StrokeCluster): String? {
        val client = recognizer ?: run {
            if (_downloadStatus.value is ModelDownloadStatus.NotDownloaded) {
                // Attempt to auto-check once
                if (checkAndInitModel()) {
                    recognizer
                } else null
            } else null
        } ?: return null

        if (cluster.strokes.isEmpty()) return null

        val ink = convertClusterToInk(cluster)
        return try {
            suspendCancellableCoroutine { cont ->
                client.recognize(ink)
                    .addOnSuccessListener { result ->
                        val topCandidate = result.candidates.firstOrNull()?.text?.trim()
                        cont.resume(if (topCandidate.isNullOrEmpty()) null else topCandidate)
                    }
                    .addOnFailureListener { err ->
                        Log.w(TAG, "Ink recognition failed for cluster ${cluster.clusterId}", err)
                        cont.resume(null)
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Recognition error", e)
            null
        }
    }

    /**
     * Translates BigNotes Stroke points into an ML Kit Ink data structure.
     */
    fun convertClusterToInk(cluster: StrokeCluster): Ink {
        val inkBuilder = Ink.builder()
        for (stroke in cluster.strokes) {
            if (stroke.points.isEmpty()) continue
            val strokeBuilder = Ink.Stroke.builder()
            for (p in stroke.points) {
                val t = if (p.timeMillis > 0L) p.timeMillis else cluster.averageTimestamp
                strokeBuilder.addPoint(Ink.Point.create(p.x, p.y, t))
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }
        return inkBuilder.build()
    }

    fun close() {
        recognizer?.close()
        recognizer = null
    }
}

package app.goodboy13.bignotes.core.search.model

import app.goodboy13.bignotes.core.model.Stroke

/**
 * Represents a spatially and temporally clustered group of strokes (e.g., a handwritten line or phrase).
 */
data class StrokeCluster(
    val clusterId: String,
    val noteId: String,
    val strokes: List<Stroke>,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val averageTimestamp: Long = 0L
)

/**
 * Represents a match found when searching recognized handwriting.
 */
data class SearchHit(
    val noteId: String,
    val clusterId: String,
    val matchedText: String,
    val query: String,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val snippet: String
)

/**
 * Status of the offline ML Kit Digital Ink Recognition model.
 */
sealed class ModelDownloadStatus {
    object NotDownloaded : ModelDownloadStatus()
    object Downloading : ModelDownloadStatus()
    object Ready : ModelDownloadStatus()
    data class Failed(val message: String) : ModelDownloadStatus()
}

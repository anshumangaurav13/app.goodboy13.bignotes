package com.antigrav.bignotes.core.search.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity storing recognized handwritten text clusters with their spatial bounding boxes.
 */
@Entity(
    tableName = "handwriting_index",
    indices = [
        Index(value = ["noteId"]),
        Index(value = ["folderId"]),
        Index(value = ["recognizedText"])
    ]
)
data class HandwritingSearchEntity(
    @PrimaryKey
    val clusterId: String,
    val noteId: String,
    val folderId: String,
    val recognizedText: String,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val updatedAt: Long
)

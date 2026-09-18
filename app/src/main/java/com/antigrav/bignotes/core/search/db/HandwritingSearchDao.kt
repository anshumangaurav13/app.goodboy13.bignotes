package com.antigrav.bignotes.core.search.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object for fast querying and indexing of handwriting search clusters.
 */
@Dao
interface HandwritingSearchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<HandwritingSearchEntity>)

    /**
     * Case-insensitive substring search matching keywords within recognized handwriting.
     */
    @Query("SELECT * FROM handwriting_index WHERE recognizedText LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<HandwritingSearchEntity>

    @Query("SELECT * FROM handwriting_index WHERE noteId = :noteId")
    suspend fun getForNote(noteId: String): List<HandwritingSearchEntity>

    @Query("DELETE FROM handwriting_index WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: String)

    @Query("DELETE FROM handwriting_index WHERE folderId = :folderId")
    suspend fun deleteForFolder(folderId: String)

    @Query("DELETE FROM handwriting_index")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM handwriting_index")
    suspend fun count(): Int

    @Query("SELECT MAX(updatedAt) FROM handwriting_index WHERE noteId = :noteId")
    suspend fun getLastIndexedTimestamp(noteId: String): Long?

    @Query("SELECT noteId, MAX(updatedAt) as lastIndexedAt FROM handwriting_index GROUP BY noteId")
    suspend fun getAllIndexedNotesTimestamps(): List<NoteIndexTimestamp>
}

data class NoteIndexTimestamp(
    val noteId: String,
    val lastIndexedAt: Long
)

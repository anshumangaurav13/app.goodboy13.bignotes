package app.goodboy13.bignotes.core.storage

import app.goodboy13.bignotes.ui.model.FolderItem
import org.junit.Assert.*
import org.junit.Test

class BackupManagerTest {

    @Test
    fun testSnapshot_roundTripSerialization() {
        val folders = listOf(
            FolderItem(id = "folder_main", name = "Main", noteCount = 2),
            FolderItem(id = "folder_work", name = "Work & Research", noteCount = 1)
        )

        val notes = listOf(
            SnapshotNoteEntry(
                id = "note_1001",
                folderId = "folder_main",
                title = "Calculus Lecture Notes",
                createdAt = 1789456000000L,
                updatedAt = 1789456500000L,
                previewText = "42 strokes",
                blobId = "note_1001_abc123.bignote"
            ),
            SnapshotNoteEntry(
                id = "note_1002",
                folderId = "folder_work",
                title = "Engine Architecture",
                createdAt = 1789456100000L,
                updatedAt = 1789456600000L,
                previewText = "15 strokes",
                blobId = "note_1002_def456.bignote"
            )
        )

        val originalSnapshot = SnapshotManifest(
            id = "snap_1789457000000",
            timestamp = 1789457000000L,
            formattedDate = "Sep 15, 2026, 1:45 PM",
            trigger = "Manual Backup",
            folders = folders,
            notes = notes
        )

        val jsonStr = BackupManager.serializeSnapshot(originalSnapshot)
        assertTrue("JSON string must not be empty", jsonStr.isNotBlank())
        assertTrue("JSON must contain id", jsonStr.contains("snap_1789457000000"))
        assertTrue("JSON must contain Calculus Lecture Notes", jsonStr.contains("Calculus Lecture Notes"))

        val deserialized = BackupManager.deserializeSnapshot(jsonStr)
        assertNotNull("Deserialized snapshot must not be null", deserialized)
        assertEquals("snap_1789457000000", deserialized!!.id)
        assertEquals(1789457000000L, deserialized.timestamp)
        assertEquals("Sep 15, 2026, 1:45 PM", deserialized.formattedDate)
        assertEquals("Manual Backup", deserialized.trigger)

        assertEquals(2, deserialized.folders.size)
        assertEquals("folder_main", deserialized.folders[0].id)
        assertEquals("Main", deserialized.folders[0].name)

        assertEquals(2, deserialized.notes.size)
        assertEquals("note_1001", deserialized.notes[0].id)
        assertEquals("Calculus Lecture Notes", deserialized.notes[0].title)
        assertEquals("note_1001_abc123.bignote", deserialized.notes[0].blobId)
    }

    @Test
    fun testTimelineIndex_roundTripSerialization() {
        val summaries = listOf(
            TimelineSnapshotSummary(
                id = "snap_2",
                timestamp = 1789460000000L,
                formattedDate = "Sep 15, 2026, 2:00 PM",
                trigger = "Auto (App Minimized)",
                totalNotes = 5,
                totalFolders = 2,
                summary = "Calculus, Physics, Engine"
            ),
            TimelineSnapshotSummary(
                id = "snap_1",
                timestamp = 1789450000000L,
                formattedDate = "Sep 15, 2026, 1:00 PM",
                trigger = "Manual Backup",
                totalNotes = 2,
                totalFolders = 1,
                summary = "Calculus, Physics"
            )
        )

        val index = TimelineIndex(
            version = 1,
            lastUpdated = 1789460000000L,
            snapshots = summaries
        )

        val jsonStr = BackupManager.serializeTimelineIndex(index)
        assertTrue(jsonStr.contains("snap_2"))

        val deserialized = BackupManager.deserializeTimelineIndex(jsonStr)
        assertNotNull(deserialized)
        assertEquals(1, deserialized!!.version)
        assertEquals(2, deserialized.snapshots.size)
        assertEquals("snap_2", deserialized.snapshots[0].id)
        assertEquals("Auto (App Minimized)", deserialized.snapshots[0].trigger)
        assertEquals(5, deserialized.snapshots[0].totalNotes)
    }

    @Test
    fun testCrc32_deterministicChecksum() {
        val data1 = "Hello BigNotes Engine".toByteArray(Charsets.UTF_8)
        val data2 = "Hello BigNotes Engine".toByteArray(Charsets.UTF_8)
        val data3 = "Different Content".toByteArray(Charsets.UTF_8)

        val crc1 = BackupManager.computeCrc32(data1)
        val crc2 = BackupManager.computeCrc32(data2)
        val crc3 = BackupManager.computeCrc32(data3)

        assertEquals("Identical data must produce identical CRC-32", crc1, crc2)
        assertNotEquals("Different data must produce different CRC-32", crc1, crc3)
    }

    @Test
    fun testPruneTimelineIndex_keepLatestFilter() {
        val summaries = (1..10).map { i ->
            TimelineSnapshotSummary(
                id = "snap_$i",
                timestamp = 1000L * i,
                formattedDate = "Date $i",
                trigger = "Auto",
                totalNotes = i,
                totalFolders = 1,
                summary = "Note $i"
            )
        }.reversed() // newest first: snap_10, snap_9, ..., snap_1

        val index = TimelineIndex(version = 1, lastUpdated = 10000L, snapshots = summaries)
        assertEquals(10, index.snapshots.size)

        // Keep latest 3
        val keepCount = 3
        val pruned = index.snapshots.take(keepCount)
        assertEquals(3, pruned.size)
        assertEquals("snap_10", pruned[0].id)
        assertEquals("snap_9", pruned[1].id)
        assertEquals("snap_8", pruned[2].id)

        val deleted = index.snapshots.drop(keepCount)
        assertEquals(7, deleted.size)
        assertEquals("snap_7", deleted[0].id)
        assertEquals("snap_1", deleted.last().id)
    }

    @Test
    fun testBlobGarbageCollection_identifiesOrphans() {
        val activeNotes = listOf(
            SnapshotNoteEntry("n1", "f1", "Note 1", 0L, 0L, "", "n1_abc.bignote"),
            SnapshotNoteEntry("n2", "f1", "Note 2", 0L, 0L, "", "n2_def.bignote")
        )
        val activeBlobIds = mutableSetOf<String>()
        for (note in activeNotes) {
            activeBlobIds.add(note.blobId)
            activeBlobIds.add("${note.id}.bignote")
        }

        val allBlobsOnDisk = listOf(
            "n1_abc.bignote",
            "n2_def.bignote",
            "n3_old_orphan.bignote",
            "n4_deleted_orphan.bignote"
        )

        val orphans = allBlobsOnDisk.filter { !activeBlobIds.contains(it) }
        assertEquals(2, orphans.size)
        assertTrue(orphans.contains("n3_old_orphan.bignote"))
        assertTrue(orphans.contains("n4_deleted_orphan.bignote"))
    }
}

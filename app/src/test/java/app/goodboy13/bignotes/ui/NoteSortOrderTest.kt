package app.goodboy13.bignotes.ui

import app.goodboy13.bignotes.ui.model.NoteItem
import app.goodboy13.bignotes.ui.model.NoteSortOrder
import app.goodboy13.bignotes.ui.model.sortedWithOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteSortOrderTest {

    private val noteA = NoteItem(
        id = "1",
        folderId = "f1",
        title = "Apple Note",
        createdAt = 1000L,
        updatedAt = 5000L
    )

    private val noteB = NoteItem(
        id = "2",
        folderId = "f1",
        title = "banana Note",
        createdAt = 2000L,
        updatedAt = 3000L
    )

    private val noteC = NoteItem(
        id = "3",
        folderId = "f1",
        title = "Cherry Note",
        createdAt = 3000L,
        updatedAt = 4000L
    )

    private val noteD = NoteItem(
        id = "4",
        folderId = "f1",
        title = "date Note",
        createdAt = 4000L,
        updatedAt = 1000L
    )

    private val notes = listOf(noteC, noteA, noteD, noteB)

    @Test
    fun testSortByModifiedDesc() {
        val sorted = notes.sortedWithOrder(NoteSortOrder.MODIFIED_DESC)
        assertEquals(listOf(noteA, noteC, noteB, noteD), sorted)
    }

    @Test
    fun testSortByModifiedAsc() {
        val sorted = notes.sortedWithOrder(NoteSortOrder.MODIFIED_ASC)
        assertEquals(listOf(noteD, noteB, noteC, noteA), sorted)
    }

    @Test
    fun testSortByCreatedDesc() {
        val sorted = notes.sortedWithOrder(NoteSortOrder.CREATED_DESC)
        assertEquals(listOf(noteD, noteC, noteB, noteA), sorted)
    }

    @Test
    fun testSortByCreatedAsc() {
        val sorted = notes.sortedWithOrder(NoteSortOrder.CREATED_ASC)
        assertEquals(listOf(noteA, noteB, noteC, noteD), sorted)
    }

    @Test
    fun testSortByTitleAscCaseInsensitive() {
        val sorted = notes.sortedWithOrder(NoteSortOrder.TITLE_ASC)
        assertEquals(listOf(noteA, noteB, noteC, noteD), sorted)
    }

    @Test
    fun testSortByTitleDescCaseInsensitive() {
        val sorted = notes.sortedWithOrder(NoteSortOrder.TITLE_DESC)
        assertEquals(listOf(noteD, noteC, noteB, noteA), sorted)
    }

    @Test
    fun testEmptyList() {
        val empty = emptyList<NoteItem>().sortedWithOrder(NoteSortOrder.MODIFIED_DESC)
        assertEquals(emptyList<NoteItem>(), empty)
    }
}

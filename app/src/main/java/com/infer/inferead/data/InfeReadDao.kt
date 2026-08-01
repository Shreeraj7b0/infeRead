package com.infer.inferead.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InfeReadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Query("SELECT * FROM users LIMIT 1")
    fun getUser(): Flow<User?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibraryFile(file: LibraryFile): Long

    @Query("SELECT * FROM library_files ORDER BY addedAt DESC")
    fun getAllLibraryFiles(): Flow<List<LibraryFile>>

    @Query("UPDATE library_files SET thumbnailUri = :uri WHERE id = :fileId")
    suspend fun updateThumbnail(fileId: Int, uri: String): Int

    @Query("UPDATE library_files SET filePath = :newPath WHERE id = :fileId")
    suspend fun updateFilePath(fileId: Int, newPath: String): Int

    @Query("SELECT * FROM library_files WHERE id = :id")
    suspend fun getLibraryFileById(id: Int): LibraryFile?

    @Query("DELETE FROM library_files WHERE id = :fileId")
    suspend fun deleteFile(fileId: Int): Int

    @Query("UPDATE library_files SET title = :newTitle WHERE id = :fileId")
    suspend fun renameFile(fileId: Int, newTitle: String): Int

    @Query("UPDATE library_files SET isFinished = :isFinished, finishedAt = :finishedAt WHERE id = :fileId")
    suspend fun markFinished(fileId: Int, isFinished: Boolean, finishedAt: Long): Int

    @Query("UPDATE library_files SET rating = :rating WHERE id = :fileId")
    suspend fun updateRating(fileId: Int, rating: Int): Int

    @Query("UPDATE library_files SET isBookmarked = :isBookmarked WHERE id = :fileId")
    suspend fun toggleBookmark(fileId: Int, isBookmarked: Boolean): Int

    @Query("UPDATE library_files SET isBookmarked = 0 WHERE id = :fileId")
    suspend fun clearBookmarks(fileId: Int): Int

    @Query("UPDATE library_files SET isToRead = :isToRead WHERE id = :fileId")
    suspend fun markToRead(fileId: Int, isToRead: Boolean): Int

    @Query("UPDATE library_files SET currentPage = :currentPage, totalPages = :totalPages WHERE id = :fileId")
    suspend fun updatePageProgress(fileId: Int, currentPage: Int, totalPages: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklist(checklist: Checklist): Long

    @Query("SELECT * FROM checklists")
    fun getAllChecklists(): Flow<List<Checklist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklistItem(item: ChecklistItem): Long

    @Query("SELECT * FROM checklist_items WHERE checklistId = :checklistId ORDER BY isPinned DESC, sortOrder ASC, id ASC")
    fun getChecklistItems(checklistId: Int): Flow<List<ChecklistItem>>

    @Query("SELECT * FROM checklist_items WHERE id = :id LIMIT 1")
    suspend fun getChecklistItemById(id: Int): ChecklistItem?
    
    @androidx.room.Update
    suspend fun updateChecklistItem(item: ChecklistItem): Int

    @Query("DELETE FROM checklists WHERE id = :id")
    suspend fun deleteChecklist(id: Int): Int

    @Query("DELETE FROM checklist_items WHERE checklistId = :checklistId")
    suspend fun clearChecklistItems(checklistId: Int): Int

    @Query("UPDATE checklists SET name = :name WHERE id = :id")
    suspend fun renameChecklist(id: Int, name: String): Int

    @Query("UPDATE checklist_items SET isCompleted = :isCompleted WHERE checklistId = :checklistId")
    suspend fun markAllChecklistItemsCompletion(checklistId: Int, isCompleted: Boolean): Int

    @Query("UPDATE checklist_items SET isCompleted = :isCompleted WHERE id = :itemId")
    suspend fun updateChecklistItemCompletion(itemId: Int, isCompleted: Boolean): Int

    @Query("DELETE FROM checklist_items WHERE id = :itemId")
    suspend fun deleteChecklistItem(itemId: Int): Int

    @Query("UPDATE checklists SET colorHex = :colorHex WHERE id = :id")
    suspend fun updateChecklistColor(id: Int, colorHex: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingSession(session: ReadingSession): Long

    @Query("SELECT * FROM reading_sessions ORDER BY date DESC")
    fun getAllReadingSessions(): Flow<List<ReadingSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: Annotation): Long

    @Query("SELECT * FROM annotations WHERE fileId = :fileId")
    fun getAnnotations(fileId: Int): Flow<List<Annotation>>

    @androidx.room.Update
    suspend fun updateAnnotation(annotation: Annotation): Int

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteAnnotation(id: Int): Int

    @Query("SELECT * FROM reading_sessions WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getReadingSessionsBetween(startDate: Long, endDate: Long): Flow<List<ReadingSession>>

    // Bookshelf
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookshelf(bookshelf: Bookshelf): Long

    @Query("SELECT * FROM bookshelves ORDER BY sortOrder ASC")
    fun getAllBookshelves(): Flow<List<Bookshelf>>

    @Query("UPDATE bookshelves SET name = :name WHERE id = :id")
    suspend fun renameBookshelf(id: Int, name: String): Int

    @Query("UPDATE bookshelves SET colorHex = :colorHex WHERE id = :id")
    suspend fun updateBookshelfColor(id: Int, colorHex: String): Int

    @Query("UPDATE bookshelves SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateBookshelfSortOrder(id: Int, sortOrder: Int): Int

    @Query("UPDATE bookshelves SET isMinimised = :isMinimised WHERE id = :id")
    suspend fun updateBookshelfMinimised(id: Int, isMinimised: Boolean): Int

    @Query("DELETE FROM bookshelves WHERE id = :id")
    suspend fun deleteBookshelf(id: Int): Int

    // BookshelfItems
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookshelfItem(item: BookshelfItem): Long

    @Query("SELECT * FROM bookshelf_items")
    fun getAllBookshelfItems(): Flow<List<BookshelfItem>>

    @Query("DELETE FROM bookshelf_items WHERE id = :itemId")
    suspend fun deleteBookshelfItem(itemId: Int): Int

    @Query("DELETE FROM bookshelf_items WHERE bookshelfId = :bookshelfId AND fileId = :fileId")
    suspend fun deleteBookshelfItemByFile(bookshelfId: Int, fileId: Int): Int

    @Query("DELETE FROM bookshelf_items WHERE bookshelfId = :bookshelfId")
    suspend fun clearBookshelfItems(bookshelfId: Int): Int

    @Query("UPDATE bookshelf_items SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateBookshelfItemSortOrder(id: Int, sortOrder: Int): Int
}

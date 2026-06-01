package com.infer.inferead.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_files")
data class LibraryFile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val filePath: String,
    val format: String, // e.g. "PDF", "EPUB", "CBZ"
    val thumbnailUri: String? = null,
    val lastReadProgress: Float = 0f, // 0.0 to 1.0
    val addedAt: Long = System.currentTimeMillis(),
    val genre: String = "all genres",
    val isFinished: Boolean = false,
    val rating: Int = 0, // 0 to 5
    val totalPages: Int = 0,
    val currentPage: Int = 0,
    val isBookmarked: Boolean = false,
    val isToRead: Boolean = false
)

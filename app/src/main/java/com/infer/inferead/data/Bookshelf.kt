package com.infer.inferead.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookshelves")
data class Bookshelf(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val colorHex: String = "#FF9800",
    val sortOrder: Int = 0,
    val isMinimised: Boolean = false
)

@Entity(tableName = "bookshelf_items")
data class BookshelfItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val bookshelfId: Int,
    val fileId: Int,
    val sortOrder: Int = 0
)

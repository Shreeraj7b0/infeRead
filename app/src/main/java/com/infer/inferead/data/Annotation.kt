package com.infer.inferead.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "annotations")
data class Annotation(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val fileId: Int, // Refers to LibraryFile id
    val cfiRange: String, // The EPUB CFI or text offset range
    val colorHex: String, // e.g. "#FFFF00" for yellow, "" if just comment
    val selectedText: String = "",
    val textComment: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

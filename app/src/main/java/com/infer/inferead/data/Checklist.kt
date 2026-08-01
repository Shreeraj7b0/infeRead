package com.infer.inferead.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "checklists")
data class Checklist(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val colorHex: String = "#FF9800"
)

@Entity(tableName = "checklist_items")
data class ChecklistItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val checklistId: Int,
    val title: String, // Or reference to a LibraryFile ID
    val isCompleted: Boolean = false,
    val sortOrder: Int = 0,
    val indentLevel: Int = 0,
    val isPinned: Boolean = false
)

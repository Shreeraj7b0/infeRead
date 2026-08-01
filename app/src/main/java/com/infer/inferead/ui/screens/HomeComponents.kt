package com.infer.inferead.ui.screens

import com.infer.inferead.data.Checklist
import com.infer.inferead.data.ChecklistItem

enum class SegregationMode { FORMAT, PAGES, FILE_SIZE, BOOKMARKED, READING_LIST }

data class ChecklistItemMatch(
    val checklist: Checklist,
    val item: ChecklistItem
)

fun getSectionDisplayName(sectionName: String): String {
    return when (sectionName) {
        "EPUB" -> "Ebooks"
        "TXT" -> "Text"
        "CBZ", "CBR", "CB7" -> "Comic/Manga"
        "CODING" -> "Coding"
        "IMAGE" -> "Images"
        "PDF" -> "PDF"
        else -> sectionName
    }
}


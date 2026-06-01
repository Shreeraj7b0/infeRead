package com.infer.inferead.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_sessions")
data class ReadingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: Long, // timestamp
    val durationMinutes: Int
)

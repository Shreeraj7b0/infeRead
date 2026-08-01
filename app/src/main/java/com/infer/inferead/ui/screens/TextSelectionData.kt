package com.infer.inferead.ui.screens

data class TextSelectionData(
    val text: String,
    val top: Float,
    val bottom: Float,
    val cfiRange: String,
    val annId: Int? = null
)

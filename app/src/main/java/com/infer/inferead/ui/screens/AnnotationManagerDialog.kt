package com.infer.inferead.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infer.inferead.data.Annotation
import com.infer.inferead.data.LibraryFile
import com.infer.inferead.viewmodel.ReaderViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationManagerDialog(
    file: LibraryFile,
    annotations: List<Annotation>,
    viewModel: ReaderViewModel,
    onNavigate: (Annotation) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val isTxtGroup = file.format in listOf("TXT", "DOC", "DOCX")
    val isPdfComic = file.format in listOf("PDF", "CBZ", "CBR", "CB7")
    val tabs = when {
        isTxtGroup -> listOf("Highlights")
        isPdfComic -> listOf("Comments")
        else -> listOf("Highlights", "Comments")
    }
    
    val filteredAnnotations = annotations.filter { ann ->
        val currentTab = tabs[selectedTab]
        if (currentTab == "Highlights") ann.textComment.isNullOrEmpty() else !ann.textComment.isNullOrEmpty()
    }.sortedByDescending { it.timestamp }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.85f)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Annotations", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (filteredAnnotations.isNotEmpty()) {
                        TextButton(onClick = {
                            filteredAnnotations.forEach { viewModel.deleteAnnotation(it) }
                        }) {
                            Text("Clear All", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }
            
            if (tabs.size > 1) {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (filteredAnnotations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No ${tabs[selectedTab].lowercase()} found.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredAnnotations) { ann ->
                        AnnotationItem(
                            annotation = ann,
                            onClick = { onNavigate(ann) },
                            onDelete = { viewModel.deleteAnnotation(ann) }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
fun AnnotationItem(annotation: Annotation, onClick: () -> Unit, onDelete: () -> Unit) {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(annotation.timestamp))
    
    val highlightColor = try {
        if (annotation.colorHex.isNotEmpty()) Color(android.graphics.Color.parseColor(annotation.colorHex)) else Color.Transparent
    } catch (e: Exception) { Color.Transparent }
    
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)
        .clickable { onClick() }
        .background(highlightColor.copy(alpha = 0.2f), shape = MaterialTheme.shapes.small)
        .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "\"${annotation.selectedText}\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        
        if (!annotation.textComment.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                    .padding(8.dp)
            ) {
                Text(
                    text = annotation.textComment!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        val pageNumStr = annotation.cfiRange.split("|").firstOrNull()
        val pageNum = pageNumStr?.toIntOrNull()
        if (pageNum != null) {
            val displayNum = if (annotation.cfiRange.contains("|PAGE")) pageNum else pageNum + 1
            Text(
                text = "Page $displayNum",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

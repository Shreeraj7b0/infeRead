package com.infer.inferead.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.AsyncImage
import com.infer.inferead.data.Bookshelf
import com.infer.inferead.data.BookshelfItem
import com.infer.inferead.data.LibraryFile
import com.infer.inferead.viewmodel.HomeViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BookShelfTab(
    viewModel: HomeViewModel,
    bookshelves: List<Bookshelf>,
    bookshelfItems: List<BookshelfItem>,
    libraryFiles: List<LibraryFile>,
    onNavigateToReader: (Int) -> Unit,
    onContextMenu: (LibraryFile) -> Unit,
    bookshelfSortMode: Int,
    bookshelfViewMode: Int,
    onSortModeChange: (Int) -> Unit,
    onViewModeChange: (Int) -> Unit,
    isAssignmentMode: Boolean,
    onToggleAssignmentMode: () -> Unit
) {
    // 0: Alpha Asc, 1: Alpha Desc, 2: Count Asc, 3: Count Desc
    val sortedBookshelves = remember(bookshelves, bookshelfItems, bookshelfSortMode) {
        when (bookshelfSortMode) {
            0 -> bookshelves.sortedBy { it.name.lowercase() }
            1 -> bookshelves.sortedByDescending { it.name.lowercase() }
            2 -> bookshelves.sortedBy { shelf -> bookshelfItems.count { it.bookshelfId == shelf.id } }
            3 -> bookshelves.sortedByDescending { shelf -> bookshelfItems.count { it.bookshelfId == shelf.id } }
            else -> bookshelves.sortedBy { it.sortOrder }
        }
    }

    var draggedShelfId by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var draggedFileId by remember { mutableStateOf<Int?>(null) }
    var draggedFromShelfId by remember { mutableStateOf<Int?>(null) } // -1 means from assignment row
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var fileDragOffsetY by remember { mutableFloatStateOf(0f) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var fileToAssignOnCreate by remember { mutableStateOf<Int?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Content
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = if (isAssignmentMode) 120.dp else 80.dp)
            ) {
                if (bookshelves.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text("No bookshelves yet. Tap + to create one.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                    }
                }

                items(sortedBookshelves, key = { it.id }) { shelf ->
                    val isDraggingThis = draggedShelfId == shelf.id
                    val itemsInShelf = bookshelfItems.filter { it.bookshelfId == shelf.id }.sortedBy { it.sortOrder }
                    val filesInShelf = itemsInShelf.mapNotNull { item -> libraryFiles.find { it.id == item.fileId } }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItemPlacement()
                            .graphicsLayer {
                                if (isDraggingThis) translationY = dragOffsetY
                                shadowElevation = if (isDraggingThis) 8.dp.toPx() else 0f
                            }
                            .zIndex(if (isDraggingThis) 10f else 1f)
                    ) {
                        BookshelfRow(
                            shelf = shelf,
                            files = filesInShelf,
                            onNavigateToReader = onNavigateToReader,
                            onContextMenu = onContextMenu,
                            viewModel = viewModel,
                            viewMode = bookshelfViewMode,
                            isAssignmentMode = isAssignmentMode,
                            onShelfDragStart = { draggedShelfId = shelf.id; dragOffsetY = 0f },
                            onShelfDrag = { change, amount -> 
                                change.consume()
                                dragOffsetY += amount.y 
                                if (bookshelfSortMode != -1) onSortModeChange(-1) // Switch to manual sort automatically
                                if (bookshelfSortMode == -1 || bookshelfSortMode == -2 || true) { // Allow reorder anytime
                                    val currentIndex = sortedBookshelves.indexOf(shelf)
                                    val newIndex = if (dragOffsetY > 150f && currentIndex < sortedBookshelves.size - 1) {
                                        dragOffsetY = 0f; currentIndex + 1
                                    } else if (dragOffsetY < -150f && currentIndex > 0) {
                                        dragOffsetY = 0f; currentIndex - 1
                                    } else currentIndex

                                    if (currentIndex != newIndex) {
                                        val mutableShelves = sortedBookshelves.toMutableList()
                                        val removed = mutableShelves.removeAt(currentIndex)
                                        mutableShelves.add(newIndex, removed)
                                        val updatedShelves = mutableShelves.mapIndexed { index, s -> s.copy(sortOrder = index) }
                                        viewModel.updateBookshelvesOrder(updatedShelves)
                                    }
                                }
                            },
                            onShelfDragEnd = { draggedShelfId = null; dragOffsetY = 0f },
                            onFileDragStart = { fileId, _ -> draggedFileId = fileId; draggedFromShelfId = shelf.id; dragOffsetX = 0f; fileDragOffsetY = 0f },
                            onFileDrag = { change, amount ->
                                change.consume()
                                dragOffsetX += amount.x
                                fileDragOffsetY += amount.y
                            },
                            onFileDragEnd = {
                                if (draggedFileId != null) {
                                    if (fileDragOffsetY > 200f || fileDragOffsetY < -200f) {
                                        val dropIndex = sortedBookshelves.indexOf(shelf) + (if (fileDragOffsetY > 0) 1 else -1)
                                        if (dropIndex in sortedBookshelves.indices) {
                                            viewModel.removeFileFromBookshelf(shelf.id, draggedFileId!!)
                                            viewModel.addFileToBookshelf(sortedBookshelves[dropIndex].id, draggedFileId!!)
                                        } else {
                                            viewModel.removeFileFromBookshelf(shelf.id, draggedFileId!!) // Move to unassigned
                                        }
                                    }
                                }
                                draggedFileId = null; draggedFromShelfId = null; dragOffsetX = 0f; fileDragOffsetY = 0f
                            }
                        )
                    }
                }

                item {
                    ReadingGoalWidget(viewModel = viewModel, onNavigateToStats = {})
                }
            }
        }


        // Assignment Bottom Row
        AnimatedVisibility(
            visible = isAssignmentMode,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Drag to assign", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    IconButton(onClick = onToggleAssignmentMode) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                ) {
                    items(libraryFiles, key = { it.id }) { file ->
                        val isDraggingThis = draggedFileId == file.id && draggedFromShelfId == -1
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .fillMaxHeight()
                                .zIndex(if (isDraggingThis) 10f else 1f)
                                .pointerInput(file.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset -> draggedFileId = file.id; draggedFromShelfId = -1; dragOffsetX = 0f; fileDragOffsetY = 0f },
                                        onDrag = { change, dragAmount -> 
                                            change.consume()
                                            dragOffsetX += dragAmount.x
                                            fileDragOffsetY += dragAmount.y
                                        },
                                        onDragEnd = {
                                            // Determine drop target roughly based on Y offset (if it's negative enough, dropped on a shelf)
                                            if (fileDragOffsetY < -200f) {
                                                // Ideally, we calculate exact coordinates, but for simplicity here we assume if they drag UP, it goes to the first visible shelf or prompt create
                                                if (bookshelves.isEmpty()) {
                                                    fileToAssignOnCreate = file.id
                                                    showCreateDialog = true
                                                } else {
                                                    // Drop on first for now or implement exact hit testing
                                                    viewModel.addFileToBookshelf(bookshelves.first().id, file.id)
                                                }
                                            }
                                            draggedFileId = null; draggedFromShelfId = null; dragOffsetX = 0f; fileDragOffsetY = 0f
                                        },
                                        onDragCancel = { draggedFileId = null; draggedFromShelfId = null; dragOffsetX = 0f; fileDragOffsetY = 0f }
                                    )
                                }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(
                                    model = file.thumbnailUri,
                                    contentDescription = null,
                                    modifier = Modifier.size(75.dp, 100.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray)
                                )
                                Text(file.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("New Bookshelf") },
                text = {
                    OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("Name") })
                },
                confirmButton = {
                    TextButton(onClick = { 
                        viewModel.createBookshelf(name, "#FF9800", fileToAssignOnCreate)
                        showCreateDialog = false
                        fileToAssignOnCreate = null
                    }) { Text("Create") }
                }
            )
        }

        if (draggedFileId != null) {
            val file = libraryFiles.find { it.id == draggedFileId }
            if (file != null) {
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.TopStart,
                    offset = androidx.compose.ui.unit.IntOffset(dragOffsetX.toInt(), fileDragOffsetY.toInt())
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = file.thumbnailUri,
                            contentDescription = null,
                            modifier = Modifier.size(75.dp, 100.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray)
                        )
                        Text(file.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfRow(
    shelf: Bookshelf,
    files: List<LibraryFile>,
    onNavigateToReader: (Int) -> Unit,
    onContextMenu: (LibraryFile) -> Unit,
    viewModel: HomeViewModel,
    viewMode: Int,
    isAssignmentMode: Boolean,
    onShelfDragStart: (androidx.compose.ui.geometry.Offset) -> Unit,
    onShelfDrag: (androidx.compose.ui.input.pointer.PointerInputChange, androidx.compose.ui.geometry.Offset) -> Unit,
    onShelfDragEnd: () -> Unit,
    onFileDragStart: ((Int, androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onFileDrag: ((androidx.compose.ui.input.pointer.PointerInputChange, androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onFileDragEnd: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    var isMinimised by remember { mutableStateOf(shelf.isMinimised) }
    val parsedColor = try { Color(android.graphics.Color.parseColor(shelf.colorHex)) } catch(e:Exception){ MaterialTheme.colorScheme.surfaceVariant }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).pointerInput(shelf.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = onShelfDragStart,
                        onDrag = onShelfDrag,
                        onDragEnd = onShelfDragEnd,
                        onDragCancel = onShelfDragEnd
                    )
                }
            ) {
                Icon(Icons.Default.DragIndicator, contentDescription = "Drag", tint = MaterialTheme.colorScheme.onBackground.copy(alpha=0.5f))
                Spacer(Modifier.width(8.dp))
                Text(shelf.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            Row {
                IconButton(onClick = { 
                    isMinimised = !isMinimised
                    viewModel.updateBookshelfMinimised(shelf.id, isMinimised)
                }) {
                    Icon(if(isMinimised) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, null)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null)
                    }
                    var showRenameDialog by remember { mutableStateOf(false) }
                    var showColorDialog by remember { mutableStateOf(false) }

                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { showRenameDialog = true; showMenu = false })
                        DropdownMenuItem(text = { Text("Change Color") }, onClick = { showColorDialog = true; showMenu = false })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { viewModel.deleteBookshelf(shelf.id); showMenu = false })
                    }

                    if (showRenameDialog) {
                        var newName by remember { mutableStateOf(shelf.name) }
                        AlertDialog(
                            onDismissRequest = { showRenameDialog = false },
                            title = { Text("Rename Bookshelf") },
                            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
                            confirmButton = {
                                TextButton(onClick = { viewModel.renameBookshelf(shelf.id, newName); showRenameDialog = false }) { Text("Save") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
                            }
                        )
                    }

                    if (showColorDialog) {
                        var customR by remember { mutableFloatStateOf(parsedColor.red) }
                        var customG by remember { mutableFloatStateOf(parsedColor.green) }
                        var customB by remember { mutableFloatStateOf(parsedColor.blue) }

                        val colors = listOf("#FF9800", "#4CAF50", "#2196F3", "#9C27B0", "#F44336", "#795548", "#607D8B", "#E91E63")
                        AlertDialog(
                            onDismissRequest = { showColorDialog = false },
                            title = { Text("Choose Color") },
                            text = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(colors) { hex ->
                                            val color = try { Color(android.graphics.Color.parseColor(hex)) } catch(e:Exception){ Color.Gray }
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .clickable {
                                                        customR = color.red
                                                        customG = color.green
                                                        customB = color.blue
                                                    }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    val previewColor = Color(customR, customG, customB)
                                    Box(modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).background(previewColor))
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) { Text("R"); Spacer(Modifier.width(8.dp)); Slider(value = customR, onValueChange = { customR = it }) }
                                    Row(verticalAlignment = Alignment.CenterVertically) { Text("G"); Spacer(Modifier.width(8.dp)); Slider(value = customG, onValueChange = { customG = it }) }
                                    Row(verticalAlignment = Alignment.CenterVertically) { Text("B"); Spacer(Modifier.width(8.dp)); Slider(value = customB, onValueChange = { customB = it }) }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { 
                                    val hexString = String.format("#%02X%02X%02X", (customR * 255).toInt(), (customG * 255).toInt(), (customB * 255).toInt())
                                    viewModel.updateBookshelfColor(shelf.id, hexString)
                                    showColorDialog = false 
                                }) { Text("Apply") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showColorDialog = false }) { Text("Cancel") }
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = !isMinimised) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(parsedColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                if (viewMode == 0) {
                    // Horizontal stack
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(files, key = { it.id }) { file ->
                            BookshelfFileItem(
                                file = file, 
                                onClick = onNavigateToReader, 
                                onLongClick = onContextMenu,
                                isAssignmentMode = isAssignmentMode,
                                onDragStart = if (onFileDragStart != null) { { offset -> onFileDragStart(file.id, offset) } } else null,
                                onDrag = onFileDrag,
                                onDragEnd = onFileDragEnd
                            )
                        }
                    }
                } else {
                    // Vertical stack
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        files.forEach { file ->
                            Row(
                                modifier = Modifier.fillMaxWidth().then(
                                    if (isAssignmentMode && onFileDragStart != null && onFileDrag != null && onFileDragEnd != null) {
                                        Modifier.pointerInput(file.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { offset -> onFileDragStart!!(file.id, offset) },
                                                onDrag = { change, amount -> onFileDrag!!(change, amount) },
                                                onDragEnd = { onFileDragEnd!!() },
                                                onDragCancel = { onFileDragEnd!!() }
                                            )
                                        }
                                    } else {
                                        Modifier.combinedClickable(
                                            onClick = { onNavigateToReader(file.id) },
                                            onLongClick = { onContextMenu(file) }
                                        )
                                    }
                                )
                            ) {
                                AsyncImage(
                                    model = file.thumbnailUri,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp, 56.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(file.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfFileItem(
    file: LibraryFile, 
    onClick: (Int)->Unit, 
    onLongClick: (LibraryFile)->Unit,
    isAssignmentMode: Boolean = false,
    onDragStart: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onDrag: ((androidx.compose.ui.input.pointer.PointerInputChange, androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
            .then(
                if (isAssignmentMode && onDragStart != null && onDrag != null && onDragEnd != null) {
                    Modifier.pointerInput(file.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset -> onDragStart!!(offset) },
                            onDrag = { change, amount -> onDrag!!(change, amount) },
                            onDragEnd = { onDragEnd!!() },
                            onDragCancel = { onDragEnd!!() }
                        )
                    }
                } else {
                    Modifier.combinedClickable(
                        onClick = { onClick(file.id) },
                        onLongClick = { onLongClick(file) }
                    )
                }
            )
    ) {
        AsyncImage(
            model = file.thumbnailUri,
            contentDescription = null,
            modifier = Modifier.size(80.dp, 110.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray)
        )
        Spacer(Modifier.height(4.dp))
        Text(file.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

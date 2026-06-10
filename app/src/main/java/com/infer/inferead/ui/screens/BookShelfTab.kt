package com.infer.inferead.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
    onNavigateToSettings: () -> Unit,
    onNavigateToStats: () -> Unit,
    onContextMenu: (LibraryFile, Int?) -> Unit,
    bookshelfSortMode: Int,
    bookshelfViewMode: Int,
    onSortModeChange: (Int) -> Unit,
    onViewModeChange: (Int) -> Unit,
    isAssignmentMode: Boolean,
    onToggleAssignmentMode: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var activeBookshelfOrder by remember { mutableStateOf(emptyList<Bookshelf>()) }
    var isBookshelfDragging by remember { mutableStateOf(false) }

    LaunchedEffect(bookshelves, bookshelfItems, bookshelfSortMode) {
        if (!isBookshelfDragging) {
            activeBookshelfOrder = when (bookshelfSortMode) {
                0 -> bookshelves.sortedBy { it.name.lowercase() }
                1 -> bookshelves.sortedByDescending { it.name.lowercase() }
                2 -> bookshelves.sortedBy { shelf -> bookshelfItems.count { it.bookshelfId == shelf.id } }
                3 -> bookshelves.sortedByDescending { shelf -> bookshelfItems.count { it.bookshelfId == shelf.id } }
                else -> bookshelves.sortedBy { it.sortOrder }
            }
        }
    }

    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            isBookshelfDragging = true
            activeBookshelfOrder = activeBookshelfOrder.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        },
        onDragEnd = { _, _ ->
            isBookshelfDragging = false
            viewModel.updateBookshelvesOrder(
                activeBookshelfOrder.mapIndexed { idx, s -> s.copy(sortOrder = idx) }
            )
        }
    )

    // Drag state for FILE assignment
    var draggedFileId by remember { mutableStateOf<Int?>(null) }
    var draggedFromShelfId by remember { mutableStateOf<Int?>(null) } // null = home, -1 = assignment row
    var fileDragX by remember { mutableFloatStateOf(0f) }
    var fileDragY by remember { mutableFloatStateOf(0f) }

    // Shelf bounding boxes for hit-testing file drop targets
    val shelfBounds = remember { mutableStateMapOf<Int, Rect>() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var fileToAssignOnCreate by remember { mutableStateOf<Int?>(null) }

    // Add-file-to-shelf dialog
    var showAddFileDialog by remember { mutableStateOf(false) }
    var addFileTargetShelfId by remember { mutableStateOf<Int?>(null) }
    var showPickerMenu by remember { mutableStateOf(false) }
    
    val massImportFolderLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: SecurityException) {}
            viewModel.massImportFolder(it, context, addFileTargetShelfId)
            showAddFileDialog = false
            addFileTargetShelfId = null
        }
    }

    // State for storage permission and folder browser
    var showStoragePermissionDialog by remember { mutableStateOf(false) }
    var showFolderBrowser by remember { mutableStateOf(false) }
    var folderBrowserPath by remember { mutableStateOf("/storage/emulated/0") }

    fun hasAllFilesAccess(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun requestAllFilesAccess() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = android.net.Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                context.startActivity(intent)
            }
        }
    }

    fun launchFolderPicker() {
        if (hasAllFilesAccess()) {
            folderBrowserPath = "/storage/emulated/0"
            showFolderBrowser = true
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            showStoragePermissionDialog = true
        } else {
            massImportFolderLauncher.launch(null)
        }
    }

    val massImportFilesLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<android.net.Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: SecurityException) {} }
            viewModel.massImportFiles(uris, addFileTargetShelfId)
            showAddFileDialog = false
            addFileTargetShelfId = null
        }
    }
    
    val isMassImporting by viewModel.isMassImporting.collectAsState()
    val massImportProgress by viewModel.massImportProgress.collectAsState()
    val massImportTotal by viewModel.massImportTotal.collectAsState()
    val isMassImportPaused by viewModel.isMassImportPaused.collectAsState()

    var rootPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootPosition = it.positionInWindow() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { if (!isAssignmentMode) showCreateDialog = true }
                )
            }
    ) {
        LazyColumn(
            state = reorderState.listState,
            modifier = Modifier.fillMaxSize().reorderable(reorderState),
            contentPadding = PaddingValues(bottom = if (isAssignmentMode) 200.dp else 80.dp)
        ) {
            if (bookshelves.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Double-tap to add a bookshelf",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            items(activeBookshelfOrder, key = { it.id }) { shelf ->
                val itemsInShelf = remember(bookshelfItems, shelf.id) { bookshelfItems.filter { it.bookshelfId == shelf.id }.sortedBy { it.sortOrder } }
                val filesInShelf = remember(itemsInShelf, libraryFiles) { itemsInShelf.mapNotNull { item -> libraryFiles.find { it.id == item.fileId } } }

                ReorderableItem(reorderState, key = shelf.id) { isDraggingThis ->
                    val elevation = if (isDraggingThis) 8.dp else 0.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation, RoundedCornerShape(8.dp))
                            .background(
                                if (isDraggingThis) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .onGloballyPositioned { coords ->
                                shelfBounds[shelf.id] = coords.boundsInWindow()
                            }
                    ) {
                        BookshelfRow(
                            shelf = shelf,
                            files = filesInShelf,
                            onNavigateToReader = onNavigateToReader,
                            onContextMenu = { file -> onContextMenu(file, shelf.id) },
                            viewModel = viewModel,
                            viewMode = bookshelfViewMode,
                            isAssignmentMode = isAssignmentMode,
                            dragModifier = Modifier.detectReorderAfterLongPress(reorderState),
                            onFileDragStart = { fileId, globalPos ->
                                draggedFileId = fileId
                                draggedFromShelfId = shelf.id
                                fileDragX = globalPos.x
                                fileDragY = globalPos.y
                            },
                        onFileDrag = { change, amount ->
                            change.consume()
                            fileDragX += amount.x
                            fileDragY += amount.y
                        },
                        onFileDragEnd = {
                            val fid = draggedFileId
                            val fromShelf = draggedFromShelfId
                            if (fid != null && fromShelf != null && fromShelf != -1) {
                                val targetShelf = shelfBounds.entries.firstOrNull { (sid, rect) ->
                                    sid != fromShelf && rect.contains(
                                        androidx.compose.ui.geometry.Offset(fileDragX, fileDragY)
                                    )
                                }?.key
                                if (targetShelf != null) {
                                    viewModel.removeFileFromBookshelf(fromShelf, fid)
                                    viewModel.addFileToBookshelf(targetShelf, fid)
                                }
                            }
                            draggedFileId = null
                            draggedFromShelfId = null
                            fileDragX = 0f
                            fileDragY = 0f
                        },
                        onAddFilesToShelf = {
                            addFileTargetShelfId = shelf.id
                            showAddFileDialog = true
                        },
                        onRemoveFileFromShelf = { fileId ->
                            viewModel.removeFileFromBookshelf(shelf.id, fileId)
                        }
                    )
                }
            }
        }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                ReadingGoalWidget(viewModel = viewModel, onNavigateToStats = onNavigateToStats)
            }
        }

        // ── Assignment Bottom Row ──────────────────────────────────────────────
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
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 10f) {
                                onToggleAssignmentMode()
                            }
                        }
                    }
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Drag to assign",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    IconButton(onClick = onToggleAssignmentMode) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                val unassignedFiles = remember(bookshelfItems, libraryFiles) {
                    val assignedIds = bookshelfItems.map { it.fileId }.toSet()
                    libraryFiles.filter { it.id !in assignedIds }
                }
                LazyRow(
                    modifier = Modifier.padding(vertical = 8.dp).consumeHorizontalNestedScroll(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(unassignedFiles, key = { it.id }) { file ->
                        var globalPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(130.dp)
                                .onGloballyPositioned { coordinates -> globalPosition = coordinates.positionInWindow() }
                                .pointerInput(file.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedFileId = file.id
                                            draggedFromShelfId = -1
                                            fileDragX = globalPosition.x
                                            fileDragY = globalPosition.y
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            fileDragX += dragAmount.x
                                            fileDragY += dragAmount.y
                                        },
                                        onDragEnd = {
                                            val fid = draggedFileId
                                            if (fid != null) {
                                                val targetShelf = shelfBounds.entries.firstOrNull { (_, rect) ->
                                                    rect.contains(androidx.compose.ui.geometry.Offset(fileDragX, fileDragY))
                                                }?.key
                                                if (targetShelf != null) {
                                                    viewModel.addFileToBookshelf(targetShelf, fid)
                                                } else if (bookshelves.isEmpty()) {
                                                    fileToAssignOnCreate = fid
                                                    showCreateDialog = true
                                                }
                                            }
                                            draggedFileId = null
                                            draggedFromShelfId = null
                                            fileDragX = 0f
                                            fileDragY = 0f
                                        },
                                        onDragCancel = {
                                            draggedFileId = null
                                            draggedFromShelfId = null
                                            fileDragX = 0f
                                            fileDragY = 0f
                                        }
                                    )
                                }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(
                                    model = file.thumbnailUri ?: if (file.format == "IMAGE" && file.filePath.startsWith("content://")) android.net.Uri.parse(file.filePath) else if (file.format == "IMAGE") java.io.File(file.filePath) else null,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(60.dp, 85.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Gray)
                                )
                                Text(
                                    file.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                    
                    item {
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(130.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                .clickable { showPickerMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Files", modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // ── Import Files Dialog ──────────────────────────────────────────────
        if (showPickerMenu) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showPickerMenu = false }) {
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Import Files", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Select a source to add files",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    showPickerMenu = false
                                    addFileTargetShelfId = null
                                    launchFolderPicker()
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Folder", style = MaterialTheme.typography.labelMedium)
                            }
                            Button(
                                onClick = {
                                    showPickerMenu = false
                                    addFileTargetShelfId = null
                                    massImportFilesLauncher.launch(arrayOf("*/*")) 
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Files", style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }

        // ── Create Bookshelf Dialog ──────────────────────────────────────────────
        if (showCreateDialog) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreateDialog = false; fileToAssignOnCreate = null },
                title = { Text("New Bookshelf") },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Name") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.createBookshelf(name, "#FF9800", fileToAssignOnCreate)
                        showCreateDialog = false
                        fileToAssignOnCreate = null
                    }) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false; fileToAssignOnCreate = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // ── Add Files to Shelf Dialog ────────────────────────────────────────────
        if (showAddFileDialog && addFileTargetShelfId != null) {
            val targetId = addFileTargetShelfId!!
            val alreadyInShelf = bookshelfItems.filter { it.bookshelfId == targetId }.map { it.fileId }.toSet()
            AlertDialog(
                onDismissRequest = { showAddFileDialog = false; addFileTargetShelfId = null },
                title = { 
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Add files to shelf")
                        var showSystemMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showSystemMenu = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Add system files")
                            }
                            if (showSystemMenu) {
                                androidx.compose.ui.window.Dialog(onDismissRequest = { showSystemMenu = false }) {
                                    androidx.compose.material3.Card(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                                        colors = androidx.compose.material3.CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text("Import Files", style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Select a source to add files",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(32.dp))
                                            
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        showSystemMenu = false
                                                        launchFolderPicker()
                                                    },
                                                    modifier = Modifier.weight(1f).height(56.dp),
                                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                                ) {
                                                    Text("Folder", style = MaterialTheme.typography.labelMedium)
                                                }
                                                Button(
                                                    onClick = {
                                                        showSystemMenu = false
                                                        massImportFilesLauncher.launch(arrayOf("*/*")) 
                                                    },
                                                    modifier = Modifier.weight(1f).height(56.dp),
                                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                                ) {
                                                    Text("Files", style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(32.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                text = {
                    Column {
                        val eligible = libraryFiles.filter { it.id !in alreadyInShelf }
                        if (eligible.isEmpty()) {
                            Text("All files are already in this shelf.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                items(eligible, key = { it.id }) { file ->
                                    ListItem(
                                        headlineContent = { Text(file.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        leadingContent = {
                                            AsyncImage(
                                                model = file.thumbnailUri ?: if (file.format == "IMAGE" && file.filePath.startsWith("content://")) android.net.Uri.parse(file.filePath) else if (file.format == "IMAGE") java.io.File(file.filePath) else null,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(36.dp, 50.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color.Gray)
                                            )
                                        },
                                        modifier = Modifier.clickable {
                                            viewModel.addFileToBookshelf(targetId, file.id)
                                            showAddFileDialog = false
                                            addFileTargetShelfId = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAddFileDialog = false; addFileTargetShelfId = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // ── Mass Import Progress Dialog ──────────────────────────────────────────
        if (isMassImporting) {
            AlertDialog(
                onDismissRequest = {}, // prevent dismissing by tapping outside
                title = { Text("Importing Files") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (massImportTotal > 0) {
                            val percent = if (massImportTotal > 0) ((massImportProgress.toFloat() / massImportTotal) * 100).toInt() else 0
                            Text("Progress: $massImportProgress / $massImportTotal ($percent%)")
                            Spacer(Modifier.height(16.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = if (massImportTotal > 0) massImportProgress.toFloat() / massImportTotal else 0f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text("Scanning folder...")
                            Spacer(Modifier.height(16.dp))
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                },
                confirmButton = {
                    if (isMassImportPaused) {
                        TextButton(onClick = { viewModel.resumeMassImport() }) { Text("Resume") }
                    } else {
                        TextButton(onClick = { viewModel.pauseMassImport() }) { Text("Pause") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelMassImport() }) { Text("Cancel") }
                }
            )
        }

        // ── Floating drag preview ────────────────────────────────────────────────
        if (draggedFileId != null) {
            val file = libraryFiles.find { it.id == draggedFileId }
            if (file != null) {
                Box(
                    modifier = Modifier
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                (fileDragX - rootPosition.x).toInt(),
                                (fileDragY - rootPosition.y).toInt()
                            )
                        }
                        .size(70.dp, 120.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AsyncImage(
                            model = file.thumbnailUri ?: if (file.format == "IMAGE" && file.filePath.startsWith("content://")) android.net.Uri.parse(file.filePath) else if (file.format == "IMAGE") java.io.File(file.filePath) else null,
                            contentDescription = null,
                            modifier = Modifier
                                .size(60.dp, 85.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Gray)
                        )
                        Text(
                            file.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .width(70.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
        }
    }

    // Storage permission request dialog
    if (showStoragePermissionDialog) {
        AlertDialog(
            onDismissRequest = { showStoragePermissionDialog = false },
            icon = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Storage Access Required") },
            text = {
                Text(
                    "Android restricts access to default folders like Downloads, Documents, and DCIM. " +
                    "To scan these folders, please grant 'All files access' permission in the next screen.\n\n" +
                    "Alternatively, you can use the system file picker which has limited folder access."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStoragePermissionDialog = false
                        requestAllFilesAccess()
                    }
                ) {
                    Text("Grant Access")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        showStoragePermissionDialog = false
                        massImportFolderLauncher.launch(null)
                    }) {
                        Text("Use File Picker")
                    }
                    TextButton(onClick = { showStoragePermissionDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Folder browser dialog
    if (showFolderBrowser) {
        val currentDir = remember(folderBrowserPath) { java.io.File(folderBrowserPath) }
        val children = remember(folderBrowserPath) {
            currentDir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        }
        val displayPath = folderBrowserPath.removePrefix("/storage/emulated/0").ifEmpty { "/" }

        AlertDialog(
            onDismissRequest = { showFolderBrowser = false },
            title = {
                Column {
                    Text("Select Folder", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        displayPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    if (folderBrowserPath != "/storage/emulated/0" && folderBrowserPath != "/storage") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val parent = currentDir.parentFile
                                    if (parent != null && parent.absolutePath.startsWith("/storage")) {
                                        folderBrowserPath = parent.absolutePath
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Go up", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Go up", fontWeight = FontWeight.SemiBold)
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    }

                    if (children.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No subfolders", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn {
                            items(children) { dir ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { folderBrowserPath = dir.absolutePath }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        dir.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFolderBrowser = false
                        viewModel.massImportFolderByPath(folderBrowserPath, addFileTargetShelfId)
                        showAddFileDialog = false
                        addFileTargetShelfId = null
                    }
                ) {
                    Text("Import from Here")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderBrowser = false }) {
                    Text("Cancel")
                }
            }
        )
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
    dragModifier: Modifier = Modifier,
    onFileDragStart: ((Int, androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onFileDrag: ((androidx.compose.ui.input.pointer.PointerInputChange, androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onFileDragEnd: (() -> Unit)? = null,
    onAddFilesToShelf: () -> Unit = {},
    onRemoveFileFromShelf: (Int) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val parsedColor = try {
        Color(android.graphics.Color.parseColor(shelf.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // ── Header Row ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle + shelf name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .then(dragModifier)
            ) {
                // 6-dot drag indicator coloured to match shelf
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = "Drag",
                    tint = parsedColor
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    shelf.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Action buttons: (+) add, (^) minimise, (⋮) menu
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Quick add-file button
                IconButton(onClick = onAddFilesToShelf, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add files", modifier = Modifier.size(18.dp))
                }
                // Minimise toggle
                IconButton(
                    onClick = {
                        viewModel.updateBookshelfMinimised(shelf.id, !shelf.isMinimised)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (shelf.isMinimised) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                // 3-dot overflow menu
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(18.dp))
                    }

                    var showRenameDialog by remember { mutableStateOf(false) }
                    var showColorDialog by remember { mutableStateOf(false) }

                    if (showMenu) {
                        @OptIn(ExperimentalMaterial3Api::class)
                        ModalBottomSheet(
                            onDismissRequest = { showMenu = false },
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = parsedColor, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(shelf.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(modifier = Modifier.height(32.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showRenameDialog = true; showMenu = false }) {
                                        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Rename", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showColorDialog = true; showMenu = false }) {
                                        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Color", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { viewModel.deleteBookshelf(shelf.id); showMenu = false }) {
                                        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Delete", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        }
                    }

                    if (showRenameDialog) {
                        var newName by remember { mutableStateOf(shelf.name) }
                        AlertDialog(
                            onDismissRequest = { showRenameDialog = false },
                            title = { Text("Rename Bookshelf") },
                            text = {
                                OutlinedTextField(
                                    value = newName,
                                    onValueChange = { newName = it },
                                    singleLine = true
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.renameBookshelf(shelf.id, newName)
                                    showRenameDialog = false
                                }) { Text("Save") }
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

                        val presets = listOf(
                            "#FF9800", "#4CAF50", "#2196F3", "#9C27B0",
                            "#F44336", "#795548", "#607D8B", "#E91E63"
                        )
                        AlertDialog(
                            onDismissRequest = { showColorDialog = false },
                            title = { Text("Choose Color") },
                            text = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(presets) { hex ->
                                            val c = try {
                                                Color(android.graphics.Color.parseColor(hex))
                                            } catch (e: Exception) { Color.Gray }
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(c)
                                                    .clickable {
                                                        customR = c.red
                                                        customG = c.green
                                                        customB = c.blue
                                                    }
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    val preview = Color(customR, customG, customB)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(preview)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("R"); Spacer(Modifier.width(8.dp))
                                        Slider(value = customR, onValueChange = { customR = it })
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("G"); Spacer(Modifier.width(8.dp))
                                        Slider(value = customG, onValueChange = { customG = it })
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("B"); Spacer(Modifier.width(8.dp))
                                        Slider(value = customB, onValueChange = { customB = it })
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val hex = String.format(
                                        "#%02X%02X%02X",
                                        (customR * 255).toInt(),
                                        (customG * 255).toInt(),
                                        (customB * 255).toInt()
                                    )
                                    viewModel.updateBookshelfColor(shelf.id, hex)
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

        // ── Shelf Content ───────────────────────────────────────────────────
        AnimatedVisibility(visible = !shelf.isMinimised) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (files.isEmpty()) 70.dp else 0.dp)
                    .background(parsedColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (files.isEmpty()) {
                    Text(
                        "Empty – drag files here",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else if (viewMode == 0) {
                    // ── Horizontal shelf (default) ──
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().consumeHorizontalNestedScroll()
                    ) {
                        items(files, key = { it.id }) { file ->
                            BookshelfFileItem(
                                file = file,
                                onClick = onNavigateToReader,
                                onLongClick = onContextMenu,
                                isAssignmentMode = isAssignmentMode,
                                onDragStart = if (onFileDragStart != null) { { offset -> onFileDragStart(file.id, offset) } } else null,
                                onDrag = onFileDrag,
                                onDragEnd = onFileDragEnd,
                                onRemove = if (isAssignmentMode) { { onRemoveFileFromShelf(file.id) } } else null
                            )
                        }
                    }
                } else {
                    // ── "Vertical stack" = shelves side-by-side, files top-to-bottom ──
                    // This mode shows files as compact horizontal cards
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        files.forEach { file ->
                            var globalPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .onGloballyPositioned { coordinates ->
                                        globalPosition = coordinates.positionInWindow()
                                    }
                                    .then(
                                        if (isAssignmentMode && onFileDragStart != null && onFileDrag != null && onFileDragEnd != null) {
                                            Modifier.pointerInput(file.id) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { onFileDragStart(file.id, globalPosition) },
                                                    onDrag = { change, amount -> onFileDrag(change, amount) },
                                                    onDragEnd = { onFileDragEnd() },
                                                    onDragCancel = { onFileDragEnd() }
                                                )
                                            }
                                        } else {
                                            Modifier.combinedClickable(
                                                onClick = { onNavigateToReader(file.id) },
                                                onLongClick = { onContextMenu(file) }
                                            )
                                        }
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    AsyncImage(
                                        model = file.thumbnailUri ?: if (file.format == "IMAGE" && file.filePath.startsWith("content://")) android.net.Uri.parse(file.filePath) else if (file.format == "IMAGE") java.io.File(file.filePath) else null,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(34.dp, 48.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color.Gray)
                                    )
                                    if (file.isFinished) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Finished",
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(14.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(2.dp),
                                         horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        if (file.isBookmarked) {
                                            Box(modifier = Modifier.size(8.dp).background(Color(0xFFFFC107), CircleShape).border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape))
                                        }
                                        if (file.isToRead) {
                                            Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape).border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape))
                                        }
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        file.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (file.isBookmarked) {
                                        val context = androidx.compose.ui.platform.LocalContext.current
                                        val bookmarkCount = remember(file.id) {
                                            val readerPrefs = context.getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE)
                                            val saved = readerPrefs.getStringSet("bookmarked_pages_${file.id}", emptySet()) ?: emptySet()
                                            saved.size
                                        }
                                        if (bookmarkCount > 0) {
                                            Text(
                                                text = "◉ $bookmarkCount bookmark${if (bookmarkCount > 1) "s" else ""}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = Color(0xFFFFC107),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                if (isAssignmentMode) {
                                    IconButton(
                                        onClick = { onRemoveFileFromShelf(file.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Remove,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
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
    onClick: (Int) -> Unit,
    onLongClick: (LibraryFile) -> Unit,
    isAssignmentMode: Boolean = false,
    onDragStart: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onDrag: ((androidx.compose.ui.input.pointer.PointerInputChange, androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    var globalPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(
        modifier = Modifier
            .width(70.dp)
            .onGloballyPositioned { coordinates -> globalPosition = coordinates.positionInWindow() }
            .then(
                if (isAssignmentMode && onDragStart != null && onDrag != null && onDragEnd != null) {
                    Modifier.pointerInput(file.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart(globalPosition) },
                            onDrag = { change, amount -> onDrag(change, amount) },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                AsyncImage(
                    model = file.thumbnailUri ?: if (file.format == "IMAGE" && file.filePath.startsWith("content://")) android.net.Uri.parse(file.filePath) else if (file.format == "IMAGE") java.io.File(file.filePath) else null,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp, 85.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Gray)
                )
                if (file.isFinished) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Finished",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp),
                     horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (file.isBookmarked) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .background(Color(0xFFFFC107), CircleShape)
                                .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                        )
                    }
                    if (file.isToRead) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                                .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                file.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (file.isBookmarked) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val bookmarkCount = remember(file.id) {
                    val readerPrefs = context.getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE)
                    val saved = readerPrefs.getStringSet("bookmarked_pages_${file.id}", emptySet()) ?: emptySet()
                    saved.size
                }
                if (bookmarkCount > 0) {
                    Text(
                        text = "◉ $bookmarkCount bookmark${if (bookmarkCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color(0xFFFFC107),
                        maxLines = 1
                    )
                }
            }
        }
        // (-) remove badge in assignment mode
        if (isAssignmentMode && onRemove != null) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Remove from shelf",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

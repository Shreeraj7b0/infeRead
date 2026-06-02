package com.infer.inferead.ui.screens

import android.net.Uri
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.infer.inferead.data.LibraryFile
import com.infer.inferead.data.Checklist
import com.infer.inferead.data.ChecklistItem
import com.infer.inferead.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import kotlinx.coroutines.flow.first
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    initialChecklistId: Int? = null,
    pendingUri: android.net.Uri? = null,
    onUriHandled: () -> Unit = {},
    onNavigateToReader: (Int) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToStats: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val homePrefs = remember { context.getSharedPreferences("home_prefs", android.content.Context.MODE_PRIVATE) }
    val readerPrefs = remember { context.getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE) }
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val libraryFiles by viewModel.libraryFiles.collectAsState()
    val checklists by viewModel.checklists.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    
    val conversionProgress by viewModel.conversionProgress.collectAsState()
    val isConverting by viewModel.isConverting.collectAsState()
    val isConversionPaused by viewModel.isConversionPaused.collectAsState()
    val convertingFileName by viewModel.convertingFileName.collectAsState()
    
    var segregationMode by remember {
        mutableStateOf(
            run {
                val savedMode = homePrefs.getString("segregation_mode", SegregationMode.FORMAT.name)
                try { SegregationMode.valueOf(savedMode!!) } catch (e: Exception) { SegregationMode.FORMAT }
            }
        )
    }
    
    var activeTab by remember { mutableIntStateOf(homePrefs.getInt("active_tab", 0)) }
    val bookshelves by viewModel.bookshelves.collectAsState()
    val bookshelfItems by viewModel.bookshelfItems.collectAsState()
    var isBookshelfAssignmentMode by remember { mutableStateOf(false) }
    var bookshelfSortMode by remember { mutableIntStateOf(homePrefs.getInt("bookshelf_sort_mode", 0)) } // 0: Alpha Asc, 1: Alpha Desc, 2: Count Asc, 3: Count Desc
    var bookshelfViewMode by remember { mutableIntStateOf(homePrefs.getInt("bookshelf_view_mode", 0)) } // 0: Shelf Stack, 1: Vertical Stack

    var contextMenuFile by remember { mutableStateOf<LibraryFile?>(null) }
    var downloadDialogFile by remember { mutableStateOf<LibraryFile?>(null) }
    var formatSelectionDialogFile by remember { mutableStateOf<LibraryFile?>(null) }
    var formatSelectionDialogIsShare by remember { mutableStateOf(false) }
    var contextMenuChecklist by remember { mutableStateOf<Checklist?>(null) }
    var activeChecklistId by remember { mutableStateOf<Int?>(null) }
    
    LaunchedEffect(initialChecklistId) {
        if (initialChecklistId != null) {
            activeChecklistId = if (initialChecklistId == -1) null else initialChecklistId
        }
    }
    
    LaunchedEffect(pendingUri) {
        if (pendingUri != null) {
            val fileId = viewModel.importFile(pendingUri)
            onUriHandled()
            if (fileId != null) {
                onNavigateToReader(fileId)
            }
        }
    }
    
    var draggedSectionIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.activity.compose.BackHandler(enabled = drawerState.isOpen || searchExpanded || activeChecklistId != null) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (searchExpanded) {
            searchExpanded = false
            searchQuery = ""
            focusManager.clearFocus()
        } else if (activeChecklistId != null) {
            activeChecklistId = null
        }
    }

    var deepSearchResults by remember { mutableStateOf<List<Any>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery, libraryFiles, checklists) {
        if (searchQuery.isBlank()) {
            deepSearchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        kotlinx.coroutines.delay(300)
        
        val results = mutableListOf<Any>()
        
        results.addAll(libraryFiles.filter { 
            it.title.contains(searchQuery, true) || it.format.contains(searchQuery, true) 
        })
        
        results.addAll(checklists.filter { it.name.contains(searchQuery, true) })
        
        for (checklist in checklists) {
            val items = viewModel.getChecklistItems(checklist.id).first()
            val matches = items.filter { it.title.contains(searchQuery, true) }
            for (match in matches) {
                results.add(ChecklistItemMatch(checklist, match))
            }
        }
        
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val txtFiles = libraryFiles.filter { it.format == "TXT" && !results.contains(it) }
            for (file in txtFiles) {
                try {
                    val f = java.io.File(file.filePath)
                    if (f.exists() && f.length() < 10 * 1024 * 1024) {
                        val text = f.readText()
                        if (text.contains(searchQuery, true)) {
                            results.add(file)
                        }
                    }
                } catch (e: Exception) {}
            }
        }
        
        deepSearchResults = results
        isSearching = false
    }
    
    val filteredFiles = libraryFiles
    
    val categories = remember(filteredFiles, segregationMode) {
        when (segregationMode) {
            SegregationMode.FORMAT -> filteredFiles.map { it.format }.distinct()
            SegregationMode.PAGES -> listOf("By Pages (Desc)")
            SegregationMode.FILE_SIZE -> listOf("By File Size (Desc)")
            SegregationMode.BOOKMARKED -> listOf("Bookmarked")
            SegregationMode.READING_LIST -> listOf("Reading List")
        }
    }
    
    val groupedFiles = remember(filteredFiles, segregationMode) {
        when (segregationMode) {
            SegregationMode.FORMAT -> filteredFiles.groupBy { it.format }
            SegregationMode.PAGES -> mapOf("By Pages (Desc)" to filteredFiles.sortedByDescending { it.totalPages })
            SegregationMode.FILE_SIZE -> mapOf("By File Size (Desc)" to filteredFiles.sortedByDescending { 
                try {
                    java.io.File(it.filePath).length()
                } catch (e: Exception) {
                    0L
                }
            })
            SegregationMode.BOOKMARKED -> mapOf("Bookmarked" to filteredFiles.filter { it.isBookmarked })
            SegregationMode.READING_LIST -> mapOf("Reading List" to filteredFiles.filter { it.isToRead })
        }
    }
    
    val availableSections = remember(categories) {
        listOf("Checklists") + categories
    }
    
    var sectionOrder by remember(availableSections, segregationMode) {
        mutableStateOf(
            run {
                val savedOrder = homePrefs.getString("section_order_${segregationMode.name}", null)
                if (!savedOrder.isNullOrEmpty()) {
                    val savedList = savedOrder.split(",")
                    val filteredSaved = savedList.filter { it in availableSections }
                    val remaining = availableSections.filter { it !in filteredSaved }
                    filteredSaved + remaining
                } else {
                    availableSections
                }
            }
        )
    }
    
    val saveSectionOrder = { order: List<String> ->
        homePrefs.edit().putString("section_order_${segregationMode.name}", order.joinToString(",")).apply()
    }
    
    var minimisedSections by remember {
        mutableStateOf(
            homePrefs.getStringSet("minimised_sections", emptySet()) ?: emptySet()
        )
    }
    
    val toggleMinimiseSection = { sectionName: String ->
        val newSet = if (minimisedSections.contains(sectionName)) {
            minimisedSections - sectionName
        } else {
            minimisedSections + sectionName
        }
        minimisedSections = newSet
        homePrefs.edit().putStringSet("minimised_sections", newSet).apply()
    }
    
    var showCreateChecklistDialog by remember { mutableStateOf(false) }
    var ratingDialogFile by remember { mutableStateOf<LibraryFile?>(null) }
    var renamingChecklist by remember { mutableStateOf<Checklist?>(null) }
    var showColorPickerDialog by remember { mutableStateOf<Checklist?>(null) }
    
    val sheetState = rememberModalBottomSheetState()
    
    var editingThumbnailFileId by remember { mutableStateOf<Int?>(null) }
    var relinkingFileId by remember { mutableStateOf<Int?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val relinkFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: SecurityException) {}
            relinkingFileId?.let { fileId ->
                coroutineScope.launch {
                    viewModel.relinkFile(fileId, it)
                }
            }
        }
        relinkingFileId = null
    }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> 
        uri?.let { 
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: SecurityException) {}
            coroutineScope.launch {
                viewModel.importFile(it)
            }
        } 
    }

    val thumbnailPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { imageUri ->
            editingThumbnailFileId?.let { fileId ->
                viewModel.updateThumbnail(fileId, imageUri)
            }
        }
        editingThumbnailFileId = null
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(280.dp)
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "infeRead Library",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                        )
                        IconButton(
                            onClick = { showCreateChecklistDialog = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create Checklist",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    NavigationDrawerItem(
                        label = { Text("My Library", fontWeight = FontWeight.SemiBold) },
                        selected = activeChecklistId == null,
                        icon = { 
                            Icon(
                                Icons.Default.MenuBook, 
                                contentDescription = null, 
                                tint = if (activeChecklistId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            ) 
                        },
                        onClick = { 
                            activeChecklistId = null
                            scope.launch { drawerState.close() } 
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        item {
                            Text(
                                "Checklists",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
                        items(checklists, key = { "drawer_checklist_${it.id}" }) { checklist ->
                            val isSelected = activeChecklistId == checklist.id
                            val checklistColor = try {
                                Color(android.graphics.Color.parseColor(checklist.colorHex))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else Color.Transparent
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            activeChecklistId = checklist.id
                                            scope.launch { drawerState.close() }
                                        },
                                        onLongClick = {
                                            contextMenuChecklist = checklist
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(checklistColor, CircleShape)
                                )
                                Text(
                                    text = checklist.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        
                        if (activeTab == 0) {
                            items(sectionOrder.filter { it != "Checklists" }, key = { "nav_$it" }) { sectionName ->
                            val filesForCategory = groupedFiles[sectionName] ?: emptyList()
                            if (filesForCategory.isNotEmpty()) {
                                val isCategoryMinimised = minimisedSections.contains(sectionName)
                                val index = sectionOrder.indexOf(sectionName)
                                val isDraggingThis = draggedSectionIndex == index
                                val translationY = if (isDraggingThis) dragOffsetY else 0f
                                val zIndex = if (isDraggingThis) 10f else 1f
                                val density = androidx.compose.ui.platform.LocalDensity.current
                                val thresholdPx = remember(density) { with(density) { 40.dp.toPx() } }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (isDraggingThis) Modifier else Modifier.animateItemPlacement())
                                        .graphicsLayer {
                                            this.translationY = translationY
                                            this.shadowElevation = if (isDraggingThis) 8.dp.toPx() else 0f
                                        }
                                        .zIndex(zIndex)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ReorderHandle(
                                                modifier = Modifier.pointerInput("nav_$sectionName") {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            val currIdx = sectionOrder.indexOf(sectionName)
                                                            if (currIdx != -1) {
                                                                 draggedSectionIndex = currIdx
                                                                 dragOffsetY = 0f
                                                            }
                                                        },
                                                        onDragEnd = {
                                                            draggedSectionIndex = null
                                                            dragOffsetY = 0f
                                                            saveSectionOrder(sectionOrder)
                                                        },
                                                        onDragCancel = {
                                                            draggedSectionIndex = null
                                                            dragOffsetY = 0f
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragOffsetY += dragAmount.y
                                                            
                                                            val currentIndex = draggedSectionIndex
                                                            if (currentIndex != null) {
                                                                if (dragOffsetY > thresholdPx && currentIndex < sectionOrder.size - 1) {
                                                                    val newList = sectionOrder.toMutableList()
                                                                    val temp = newList[currentIndex]
                                                                    newList[currentIndex] = newList[currentIndex + 1]
                                                                    newList[currentIndex + 1] = temp
                                                                    sectionOrder = newList
                                                                    draggedSectionIndex = currentIndex + 1
                                                                    dragOffsetY = 0f
                                                                } else if (dragOffsetY < -thresholdPx && currentIndex > 0) {
                                                                    val newList = sectionOrder.toMutableList()
                                                                    val temp = newList[currentIndex]
                                                                    newList[currentIndex] = newList[currentIndex - 1]
                                                                    newList[currentIndex - 1] = temp
                                                                    sectionOrder = newList
                                                                    draggedSectionIndex = currentIndex - 1
                                                                    dragOffsetY = 0f
                                                                }
                                                            }
                                                        }
                                                    )
                                                }
                                            )
                                            Text(
                                                text = getSectionDisplayName(sectionName),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                                                    .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), CircleShape)
                                                    .clickable { toggleMinimiseSection(sectionName) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isCategoryMinimised) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                                    contentDescription = if (isCategoryMinimised) "Expand" else "Collapse",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        
                                        AnimatedVisibility(
                                            visible = !isCategoryMinimised,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut()
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                filesForCategory.forEach { file ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(36.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .combinedClickable(
                                                                onClick = {
                                                                    onNavigateToReader(file.id)
                                                                    scope.launch { drawerState.close() }
                                                                },
                                                                onLongClick = {
                                                                    contextMenuFile = file
                                                                }
                                                            )
                                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Description,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                            text = file.title,
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontWeight = FontWeight.Normal,
                                                                fontSize = 13.sp
                                                            ),
                                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        } else if (activeTab == 1) {
                            items(bookshelves.sortedBy { it.sortOrder }, key = { "nav_bookshelf_${it.id}" }) { shelf ->
                                val itemsInShelf = bookshelfItems.filter { it.bookshelfId == shelf.id }
                                val shelfFiles = itemsInShelf.mapNotNull { bItem -> libraryFiles.find { it.id == bItem.fileId } }
                                val shelfColor = try { Color(android.graphics.Color.parseColor(shelf.colorHex)) } catch(e:Exception){ MaterialTheme.colorScheme.primary }
                                
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        var shelfDragOffsetY by remember { mutableStateOf(0f) }
                                        Icon(
                                            imageVector = Icons.Default.DragIndicator, 
                                            contentDescription = "Drag to reorder", 
                                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha=0.3f), 
                                            modifier = Modifier.size(20.dp).pointerInput(shelf.id) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { shelfDragOffsetY = 0f },
                                                    onDragEnd = { shelfDragOffsetY = 0f },
                                                    onDragCancel = { shelfDragOffsetY = 0f },
                                                    onDrag = { change, amount -> 
                                                        change.consume()
                                                        shelfDragOffsetY += amount.y
                                                        
                                                        val sortedShelves = bookshelves.sortedBy { it.sortOrder }
                                                        val currentIndex = sortedShelves.indexOfFirst { it.id == shelf.id }
                                                        if (currentIndex != -1) {
                                                            val newIndex = if (shelfDragOffsetY > 100f && currentIndex < sortedShelves.size - 1) {
                                                                shelfDragOffsetY = 0f; currentIndex + 1
                                                            } else if (shelfDragOffsetY < -100f && currentIndex > 0) {
                                                                shelfDragOffsetY = 0f; currentIndex - 1
                                                            } else currentIndex
                                                            
                                                            if (currentIndex != newIndex) {
                                                                val mutableShelves = sortedShelves.toMutableList()
                                                                val removed = mutableShelves.removeAt(currentIndex)
                                                                mutableShelves.add(newIndex, removed)
                                                                viewModel.updateBookshelvesOrder(mutableShelves.mapIndexed { index, s -> s.copy(sortOrder = index) })
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        )
                                        Text(
                                            text = shelf.name,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = shelfColor),
                                            modifier = Modifier.weight(1f)
                                        )
                                        var isMinimised by remember { mutableStateOf(shelf.isMinimised) }
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                                                .clickable { isMinimised = !isMinimised; viewModel.updateBookshelfMinimised(shelf.id, isMinimised) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(if (isMinimised) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, null, tint = shelfColor, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    AnimatedVisibility(visible = !shelf.isMinimised) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            shelfFiles.forEach { file ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(6.dp))
                                                        .combinedClickable(
                                                            onClick = { onNavigateToReader(file.id); scope.launch { drawerState.close() } },
                                                            onLongClick = { contextMenuFile = file }
                                                        ).padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(Icons.Default.Description, null, tint = shelfColor.copy(alpha=0.7f), modifier = Modifier.size(16.dp))
                                                    Text(file.title, style = MaterialTheme.typography.bodySmall.copy(fontSize=13.sp), color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                }
                                                Spacer(Modifier.height(4.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Special section: Bookmarked Files
                        val bookmarkedFiles = libraryFiles.filter { it.isBookmarked }
                        if (bookmarkedFiles.isNotEmpty()) {
                            item {
                                Text(
                                    text = "⭐ Bookmarked",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFC107)
                                    ),
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                            }
                            items(bookmarkedFiles, key = { "drawer_bookmark_${it.id}" }) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .combinedClickable(
                                            onClick = {
                                                onNavigateToReader(file.id)
                                                scope.launch { drawerState.close() }
                                            },
                                            onLongClick = { contextMenuFile = file }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFFC107), CircleShape))
                                    Text(
                                        text = file.title,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }

                        // Special section: Reading List
                        val readingListFiles = libraryFiles.filter { it.isToRead }
                        if (readingListFiles.isNotEmpty()) {
                            item {
                                Text(
                                    text = "📖 Reading List",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    ),
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                            }
                            items(readingListFiles, key = { "drawer_reading_list_${it.id}" }) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .combinedClickable(
                                            onClick = {
                                                onNavigateToReader(file.id)
                                                scope.launch { drawerState.close() }
                                            },
                                            onLongClick = { contextMenuFile = file }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkAdd,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = file.title,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                    
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Compact Tab Toggle
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (activeTab == 0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { activeTab = 0; homePrefs.edit().putInt("active_tab", 0).apply() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Library", style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal, color = if (activeTab == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface))
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (activeTab == 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { activeTab = 1; homePrefs.edit().putInt("active_tab", 1).apply() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("BookShelf", style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal, color = if (activeTab == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface))
                            }
                        }

                        IconButton(
                            onClick = { 
                                scope.launch { drawerState.close() }
                                onNavigateToSettings()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                    CenterAlignedTopAppBar(
                        title = { 
                            Text("infeRead", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground) 
                        },
                        navigationIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.primary)
                                }
                                val toggleTheme = com.infer.inferead.LocalThemeToggle.current
                                TextButton(onClick = { toggleTheme() }) {
                                    Text("🌓", fontSize = 18.sp)
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { onNavigateToSettings() }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                    )
                    TabRow(
                        selectedTabIndex = activeTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = { Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) }
                    ) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0; homePrefs.edit().putInt("active_tab", 0).apply() },
                            text = { Text("Library", fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal) }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1; homePrefs.edit().putInt("active_tab", 1).apply() },
                            text = { Text("BookShelf", fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
            ) {
                if (activeChecklistId != null) {
                    val activeChecklist = checklists.find { it.id == activeChecklistId }
                    if (activeChecklist != null) {
                        val activeChecklistItems by remember(activeChecklistId) {
                            viewModel.getChecklistItems(activeChecklistId!!)
                        }.collectAsState(initial = emptyList())
                        
                        var newItemText by remember { mutableStateOf("") }
                        
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(onClick = { activeChecklistId = null }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                }
                                Text(
                                    text = activeChecklist.name,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                var showHeaderMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showHeaderMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                    }
                                    DropdownMenu(
                                        expanded = showHeaderMenu,
                                        onDismissRequest = { showHeaderMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Download PDF") },
                                            onClick = { 
                                                showHeaderMenu = false
                                                viewModel.convertChecklistToPdf(activeChecklist.id)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Share PDF") },
                                            onClick = { 
                                                showHeaderMenu = false
                                                viewModel.shareChecklistAsPdf(activeChecklist.id)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Rename") },
                                            onClick = {
                                                showHeaderMenu = false
                                                renamingChecklist = activeChecklist
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Mark All Done") },
                                            onClick = {
                                                showHeaderMenu = false
                                                viewModel.markAllChecklistItemsDone(activeChecklist.id, true)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Mark All Undone") },
                                            onClick = {
                                                showHeaderMenu = false
                                                viewModel.markAllChecklistItemsDone(activeChecklist.id, false)
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Add Item Box
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newItemText,
                                    onValueChange = { newItemText = it },
                                    placeholder = { Text("Add item or book title...") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Button(
                                    onClick = {
                                        if (newItemText.isNotBlank()) {
                                            viewModel.addChecklistItem(activeChecklist.id, newItemText)
                                            newItemText = ""
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(56.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add")
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Items list
                            if (activeChecklistItems.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "This checklist is empty.",
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                }
                            } else {
                                val localItems = remember { mutableStateListOf<com.infer.inferead.data.ChecklistItem>() }
                                LaunchedEffect(activeChecklistItems) {
                                    localItems.clear()
                                    localItems.addAll(activeChecklistItems)
                                }
                                val reorderState = rememberReorderableLazyListState(
                                      onMove = { from, to ->
                                          localItems.apply {
                                              if (from.index >= 0 && to.index >= 0 && from.index < size && to.index < size) {
                                                  if (!get(from.index).isPinned && !get(to.index).isPinned) {
                                                      add(to.index, removeAt(from.index))
                                                  }
                                              }
                                          }
                                      },
                                      onDragEnd = { startIndex, endIndex ->
                                          if (startIndex != endIndex) {
                                              localItems.forEachIndexed { index, checklistItem ->
                                                  viewModel.updateChecklistItem(checklistItem.copy(sortOrder = index))
                                              }
                                          }
                                      }
                                  )
                                  LazyColumn(
                                      state = reorderState.listState,
                                      modifier = Modifier.fillMaxWidth().weight(1f).reorderable(reorderState),
                                      verticalArrangement = Arrangement.spacedBy(8.dp)
                                  ) {
                                      items(localItems, key = { it.id }) { item ->
                                          var isHoverMenuVisible by remember { mutableStateOf(false) }
                                          var showEditDialog by remember { mutableStateOf(false) }
                                          
                                          if (showEditDialog) {
                                              var editText by remember { mutableStateOf(item.title) }
                                              AlertDialog(
                                                  onDismissRequest = { showEditDialog = false },
                                                  title = { Text("Edit Item") },
                                                  text = { 
                                                      OutlinedTextField(
                                                          value = editText, 
                                                          onValueChange = { editText = it }, 
                                                          singleLine = true,
                                                          modifier = Modifier.fillMaxWidth()
                                                      )
                                                  },
                                                  confirmButton = {
                                                      TextButton(onClick = { 
                                                          viewModel.updateChecklistItem(item.copy(title = editText))
                                                          showEditDialog = false
                                                      }) { Text("Save") }
                                                  },
                                                  dismissButton = {
                                                      TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
                                                  }
                                              )
                                          }

                                          ReorderableItem(reorderState, key = item.id) { isDragging ->
                                              val elevation = if (isDragging) 8.dp else 0.dp
                                              Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                                  Column(
                                                      modifier = Modifier
                                                          .fillMaxWidth()
                                                          .shadow(elevation, RoundedCornerShape(8.dp))
                                                          .background(
                                                              if (isDragging) MaterialTheme.colorScheme.surfaceVariant else if (item.isPinned) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                              RoundedCornerShape(8.dp)
                                                          )
                                                  ) {
                                                      // Top Lisp
                                                      if (!item.isPinned) {
                                                          Box(
                                                              modifier = Modifier
                                                                  .fillMaxWidth()
                                                                  .height(15.dp)
                                                                  .background(
                                                                      MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                                      RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                                                  )
                                                                  .detectReorderAfterLongPress(reorderState),
                                                              contentAlignment = Alignment.Center
                                                          ) {
                                                              Text("≡", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                          }
                                                      }

                                                      Row(
                                                          modifier = Modifier
                                                              .fillMaxWidth()
                                                              .pointerInput(item.id) {
                                                                  detectTapGestures(
                                                                      onTap = {
                                                                          viewModel.toggleChecklistItemCompletion(item)
                                                                      },
                                                                      onLongPress = {
                                                                          isHoverMenuVisible = true
                                                                      }
                                                                  )
                                                              }
                                                              .pointerInput(item.id + 1000) {
                                                                  detectHorizontalDragGestures(
                                                                      onDragEnd = { },
                                                                      onHorizontalDrag = { change, dragAmount ->
                                                                          if (!isDragging) {
                                                                              change.consume()
                                                                              if (dragAmount > 20) {
                                                                                  viewModel.updateChecklistItem(item.copy(indentLevel = minOf(3, item.indentLevel + 1)))
                                                                              } else if (dragAmount < -20) {
                                                                                  viewModel.updateChecklistItem(item.copy(indentLevel = maxOf(0, item.indentLevel - 1)))
                                                                              }
                                                                          }
                                                                      }
                                                                  )
                                                              }
                                                              .padding(start = (item.indentLevel * 16).dp)
                                                              .padding(horizontal = 8.dp, vertical = 6.dp),
                                                          verticalAlignment = Alignment.CenterVertically
                                                      ) {
                                                          Checkbox(
                                                              checked = item.isCompleted,
                                                              onCheckedChange = { 
                                                                  viewModel.toggleChecklistItemCompletion(item)
                                                              }
                                                          )
                                                          Text(
                                                              text = item.title,
                                                              style = MaterialTheme.typography.bodyMedium.copy(
                                                                  textDecoration = if (item.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                                                  color = if (item.isCompleted) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onBackground
                                                              ),
                                                              modifier = Modifier.weight(1f)
                                                          )
                                                          if (item.isPinned) {
                                                              IconButton(onClick = { viewModel.updateChecklistItem(item.copy(isPinned = false)) }) {
                                                                  Icon(Icons.Default.PushPin, contentDescription = "Unpin", tint = MaterialTheme.colorScheme.primary)
                                                              }
                                                          } else {
                                                              IconButton(onClick = { viewModel.deleteChecklistItem(item.id) }) {
                                                                  Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                                              }
                                                          }
                                                      }
                                                  }
                                                  
                                                  // Hover Menu overlay
                                                  DropdownMenu(
                                                      expanded = isHoverMenuVisible,
                                                      onDismissRequest = { isHoverMenuVisible = false }
                                                  ) {
                                                      DropdownMenuItem(
                                                          text = { Text("Edit") },
                                                          onClick = {
                                                              showEditDialog = true
                                                              isHoverMenuVisible = false
                                                          }
                                                      )
                                                      DropdownMenuItem(
                                                          text = { Text("Copy Text") },
                                                          onClick = {
                                                              val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                              val clip = android.content.ClipData.newPlainText("Copied Text", item.title)
                                                              clipboardManager.setPrimaryClip(clip)
                                                              isHoverMenuVisible = false
                                                          }
                                                      )
                                                      DropdownMenuItem(
                                                          text = { Text(if (item.isPinned) "Unpin" else "Pin") },
                                                          onClick = {
                                                              viewModel.updateChecklistItem(item.copy(isPinned = !item.isPinned))
                                                              isHoverMenuVisible = false
                                                          }
                                                      )
                                                  }
                                              }
                                          }
                                      }
                                  }
                            }
                        }
                    }
                } else if (activeTab == 0) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        if (searchQuery.isNotBlank()) {
                            if (isSearching) {
                                item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("Searching...", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) } }
                            } else if (deepSearchResults.isEmpty()) {
                                item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No results found.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) } }
                            } else {
                                items(deepSearchResults, key = { result ->
                                    when (result) {
                                        is LibraryFile -> "search_file_${result.id}"
                                        is Checklist -> "search_checklist_${result.id}"
                                        is ChecklistItemMatch -> "search_item_${result.item.id}"
                                        else -> result.hashCode()
                                    }
                                }) { result ->
                                    Box(modifier = Modifier.animateItemPlacement().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                        when (result) {
                                            is LibraryFile -> {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(8.dp)).clickable { onNavigateToReader(result.id) }.padding(horizontal = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                                    Spacer(Modifier.width(12.dp))
                                                    Text(result.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                            is Checklist -> {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(8.dp)).clickable { activeChecklistId = result.id }.padding(horizontal = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                                                    Spacer(Modifier.width(12.dp))
                                                    Text(result.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                            is ChecklistItemMatch -> {
                                                var isChecked by remember(result.item.id) { mutableStateOf(result.item.isCompleted) }
                                                Column(
                                                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = "From checklist: ${result.checklist.name}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                                                    )
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(
                                                            checked = isChecked, 
                                                            onCheckedChange = { 
                                                                isChecked = it
                                                                viewModel.toggleChecklistItemCompletion(result.item.copy(isCompleted = it)) 
                                                            }
                                                        )
                                                        Text(
                                                            text = result.item.title,
                                                            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = if (isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null),
                                                            color = if (isChecked) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onBackground,
                                                            modifier = Modifier.weight(1f).clickable { activeChecklistId = result.checklist.id }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            items(sectionOrder, key = { it }) { sectionName ->
                            val index = sectionOrder.indexOf(sectionName)
                            val isDraggingThis = draggedSectionIndex == index
                            val translationY = if (isDraggingThis) dragOffsetY else 0f
                            val zIndex = if (isDraggingThis) 10f else 1f
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val thresholdPx = remember(density) { with(density) { 180.dp.toPx() } }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isDraggingThis) Modifier else Modifier.animateItemPlacement())
                                    .graphicsLayer {
                                        this.translationY = translationY
                                        this.shadowElevation = if (isDraggingThis) 8.dp.toPx() else 0f
                                    }
                                    .zIndex(zIndex)
                            ) {
                                if (sectionName == "Checklists") {
                                    val isChecklistsMinimised = minimisedSections.contains("Checklists")
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val usernameText = currentUser?.username?.let { "$it's" } ?: "My"
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                ReorderHandle(
                                                    modifier = Modifier
                                                        .pointerInput(sectionName) {
                                                            detectDragGesturesAfterLongPress(
                                                                onDragStart = {
                                                                    val currIdx = sectionOrder.indexOf(sectionName)
                                                                    if (currIdx != -1) {
                                                                        draggedSectionIndex = currIdx
                                                                        dragOffsetY = 0f
                                                                    }
                                                                },
                                                                onDragEnd = {
                                                                    draggedSectionIndex = null
                                                                    dragOffsetY = 0f
                                                                    saveSectionOrder(sectionOrder)
                                                                },
                                                                onDragCancel = {
                                                                    draggedSectionIndex = null
                                                                    dragOffsetY = 0f
                                                                },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    dragOffsetY += dragAmount.y
                                                                    
                                                                    val currentIndex = draggedSectionIndex
                                                                    if (currentIndex != null) {
                                                                        if (dragOffsetY > thresholdPx && currentIndex < sectionOrder.size - 1) {
                                                                            val newList = sectionOrder.toMutableList()
                                                                            val temp = newList[currentIndex]
                                                                            newList[currentIndex] = newList[currentIndex + 1]
                                                                            newList[currentIndex + 1] = temp
                                                                            sectionOrder = newList
                                                                            draggedSectionIndex = currentIndex + 1
                                                                            dragOffsetY = 0f
                                                                        } else if (dragOffsetY < -thresholdPx && currentIndex > 0) {
                                                                            val newList = sectionOrder.toMutableList()
                                                                            val temp = newList[currentIndex]
                                                                            newList[currentIndex] = newList[currentIndex - 1]
                                                                            newList[currentIndex - 1] = temp
                                                                            sectionOrder = newList
                                                                            draggedSectionIndex = currentIndex - 1
                                                                            dragOffsetY = 0f
                                                                        }
                                                                    }
                                                                }
                                                            )
                                                        }
                                                )
                                                Text(
                                                    text = "$usernameText Checklists",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                                                        .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), CircleShape)
                                                        .clickable { toggleMinimiseSection("Checklists") },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (isChecklistsMinimised) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                                        contentDescription = if (isChecklistsMinimised) "Expand Checklists" else "Collapse Checklists",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { showCreateChecklistDialog = true },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Create Checklist",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    AnimatedVisibility(
                                        visible = !isChecklistsMinimised,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            if (checklists.isEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                                        .height(90.dp)
                                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        "No checklists created. Click + next to title to add.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            } else {
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                    modifier = Modifier.padding(bottom = 8.dp)
                                                ) {
                                                    items(checklists, key = { "home_checklist_${it.id}" }) { checklist ->
                                                        val checklistColor = try {
                                                            Color(android.graphics.Color.parseColor(checklist.colorHex))
                                                        } catch (e: Exception) {
                                                            MaterialTheme.colorScheme.primary
                                                        }
                                                        Card(
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = checklistColor.copy(alpha = 0.08f)
                                                            ),
                                                            border = BorderStroke(1.5.dp, checklistColor.copy(alpha = 0.24f)),
                                                            shape = RoundedCornerShape(16.dp),
                                                            modifier = Modifier
                                                                .size(115.dp)
                                                                .shadow(2.dp, RoundedCornerShape(16.dp))
                                                                .combinedClickable(
                                                                    onClick = { activeChecklistId = checklist.id },
                                                                    onLongClick = { contextMenuChecklist = checklist }
                                                                )
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.fillMaxSize().padding(14.dp),
                                                                verticalArrangement = Arrangement.SpaceBetween,
                                                                horizontalAlignment = Alignment.Start
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.CheckCircle,
                                                                    contentDescription = null,
                                                                    tint = checklistColor,
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                                Text(
                                                                    text = checklist.name,
                                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                                                    color = MaterialTheme.colorScheme.onBackground,
                                                                    maxLines = 2,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Divider(
                                        modifier = Modifier.padding(vertical = 16.dp),
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
                                    )
                                }
                            } else {
                                val filesForCategory = groupedFiles[sectionName] ?: emptyList()
                                if (filesForCategory.isNotEmpty()) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        val isCategoryMinimised = minimisedSections.contains(sectionName)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ReorderHandle(
                                                modifier = Modifier
                                                    .pointerInput(sectionName) {
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = {
                                                                val currIdx = sectionOrder.indexOf(sectionName)
                                                                if (currIdx != -1) {
                                                                    draggedSectionIndex = currIdx
                                                                    dragOffsetY = 0f
                                                                }
                                                            },
                                                            onDragEnd = {
                                                                draggedSectionIndex = null
                                                                dragOffsetY = 0f
                                                            },
                                                            onDragCancel = {
                                                                draggedSectionIndex = null
                                                                dragOffsetY = 0f
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                dragOffsetY += dragAmount.y
                                                                
                                                                val currentIndex = draggedSectionIndex
                                                                if (currentIndex != null) {
                                                                    if (dragOffsetY > thresholdPx && currentIndex < sectionOrder.size - 1) {
                                                                        val newList = sectionOrder.toMutableList()
                                                                        val temp = newList[currentIndex]
                                                                        newList[currentIndex] = newList[currentIndex + 1]
                                                                        newList[currentIndex + 1] = temp
                                                                        sectionOrder = newList
                                                                        saveSectionOrder(newList)
                                                                        draggedSectionIndex = currentIndex + 1
                                                                        dragOffsetY = 0f
                                                                    } else if (dragOffsetY < -thresholdPx && currentIndex > 0) {
                                                                        val newList = sectionOrder.toMutableList()
                                                                        val temp = newList[currentIndex]
                                                                        newList[currentIndex] = newList[currentIndex - 1]
                                                                        newList[currentIndex - 1] = temp
                                                                        sectionOrder = newList
                                                                        saveSectionOrder(newList)
                                                                        draggedSectionIndex = currentIndex - 1
                                                                        dragOffsetY = 0f
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    }
                                            )
                                            Text(
                                                text = getSectionDisplayName(sectionName),
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                                                    .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), CircleShape)
                                                    .clickable { toggleMinimiseSection(sectionName) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isCategoryMinimised) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                                    contentDescription = if (isCategoryMinimised) "Expand $sectionName" else "Collapse $sectionName",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        AnimatedVisibility(
                                            visible = !isCategoryMinimised,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut()
                                        ) {
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                items(filesForCategory, key = { "home_file_${it.id}" }) { file ->
                                                    Column(
                                                        modifier = Modifier
                                                            .width(120.dp)
                                                            .combinedClickable(
                                                                onClick = { onNavigateToReader(file.id) },
                                                                onLongClick = { contextMenuFile = file }
                                                            )
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .aspectRatio(0.7f)
                                                                .shadow(2.dp, RoundedCornerShape(12.dp))
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (file.thumbnailUri != null) {
                                                                AsyncImage(
                                                                    model = file.thumbnailUri,
                                                                    contentDescription = "Thumbnail",
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    contentScale = ContentScale.Crop
                                                                )
                                                            } else {
                                                                Text(
                                                                    text = file.format, 
                                                                    style = MaterialTheme.typography.titleMedium, 
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                            if (file.isFinished) {
                                                                Icon(
                                                                    Icons.Default.CheckCircle,
                                                                    contentDescription = "Finished",
                                                                    tint = Color(0xFF4CAF50),
                                                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                                                                )
                                                            }
                                                            // Bookmark and To-Read dots on thumbnail
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
                                                        Spacer(Modifier.height(8.dp))
                                                        Text(
                                                            text = file.title,
                                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                            color = MaterialTheme.colorScheme.onBackground,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (file.rating > 0) {
                                                            Text(
                                                                text = "★".repeat(file.rating),
                                                                color = Color(0xFFFFC107),
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                        // Bookmark count caption
                                                        if (file.isBookmarked) {
                                                            val bookmarkCount = remember(file.id) {
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
                                                }
                                            }
                                        }
                                        Divider(
                                            modifier = Modifier.padding(vertical = 16.dp),
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
                                        )
                                    }
                                }
                            }
                            }
                        }
                        
                        if (libraryFiles.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Your library is empty.",
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                        } // End of else block for search query

                        item {
                            ReadingGoalWidget(viewModel, onNavigateToStats)
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }
                } else if (activeTab == 1) {
                    BookShelfTab(
                        viewModel = viewModel,
                        bookshelves = bookshelves,
                        bookshelfItems = bookshelfItems,
                        libraryFiles = libraryFiles,
                        onNavigateToReader = {
                            onNavigateToReader(it)
                            scope.launch { drawerState.close() }
                        },
                        onContextMenu = { contextMenuFile = it },
                        bookshelfSortMode = bookshelfSortMode,
                        bookshelfViewMode = bookshelfViewMode,
                        onSortModeChange = { 
                            bookshelfSortMode = it
                            homePrefs.edit().putInt("bookshelf_sort_mode", it).apply()
                        },
                        onViewModeChange = {
                            bookshelfViewMode = it
                            homePrefs.edit().putInt("bookshelf_view_mode", it).apply()
                        },
                        isAssignmentMode = isBookshelfAssignmentMode,
                        onToggleAssignmentMode = { isBookshelfAssignmentMode = !isBookshelfAssignmentMode }
                    )
                }

                if (activeChecklistId == null) {
                    val focusRequester = remember { FocusRequester() }
                    var sortExpanded by remember { mutableStateOf(false) }
                    
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            if (activeTab == 0) {
                                FloatingActionButton(
                                    onClick = { sortExpanded = true },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(48.dp),
                                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = "Sort By"
                                    )
                                }
                                DropdownMenu(
                                    expanded = sortExpanded,
                                    onDismissRequest = { sortExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Files") },
                                        onClick = {
                                            segregationMode = SegregationMode.FORMAT
                                            homePrefs.edit().putString("segregation_mode", SegregationMode.FORMAT.name).apply()
                                            sortExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Num of Pages") },
                                        onClick = {
                                            segregationMode = SegregationMode.PAGES
                                            homePrefs.edit().putString("segregation_mode", SegregationMode.PAGES.name).apply()
                                            sortExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("File Size") },
                                        onClick = {
                                            segregationMode = SegregationMode.FILE_SIZE
                                            homePrefs.edit().putString("segregation_mode", SegregationMode.FILE_SIZE.name).apply()
                                            sortExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Bookmarked") },
                                        onClick = {
                                            segregationMode = SegregationMode.BOOKMARKED
                                            homePrefs.edit().putString("segregation_mode", SegregationMode.BOOKMARKED.name).apply()
                                            sortExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Reading List") },
                                        onClick = {
                                            segregationMode = SegregationMode.READING_LIST
                                            homePrefs.edit().putString("segregation_mode", SegregationMode.READING_LIST.name).apply()
                                            sortExpanded = false
                                        }
                                    )
                                }
                            } else if (activeTab == 1) {
                                FloatingActionButton(
                                    onClick = { sortExpanded = true },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(48.dp),
                                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                                ) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Sort/Filter")
                                }
                                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                                    DropdownMenuItem(text = { Text("Manual") }, onClick = { bookshelfSortMode = -1; homePrefs.edit().putInt("bookshelf_sort_mode", -1).apply(); sortExpanded = false }, trailingIcon = { if(bookshelfSortMode==-1) Icon(Icons.Default.Check, null) })
                                    DropdownMenuItem(text = { Text("Alphabetical (A-Z)") }, onClick = { bookshelfSortMode = 0; homePrefs.edit().putInt("bookshelf_sort_mode", 0).apply(); sortExpanded = false }, trailingIcon = { if(bookshelfSortMode==0) Icon(Icons.Default.Check, null) })
                                    DropdownMenuItem(text = { Text("Alphabetical (Z-A)") }, onClick = { bookshelfSortMode = 1; homePrefs.edit().putInt("bookshelf_sort_mode", 1).apply(); sortExpanded = false }, trailingIcon = { if(bookshelfSortMode==1) Icon(Icons.Default.Check, null) })
                                    DropdownMenuItem(text = { Text("File Count (Asc)") }, onClick = { bookshelfSortMode = 2; homePrefs.edit().putInt("bookshelf_sort_mode", 2).apply(); sortExpanded = false }, trailingIcon = { if(bookshelfSortMode==2) Icon(Icons.Default.Check, null) })
                                    DropdownMenuItem(text = { Text("File Count (Desc)") }, onClick = { bookshelfSortMode = 3; homePrefs.edit().putInt("bookshelf_sort_mode", 3).apply(); sortExpanded = false }, trailingIcon = { if(bookshelfSortMode==3) Icon(Icons.Default.Check, null) })
                                    Divider()
                                    DropdownMenuItem(
                                        text = { Text(if (bookshelfViewMode == 0) "Switch to Vertical Stack" else "Switch to Shelf Stack") },
                                        onClick = { bookshelfViewMode = if (bookshelfViewMode == 0) 1 else 0; homePrefs.edit().putInt("bookshelf_view_mode", bookshelfViewMode).apply(); sortExpanded = false }
                                    )
                                }
                            }
                        }

                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            singleLine = true,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .focusRequester(focusRequester)
                                .onFocusChanged { 
                                    if (it.isFocused) {
                                        searchExpanded = true
                                    }
                                },
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Search library...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { 
                                                searchQuery = ""
                                                focusManager.clearFocus()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear Search",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )

                        if (activeTab == 0) {
                            FloatingActionButton(
                                onClick = { 
                                    filePickerLauncher.launch(arrayOf("*/*"))
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp),
                                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Book")
                            }
                        } else if (activeTab == 1) {
                            if (!isBookshelfAssignmentMode) {
                                FloatingActionButton(
                                    onClick = { isBookshelfAssignmentMode = true },
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(48.dp),
                                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                                ) {
                                    Icon(Icons.Default.SyncAlt, contentDescription = "Assignment Mode")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (contextMenuFile != null) {
        ModalBottomSheet(
            onDismissRequest = { contextMenuFile = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text(
                    contextMenuFile!!.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                ListItem(
                    headlineContent = { Text("Rename File") },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        // TODO Show rename dialog
                        contextMenuFile = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Relink File") },
                    leadingContent = { Icon(Icons.Default.Link, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        relinkingFileId = contextMenuFile!!.id
                        relinkFilePickerLauncher.launch(arrayOf("*/*"))
                        contextMenuFile = null
                    }
                )
                ListItem(
                    headlineContent = { Text(if (contextMenuFile!!.isFinished) "Mark as Unfinished" else "Mark as Finished") },
                    leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        viewModel.markFinished(contextMenuFile!!.id, !contextMenuFile!!.isFinished)
                        contextMenuFile = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Personal Rating") },
                    leadingContent = { Icon(Icons.Default.Star, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        ratingDialogFile = contextMenuFile
                        contextMenuFile = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Set Custom Cover") },
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        editingThumbnailFileId = contextMenuFile!!.id
                        thumbnailPickerLauncher.launch(arrayOf("image/*"))
                        contextMenuFile = null
                    }
                )
                ListItem(
                    headlineContent = { Text(if (contextMenuFile!!.isToRead) "Remove from Reading List" else "Mark for Reading") },
                    leadingContent = { Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        viewModel.markToRead(contextMenuFile!!.id, !contextMenuFile!!.isToRead)
                        contextMenuFile = null
                    }
                )
                if (contextMenuFile!!.isBookmarked) {
                    ListItem(
                        headlineContent = { Text("Remove All Bookmarks", color = Color(0xFFFFA000)) },
                        leadingContent = { Icon(Icons.Default.BookmarkRemove, contentDescription = null, tint = Color(0xFFFFA000)) },
                        modifier = Modifier.clickable {
                            val fileId = contextMenuFile!!.id
                            viewModel.clearBookmarks(fileId)
                            readerPrefs.edit().remove("bookmarked_pages_$fileId").apply()
                            contextMenuFile = null
                        }
                    )
                }
                if (contextMenuFile!!.format == "PDF") {
                    ListItem(
                        headlineContent = { Text("Convert to EPUB", color = MaterialTheme.colorScheme.tertiary) },
                        leadingContent = { 
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) 
                        },
                        modifier = Modifier.clickable { 
                            viewModel.convertPdfToEpub(contextMenuFile!!)
                            contextMenuFile = null
                        }
                    )
                }
                ListItem(
                    headlineContent = { Text("Share File", color = MaterialTheme.colorScheme.primary) },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { 
                        if (contextMenuFile!!.format == "CODING") {
                            formatSelectionDialogFile = contextMenuFile
                            formatSelectionDialogIsShare = true
                        } else {
                            viewModel.exportFile(context, contextMenuFile!!, modified = false) // Add normal share if needed later, or ignore
                        }
                        contextMenuFile = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Download File", color = MaterialTheme.colorScheme.primary) },
                    leadingContent = { Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { 
                        if (contextMenuFile!!.format == "CODING") {
                            formatSelectionDialogFile = contextMenuFile
                            formatSelectionDialogIsShare = false
                        } else {
                            downloadDialogFile = contextMenuFile
                        }
                        contextMenuFile = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Delete File", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { 
                        viewModel.deleteFile(contextMenuFile!!.id)
                        contextMenuFile = null
                    }
                )
            }
        }
    }

    if (contextMenuChecklist != null) {
        ModalBottomSheet(
            onDismissRequest = { contextMenuChecklist = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            val checklistColor = try {
                Color(android.graphics.Color.parseColor(contextMenuChecklist!!.colorHex))
            } catch (e: Exception) {
                MaterialTheme.colorScheme.primary
            }
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text(
                    contextMenuChecklist!!.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                ListItem(
                    headlineContent = { Text("Rename Checklist") },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        renamingChecklist = contextMenuChecklist
                        contextMenuChecklist = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Mark as Completed") },
                    leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        viewModel.markChecklistFinished(contextMenuChecklist!!.id, true)
                        contextMenuChecklist = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Unmark") },
                    leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Gray) },
                    modifier = Modifier.clickable { 
                        viewModel.markChecklistFinished(contextMenuChecklist!!.id, false)
                        contextMenuChecklist = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Change Color") },
                    leadingContent = { Icon(Icons.Default.Build, contentDescription = null, tint = checklistColor) },
                    modifier = Modifier.clickable {
                        showColorPickerDialog = contextMenuChecklist
                        contextMenuChecklist = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Export as PDF") },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                    modifier = Modifier.clickable {
                        viewModel.shareChecklistAsPdf(contextMenuChecklist!!.id)
                        contextMenuChecklist = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Delete Checklist", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { 
                        viewModel.deleteChecklist(contextMenuChecklist!!.id)
                        contextMenuChecklist = null
                    }
                )
            }
        }
    }

    if (ratingDialogFile != null) {
        val file = ratingDialogFile!!
        var currentRating by remember(file) { mutableStateOf(file.rating) }
        AlertDialog(
            onDismissRequest = { ratingDialogFile = null },
            title = { Text("Personal Rating") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Rate ${file.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 1..5) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Star $i",
                                tint = if (i <= currentRating) Color(0xFFFFC107) else Color.Gray.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { currentRating = i }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateRating(file.id, currentRating)
                    ratingDialogFile = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { ratingDialogFile = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateChecklistDialog) {
        var checklistName by remember { mutableStateOf("") }
        val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { showCreateChecklistDialog = false },
            title = { Text("Create Checklist") },
            text = {
                OutlinedTextField(
                    modifier = Modifier.focusRequester(focusRequester),
                    value = checklistName,
                    onValueChange = { checklistName = it },
                    placeholder = { Text("Checklist Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (checklistName.isNotBlank()) {
                            viewModel.createChecklist(checklistName)
                            showCreateChecklistDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateChecklistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (renamingChecklist != null) {
        var newChecklistName by remember { mutableStateOf(renamingChecklist!!.name) }
        AlertDialog(
            onDismissRequest = { renamingChecklist = null },
            title = { Text("Rename Checklist") },
            text = {
                OutlinedTextField(
                    value = newChecklistName,
                    onValueChange = { newChecklistName = it },
                    placeholder = { Text("New Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newChecklistName.isNotBlank()) {
                            viewModel.renameChecklist(renamingChecklist!!.id, newChecklistName)
                            renamingChecklist = null
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingChecklist = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val brightColors = listOf(
        "#FF1744", // Bright Red
        "#F50057", // Bright Pink
        "#D500F9", // Bright Purple
        "#651FFF", // Bright Deep Purple
        "#3D5AFE", // Bright Indigo
        "#2979FF", // Bright Blue
        "#00B0FF", // Bright Light Blue
        "#00E5FF", // Bright Cyan
        "#1DE9B6", // Bright Teal
        "#00E676", // Bright Green
        "#76FF03", // Bright Light Green
        "#FFEA00", // Bright Yellow
        "#FFC400", // Bright Amber
        "#FF9100", // Bright Orange
        "#FF3D00"  // Bright Deep Orange
    )

    if (showColorPickerDialog != null) {
        AlertDialog(
            onDismissRequest = { showColorPickerDialog = null },
            title = { Text("Choose Checklist Color") },
            text = {
                Column {
                    Text("Select a color for '${showColorPickerDialog!!.name}'", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val chunks = brightColors.chunked(5)
                        chunks.forEach { rowColors ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowColors.forEach { hex ->
                                    val parsedColor = Color(android.graphics.Color.parseColor(hex))
                                    val isSelected = showColorPickerDialog!!.colorHex == hex
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(parsedColor)
                                            .clickable {
                                                viewModel.updateChecklistColor(showColorPickerDialog!!.id, hex)
                                                showColorPickerDialog = null
                                            }
                                            .padding(4.dp)
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.align(Alignment.Center)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showColorPickerDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isConverting) {
        AlertDialog(
            onDismissRequest = { /* Cannot dismiss without action */ },
            title = { Text("Converting...") },
            text = {
                Column {
                    Text(convertingFileName ?: "Unknown file")
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = conversionProgress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$conversionProgress%",
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isConversionPaused) {
                        viewModel.resumeConversion()
                    } else {
                        viewModel.pauseConversion()
                    }
                }) {
                    Text(if (isConversionPaused) "Resume" else "Pause")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelConversion() }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    if (downloadDialogFile != null) {
        AlertDialog(
            onDismissRequest = { downloadDialogFile = null },
            title = { Text("Download") },
            text = { Text("Choose a download option for ${downloadDialogFile!!.title}") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.exportFile(context, downloadDialogFile!!, modified = true)
                    downloadDialogFile = null
                }) {
                    Text("With Modifications")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.exportFile(context, downloadDialogFile!!, modified = false)
                    downloadDialogFile = null
                }) {
                    Text("Original")
                }
            }
        )
    }

    if (formatSelectionDialogFile != null) {
        val actionType = if (formatSelectionDialogIsShare) "Share" else "Download"
        AlertDialog(
            onDismissRequest = { formatSelectionDialogFile = null },
            title = { Text(actionType) },
            text = {
                Column {
                    Text("Select format for ${formatSelectionDialogFile!!.title}:")
                    Spacer(modifier = Modifier.height(8.dp))
                    listOf("PDF", "TXT", "IMG(JPG)").forEach { format ->
                        TextButton(
                            onClick = {
                                viewModel.convertCodeFile(context, formatSelectionDialogFile!!, format, formatSelectionDialogIsShare)
                                formatSelectionDialogFile = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(format)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { formatSelectionDialogFile = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ReorderHandle(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .size(36.dp)
            .padding(8.dp)
    ) {
        val width = size.width
        val height = size.height
        val barHeight = 2.dp.toPx()
        val spacing = 4.dp.toPx()
        
        val startY1 = height / 2f - barHeight - spacing / 2f
        val startY2 = height / 2f + spacing / 2f
        
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(0f, startY1),
            size = androidx.compose.ui.geometry.Size(width, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2f)
        )
        
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(0f, startY2),
            size = androidx.compose.ui.geometry.Size(width, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2f)
        )
    }
}

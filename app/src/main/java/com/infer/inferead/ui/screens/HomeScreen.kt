package com.infer.inferead.ui.screens

import android.net.Uri
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.alpha
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.infer.inferead.ui.modifiers.bounceClick
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
    initialNewChecklist: Boolean = false,
    pendingUri: android.net.Uri? = null,
    onUriHandled: () -> Unit = {},
    onNavigateToReader: (Int) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToStats: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE) }
    var isOfflineMode by remember { mutableStateOf(prefs.getBoolean("is_offline_mode", false)) }
    
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "is_offline_mode") {
                isOfflineMode = sharedPreferences.getBoolean("is_offline_mode", false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    

    val homePrefs = remember { context.getSharedPreferences("home_prefs", android.content.Context.MODE_PRIVATE) }
    val readerPrefs = remember { context.getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE) }
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Gate flag: prevents drawer from flashing on navigation entry
    var drawerReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        drawerState.snapTo(DrawerValue.Closed)
        drawerReady = true
    }
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
    
    var selectedLibraryTab by remember { mutableIntStateOf(homePrefs.getInt("active_tab", 0)) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = selectedLibraryTab, pageCount = { 2 })
    LaunchedEffect(pagerState.currentPage) {
        if (selectedLibraryTab != pagerState.currentPage) {
            selectedLibraryTab = pagerState.currentPage
            homePrefs.edit().putInt("active_tab", selectedLibraryTab).apply()
        }
    }
    val bookshelves by viewModel.bookshelves.collectAsState()
    val bookshelfItems by viewModel.bookshelfItems.collectAsState()
    var isBookshelfAssignmentMode by remember { mutableStateOf(false) }
    var bookshelfSortMode by remember { mutableIntStateOf(homePrefs.getInt("bookshelf_sort_mode", 0)) } // 0: Alpha Asc, 1: Alpha Desc, 2: Count Asc, 3: Count Desc
    var bookshelfViewMode by remember { mutableIntStateOf(homePrefs.getInt("bookshelf_view_mode", 0)) } // 0: Shelf Stack, 1: Vertical Stack

    var contextMenuFile by remember { mutableStateOf<LibraryFile?>(null) }
    var contextMenuFileShelfId by remember { mutableStateOf<Int?>(null) } // non-null when from a bookshelf
    var downloadDialogFile by remember { mutableStateOf<LibraryFile?>(null) }
    var formatSelectionDialogFile by remember { mutableStateOf<LibraryFile?>(null) }
    var formatSelectionDialogIsShare by remember { mutableStateOf(false) }
    var contextMenuChecklist by remember { mutableStateOf<Checklist?>(null) }
    var activeChecklistId by remember { mutableStateOf<Int?>(null) }
    var activeNavTab by remember { mutableStateOf("library") }
    
    val onlineViewModel: com.infer.inferead.viewmodel.OnlineStoreViewModel = viewModel()
    val currentOnlineSource by onlineViewModel.currentSource.collectAsState()
    val hideWebUi by onlineViewModel.hideWebUi.collectAsState()
    
    LaunchedEffect(isOfflineMode) {
        if (isOfflineMode && activeNavTab == "online") {
            activeNavTab = "library"
        }
    }
    
    LaunchedEffect(initialChecklistId) {
        if (initialChecklistId != null) {
            activeChecklistId = if (initialChecklistId == -1) null else initialChecklistId
        }
    }
    
    LaunchedEffect(pendingUri) {
        if (pendingUri != null) {
            // Link the file rather than copying it into app storage
            val fileId = viewModel.linkOrImportFile(pendingUri)
            onUriHandled()
        }
    }
    
    var draggedSectionIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    var renamingFile by remember { mutableStateOf<com.infer.inferead.data.LibraryFile?>(null) }
    var fileToDelete by remember { mutableStateOf<com.infer.inferead.data.LibraryFile?>(null) }
    
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.activity.compose.BackHandler(enabled = drawerState.isOpen || searchExpanded || activeChecklistId != null || activeNavTab == "online") {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (searchExpanded) {
            searchExpanded = false
            searchQuery = ""
            focusManager.clearFocus()
        } else if (activeChecklistId != null) {
            activeChecklistId = null
        } else if (activeNavTab == "online") {
            activeNavTab = "library"
        }
    }

    var deepSearchResults by remember { mutableStateOf<List<Any>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery, libraryFiles, checklists, bookshelves) {
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
        results.addAll(bookshelves.filter { it.name.contains(searchQuery, true) })
        
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
    var fileSizes by remember { mutableStateOf(mapOf<Int, Long>()) }
    LaunchedEffect(filteredFiles) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            fileSizes = filteredFiles.associate { it.id to (try { java.io.File(it.filePath).length() } catch (e: Exception) { 0L }) }
        }
    }
    
    val groupedFiles = remember(filteredFiles, segregationMode) {
        when (segregationMode) {
            SegregationMode.FORMAT -> filteredFiles.groupBy { it.format }
            SegregationMode.PAGES -> mapOf("By Pages (Desc)" to filteredFiles.sortedByDescending { it.totalPages })
            SegregationMode.FILE_SIZE -> mapOf("By File Size (Desc)" to filteredFiles.sortedByDescending { fileSizes[it.id] ?: 0L })
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
    
    var minimisedNavSections by remember { mutableStateOf(homePrefs.getStringSet("minimised_nav_sections", emptySet()) ?: emptySet()) }
    var minimisedNavBookshelves by remember { mutableStateOf(homePrefs.getStringSet("minimised_nav_bookshelves", emptySet()) ?: emptySet()) }
    var navPaneWidth by remember { mutableStateOf(homePrefs.getFloat("navpane_width_dp", 300f).dp) }

    val toggleMinimiseNavSection = { sectionName: String ->
        val newSet = if (minimisedNavSections.contains(sectionName)) minimisedNavSections - sectionName else minimisedNavSections + sectionName
        minimisedNavSections = newSet
        homePrefs.edit().putStringSet("minimised_nav_sections", newSet).apply()
    }

    val toggleMinimiseNavBookshelf = { shelfId: String ->
        val newSet = if (minimisedNavBookshelves.contains(shelfId)) minimisedNavBookshelves - shelfId else minimisedNavBookshelves + shelfId
        minimisedNavBookshelves = newSet
        homePrefs.edit().putStringSet("minimised_nav_bookshelves", newSet).apply()
    }
    
    var showCreateChecklistDialog by remember { mutableStateOf(initialNewChecklist) }
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
            // Link via persistent URI — no file copy, survives moves
            viewModel.linkOrImportFile(it)
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

    val isDrawerClosed = drawerState.currentValue == DrawerValue.Closed && drawerState.targetValue == DrawerValue.Closed

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        scrimColor = if (isDrawerClosed) Color.Transparent else androidx.compose.material3.DrawerDefaults.scrimColor,
        drawerContent = {
            SharedNavPane(
                drawerState = drawerState,
                drawerReady = drawerReady,
                isResizable = true,
                initialWidth = navPaneWidth,
                onWidthChange = { 
                    navPaneWidth = it
                    homePrefs.edit().putFloat("navpane_width_dp", it.value).apply()
                },
                headerContent = {
                    Spacer(Modifier.height(24.dp))
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "infeRead",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = (-0.5).sp
                                )
                            )
                        }
                        Surface(
                            onClick = { 
                                scope.launch { drawerState.close() }
                                onNavigateToSettings()
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                },
                topActionItem = {
                    Spacer(modifier = Modifier.height(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    NavigationDrawerItem(
                        label = { Text("My Library", fontWeight = FontWeight.SemiBold) },
                        selected = activeNavTab == "library" && activeChecklistId == null,
                        icon = { 
                            Icon(
                                Icons.Default.AutoStories, 
                                contentDescription = null, 
                                tint = if (activeNavTab == "library" && activeChecklistId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            ) 
                        },
                        onClick = { 
                            activeNavTab = "library"
                            activeChecklistId = null
                            scope.launch { drawerState.close() } 
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    NavigationDrawerItem(
                        label = { Text("Online Sources", fontWeight = FontWeight.SemiBold) },
                        selected = activeNavTab == "online",
                        icon = { 
                            Box {
                                Icon(
                                    Icons.Default.Language, 
                                    contentDescription = null, 
                                    tint = if (activeNavTab == "online") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isOfflineMode) {
                                    androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                                        drawLine(
                                            color = Color.Red,
                                            start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                            end = androidx.compose.ui.geometry.Offset(0f, size.height),
                                            strokeWidth = 3f
                                        )
                                    }
                                }
                            }
                        },
                        onClick = { 
                            if (isOfflineMode) {
                                android.widget.Toast.makeText(context, "Offline Mode Enabled, switch to Online Mode for browser functionality.", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                activeNavTab = "online"
                                activeChecklistId = null
                                scope.launch { drawerState.close() }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                },
                listContent = {
                    val bookmarkedFiles = remember(libraryFiles) { libraryFiles.filter { it.isBookmarked } }
                    val readingListFiles = remember(libraryFiles) { libraryFiles.filter { it.isToRead } }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "CHECKLISTS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        letterSpacing = 1.5.sp
                                    )
                                )
                                IconButton(
                                    onClick = { showCreateChecklistDialog = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Create Checklist",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                        items(checklists, key = { "drawer_checklist_${it.id}" }) { checklist ->
                            val isSelected = activeChecklistId == checklist.id
                            val checklistColor = try {
                                Color(android.graphics.Color.parseColor(checklist.colorHex))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            }
                            NavigationDrawerItem(
                                label = { 
                                    Text(
                                        text = checklist.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                selected = isSelected,
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(checklistColor, CircleShape)
                                    )
                                },
                                onClick = {
                                    activeNavTab = "library"
                                    activeChecklistId = checklist.id
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                    .height(42.dp)
                                    .combinedClickable(
                                        onClick = {
                                            activeNavTab = "library"
                                            activeChecklistId = checklist.id
                                            scope.launch { drawerState.close() }
                                        },
                                        onLongClick = {
                                            contextMenuChecklist = checklist
                                        }
                                    ),
                                shape = RoundedCornerShape(10.dp),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                    unselectedContainerColor = Color.Transparent,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            )
                        }
                        
                        if (selectedLibraryTab == 0) {
                            items(sectionOrder.filter { it != "Checklists" }, key = { "nav_$it" }) { sectionName ->
                            val filesForCategory = groupedFiles[sectionName] ?: emptyList()
                            if (filesForCategory.isNotEmpty()) {
                                val isCategoryMinimised = minimisedNavSections.contains(sectionName)
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
                                                text = getSectionDisplayName(sectionName).uppercase(),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    letterSpacing = 1.2.sp
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${filesForCategory.size}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                ),
                                                modifier = Modifier.padding(end = 4.dp)
                                            )
                                            Icon(
                                                imageVector = if (isCategoryMinimised) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                                contentDescription = if (isCategoryMinimised) "Expand" else "Collapse",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(18.dp).clickable { toggleMinimiseNavSection(sectionName) }
                                            )
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
                                                            .height(40.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .combinedClickable(
                                                                onClick = {
                                                                    scope.launch { 
                                                                        drawerState.close()
                                                                        onNavigateToReader(file.id)
                                                                    }
                                                                },
                                                                onLongClick = {
                                                                    contextMenuFile = file
                                                                }
                                                            )
                                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Description,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                            text = file.title,
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontWeight = FontWeight.Normal,
                                                                fontSize = 13.sp
                                                            ),
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        } else if (selectedLibraryTab == 1) {
                            items(bookshelves.sortedBy { it.sortOrder }, key = { "nav_bookshelf_${it.id}" }) { shelf ->
                                val itemsInShelf = remember(bookshelfItems, shelf.id) { bookshelfItems.filter { it.bookshelfId == shelf.id } }
                                val shelfFiles = remember(itemsInShelf, libraryFiles) { itemsInShelf.mapNotNull { bItem -> libraryFiles.find { it.id == bItem.fileId } } }
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
                                            text = shelf.name.uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = shelfColor.copy(alpha = 0.8f),
                                                letterSpacing = 1.2.sp
                                            ),
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        var isMinimised by remember { mutableStateOf(shelf.isMinimised) }
                                        Text(
                                            text = "${shelfFiles.size}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            ),
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        Icon(
                                            imageVector = if (isMinimised) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                            contentDescription = null,
                                            tint = shelfColor.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp).clickable { isMinimised = !isMinimised; viewModel.updateBookshelfMinimised(shelf.id, isMinimised) }
                                        )
                                    }
                                    AnimatedVisibility(visible = !shelf.isMinimised) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            shelfFiles.forEach { file ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp))
                                                        .combinedClickable(
                                                            onClick = { scope.launch { drawerState.close(); activeNavTab = "library"; onNavigateToReader(file.id) } },
                                                            onLongClick = { contextMenuFile = file }
                                                        ).padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Icon(Icons.Default.Description, null, tint = shelfColor.copy(alpha=0.5f), modifier = Modifier.size(16.dp))
                                                    Text(file.title, style = MaterialTheme.typography.bodySmall.copy(fontSize=13.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                }
                                                Spacer(Modifier.height(2.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Special section: Bookmarked Files
                        if (bookmarkedFiles.isNotEmpty()) {
                            item {
                                Text(
                                    text = "⭐ BOOKMARKED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFFFC107).copy(alpha = 0.8f),
                                        letterSpacing = 1.2.sp
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
                                                scope.launch { 
                                                    drawerState.close()
                                                    activeNavTab = "library"
                                                    onNavigateToReader(file.id)
                                                }
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
                        if (readingListFiles.isNotEmpty()) {
                            item {
                                Text(
                                    text = "📖 READING LIST",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                                        letterSpacing = 1.2.sp
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
                                                scope.launch { 
                                                    drawerState.close()
                                                    activeNavTab = "library"
                                                    onNavigateToReader(file.id)
                                                }
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
                },
                bottomBarContent = {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Polished Tab Toggle
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { 
                                        selectedLibraryTab = 0
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selectedLibraryTab == 0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.AutoStories, null,
                                            tint = if (selectedLibraryTab == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp).padding(end = 6.dp)
                                        )
                                        Text("Library", style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (selectedLibraryTab == 0) FontWeight.Bold else FontWeight.SemiBold, color = if (selectedLibraryTab == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Spacer(Modifier.width(2.dp))
                                Surface(
                                    onClick = { 
                                        selectedLibraryTab = 1
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selectedLibraryTab == 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.CollectionsBookmark, null,
                                            tint = if (selectedLibraryTab == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp).padding(end = 6.dp)
                                        )
                                        Text("Shelf", style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (selectedLibraryTab == 1) FontWeight.Bold else FontWeight.SemiBold, color = if (selectedLibraryTab == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            )
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
                            if (activeNavTab == "online" && (currentOnlineSource == "Anna's Archive" || currentOnlineSource == "Internet Archive")) {
                                IconButton(onClick = { onlineViewModel.setHideWebUi(!hideWebUi) }) {
                                    Icon(
                                        if (hideWebUi) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Web UI",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = { onNavigateToSettings() }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                    )
                    TabRow(
                        selectedTabIndex = if (activeChecklistId != null) 0 else pagerState.currentPage,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        indicator = { tabPositions ->
                            if (activeChecklistId == null && pagerState.currentPage >= 0 && pagerState.currentPage < tabPositions.size) {
                                TabRowDefaults.Indicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        divider = { Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) }
                    ) {
                        Tab(
                            selected = activeChecklistId == null && pagerState.currentPage == 0,
                            onClick = { 
                                activeNavTab = "library"
                                activeChecklistId = null
                                scope.launch { pagerState.animateScrollToPage(0) } 
                            },
                            text = { Text("Library", fontWeight = if (activeChecklistId == null && pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal) }
                        )
                        Tab(
                            selected = activeChecklistId == null && pagerState.currentPage == 1,
                            onClick = { 
                                activeNavTab = "library"
                                activeChecklistId = null
                                scope.launch { pagerState.animateScrollToPage(1) } 
                            },
                            text = { Text("BookShelf", fontWeight = if (activeChecklistId == null && pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal) }
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
                if (activeNavTab == "online") {
                    com.infer.inferead.ui.screens.OnlineStoreTab(viewModel = onlineViewModel, homeViewModel = viewModel)
                } else if (activeChecklistId != null) {
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
                                var showExportDialog by remember { mutableStateOf<String?>(null) }
                                Box {
                                    IconButton(onClick = { showHeaderMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                    }
                                    DropdownMenu(
                                        expanded = showHeaderMenu,
                                        onDismissRequest = { showHeaderMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Download") },
                                            onClick = { 
                                                showHeaderMenu = false
                                                showExportDialog = "DOWNLOAD"
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Share") },
                                            onClick = { 
                                                showHeaderMenu = false
                                                showExportDialog = "SHARE"
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

                                    if (showExportDialog != null) {
                                        androidx.compose.material3.AlertDialog(
                                            onDismissRequest = { showExportDialog = null },
                                            title = { Text(if (showExportDialog == "DOWNLOAD") "Download Checklist" else "Share Checklist") },
                                            text = { Text("Choose format to export:") },
                                            confirmButton = {
                                                TextButton(onClick = {
                                                    if (showExportDialog == "DOWNLOAD") {
                                                        viewModel.convertChecklistToPdf(activeChecklist.id)
                                                    } else {
                                                        viewModel.shareChecklistAsPdf(activeChecklist.id)
                                                    }
                                                    showExportDialog = null
                                                }) { Text("PDF") }
                                            },
                                            dismissButton = {
                                                TextButton(onClick = {
                                                    if (showExportDialog == "DOWNLOAD") {
                                                        viewModel.convertChecklistToTxt(activeChecklist.id)
                                                    } else {
                                                        viewModel.shareChecklistAsTxt(activeChecklist.id)
                                                    }
                                                    showExportDialog = null
                                                }) { Text("TXT") }
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
                                                          minLines = 3,
                                                          maxLines = 8,
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
                                              Box(modifier = Modifier.fillMaxWidth().padding(start = (item.indentLevel * 24).dp, bottom = 8.dp)) {
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
                                                              Icon(Icons.Default.DragHandle, contentDescription = "Drag Handle", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                                          }
                                                      }

                                                      Row(
                                                          modifier = Modifier
                                                              .fillMaxWidth()
                                                              .pointerInput(item.id) {
                                                                  detectTapGestures(
                                                                      onLongPress = {
                                                                          isHoverMenuVisible = true
                                                                      }
                                                                  )
                                                              }
                                                              .pointerInput(item) {
                                                                  var accumulatedDrag = 0f
                                                                  detectHorizontalDragGestures(
                                                                      onDragStart = { accumulatedDrag = 0f },
                                                                      onDragEnd = { accumulatedDrag = 0f },
                                                                      onHorizontalDrag = { change, dragAmount ->
                                                                          if (!isDragging) {
                                                                              accumulatedDrag += dragAmount
                                                                              if (accumulatedDrag > 80f) {
                                                                                  change.consume()
                                                                                  viewModel.updateChecklistItemIndent(item.id, minOf(3, item.indentLevel + 1))
                                                                                  accumulatedDrag = 0f
                                                                              } else if (accumulatedDrag < -80f) {
                                                                                  change.consume()
                                                                                  viewModel.updateChecklistItemIndent(item.id, maxOf(0, item.indentLevel - 1))
                                                                                  accumulatedDrag = 0f
                                                                              }
                                                                          }
                                                                      }
                                                                  )
                                                              }
                                                              .padding(horizontal = 8.dp, vertical = 6.dp),
                                                          verticalAlignment = Alignment.CenterVertically
                                                      ) {
                                                          Checkbox(
                                                              checked = item.isCompleted,
                                                              onCheckedChange = { checked ->
                                                                  val updatedItem = if (checked && item.isPinned) {
                                                                      item.copy(isCompleted = true, isPinned = false)
                                                                  } else {
                                                                      item.copy(isCompleted = checked)
                                                                  }
                                                                  viewModel.updateChecklistItem(updatedItem)
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
                                                  if (isHoverMenuVisible) {
                                                      @OptIn(ExperimentalMaterial3Api::class)
                                                      ModalBottomSheet(
                                                          onDismissRequest = { isHoverMenuVisible = false },
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
                                                                  Icon(Icons.Default.Checklist, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                                                  Spacer(modifier = Modifier.width(12.dp))
                                                                  Text("Task Options", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                              }
                                                              Spacer(modifier = Modifier.height(32.dp))
                                                              
                                                              Row(
                                                                  modifier = Modifier.fillMaxWidth(),
                                                                  horizontalArrangement = Arrangement.SpaceEvenly
                                                              ) {
                                                                  // Edit
                                                                  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showEditDialog = true; isHoverMenuVisible = false }) {
                                                                      Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                                                          Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                      }
                                                                      Spacer(modifier = Modifier.height(8.dp))
                                                                      Text("Edit", style = MaterialTheme.typography.labelMedium)
                                                                  }
                                                                  // Copy Text
                                                                  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { 
                                                                      val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                                      val clip = android.content.ClipData.newPlainText("Copied Text", item.title)
                                                                      clipboardManager.setPrimaryClip(clip)
                                                                      isHoverMenuVisible = false 
                                                                  }) {
                                                                      Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                                                          Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                      }
                                                                      Spacer(modifier = Modifier.height(8.dp))
                                                                      Text("Copy", style = MaterialTheme.typography.labelMedium)
                                                                  }
                                                                  // Pin
                                                                  if (!item.isCompleted) {
                                                                      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { 
                                                                          viewModel.updateChecklistItem(item.copy(isPinned = !item.isPinned))
                                                                          isHoverMenuVisible = false 
                                                                      }) {
                                                                          Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                                                              Icon(if (item.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                          }
                                                                          Spacer(modifier = Modifier.height(8.dp))
                                                                          Text(if (item.isPinned) "Unpin" else "Pin", style = MaterialTheme.typography.labelMedium)
                                                                      }
                                                                  }
                                                              }
                                                              Spacer(modifier = Modifier.height(32.dp))
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
                        } else if (searchQuery.isNotBlank()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
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
                                        is com.infer.inferead.data.Bookshelf -> "search_bookshelf_${result.id}"
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
                                                    Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
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
                                            is com.infer.inferead.data.Bookshelf -> {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(8.dp)).clickable { 
                                                        searchQuery = ""
                                                        focusManager.clearFocus()
                                                        scope.launch { pagerState.animateScrollToPage(1) }
                                                    }.padding(horizontal = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.CollectionsBookmark, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                                                    Spacer(Modifier.width(12.dp))
                                                    Text("Bookshelf: ${result.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    }
                } else {
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondBoundsPageCount = 1
                    ) { page ->
                        if (page == 0) {
                            LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
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
                                                    modifier = Modifier.padding(bottom = 8.dp).consumeHorizontalNestedScroll()
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
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                modifier = Modifier.consumeHorizontalNestedScroll()
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
                                                            if (file.thumbnailUri != null || file.format == "IMAGE") {
                                                                AsyncImage(
                                                                    model = file.thumbnailUri ?: if (file.format == "IMAGE" && file.filePath.startsWith("content://")) android.net.Uri.parse(file.filePath) else if (file.format == "IMAGE") java.io.File(file.filePath) else null,
                                                                    contentDescription = "Thumbnail",
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    contentScale = ContentScale.Crop
                                                                )
                                                            } else {
                                                                Text(
                                                                    text = if (file.format == "CODING" || file.format == "TXT" || file.format == "TEXT") file.filePath.substringAfterLast('.', "").uppercase().takeIf { it.isNotEmpty() && it.length <= 4 } ?: file.format else file.format, 
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

                        item {
                            ReadingGoalWidget(viewModel, onNavigateToStats)
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }
                } else if (page == 1) {
                    BookShelfTab(
                        viewModel = viewModel,
                        bookshelves = bookshelves,
                        bookshelfItems = bookshelfItems,
                        libraryFiles = libraryFiles,
                        onNavigateToReader = {
                            scope.launch { 
                                drawerState.close()
                                onNavigateToReader(it)
                            }
                        },
                        onContextMenu = { file, shelfId ->
                            contextMenuFile = file
                            contextMenuFileShelfId = shelfId
                        },
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
                        onToggleAssignmentMode = { isBookshelfAssignmentMode = !isBookshelfAssignmentMode },
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToStats = onNavigateToStats
                    )
                        }
                    }
                }

                if (activeChecklistId == null && activeNavTab != "online") {
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
                            if (pagerState.currentPage == 0) {
                                FloatingActionButton(
                                    onClick = { sortExpanded = true },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(48.dp).bounceClick(),
                                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
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
                                    Divider()
                                    Text("Minimise/Maximise All", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primary)
                                    DropdownMenuItem(
                                        text = { Text("Minimise All") },
                                        onClick = {
                                            val newSet = availableSections.toSet()
                                            minimisedSections = newSet
                                            homePrefs.edit().putStringSet("minimised_sections", newSet).apply()
                                            sortExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Maximise All") },
                                        onClick = {
                                            minimisedSections = emptySet()
                                            homePrefs.edit().putStringSet("minimised_sections", emptySet()).apply()
                                            sortExpanded = false
                                        }
                                    )
                                }
                            } else if (pagerState.currentPage == 1) {
                                FloatingActionButton(
                                    onClick = { sortExpanded = true },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(48.dp).bounceClick(),
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
                                    Divider()
                                    Text("Minimise/Maximise All", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primary)
                                    DropdownMenuItem(
                                        text = { Text("Minimise All") },
                                        onClick = {
                                            viewModel.setAllBookshelvesMinimised(true)
                                            sortExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Maximise All") },
                                        onClick = {
                                            viewModel.setAllBookshelvesMinimised(false)
                                            sortExpanded = false
                                        }
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

                        if (pagerState.currentPage == 0) {
                            FloatingActionButton(
                                onClick = { 
                                    filePickerLauncher.launch(arrayOf("application/pdf", "application/epub+zip", "text/plain", "image/png", "image/jpeg", "image/webp", "image/bmp", "image/heic", "image/heif", "application/rar", "application/x-rar-compressed", "application/zip", "application/x-zip-compressed", "application/x-cbz", "application/x-cbr", "application/x-7z-compressed", "text/html", "text/css", "text/javascript", "application/javascript", "application/json", "text/xml", "application/xml", "text/x-c", "text/x-java-source", "text/x-python", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                                },
                                modifier = Modifier.size(48.dp).bounceClick(),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                                shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Book")
                            }
                        } else if (pagerState.currentPage == 1) {
                            if (!isBookshelfAssignmentMode) {
                                FloatingActionButton(
                                    onClick = { isBookshelfAssignmentMode = true },
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(48.dp).bounceClick(),
                                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                                ) {
                                    Icon(com.infer.inferead.ui.components.docs_add_on, contentDescription = "Assignment Mode")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (renamingFile != null) {
        var newFileName by remember { mutableStateOf(renamingFile!!.title) }
        AlertDialog(
            onDismissRequest = { renamingFile = null },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFileName.isNotBlank()) {
                        viewModel.renameFile(renamingFile!!.id, newFileName.trim())
                    }
                    renamingFile = null
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renamingFile = null }) { Text("Cancel") }
            }
        )
    }

    if (fileToDelete != null) {
        val file = fileToDelete!!
        val isLinked = !file.filePath.startsWith(context.filesDir.absolutePath + "/library")
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text(if (isLinked) "Remove Linked File" else "Delete File") },
            text = { 
                Text(
                    if (isLinked) "Are you sure you want to remove this file from your library? The original file will remain on your device."
                    else "Are you sure you want to permanently delete this file from the app's internal storage?"
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFile(file.id)
                        fileToDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (contextMenuFile != null) {
        val contentResolver = context.contentResolver
        var fileSize by remember(contextMenuFile) { mutableStateOf<String>("Loading size...") }
        LaunchedEffect(contextMenuFile) {
            if (contextMenuFile != null) {
                val path = contextMenuFile!!.filePath
                var bytes = 0L
                if (path.startsWith("content://")) {
                    try {
                        val cursor = contentResolver.query(android.net.Uri.parse(path), null, null, null, null)
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                if (sizeIndex != -1) {
                                    bytes = it.getLong(sizeIndex)
                                }
                            }
                        }
                    } catch (e: Exception) {}
                } else {
                    bytes = java.io.File(path).length()
                }
                if (bytes > 0) {
                    val kb = bytes / 1024
                    fileSize = if (kb > 1024) "${kb / 1024} MB" else "$kb KB"
                } else {
                    fileSize = "Unknown Size"
                }
            }
        }

        val isLinked = !contextMenuFile!!.filePath.startsWith(context.filesDir.absolutePath + "/library")
        val dateAdded = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(contextMenuFile!!.addedAt))

        ModalBottomSheet(
            onDismissRequest = { contextMenuFile = null; contextMenuFileShelfId = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when(contextMenuFile!!.format) {
                            "PDF" -> androidx.compose.material.icons.Icons.Default.PictureAsPdf
                            "EPUB" -> androidx.compose.material.icons.Icons.Default.Menu
                            "TXT" -> androidx.compose.material.icons.Icons.Default.Description
                            else -> androidx.compose.material.icons.Icons.Default.InsertDriveFile
                        }
                        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contextMenuFile!!.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        val totalStr = if (contextMenuFile!!.totalPages > 0) " • ${contextMenuFile!!.totalPages} Pages" else ""
                        Text(
                            text = "${contextMenuFile!!.format}$totalStr • $fileSize",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isLinked) androidx.compose.material.icons.Icons.Default.Link else androidx.compose.material.icons.Icons.Default.Upload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${if (isLinked) "Linked" else "Imported"} • Added $dateAdded",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Quick Actions
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { 
                        if (contextMenuFile!!.format == "CODING") {
                            formatSelectionDialogFile = contextMenuFile; formatSelectionDialogIsShare = true
                        } else {
                            viewModel.exportFile(context, contextMenuFile!!, modified = false)
                        }
                        contextMenuFile = null; contextMenuFileShelfId = null
                    }.padding(8.dp)) {
                        Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Share", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { 
                        if (contextMenuFile!!.format == "CODING") {
                            formatSelectionDialogFile = contextMenuFile; formatSelectionDialogIsShare = false
                        } else {
                            downloadDialogFile = contextMenuFile
                        }
                        contextMenuFile = null; contextMenuFileShelfId = null
                    }.padding(8.dp)) {
                        Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Download", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { 
                        renamingFile = contextMenuFile
                        contextMenuFile = null; contextMenuFileShelfId = null
                    }.padding(8.dp)) {
                        Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Rename", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { 
                        fileToDelete = contextMenuFile
                        contextMenuFile = null; contextMenuFileShelfId = null
                    }.padding(8.dp)) {
                        Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                androidx.compose.material3.Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // List Options
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    if (contextMenuFileShelfId != null) {
                        ListItem(
                            headlineContent = { Text("Remove from Bookshelf", color = MaterialTheme.colorScheme.error) },
                            leadingContent = { Icon(Icons.Default.Remove, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.clickable {
                                viewModel.removeFileFromBookshelf(contextMenuFileShelfId!!, contextMenuFile!!.id)
                                contextMenuFile = null; contextMenuFileShelfId = null
                            },
                            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                    ListItem(
                        headlineContent = { Text(if (contextMenuFile!!.isToRead) "Remove from Reading List" else "Mark for Reading") },
                        leadingContent = { Icon(if (contextMenuFile!!.isToRead) Icons.Default.BookmarkRemove else Icons.Default.BookmarkAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            viewModel.markToRead(contextMenuFile!!.id, !contextMenuFile!!.isToRead)
                            contextMenuFile = null; contextMenuFileShelfId = null
                        },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (contextMenuFile!!.format in listOf("PDF", "EPUB", "CBZ", "CBR", "CB7", "CODING")) {
                        ListItem(
                            headlineContent = { Text(if (contextMenuFile!!.isFinished) "Mark as Unfinished" else "Mark as Finished") },
                            leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                viewModel.markFinished(contextMenuFile!!.id, !contextMenuFile!!.isFinished)
                                contextMenuFile = null; contextMenuFileShelfId = null
                            },
                            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                    if (contextMenuFile!!.isBookmarked) {
                        ListItem(
                            headlineContent = { Text("Remove All Bookmarks", color = Color(0xFFFFA000)) },
                            leadingContent = { Icon(Icons.Default.BookmarkRemove, contentDescription = null, tint = Color(0xFFFFA000)) },
                            modifier = Modifier.clickable {
                                val fileId = contextMenuFile!!.id
                                viewModel.clearBookmarks(fileId)
                                readerPrefs.edit().remove("bookmarked_pages_$fileId").apply()
                                contextMenuFile = null; contextMenuFileShelfId = null
                            },
                            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                    ListItem(
                        headlineContent = { Text("Personal Rating") },
                        leadingContent = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            ratingDialogFile = contextMenuFile
                            contextMenuFile = null; contextMenuFileShelfId = null
                        },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    ListItem(
                        headlineContent = { Text("Set Custom Cover") },
                        leadingContent = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            editingThumbnailFileId = contextMenuFile!!.id
                            thumbnailPickerLauncher.launch(arrayOf("image/*"))
                            contextMenuFile = null; contextMenuFileShelfId = null
                        },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    ListItem(
                        headlineContent = { Text("Relink File") },
                        leadingContent = { Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            relinkingFileId = contextMenuFile!!.id
                            relinkFilePickerLauncher.launch(arrayOf("*/*"))
                            contextMenuFile = null; contextMenuFileShelfId = null
                        },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (contextMenuFile!!.format == "PDF") {
                        ListItem(
                            headlineContent = { Text("Convert to EPUB", color = MaterialTheme.colorScheme.tertiary) },
                            leadingContent = { Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                            modifier = Modifier.clickable {
                                viewModel.convertPdfToEpub(contextMenuFile!!)
                                contextMenuFile = null; contextMenuFileShelfId = null
                            },
                            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(checklistColor.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Checklist, contentDescription = null, tint = checklistColor)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contextMenuChecklist!!.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Checklist",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Quick Actions
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { 
                        viewModel.shareChecklistAsPdf(contextMenuChecklist!!.id)
                        contextMenuChecklist = null
                    }.padding(8.dp)) {
                        Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Export PDF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { 
                        showColorPickerDialog = contextMenuChecklist
                        contextMenuChecklist = null
                    }.padding(8.dp)) {
                        Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Palette, contentDescription = "Color", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Color", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { 
                        renamingChecklist = contextMenuChecklist
                        contextMenuChecklist = null
                    }.padding(8.dp)) {
                        Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Rename", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { 
                        viewModel.deleteChecklist(contextMenuChecklist!!.id)
                        contextMenuChecklist = null
                    }.padding(8.dp)) {
                        Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                
                // List items for specific actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                viewModel.markChecklistFinished(contextMenuChecklist!!.id, true)
                                contextMenuChecklist = null
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text("Mark all as Completed", style = MaterialTheme.typography.bodyMedium)
                    }
                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                viewModel.markChecklistFinished(contextMenuChecklist!!.id, false)
                                contextMenuChecklist = null
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text("Unmark all", style = MaterialTheme.typography.bodyMedium)
                    }
                }
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

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    if (downloadDialogFile != null) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { downloadDialogFile = null },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Download File", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = downloadDialogFile!!.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.exportFile(context, downloadDialogFile!!, modified = false)
                            downloadDialogFile = null
                        },
                        modifier = Modifier.weight(0.8f).height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Original", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = {
                            viewModel.exportFile(context, downloadDialogFile!!, modified = true)
                            downloadDialogFile = null
                        },
                        modifier = Modifier.weight(1.2f).height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("With Modifications", style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    if (formatSelectionDialogFile != null) {
        val actionType = if (formatSelectionDialogIsShare) "Share" else "Download"
        val icon = if (formatSelectionDialogIsShare) Icons.Default.Share else Icons.Default.Download
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { formatSelectionDialogFile = null },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("$actionType Format", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select a format for ${formatSelectionDialogFile!!.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    listOf("PDF", "TXT", "IMG(JPG)").forEach { format ->
                        androidx.compose.material3.ElevatedCard(
                            onClick = {
                                viewModel.convertCodeFile(context, formatSelectionDialogFile!!, format, formatSelectionDialogIsShare)
                                formatSelectionDialogFile = null
                            },
                            modifier = Modifier.padding(horizontal = 4.dp).size(width = 100.dp, height = 80.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(format, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
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

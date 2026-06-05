@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.infer.inferead.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.infer.inferead.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infer.inferead.viewmodel.ContrastMode
import com.infer.inferead.viewmodel.ReaderViewModel
import kotlin.math.roundToInt
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.infer.inferead.data.LibraryFile
import com.infer.inferead.data.Checklist
import com.infer.inferead.data.ChecklistItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StarAnimationOverlay(modifier: Modifier = Modifier, onAnimationFinished: () -> Unit = {}) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1000)
        onAnimationFinished()
    }
    Box(modifier = modifier.fillMaxSize())
}

@Composable
fun SettingsCardToggle(
    title: String,
    description: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    textColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    iconContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (iconContent != null) {
                iconContent()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, color = textColor)
                if (description.isNotEmpty()) {
                    Text(text = description, style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.7f))
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun ReaderScreen(
    fileId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToChecklist: (Int) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: ReaderViewModel = viewModel()
) {
    val currentFile by viewModel.currentFile.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val activeHighlightMode by viewModel.activeHighlightMode.collectAsState(initial = null)
    
    val liveMinutes by viewModel.liveMinutes.collectAsState()
    val initialTodayMinutes by viewModel.initialTodayMinutes.collectAsState()
    val goalMinutes by viewModel.goalMinutes.collectAsState()
    
    var showGoalCelebration by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while(true) {
            kotlinx.coroutines.delay(60000)
            viewModel.incrementLiveMinutes()
        }
    }
    
    LaunchedEffect(liveMinutes) {
        if (goalMinutes > 0 && liveMinutes > 0 && (initialTodayMinutes + liveMinutes == goalMinutes)) {
            showGoalCelebration = true
        }
    }


    LaunchedEffect(fileId) {
        viewModel.loadFile(fileId)
    }

    DisposableEffect(Unit) {
        viewModel.startReadingSession()
        onDispose {
            viewModel.endReadingSession()
        }
    }

    if (currentFile == null) {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text("Loading...")
        }
        return
    }
    val file = currentFile!!

    val homeViewModel: com.infer.inferead.viewmodel.HomeViewModel = viewModel()
    val libraryFiles by homeViewModel.libraryFiles.collectAsState()
    val checklists by homeViewModel.checklists.collectAsState()
    val currentUser by homeViewModel.currentUser.collectAsState()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val homePrefs = remember { context.getSharedPreferences("home_prefs", android.content.Context.MODE_PRIVATE) }
    
    val bookshelves by homeViewModel.bookshelves.collectAsState()
    val bookshelfItems by homeViewModel.bookshelfItems.collectAsState()
    var activeTab by remember { mutableIntStateOf(homePrefs.getInt("active_tab", 0)) }
    
    val segregationMode = remember(homePrefs) {
        val savedMode = homePrefs.getString("segregation_mode", "FORMAT")
        try { com.infer.inferead.ui.screens.SegregationMode.valueOf(savedMode!!) } catch (e: Exception) { com.infer.inferead.ui.screens.SegregationMode.FORMAT }
    }
    
    val groupedFiles = remember(libraryFiles, segregationMode) {
        when (segregationMode) {
            com.infer.inferead.ui.screens.SegregationMode.FORMAT -> libraryFiles.groupBy { it.format }
            com.infer.inferead.ui.screens.SegregationMode.PAGES -> mapOf("By Pages (Desc)" to libraryFiles.sortedByDescending { it.totalPages })
            com.infer.inferead.ui.screens.SegregationMode.FILE_SIZE -> mapOf("By File Size (Desc)" to libraryFiles.sortedByDescending { 
                try {
                    java.io.File(it.filePath).length()
                } catch (e: Exception) {
                    0L
                }
            })
            com.infer.inferead.ui.screens.SegregationMode.BOOKMARKED -> mapOf("Bookmarked" to libraryFiles.filter { it.isBookmarked })
            com.infer.inferead.ui.screens.SegregationMode.READING_LIST -> mapOf("Reading List" to libraryFiles.filter { it.isToRead })
        }
    }
    
    val categories = remember(libraryFiles, segregationMode) {
        when (segregationMode) {
            com.infer.inferead.ui.screens.SegregationMode.FORMAT -> libraryFiles.map { it.format }.distinct()
            com.infer.inferead.ui.screens.SegregationMode.PAGES -> listOf("By Pages (Desc)")
            com.infer.inferead.ui.screens.SegregationMode.FILE_SIZE -> listOf("By File Size (Desc)")
            com.infer.inferead.ui.screens.SegregationMode.BOOKMARKED -> listOf("Bookmarked")
            com.infer.inferead.ui.screens.SegregationMode.READING_LIST -> listOf("Reading List")
        }
    }
    
    val availableSections = remember(categories) {
        listOf("Checklists") + categories
    }
    
    val sectionOrder = remember(availableSections, segregationMode) {
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

    var contextMenuFile by remember { mutableStateOf<com.infer.inferead.data.LibraryFile?>(null) }
    var contextMenuChecklist by remember { mutableStateOf<com.infer.inferead.data.Checklist?>(null) }
    var showCreateChecklistDialog by remember { mutableStateOf(false) }
    var renamingChecklist by remember { mutableStateOf<com.infer.inferead.data.Checklist?>(null) }
    var showColorPickerDialog by remember { mutableStateOf<com.infer.inferead.data.Checklist?>(null) }
    
    var editingThumbnailFileId by remember { mutableStateOf<Int?>(null) }
    val thumbnailPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { imageUri ->
            editingThumbnailFileId?.let { fileId ->
                homeViewModel.updateThumbnail(fileId, imageUri)
            }
        }
        editingThumbnailFileId = null
    }

    val navPaneWidth by remember { mutableStateOf(homePrefs.getFloat("navpane_width_dp", 300f).dp) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    var showSettingsSheet by remember { mutableStateOf(false) }
    var chapterPreviews by remember { mutableStateOf<List<String>?>(null) }
    var targetScrollAnnId by remember { mutableStateOf<Int?>(null) }
    var showPageAnnotationManager by remember { mutableStateOf(false) }
    var showScrubber by remember { mutableStateOf(false) }
    var showPageCommentsDialog by remember { mutableStateOf(false) }
    var isTitleExpanded by remember { mutableStateOf(false) }
    val textScrollState = rememberScrollState()
    val bookmarkedPages by viewModel.bookmarkedPages.collectAsState()
    
    var verticalScrollProgress by remember { mutableStateOf(0f) }
    var targetVerticalProgress by remember { mutableStateOf<Float?>(null) }
    var annotationPositions by remember { mutableStateOf(emptyList<Pair<Int, Float>>()) }
    var showVerticalScrubber by remember { mutableStateOf(false) }
    var verticalScrubberTimeoutJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(settings.isReaderModeActive) {
        if (settings.isReaderModeActive) {
            showScrubber = false
        } else {
            showVerticalScrubber = true
            verticalScrubberTimeoutJob?.cancel()
            verticalScrubberTimeoutJob = scope.launch {
                kotlinx.coroutines.delay(1500)
                showVerticalScrubber = false
            }
        }
    }

    LaunchedEffect(verticalScrollProgress) {
        if (verticalScrollProgress > 0f && verticalScrollProgress < 1f) {
            showVerticalScrubber = true
            verticalScrubberTimeoutJob?.cancel()
            verticalScrubberTimeoutJob = scope.launch {
                kotlinx.coroutines.delay(1500)
                showVerticalScrubber = false
            }
        }
    }

    // Determine colors based on ContrastMode and Warm Filter
    val backgroundColor = remember(settings.contrastMode, settings.isWarmFilterActive) {
        when (settings.contrastMode) {
            ContrastMode.Dark -> Color(0xFF1A1A1A)
            ContrastMode.HighContrastDark -> Color(0xFF000000)
            ContrastMode.HighContrastLight -> Color(0xFFFFFFFF)
            ContrastMode.EInk -> Color(0xFFF0F0F0)
            ContrastMode.Normal -> {
                if (settings.isWarmFilterActive) Color(0xFFF4ECD8) else Color(0xFFF5F5F5)
            }
        }
    }

    val textColor = remember(settings.contrastMode, settings.isWarmFilterActive) {
        when (settings.contrastMode) {
            ContrastMode.Dark -> Color(0xFFE0E0E0)
            ContrastMode.HighContrastDark -> Color(0xFFFFFFFF)
            ContrastMode.HighContrastLight, ContrastMode.EInk -> Color(0xFF000000)
            ContrastMode.Normal -> {
                if (settings.isWarmFilterActive) Color(0xFF5C4033) else Color(0xFF1C1C1E)
            }
        }
    }

    val barColor = remember(settings.contrastMode, settings.isWarmFilterActive) {
        when (settings.contrastMode) {
            ContrastMode.Dark -> Color(0xFF242424)
            ContrastMode.HighContrastDark -> Color(0xFF121212)
            ContrastMode.HighContrastLight -> Color(0xFFF2F2F7)
            ContrastMode.EInk -> Color(0xFFFFFFFF)
            ContrastMode.Normal -> {
                if (settings.isWarmFilterActive) Color(0xFFEFE6D5) else Color(0xFFFFFFFF)
            }
        }
    }

    val sheetState = rememberModalBottomSheetState()

    val isDrawerClosed = drawerState.currentValue == DrawerValue.Closed && drawerState.targetValue == DrawerValue.Closed

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        scrimColor = if (isDrawerClosed) Color.Transparent else androidx.compose.material3.DrawerDefaults.scrimColor,
        drawerContent = {
            SharedNavPane(
                drawerState = drawerState,
                drawerReady = true, // Reader screen doesn't have the initial animation issue
                isResizable = false,
                initialWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp * 0.6f,
                onWidthChange = {},
                headerContent = {
                    Spacer(Modifier.height(24.dp))
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
                                        androidx.compose.ui.graphics.Brush.linearGradient(
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
                    Spacer(modifier = Modifier.height(8.dp))
                    NavigationDrawerItem(
                        label = { Text("My Library", fontWeight = FontWeight.Bold) },
                        selected = true,
                        icon = { 
                            Icon(
                                Icons.Default.AutoStories, 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.primary
                            ) 
                        },
                        onClick = { 
                            scope.launch { drawerState.close() } 
                            onNavigateBack()
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
                    val primaryColor = MaterialTheme.colorScheme.primary
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Checklists",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                                IconButton(
                                    onClick = { showCreateChecklistDialog = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Create Checklist",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        items(checklists) { checklist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .combinedClickable(
                                        onClick = {
                                            scope.launch { drawerState.close() }
                                            onNavigateToChecklist(checklist.id)
                                        },
                                        onLongClick = {
                                            contextMenuChecklist = checklist
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val checklistColor = try {
                                    Color(android.graphics.Color.parseColor(checklist.colorHex))
                                } catch (e: Exception) {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                }
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = checklistColor
                                )
                                Text(
                                    text = checklist.name,
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
                        
                        if (activeTab == 0) {
                            sectionOrder.filter { it != "Checklists" }.forEach { sectionName ->
                                val filesForCategory = groupedFiles[sectionName] ?: emptyList()
                                if (filesForCategory.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = sectionName,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                        )
                                    }
                                    items(filesForCategory) { file ->
                                        val isCurrentFile = file.id == fileId
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isCurrentFile) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
                                                )
                                                .combinedClickable(
                                                    onClick = {
                                                        scope.launch { drawerState.close() }
                                                        if (file.id != fileId) {
                                                            viewModel.loadFile(file.id)
                                                        }
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
                                                    fontWeight = if (isCurrentFile) FontWeight.SemiBold else FontWeight.Normal,
                                                    fontSize = 13.sp
                                                ),
                                                color = if (isCurrentFile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                            }
                        } else if (activeTab == 1) {
                            bookshelves.sortedBy { it.sortOrder }.forEach { shelf ->
                                val itemsInShelf = bookshelfItems.filter { it.bookshelfId == shelf.id }
                                val shelfFiles = itemsInShelf.mapNotNull { bItem -> libraryFiles.find { it.id == bItem.fileId } }
                                val shelfColor = try { Color(android.graphics.Color.parseColor(shelf.colorHex)) } catch(e:Exception){ primaryColor }
                                
                                if (shelfFiles.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = shelf.name,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = shelfColor),
                                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                        )
                                    }
                                    items(shelfFiles) { file ->
                                        val isCurrentFile = file.id == fileId
                                        Row(
                                            modifier = Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(6.dp))
                                                .background(if (isCurrentFile) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                                .combinedClickable(
                                                    onClick = { scope.launch { drawerState.close() }; if (file.id != fileId) viewModel.loadFile(file.id) },
                                                    onLongClick = { contextMenuFile = file }
                                                ).padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.Description, null, tint = shelfColor.copy(alpha=0.7f), modifier = Modifier.size(16.dp))
                                            Text(file.title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = if(isCurrentFile) FontWeight.SemiBold else FontWeight.Normal, fontSize=13.sp), color = if (isCurrentFile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha=0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        }
                                        Spacer(Modifier.height(4.dp))
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
                            items(bookmarkedFiles) { file ->
                                val isCurrentFile = file.id == fileId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isCurrentFile) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                        .combinedClickable(
                                            onClick = {
                                                scope.launch { drawerState.close() }
                                                if (file.id != fileId) viewModel.loadFile(file.id)
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
                                        color = if (isCurrentFile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
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
                            items(readingListFiles) { file ->
                                val isCurrentFile = file.id == fileId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isCurrentFile) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                        .combinedClickable(
                                            onClick = {
                                                scope.launch { drawerState.close() }
                                                if (file.id != fileId) viewModel.loadFile(file.id)
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
                                        color = if (isCurrentFile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(4.dp)) {
                                Surface(
                                    onClick = { activeTab = 0 },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (activeTab == 0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.AutoStories, null,
                                            tint = if (activeTab == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp).padding(end = 6.dp)
                                        )
                                        Text("Library", style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.SemiBold, color = if (activeTab == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Spacer(Modifier.width(2.dp))
                                Surface(
                                    onClick = { activeTab = 1 },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (activeTab == 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.CollectionsBookmark, null,
                                            tint = if (activeTab == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp).padding(end = 6.dp)
                                        )
                                        Text("Shelf", style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.SemiBold, color = if (activeTab == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            containerColor = backgroundColor,
            topBar = {
            AnimatedVisibility(
                visible = !settings.isReaderModeActive,
                enter = if (settings.contrastMode == ContrastMode.EInk) EnterTransition.None else (fadeIn() + slideInVertically(initialOffsetY = { -it })),
                exit = if (settings.contrastMode == ContrastMode.EInk) ExitTransition.None else (fadeOut() + slideOutVertically(targetOffsetY = { -it }))
            ) {
                Surface(
                    color = barColor.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                        ) {
                            Text(
                                text = currentFile?.title ?: "Loading...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor,
                                maxLines = if (isTitleExpanded) Int.MAX_VALUE else 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        isTitleExpanded = !isTitleExpanded
                                    }
                            )
                            currentFile?.format?.let { format ->
                                var formatDropdownExpanded by remember { mutableStateOf(false) }
                                Box {
                                    Surface(
                                        color = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        contentColor = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(4.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = 1.dp,
                                            color = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.clickable { formatDropdownExpanded = true }
                                    ) {
                                        Text(
                                            text = format,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    androidx.compose.material3.DropdownMenu(
                                        expanded = formatDropdownExpanded,
                                        onDismissRequest = { formatDropdownExpanded = false }
                                    ) {
                                        val sectionName = when(format) {
                                            "EPUB" -> "Ebooks"
                                            "TXT" -> "Text"
                                            "CBZ", "CBR", "CB7" -> "Comic/Manga"
                                            "CODING" -> "Coding"
                                            "IMAGE" -> "Images"
                                            "PDF" -> "PDF"
                                            else -> format
                                        }
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Type: $sectionName") },
                                            onClick = { formatDropdownExpanded = false }
                                        )
                                    }
                                }
                            }
                            val shouldShowBookmark = currentFile?.format !in listOf("TXT", "CODING") || settings.isHorizontalScroll
                            if (shouldShowBookmark) {
                                IconButton(
                                    onClick = { viewModel.toggleBookmark() },
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                ) {
                                    val isCurrentPageBookmarked = bookmarkedPages.contains(currentFile?.currentPage ?: -1)
                                    if (isCurrentPageBookmarked) {
                                        val dotColor = Color(0xFFFFC107)
                                        Canvas(
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            drawCircle(
                                                color = dotColor,
                                                radius = 3.dp.toPx()
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "Bookmark",
                                        tint = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !settings.isReaderModeActive,
                enter = if (settings.contrastMode == ContrastMode.EInk) EnterTransition.None else fadeIn(),
                exit = if (settings.contrastMode == ContrastMode.EInk) ExitTransition.None else fadeOut()
            ) {
                // Right FABs for settings and comments
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
                ) {
                    val allAnns by remember(fileId) { viewModel.getAnnotationsForFile(fileId) }.collectAsState(initial = emptyList<com.infer.inferead.data.Annotation>())
                    val pageAnns by androidx.compose.runtime.remember(allAnns, currentFile?.currentPage, currentFile?.format) {
                        androidx.compose.runtime.derivedStateOf {
                            val index = currentFile?.currentPage ?: 0
                            allAnns.filter { it.cfiRange.startsWith("${index}|") || it.cfiRange == "${index}|PAGE" }
                        }
                    }

                    if (pageAnns.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = { showPageAnnotationManager = true },
                            containerColor = barColor,
                            contentColor = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Bookmarks, contentDescription = "Annotation Manager")
                        }
                    }

                    FloatingActionButton(
                        onClick = { showSettingsSheet = true },
                        containerColor = barColor,
                        contentColor = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.ViewColumn, contentDescription = "Settings")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            AnimatedVisibility(
                visible = settings.isReaderModeActive,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(36.dp)
                        .background(barColor.copy(alpha = 0.8f), CircleShape)
                        .border(1.dp, textColor.copy(alpha = 0.15f), CircleShape)
                        .clickable { scope.launch { drawerState.open() } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Drawer",
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            val contentModifier = Modifier.fillMaxSize()
            // To prevent layout resizing lag when toggling reader mode, we always fillMaxSize.
            // The top and bottom bars will just overlay the content, which is standard for readers.
            
            Box(modifier = contentModifier) {
                currentFile?.let { file ->
                    val toggleReaderMode = { viewModel.setReaderModeActive(!settings.isReaderModeActive) }
                    val allAnns by androidx.compose.runtime.remember(file.id) { viewModel.getAnnotationsForFile(file.id) }.collectAsState(initial = emptyList())
                    // For EPUB, filter annotations to only the current chapter to avoid bleed across pages
                    val pageAnns by androidx.compose.runtime.remember(allAnns, file.currentPage, file.format) {
                        androidx.compose.runtime.derivedStateOf {
                            if (file.format == "EPUB") {
                                allAnns.filter { it.cfiRange.startsWith("${file.currentPage}|") }
                            } else {
                                allAnns
                            }
                        }
                    }
                    // Custom Text Selection State
                    var textSelectionData by remember { mutableStateOf<com.infer.inferead.ui.screens.TextSelectionData?>(null) }
                    var commentingSelectionData by remember { mutableStateOf<com.infer.inferead.ui.screens.TextSelectionData?>(null) }
                    var showHighlightColorsForSelection by remember { mutableStateOf(false) }
                    var editingAnnotation by androidx.compose.runtime.remember { mutableStateOf<com.infer.inferead.data.Annotation?>(null) }
                    var editingHighlight by androidx.compose.runtime.remember { mutableStateOf<com.infer.inferead.data.Annotation?>(null) }
                    var commentText by remember { mutableStateOf("") }
                    var showCommentDialogForSelection by remember { mutableStateOf(false) }
                    
                    val pageCommentTrigger by viewModel.pageCommentTrigger.collectAsState()
                    var lastCommentTrigger by remember { mutableStateOf(0) }
                    LaunchedEffect(pageCommentTrigger) {
                        if (pageCommentTrigger > lastCommentTrigger) {
                            lastCommentTrigger = pageCommentTrigger
                            editingAnnotation = com.infer.inferead.data.Annotation(
                                fileId = file.id,
                                selectedText = "Page ${file.currentPage}",
                                cfiRange = "${file.currentPage}|PAGE",
                                colorHex = "",
                                timestamp = System.currentTimeMillis()
                            )
                            commentText = ""
                        }
                    }
                    
                    when (file.format) {
                        "PDF" -> PdfViewer(
                            filePath = file.filePath,
                            contrastMode = settings.contrastMode,
                            isWarmFilterActive = settings.isWarmFilterActive,
                            isHorizontalScroll = settings.isHorizontalScroll,
                            isReaderModeActive = settings.isReaderModeActive,
                            isNegative = settings.isNegative,
                            currentPage = file.currentPage,
                            onPageChanged = { page -> viewModel.updateCurrentPage(page) },
                            onTotalPages = { total -> viewModel.updateTotalPages(total) },
                            onTap = toggleReaderMode,
                            targetVerticalProgress = targetVerticalProgress,
                            onScrollProgress = { p -> verticalScrollProgress = p }
                        )
                        "TXT", "CODING" -> TXTReader(
                            filePath = file.filePath,
                            format = file.format,
                            settings = settings,
                            chapterIndex = file.currentPage,
                            onPageChanged = { page -> viewModel.updateCurrentPage(page) },
                            onTotalPagesLoaded = { total, previews -> 
                                viewModel.updateTotalPages(total)
                            },
                            onTap = toggleReaderMode,
                            targetVerticalProgress = targetVerticalProgress,
                            onScrollProgress = { progress ->
                                verticalScrollProgress = progress
                                if (!showVerticalScrubber) {
                                    showVerticalScrubber = true
                                }
                                verticalScrubberTimeoutJob?.cancel()
                                verticalScrubberTimeoutJob = scope.launch {
                                    kotlinx.coroutines.delay(1500)
                                    showVerticalScrubber = false
                                }
                            }
                        )
                        "IMAGE" -> ImageViewer(file.filePath, isNoir = settings.isNoir, isNegative = settings.isNegative, vignetteStrength = settings.vignetteStrength, onTap = toggleReaderMode)
                        "CBZ", "CBR", "CB7" -> ComicArchiveViewer(
                            filePath = file.filePath,
                            format = file.format,
                            settings = settings,
                            currentPage = file.currentPage,
                            onPageChanged = { page -> viewModel.updateCurrentPage(page) },
                            onTotalPagesLoaded = { total -> viewModel.updateTotalPages(total) },
                            onTap = toggleReaderMode
                        )
                        "EPUB" -> EPUBReader(
                            filePath = file.filePath,
                            settings = settings,
                            chapterIndex = file.currentPage,
                            onPageChanged = { page -> viewModel.updateCurrentPage(page) },
                            onTotalPagesLoaded = { total, previews -> 
                                viewModel.updateTotalPages(total)
                                chapterPreviews = previews
                            },
                            onTap = toggleReaderMode,
                            onTextSelected = { text, top, bottom, cfiRange ->
                                val finalCfi = if (file.format == "EPUB") "${file.currentPage}|$cfiRange" else cfiRange
                                if (activeHighlightMode.isNullOrEmpty()) {
                                    textSelectionData = com.infer.inferead.ui.screens.TextSelectionData(text, top, bottom, finalCfi)
                                }
                            },
                            onSelectionFinished = { text, top, bottom, cfiRange ->
                                val finalCfi = if (file.format == "EPUB") "${file.currentPage}|$cfiRange" else cfiRange
                                if (!activeHighlightMode.isNullOrEmpty()) {
                                    if (activeHighlightMode == "COMMENT_MODE") {
                                        commentingSelectionData = com.infer.inferead.ui.screens.TextSelectionData(text, top, bottom, finalCfi)
                                        showCommentDialogForSelection = true
                                        viewModel.setActiveHighlightMode("")
                                    } else {
                                        viewModel.insertAnnotation(
                                            com.infer.inferead.data.Annotation(
                                                fileId = file.id,
                                                selectedText = text,
                                                cfiRange = finalCfi,
                                                colorHex = activeHighlightMode ?: "#c25d5d",
                                                timestamp = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                    true
                                } else {
                                    textSelectionData = com.infer.inferead.ui.screens.TextSelectionData(text, top, bottom, finalCfi)
                                    false
                                }
                            },
                            onTextSelectionCleared = {
                                textSelectionData = null
                            },
                            onAnnotationClicked = { annId, top, bottom ->
                                val clickedAnn = pageAnns.find { it.id == annId }
                                if (clickedAnn != null) {
                                    if (clickedAnn.textComment.isNullOrEmpty()) {
                                        editingHighlight = clickedAnn
                                    } else {
                                        editingAnnotation = clickedAnn
                                        commentText = clickedAnn.textComment ?: ""
                                    }
                                }
                            },
                            annotations = pageAnns,
                            targetScrollAnnId = targetScrollAnnId,
                            targetVerticalProgress = targetVerticalProgress,
                            onScrollProgress = { progress ->
                                verticalScrollProgress = progress
                                if (!showVerticalScrubber) {
                                    showVerticalScrubber = true
                                }
                                verticalScrubberTimeoutJob?.cancel()
                                verticalScrubberTimeoutJob = scope.launch {
                                    kotlinx.coroutines.delay(1500)
                                    showVerticalScrubber = false
                                }
                            },
                            onAnnotationPositions = { positions ->
                                annotationPositions = positions
                            }
                        )
                        else -> {
                            Text(
                                "Unsupported format.",
                                modifier = Modifier.padding(16.dp),
                                color = textColor
                            )
                        }
                    }

                    // Text Selection Hover Menu
                    if (textSelectionData != null && (file.format == "EPUB" || file.format in listOf("TXT", "DOC", "DOCX"))) {
                        val sel = textSelectionData!!
                        val density = LocalDensity.current.density
                        // Determine whether to show above or below based on position
                        val screenHeightPx = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * density
                        val isTopHalf = sel.top < screenHeightPx / 2
                        val menuY = if (isTopHalf) (sel.bottom / density).dp + 10.dp else (sel.top / density).dp - 60.dp
                        
                        androidx.compose.ui.window.Popup(
                            alignment = Alignment.TopCenter,
                            offset = androidx.compose.ui.unit.IntOffset(0, (menuY.value * density).toInt()),
                            properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .shadow(8.dp, RoundedCornerShape(8.dp)),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp).width(IntrinsicSize.Max)
                                ) {
                                    if (showHighlightColorsForSelection) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val colors = listOf("#c25d5d", "#56b056", "#d9cb36", "#a25dc2", "#78a1e3")
                                            colors.forEach { color ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .padding(4.dp)
                                                        .background(Color(android.graphics.Color.parseColor(color)), CircleShape)
                                                        .clickable {
                                                            viewModel.insertAnnotation(
                                                                com.infer.inferead.data.Annotation(
                                                                    id = sel.annId ?: 0,
                                                                    fileId = file.id,
                                                                    selectedText = sel.text,
                                                                    cfiRange = sel.cfiRange,
                                                                    colorHex = color,
                                                                    timestamp = System.currentTimeMillis()
                                                                )
                                                            )
                                                            textSelectionData = null
                                                            showHighlightColorsForSelection = false
                                                        }
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            if (sel.annId != null) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .padding(4.dp)
                                                        .clickable {
                                                            viewModel.deleteAnnotation(
                                                                com.infer.inferead.data.Annotation(id = sel.annId!!, fileId = 0, cfiRange = "", colorHex = "")
                                                            )
                                                            showHighlightColorsForSelection = false
                                                            textSelectionData = null
                                                        }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Icon(
                                                imageVector = Icons.Default.Close, 
                                                contentDescription = "Close Colors",
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .padding(4.dp)
                                                    .clickable { showHighlightColorsForSelection = false }
                                            )
                                        }
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text("Copy") },
                                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                            onClick = { 
                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("InfeRead Copied Text", sel.text)
                                                clipboard.setPrimaryClip(clip)
                                                textSelectionData = null
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Highlight") },
                                            leadingIcon = { Icon(Icons.Default.FormatColorText, contentDescription = null) },
                                            onClick = { showHighlightColorsForSelection = true }
                                        )
                                        
                                        if (file.format == "EPUB") {
                                            DropdownMenuItem(
                                                text = { Text("Comment") },
                                                leadingIcon = { Icon(Icons.Default.Comment, contentDescription = null) },
                                                onClick = { 
                                                    commentingSelectionData = sel
                                                    showCommentDialogForSelection = true
                                                    textSelectionData = null
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { 
                                                    val displayText = sel.text.trim().take(15) + if (sel.text.length > 15) "..." else ""
                                                    Text("Search \"$displayText\"") 
                                                },
                                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                                onClick = { 
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH)
                                                    intent.putExtra(android.app.SearchManager.QUERY, sel.text)
                                                    context.startActivity(intent)
                                                    textSelectionData = null
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        showHighlightColorsForSelection = false
                    }

                    // Edit Annotation Dialog
                    if (editingHighlight != null) {
                        AlertDialog(
                            onDismissRequest = { editingHighlight = null },
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 8.dp,
                            title = { Text("Highlight Options") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(
                                        text = "\"${editingHighlight!!.selectedText.trim()}\"",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val colors = listOf("#c25d5d", "#56b056", "#d9cb36", "#a25dc2", "#78a1e3")
                                        colors.forEach { color ->
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(Color(android.graphics.Color.parseColor(color)), CircleShape)
                                                    .border(if (editingHighlight!!.colorHex == color) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                                    .clickable {
                                                        viewModel.insertAnnotation(editingHighlight!!.copy(colorHex = color))
                                                        editingHighlight = null
                                                    }
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { editingHighlight = null }) { Text("Close") }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.deleteAnnotation(editingHighlight!!)
                                        editingHighlight = null
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text("Delete") }
                            }
                        )
                    }

                    if (editingAnnotation != null) {
                        AlertDialog(
                            onDismissRequest = { 
                                editingAnnotation = null
                                commentText = ""
                                commentingSelectionData = null
                            },
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 8.dp,
                            title = { Text("Edit Annotation") },
                            text = {
                                Column {
                                    Text("Selected: \"${editingAnnotation!!.selectedText.take(50)}${if (editingAnnotation!!.selectedText.length > 50) "..." else ""}\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = commentText,
                                        onValueChange = { if (it.length <= 500) commentText = it },
                                        modifier = Modifier.fillMaxWidth().height(120.dp),
                                        placeholder = { Text("Enter your comment (max 500 chars)") },
                                        maxLines = 5,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${commentText.length}/500", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val updatedAnn = editingAnnotation!!.copy(
                                        textComment = commentText,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    viewModel.insertAnnotation(updatedAnn) // Upsert
                                    editingAnnotation = null
                                    commentText = ""
                                }) {
                                    Text("Save")
                                }
                            },
                            dismissButton = {
                                Row {
                                    TextButton(onClick = { 
                                        viewModel.deleteAnnotation(editingAnnotation!!)
                                        editingAnnotation = null
                                        commentText = ""
                                    }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                        Text("Delete")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(onClick = { 
                                        editingAnnotation = null
                                        commentText = ""
                                    }) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        )
                    }

                    if (showCommentDialogForSelection && commentingSelectionData != null) {
                        val sel = commentingSelectionData!!
                        AlertDialog(
                            onDismissRequest = { 
                                showCommentDialogForSelection = false
                                commentText = ""
                                commentingSelectionData = null
                            },
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 8.dp,
                            title = { Text("Add Comment") },
                            text = {
                                Column {
                                    Text("Selected: \"${sel.text.take(50)}${if (sel.text.length > 50) "..." else ""}\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = commentText,
                                        onValueChange = { if (it.length <= 500) commentText = it },
                                        modifier = Modifier.fillMaxWidth().height(120.dp),
                                        placeholder = { Text("Enter your comment (max 500 chars)") },
                                        maxLines = 5,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${commentText.length}/500", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.insertAnnotation(
                                        com.infer.inferead.data.Annotation(
                                            id = sel.annId ?: 0,
                                            fileId = file.id,
                                            selectedText = sel.text,
                                            cfiRange = sel.cfiRange,
                                            colorHex = "#c25d5d",
                                            textComment = commentText,
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )
                                    showCommentDialogForSelection = false
                                    commentText = ""
                                    commentingSelectionData = null
                                }) {
                                    Text("Save")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { 
                                    showCommentDialogForSelection = false
                                    commentText = ""
                                }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }

            // Warm Amber Overlay (Reading Mode) - allows gestures through
            if (settings.isWarmFilterActive && settings.contrastMode != ContrastMode.EInk) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFFFF1E0).copy(alpha = 0.12f))
                )
            }
            
            // Overlays UI
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (currentFile?.format in listOf("TXT", "CODING", "EPUB", "PDF") && !settings.isHorizontalScroll) {
                    val scrubberAllAnns by remember(currentFile) { viewModel.getAnnotationsForFile(currentFile?.id ?: 0) }.collectAsState(initial = emptyList())
                    val scrubberAnns = remember(scrubberAllAnns, currentFile?.currentPage, currentFile?.format) {
                        if (currentFile?.format == "EPUB") {
                            scrubberAllAnns.filter { it.cfiRange.startsWith("${currentFile?.currentPage}|") }
                        } else {
                            scrubberAllAnns
                        }
                    }
                    VerticalScrubber(
                        progressProvider = { verticalScrollProgress },
                        onProgressChange = { p -> targetVerticalProgress = p },
                        annotationPositions = annotationPositions,
                        annotations = scrubberAnns,
                        isBookmarked = bookmarkedPages.contains(currentFile?.currentPage ?: -1),
                        visible = showVerticalScrubber,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = 4.dp)
                    )
                }

                // Bottom left scrubber button (not shown for IMAGE files)
                AnimatedVisibility(
                    visible = !settings.isReaderModeActive && currentFile?.format != "IMAGE",
                    enter = if (settings.contrastMode == ContrastMode.EInk) EnterTransition.None else fadeIn(animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)),
                    exit = if (settings.contrastMode == ContrastMode.EInk) ExitTransition.None else fadeOut(animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    FloatingActionButton(
                        onClick = { showScrubber = !showScrubber },
                        containerColor = barColor,
                        contentColor = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(Icons.Default.List, contentDescription = "Scrubber")
                    }
                }

                // Bottom center pagination
                AnimatedVisibility(
                    visible = !settings.isReaderModeActive && currentFile != null && currentFile!!.totalPages > 0,
                    enter = if (settings.contrastMode == ContrastMode.EInk) EnterTransition.None else fadeIn(animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)),
                    exit = if (settings.contrastMode == ContrastMode.EInk) ExitTransition.None else fadeOut(animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        color = barColor.copy(alpha = 0.9f),
                        shape = CircleShape,
                        shadowElevation = 2.dp,
                        modifier = Modifier.padding(bottom = 28.dp)
                    ) {
                        Text(
                            text = "${currentFile!!.currentPage} / ${currentFile!!.totalPages}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Persistent Highlight Toolbar
            androidx.compose.animation.AnimatedVisibility(
                visible = activeHighlightMode != null && activeHighlightMode != "" && activeHighlightMode != "COMMENT_MODE",
                enter = androidx.compose.animation.slideInVertically(
                    initialOffsetY = { -it }, 
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
                ) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(
                    targetOffsetY = { -it }, 
                    animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
                ) + androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = innerPadding.calculateTopPadding() + 64.dp)
            ) {
                Surface(
                    color = barColor.copy(alpha = 0.95f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
                    ) {
                        val colors = listOf("#c25d5d", "#56b056", "#d9cb36", "#a25dc2", "#78a1e3")
                        colors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(color)), androidx.compose.foundation.shape.CircleShape)
                                    .border(
                                        if (activeHighlightMode == color) 2.dp else 0.dp,
                                        if (activeHighlightMode == color) textColor else Color.Transparent,
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                                    .clickable { viewModel.setActiveHighlightMode(color) }
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { viewModel.setActiveHighlightMode(null) }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Highlight Mode", tint = textColor)
                        }
                    }
                }
            }

            // Page Scrubber Overlay Dialog
            if (showScrubber && currentFile != null && currentFile!!.totalPages > 0) {
                var scrubbingPage by remember(showScrubber) {
                    mutableFloatStateOf(currentFile?.currentPage?.toFloat() ?: 1f)
                }
                val previewListState = rememberLazyListState()
                val density = LocalDensity.current
                val itemWidth = 95.dp
                val itemWidthPx = with(density) { itemWidth.toPx() }
                var rowWidthPx by remember { mutableIntStateOf(0) }
                val centerIndex = (scrubbingPage.roundToInt() - 1).coerceIn(0, currentFile!!.totalPages - 1)
                
                LaunchedEffect(centerIndex, rowWidthPx) {
                    if (rowWidthPx > 0) {
                        val scrollOffset = - (rowWidthPx / 2f - itemWidthPx / 2f).roundToInt()
                        previewListState.scrollToItem(centerIndex, scrollOffset)
                    }
                }

                // Bidirectional sync: scroll -> slider update
                val isListDragged by previewListState.interactionSource.collectIsDraggedAsState()
                val centeredItemIndex by remember(previewListState) {
                    derivedStateOf {
                        val layoutInfo = previewListState.layoutInfo
                        val visibleItems = layoutInfo.visibleItemsInfo
                        if (visibleItems.isEmpty()) return@derivedStateOf -1
                        val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                        
                        var closestIndex = -1
                        var minDistance = Float.MAX_VALUE
                        for (item in visibleItems) {
                            val itemCenter = item.offset + item.size / 2f
                            val distance = kotlin.math.abs(itemCenter - viewportCenter)
                            if (distance < minDistance) {
                                minDistance = distance
                                closestIndex = item.index
                            }
                        }
                        closestIndex
                    }
                }

                LaunchedEffect(isListDragged, centeredItemIndex) {
                    if (isListDragged && centeredItemIndex != -1) {
                        val page = centeredItemIndex + 1
                        scrubbingPage = page.toFloat()
                        viewModel.updateCurrentPage(page)
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures { /* Intercept click so it doesn't dismiss */ }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LazyRow(
                        state = previewListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp)
                            .onSizeChanged { rowWidthPx = it.width },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(currentFile!!.totalPages) { index ->
                            val isCurrent = index == centerIndex
                            val scale = if (isCurrent) 1.0f else 0.82f
                            val alpha = if (isCurrent) 1.0f else 0.6f
                            
                            Box(
                                modifier = Modifier
                                    .width(itemWidth)
                                    .height(135.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        this.alpha = alpha
                                    }
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isCurrent) 2.dp else 1.dp,
                                        color = if (isCurrent) {
                                            if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary
                                        } else {
                                            textColor.copy(alpha = 0.3f)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        scrubbingPage = (index + 1).toFloat()
                                        viewModel.updateCurrentPage(index + 1)
                                    }
                            ) {
                                if (currentFile!!.format == "PDF") {
                                    PdfPagePreview(
                                        filePath = currentFile!!.filePath,
                                        pageIndex = index,
                                        contrastMode = settings.contrastMode,
                                        isWarmFilterActive = settings.isWarmFilterActive,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else if (currentFile!!.format == "CBZ" || currentFile!!.format == "CBR" || currentFile!!.format == "CB7") {
                                    com.infer.inferead.ui.screens.CbzPagePreview(
                                        filePath = currentFile!!.filePath,
                                        pageIndex = index,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    val previewText = chapterPreviews?.getOrNull(index) ?: if (currentFile!!.format == "EPUB") "Chapter ${index + 1}" else "${index + 1}"
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).clip(RoundedCornerShape(8.dp)).padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(previewText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                val hasBookmark = bookmarkedPages.contains(index + 1)
                                val pageAnns by remember(currentFile) { viewModel.getAnnotationsForFile(currentFile!!.id) }.collectAsState(initial = emptyList<com.infer.inferead.data.Annotation>())
                                val hasComment = pageAnns.any { it.cfiRange == "${index + 1}|PAGE" }

                                if (hasBookmark || hasComment) {
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(5.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (hasComment) {
                                            Icon(
                                                imageVector = Icons.Default.Comment,
                                                contentDescription = "Has Comment",
                                                tint = Color(0xFFB39DDB),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        if (hasBookmark) {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .background(Color(0xFFFFC107), CircleShape)
                                                    .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Page pill capsule
                    Surface(
                        color = barColor.copy(alpha = 0.85f),
                        shape = CircleShape,
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = "Page ${scrubbingPage.roundToInt()} of ${currentFile!!.totalPages}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Scrubber slider with bookmark dots overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .height(32.dp)
                    ) {
                        Slider(
                            value = scrubbingPage,
                            onValueChange = { pageVal ->
                                scrubbingPage = pageVal
                                viewModel.updateCurrentPage(pageVal.roundToInt())
                            },
                            valueRange = 1f..currentFile!!.totalPages.toFloat(),
                            steps = if (currentFile!!.totalPages > 2) currentFile!!.totalPages - 2 else 0,
                            colors = SliderDefaults.colors(
                                activeTrackColor = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = textColor.copy(alpha = 0.24f)
                            ),
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .size(23.dp)
                                        .shadow(2.dp, androidx.compose.foundation.shape.CircleShape)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(if (bookmarkedPages.contains(scrubbingPage.roundToInt())) Color(0xFFFFC107) else if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary)
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        // Bookmark dot markers
                        if (bookmarkedPages.isNotEmpty() && currentFile!!.totalPages > 1) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val trackStart = 10.dp.toPx()
                                val trackEnd = size.width - 10.dp.toPx()
                                val trackWidth = trackEnd - trackStart
                                val cy = size.height / 2f
                                bookmarkedPages.forEach { page ->
                                    val fraction = (page - 1).toFloat() / (currentFile!!.totalPages - 1).toFloat()
                                    val cx = trackStart + fraction * trackWidth
                                    drawCircle(
                                        color = Color(0xFFFFC107),
                                        radius = 5.dp.toPx(),
                                        center = Offset(cx, cy)
                                    )
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.6f),
                                        radius = 5.dp.toPx(),
                                        center = Offset(cx, cy),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                    )
                                }
                            }
                        }
                        // Comment dot markers
                        val pageAnns by remember(currentFile) { viewModel.getAnnotationsForFile(currentFile!!.id) }.collectAsState(initial = emptyList<com.infer.inferead.data.Annotation>())
                        if (pageAnns.any { it.cfiRange.contains("|PAGE") } && currentFile!!.totalPages > 1) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val trackStart = 10.dp.toPx()
                                val trackEnd = size.width - 10.dp.toPx()
                                val trackWidth = trackEnd - trackStart
                                val cy = size.height / 2f
                                pageAnns.filter { it.cfiRange.contains("|PAGE") }.forEach { ann ->
                                    val pageIndex = ann.cfiRange.split("|")[0].toIntOrNull() ?: 1
                                    val fraction = (pageIndex - 1).toFloat() / (currentFile!!.totalPages - 1).toFloat()
                                    val cx = trackStart + fraction * trackWidth
                                    drawCircle(
                                        color = Color(0xFFB39DDB),
                                        radius = 4.dp.toPx(),
                                        center = Offset(cx, cy - 12.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Removed custom TXT scrubbers in favor of the universal scrubber above.
            }
        }
        
        if (showGoalCelebration) {
            StarAnimationOverlay(onAnimationFinished = { showGoalCelebration = false })
        }
    }



    // Settings Bottom Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = barColor,
            dragHandle = { BottomSheetDefaults.DragHandle(color = textColor.copy(alpha = 0.4f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp)
            ) {
                val formatGroup = when (currentFile?.format) {
                    "EPUB" -> "EPUB"
                    "TXT", "DOC", "DOCX" -> "TXT_DOC_DOCX"
                    "CODING" -> "CODING"
                    "CBZ", "CBR", "CB7" -> "CBZ_CBR_CB7"
                    "PDF" -> "PDF"
                    "IMAGE" -> "IMAGE"
                    else -> "OTHER"
                }

                Text(
                    text = if (formatGroup == "IMAGE") "Image Settings" else "Reader Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                if (formatGroup == "IMAGE") {
                    // Image-specific settings
                    SettingsCardToggle(
                        title = "Noir",
                        description = "Black & white filter",
                        checked = settings.isNoir,
                        onCheckedChange = { viewModel.setNoir(it) },
                        textColor = textColor,
                        iconContent = {
                            Canvas(modifier = Modifier.size(28.dp)) {
                                drawArc(color = Color.Black, startAngle = -90f, sweepAngle = 180f, useCenter = true)
                                drawArc(color = Color.White, startAngle = 90f, sweepAngle = 180f, useCenter = true)
                                drawCircle(color = Color.Gray.copy(alpha = 0.3f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsCardToggle(
                        title = "Negative",
                        description = "Inverts all colours",
                        checked = settings.isNegative,
                        onCheckedChange = { viewModel.setNegative(it) },
                        textColor = textColor,
                        iconContent = {
                            Canvas(modifier = Modifier.size(28.dp)) {
                                drawRect(color = Color.Black)
                                drawCircle(color = Color.White, radius = size.minDimension * 0.3f)
                                drawRect(color = Color.White.copy(alpha = 0.15f), size = size / 2f)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Vignette Effect",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            val displayValue = String.format(java.util.Locale.US, "%.2f", settings.vignetteStrength)
                            val displayStr = if (settings.vignetteStrength > 0) "+$displayValue" else displayValue
                            Text(
                                text = displayStr,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Slide left for white borders, right for black borders",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = settings.vignetteStrength,
                            onValueChange = { viewModel.setVignetteStrength(Math.round(it * 20f) / 20f) },
                            valueRange = -1f..1f,
                            steps = 39,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (formatGroup == "EPUB") {
                    var showCommentOptions by remember { mutableStateOf(false) }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { 
                            viewModel.setActiveHighlightMode("#c25d5d")
                            showSettingsSheet = false
                            showCommentOptions = false 
                        }) { Icon(Icons.Default.Highlight, contentDescription = "Highlight", tint = textColor) }
                        IconButton(onClick = { showCommentOptions = !showCommentOptions }) { Icon(Icons.Default.Comment, contentDescription = "Comment", tint = textColor) }
                        IconButton(onClick = { 
                            showSettingsSheet = false
                            viewModel.setShowAnnotationManager(true) 
                        }) { Icon(Icons.Default.Edit, contentDescription = "Edit Annotations", tint = textColor) }
                        
                        var showHiddenMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showHiddenMenu = true }) { 
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "More Options", tint = textColor) 
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = showHiddenMenu,
                                onDismissRequest = { showHiddenMenu = false }
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Search File") },
                                    onClick = { showHiddenMenu = false }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Other Options") },
                                    onClick = { showHiddenMenu = false }
                                )
                            }
                        }
                    }
                    
                    if (showCommentOptions) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = {
                                viewModel.setActiveHighlightMode("COMMENT_MODE")
                                showSettingsSheet = false
                                showCommentOptions = false
                            }) { Text("Select Text", color = textColor) }
                            TextButton(onClick = {
                                showSettingsSheet = false
                                showCommentOptions = false
                                viewModel.triggerPageComment()
                            }) { Text("Current Page", color = textColor) }
                        }
                    }

                    androidx.compose.material3.Divider(color = textColor.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(modifier = Modifier.size(width = 48.dp, height = 32.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF9F5EB)).border(1.dp, if(settings.contrastMode == ContrastMode.Normal) Color.Blue else Color.Transparent, RoundedCornerShape(12.dp)).clickable { viewModel.setContrastMode(ContrastMode.Normal) })
                            Box(modifier = Modifier.size(width = 48.dp, height = 32.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C2C)).border(1.dp, if(settings.contrastMode == ContrastMode.Dark) Color.Blue else Color.Transparent, RoundedCornerShape(12.dp)).clickable { viewModel.setContrastMode(ContrastMode.Dark) })
                            Box(modifier = Modifier.size(width = 48.dp, height = 32.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFFFFF)).border(1.dp, if(settings.contrastMode == ContrastMode.HighContrastLight) Color.Blue else Color.Gray, RoundedCornerShape(12.dp)).clickable { viewModel.setContrastMode(ContrastMode.HighContrastLight) })
                            Box(modifier = Modifier.size(width = 48.dp, height = 32.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF000000)).border(1.dp, if(settings.contrastMode == ContrastMode.HighContrastDark) Color.Blue else Color.Gray, RoundedCornerShape(12.dp)).clickable { viewModel.setContrastMode(ContrastMode.HighContrastDark) })
                            Box(modifier = Modifier.size(width = 48.dp, height = 32.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, if(settings.contrastMode == ContrastMode.EInk) Color.Blue else Color.Gray, RoundedCornerShape(12.dp)).clickable { viewModel.setContrastMode(ContrastMode.EInk) }) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    Box(modifier = Modifier.fillMaxHeight().weight(1f).background(Color(0xFFE0E0E0)))
                                    Box(modifier = Modifier.fillMaxHeight().weight(1f).background(Color(0xFFFFFFFF)))
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.weight(2f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Font Size", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = { viewModel.setFontSizeMultiplier(settings.fontSizeMultiplier - 0.1f) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Remove, contentDescription = "-", tint = textColor) }
                                    Text("${(settings.fontSizeMultiplier * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = textColor, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                                    IconButton(onClick = { viewModel.setFontSizeMultiplier(settings.fontSizeMultiplier + 0.1f) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, contentDescription = "+", tint = textColor) }
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Word Spacing", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = { viewModel.setWordSpacing((settings.wordSpacingMultiplier - 0.1f).coerceIn(0.5f, 3.0f)) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Remove, contentDescription = "-", tint = textColor) }
                                    Text("${(settings.wordSpacingMultiplier * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = textColor, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                                    IconButton(onClick = { viewModel.setWordSpacing((settings.wordSpacingMultiplier + 0.1f).coerceIn(0.5f, 3.0f)) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, contentDescription = "+", tint = textColor) }
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Line Spacing", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = { viewModel.setLineSpacing((settings.lineSpacingMultiplier - 0.1f).coerceIn(0.5f, 3.0f)) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Remove, contentDescription = "-", tint = textColor) }
                                    Text("${(settings.lineSpacingMultiplier * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = textColor, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                                    IconButton(onClick = { viewModel.setLineSpacing((settings.lineSpacingMultiplier + 0.1f).coerceIn(0.5f, 3.0f)) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, contentDescription = "+", tint = textColor) }
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconButton(onClick = { viewModel.setWarmFilterActive(!settings.isWarmFilterActive) }, modifier = Modifier.border(width = if (settings.isWarmFilterActive) 2.dp else 0.dp, color = if (settings.isWarmFilterActive) Color(0xFFCC7722) else Color.Transparent, shape = RoundedCornerShape(8.dp)).padding(4.dp)) { Icon(Icons.Default.MenuBook, contentDescription = "Reading Mode", tint = if (settings.isWarmFilterActive) Color(0xFFCC7722) else textColor) }
                            IconButton(onClick = { 
                                viewModel.setReaderModeActive(!settings.isReaderModeActive)
                                val activity = context as? android.app.Activity
                                val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(activity!!.window, activity.window.decorView)
                                if (!settings.isReaderModeActive) { windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()); windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE } 
                                else { windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
                            }) { Icon(imageVector = if (settings.isReaderModeActive) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = "Immersive Mode", tint = if (settings.isReaderModeActive) MaterialTheme.colorScheme.primary else textColor, modifier = Modifier.size(if (settings.isReaderModeActive) 32.dp else 24.dp)) }
                            IconButton(onClick = { viewModel.setFontBold(!settings.fontBold) }) { Text("B", fontWeight = if (settings.fontBold) FontWeight.Black else FontWeight.Normal, color = if (settings.fontBold) MaterialTheme.colorScheme.primary else textColor, fontSize = 20.sp) }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    val scriptType = remember(file.title) {
                        val text = file.title
                        val devanagariRegex = Regex("[\\u0900-\\u097F]")
                        val latinRegex = Regex("[a-zA-Z]")
                        val hasDevanagari = devanagariRegex.containsMatchIn(text)
                        val hasLatin = latinRegex.containsMatchIn(text)
                        when {
                            hasDevanagari && hasLatin -> "MIXED"
                            hasDevanagari -> "DEVANAGARI"
                            else -> "LATIN"
                        }
                    }

                    val latinFonts = listOf(
                        "SansSerif" to FontFamily.SansSerif,
                        "Google Sans" to FontFamily(androidx.compose.ui.text.font.Font("fonts/google_sans.ttf", context.assets)),
                        "Literata" to FontFamily(androidx.compose.ui.text.font.Font("fonts/literata.ttf", context.assets)),
                        "Serif" to FontFamily.Serif,
                        "Monospace" to FontFamily.Monospace
                    )
                    
                    val devanagariFonts = listOf(
                        "Original" to FontFamily.Default,
                        "Amita" to FontFamily(androidx.compose.ui.text.font.Font("fonts/amita.ttf", context.assets)),
                        "Hind" to FontFamily(androidx.compose.ui.text.font.Font("fonts/hind.ttf", context.assets)),
                        "Yatra One" to FontFamily(androidx.compose.ui.text.font.Font("fonts/yatra_one.ttf", context.assets))
                    )

                    @Composable
                    fun FontRow(fonts: List<Pair<String, FontFamily>>) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.Top
                        ) {
                            fonts.forEach { (name, font) ->
                                val isSelected = settings.fontFamily == name
                                Column(
                                    modifier = Modifier.weight(1f, fill = false).width(64.dp).padding(horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        onClick = { viewModel.setFontFamily(name) },
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF5C5E8F) else MaterialTheme.colorScheme.primary) else Color.Transparent,
                                        border = BorderStroke(1.dp, if (isSelected) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF5C5E8F) else MaterialTheme.colorScheme.primary) else textColor.copy(alpha = 0.3f))
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Text(
                                                text = "Aa", 
                                                fontFamily = font, 
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, 
                                                color = if (isSelected) Color.White else textColor, 
                                                fontSize = 24.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = name.replace(" ", ""), 
                                        fontFamily = FontFamily.Default, 
                                        color = if (isSelected) textColor else textColor.copy(alpha = 0.7f), 
                                        fontSize = 11.sp, 
                                        maxLines = 1,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    if (scriptType == "DEVANAGARI") {
                        FontRow(devanagariFonts)
                    } else if (scriptType == "MIXED") {
                        FontRow(latinFonts)
                        Spacer(modifier = Modifier.height(12.dp))
                        FontRow(devanagariFonts)
                    } else {
                        FontRow(latinFonts)
                    }

                } else if (formatGroup == "TXT_DOC_DOCX" || formatGroup == "CODING") {
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().height(230.dp).padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            val contrastModifier = Modifier.size(width = 48.dp, height = 32.dp).clip(RoundedCornerShape(12.dp))
                            Box(modifier = contrastModifier.background(Color(0xFFF9F5EB)).border(1.dp, if(settings.contrastMode == ContrastMode.Normal) Color.Blue else Color.Transparent, RoundedCornerShape(12.dp)).clickable { viewModel.setContrastMode(ContrastMode.Normal) })
                            Box(modifier = contrastModifier.background(Color(0xFF2C2C2C)).border(1.dp, if(settings.contrastMode == ContrastMode.Dark) Color.Blue else Color.Transparent, RoundedCornerShape(12.dp)).clickable { viewModel.setContrastMode(ContrastMode.Dark) })
                            Box(modifier = contrastModifier.background(Color(0xFFFFFFFF)).border(1.dp, if(settings.contrastMode == ContrastMode.HighContrastLight) Color.Blue else Color.Gray, RoundedCornerShape(12.dp)).clickable { viewModel.setContrastMode(ContrastMode.HighContrastLight) })
                            Box(modifier = contrastModifier.background(Color(0xFF000000)).border(1.dp, if(settings.contrastMode == ContrastMode.HighContrastDark) Color.Blue else Color.Gray, RoundedCornerShape(12.dp)).clickable { viewModel.setContrastMode(ContrastMode.HighContrastDark) })
                            Box(modifier = contrastModifier.border(1.dp, if(settings.contrastMode == ContrastMode.EInk) Color.Blue else Color.Gray, RoundedCornerShape(12.dp)).clickable { viewModel.setContrastMode(ContrastMode.EInk) }) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    Box(modifier = Modifier.fillMaxHeight().weight(1f).background(Color(0xFFE0E0E0)))
                                    Box(modifier = Modifier.fillMaxHeight().weight(1f).background(Color(0xFFFFFFFF)))
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.weight(2f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Font Size", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = { viewModel.setFontSizeMultiplier(settings.fontSizeMultiplier - 0.1f) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Remove, contentDescription = "-", tint = textColor) }
                                    Text("${(settings.fontSizeMultiplier * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = textColor, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                                    IconButton(onClick = { viewModel.setFontSizeMultiplier(settings.fontSizeMultiplier + 0.1f) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, contentDescription = "+", tint = textColor) }
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Word Spacing", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = { viewModel.setWordSpacing((settings.wordSpacingMultiplier - 0.1f).coerceIn(0.5f, 3.0f)) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Remove, contentDescription = "-", tint = textColor) }
                                    Text("${(settings.wordSpacingMultiplier * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = textColor, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                                    IconButton(onClick = { viewModel.setWordSpacing((settings.wordSpacingMultiplier + 0.1f).coerceIn(0.5f, 3.0f)) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, contentDescription = "+", tint = textColor) }
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Line Spacing", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = { viewModel.setLineSpacing((settings.lineSpacingMultiplier - 0.1f).coerceIn(0.5f, 3.0f)) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Remove, contentDescription = "-", tint = textColor) }
                                    Text("${(settings.lineSpacingMultiplier * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = textColor, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                                    IconButton(onClick = { viewModel.setLineSpacing((settings.lineSpacingMultiplier + 0.1f).coerceIn(0.5f, 3.0f)) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, contentDescription = "+", tint = textColor) }
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.95f)
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(textColor.copy(alpha = 0.08f))
                            ) {
                                Row(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                                    val activeColor = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(if (!settings.isHorizontalScroll) activeColor.copy(alpha = 0.2f) else Color.Transparent).clickable { viewModel.setHorizontalScroll(false) }, contentAlignment = Alignment.Center) { 
                                        Icon(Icons.Default.SwapVert, contentDescription = "Vertical", tint = if (!settings.isHorizontalScroll) activeColor else textColor, modifier = Modifier.size(28.dp))
                                    }
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(if (settings.isHorizontalScroll) activeColor.copy(alpha = 0.2f) else Color.Transparent).clickable { viewModel.setHorizontalScroll(true) }, contentAlignment = Alignment.Center) { 
                                        Icon(Icons.Default.SwapHoriz, contentDescription = "Horizontal", tint = if (settings.isHorizontalScroll) activeColor else textColor, modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = { viewModel.setWarmFilterActive(!settings.isWarmFilterActive) }, modifier = Modifier.size(48.dp).border(width = if (settings.isWarmFilterActive) 2.dp else 0.dp, color = if (settings.isWarmFilterActive) Color(0xFFCC7722) else Color.Transparent, shape = RoundedCornerShape(8.dp)).padding(4.dp)) { Icon(Icons.Default.MenuBook, contentDescription = "Reading Mode", tint = if (settings.isWarmFilterActive) Color(0xFFCC7722) else textColor, modifier = Modifier.size(28.dp)) }
                            IconButton(onClick = { 
                                viewModel.setReaderModeActive(!settings.isReaderModeActive)
                                val activity = context as? android.app.Activity
                                val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(activity!!.window, activity.window.decorView)
                                if (!settings.isReaderModeActive) { windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()); windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE } 
                                else { windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
                            }, modifier = Modifier.size(48.dp)) { Icon(imageVector = if (settings.isReaderModeActive) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = "Immersive Mode", tint = if (settings.isReaderModeActive) MaterialTheme.colorScheme.primary else textColor, modifier = Modifier.size(if (settings.isReaderModeActive) 32.dp else 28.dp)) }
                            IconButton(onClick = { viewModel.setFontBold(!settings.fontBold) }, modifier = Modifier.size(48.dp)) { Text("B", fontWeight = if (settings.fontBold) FontWeight.Black else FontWeight.Normal, color = if (settings.fontBold) MaterialTheme.colorScheme.primary else textColor, fontSize = 24.sp) }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val scriptType = remember(file.title) {
                        val text = file.title
                        val devanagariRegex = Regex("[\\u0900-\\u097F]")
                        val latinRegex = Regex("[a-zA-Z]")
                        val hasDevanagari = devanagariRegex.containsMatchIn(text)
                        val hasLatin = latinRegex.containsMatchIn(text)
                        when {
                            hasDevanagari && hasLatin -> "MIXED"
                            hasDevanagari -> "DEVANAGARI"
                            else -> "LATIN"
                        }
                    }

                    val latinFonts = listOf(
                        "SansSerif" to FontFamily.SansSerif,
                        "Google Sans" to FontFamily(androidx.compose.ui.text.font.Font("fonts/google_sans.ttf", context.assets)),
                        "Literata" to FontFamily(androidx.compose.ui.text.font.Font("fonts/literata.ttf", context.assets)),
                        "Serif" to FontFamily.Serif,
                        "Monospace" to FontFamily.Monospace
                    )
                    
                    val devanagariFonts = listOf(
                        "Original" to FontFamily.Default,
                        "Amita" to FontFamily(androidx.compose.ui.text.font.Font("fonts/amita.ttf", context.assets)),
                        "Hind" to FontFamily(androidx.compose.ui.text.font.Font("fonts/hind.ttf", context.assets)),
                        "Yatra One" to FontFamily(androidx.compose.ui.text.font.Font("fonts/yatra_one.ttf", context.assets))
                    )

                    @Composable
                    fun FontRowTxt(fonts: List<Pair<String, FontFamily>>) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.Top
                        ) {
                            fonts.forEach { (name, font) ->
                                val isSelected = settings.fontFamily == name
                                Column(
                                    modifier = Modifier.weight(1f, fill = false).width(64.dp).padding(horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        onClick = { viewModel.setFontFamily(name) },
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF5C5E8F) else MaterialTheme.colorScheme.primary) else Color.Transparent,
                                        border = BorderStroke(1.dp, if (isSelected) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF5C5E8F) else MaterialTheme.colorScheme.primary) else textColor.copy(alpha = 0.3f))
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Text(
                                                text = "Aa", 
                                                fontFamily = font, 
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, 
                                                color = if (isSelected) Color.White else textColor, 
                                                fontSize = 24.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = name.replace(" ", ""), 
                                        fontFamily = FontFamily.Default, 
                                        color = if (isSelected) textColor else textColor.copy(alpha = 0.7f), 
                                        fontSize = 11.sp, 
                                        maxLines = 1,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    if (scriptType == "DEVANAGARI") {
                        FontRowTxt(devanagariFonts)
                    } else if (scriptType == "MIXED") {
                        FontRowTxt(latinFonts)
                        Spacer(modifier = Modifier.height(12.dp))
                        FontRowTxt(devanagariFonts)
                    } else {
                        FontRowTxt(latinFonts)
                    }
                    
                } else if (formatGroup == "PDF" || formatGroup == "CBZ_CBR_CB7") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { 
                            showSettingsSheet = false
                            viewModel.triggerPageComment()
                        }) { Icon(Icons.Default.Comment, contentDescription = "Comment", tint = textColor) }
                        IconButton(onClick = { 
                            showSettingsSheet = false
                            viewModel.setShowAnnotationManager(true) 
                        }) { Icon(Icons.Default.Search, contentDescription = "Search Annotations", tint = textColor) }
                    }

                    androidx.compose.material3.Divider(color = textColor.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(width = 48.dp, height = 32.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF9F5EB)).border(1.dp, if(settings.contrastMode == ContrastMode.Normal) Color.Blue else Color.Transparent, RoundedCornerShape(12.dp)).clickable { viewModel.setContrastMode(ContrastMode.Normal) })
                        Box(modifier = Modifier.size(width = 48.dp, height = 32.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C2C)).border(1.dp, if(settings.contrastMode == ContrastMode.Dark) Color.Blue else Color.Transparent, RoundedCornerShape(12.dp)).clickable { viewModel.setContrastMode(ContrastMode.Dark) })
                        Box(modifier = Modifier.size(width = 48.dp, height = 32.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFFFFF)).border(1.dp, if(settings.contrastMode == ContrastMode.HighContrastLight) Color.Blue else Color.Gray, RoundedCornerShape(12.dp)).clickable { viewModel.setContrastMode(ContrastMode.HighContrastLight) })
                        Box(modifier = Modifier.size(width = 48.dp, height = 32.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF000000)).border(1.dp, if(settings.contrastMode == ContrastMode.HighContrastDark) Color.Blue else Color.Gray, RoundedCornerShape(12.dp)).clickable { viewModel.setContrastMode(ContrastMode.HighContrastDark) })
                        Box(modifier = Modifier.size(width = 48.dp, height = 32.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, if(settings.contrastMode == ContrastMode.EInk) Color.Blue else Color.Gray, RoundedCornerShape(12.dp)).clickable { viewModel.setContrastMode(ContrastMode.EInk) }) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.fillMaxHeight().weight(1f).background(Color(0xFFE0E0E0)))
                                Box(modifier = Modifier.fillMaxHeight().weight(1f).background(Color(0xFFFFFFFF)))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val primaryColor = MaterialTheme.colorScheme.primary
                        IconButton(onClick = { viewModel.setNegative(!settings.isNegative) }, modifier = Modifier.size(48.dp)) {
                            Canvas(modifier = Modifier.size(32.dp)) {
                                drawRect(color = if (settings.isNegative) primaryColor else textColor)
                                drawCircle(color = barColor, radius = size.minDimension * 0.3f)
                                drawRect(color = barColor.copy(alpha = 0.15f), size = size / 2f)
                            }
                        }
                        IconButton(onClick = { 
                            viewModel.setReaderModeActive(!settings.isReaderModeActive)
                            val activity = context as? android.app.Activity
                            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(activity!!.window, activity.window.decorView)
                            if (!settings.isReaderModeActive) { windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()); windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE } 
                            else { windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
                        }, modifier = Modifier.size(48.dp)) { Icon(imageVector = if (settings.isReaderModeActive) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = "Immersive Mode", tint = if (settings.isReaderModeActive) MaterialTheme.colorScheme.primary else textColor, modifier = Modifier.size(if (settings.isReaderModeActive) 32.dp else 28.dp)) }
                        IconButton(onClick = { viewModel.setWarmFilterActive(!settings.isWarmFilterActive) }, modifier = Modifier.size(48.dp).border(width = if (settings.isWarmFilterActive) 2.dp else 0.dp, color = if (settings.isWarmFilterActive) Color(0xFFCC7722) else Color.Transparent, shape = RoundedCornerShape(8.dp)).padding(4.dp)) { Icon(Icons.Default.MenuBook, contentDescription = "Reading Mode", tint = if (settings.isWarmFilterActive) Color(0xFFCC7722) else textColor, modifier = Modifier.size(28.dp)) }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(textColor.copy(alpha = 0.08f))
                    ) {
                        Row(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                            val activeColor = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(if (!settings.isHorizontalScroll) activeColor.copy(alpha = 0.2f) else Color.Transparent).clickable { viewModel.setHorizontalScroll(false) }, contentAlignment = Alignment.Center) { 
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.SwapVert, contentDescription = "Vertical", tint = if (!settings.isHorizontalScroll) activeColor else textColor, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Vertical", color = if (!settings.isHorizontalScroll) activeColor else textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(if (settings.isHorizontalScroll) activeColor.copy(alpha = 0.2f) else Color.Transparent).clickable { viewModel.setHorizontalScroll(true) }, contentAlignment = Alignment.Center) { 
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = "Horizontal", tint = if (settings.isHorizontalScroll) activeColor else textColor, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Horizontal", color = if (settings.isHorizontalScroll) activeColor else textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val showAnnotationManager by viewModel.showAnnotationManager.collectAsState()
    val allAnns by remember(fileId) { viewModel.getAnnotationsForFile(fileId) }.collectAsState(initial = emptyList<com.infer.inferead.data.Annotation>())
    if (showAnnotationManager && currentFile != null) {
        AnnotationManagerDialog(
            file = currentFile!!,
            annotations = allAnns,
            viewModel = viewModel,
            onNavigate = { ann ->
                viewModel.setShowAnnotationManager(false)
                if (currentFile!!.format == "EPUB") {
                    val chapterStr = ann.cfiRange.substringBefore("|")
                    val chapterNum = chapterStr.toIntOrNull()
                    if (chapterNum != null) {
                        viewModel.updateCurrentPage(chapterNum)
                    }
                    targetScrollAnnId = ann.id
                } else {
                    val pageStr = ann.cfiRange.substringBefore("|")
                    val pageNum = pageStr.toIntOrNull()
                    if (pageNum != null) {
                        viewModel.updateCurrentPage(pageNum)
                    }
                    targetScrollAnnId = ann.id
                }
            },
            onDismiss = { viewModel.setShowAnnotationManager(false) }
        )
    }

    if (showPageAnnotationManager && currentFile != null) {
        val pageAnns = allAnns.filter { ann ->
            val index = currentFile!!.currentPage
            ann.cfiRange.startsWith("${index}|") || ann.cfiRange == "${index}|PAGE"
        }
        AnnotationManagerDialog(
            file = currentFile!!,
            annotations = pageAnns,
            viewModel = viewModel,
            onNavigate = { ann ->
                showPageAnnotationManager = false
                targetScrollAnnId = ann.id
            },
            onDismiss = { showPageAnnotationManager = false }
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
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
            title = { Text("Create Checklist") },
            text = {
                OutlinedTextField(
                    modifier = Modifier.focusRequester(focusRequester).fillMaxWidth(),
                    value = checklistName,
                    onValueChange = { checklistName = it },
                    placeholder = { Text("Checklist Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (checklistName.isNotBlank()) {
                            homeViewModel.createChecklist(checklistName)
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

}

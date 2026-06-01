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

    LaunchedEffect(settings.isReaderModeActive) {
        if (settings.isReaderModeActive) {
            showScrubber = false
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(220.dp)
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Checklists",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
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
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    NavigationDrawerItem(
                        label = { Text("My Library", fontWeight = FontWeight.Medium) },
                        selected = false,
                        onClick = { 
                            scope.launch { drawerState.close() }
                            onNavigateBack()
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).height(40.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color.Transparent,
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        item {
                            Text(
                                "Checklists",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
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
                    
                    Spacer(Modifier.height(16.dp))
                }
            }
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
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top
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
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = currentFile?.title ?: "Loading...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor,
                                maxLines = if (isTitleExpanded) Int.MAX_VALUE else 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        isTitleExpanded = !isTitleExpanded
                                    }
                            )
                            currentFile?.format?.let { format ->
                                Surface(
                                    color = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    contentColor = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(
                                        text = format,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { viewModel.toggleBookmark() },
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            val isCurrentPageBookmarked = bookmarkedPages.contains(currentFile?.currentPage ?: -1)
                            val dotColor = if (isCurrentPageBookmarked) Color(0xFFFFC107)
                                else (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary).copy(alpha = 0.5f)
                            Canvas(
                                modifier = Modifier.size(24.dp)
                            ) {
                                val r = size.minDimension / 2f
                                if (isCurrentPageBookmarked) {
                                    drawCircle(color = dotColor, radius = r)
                                } else {
                                    drawCircle(color = dotColor, radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
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
                    val allAnns by viewModel.getAnnotationsForFile(fileId).collectAsState(initial = emptyList<com.infer.inferead.data.Annotation>())
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
                                cfiRange = "${file.currentPage}|0,0",
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
                            currentPage = file.currentPage,
                            onPageChanged = { page -> viewModel.updateCurrentPage(page) },
                            onTotalPages = { total -> viewModel.updateTotalPages(total) },
                            onTap = toggleReaderMode
                        )
                        "TXT" -> TextViewer(
                            filePath = file.filePath,
                            settings = settings,
                            isReaderModeActive = settings.isReaderModeActive,
                            currentPage = file.currentPage,
                            scrollState = textScrollState,
                            onPageChanged = { page -> viewModel.updateCurrentPage(page) },
                            onTotalPages = { total -> viewModel.updateTotalPages(total) },
                            onTap = toggleReaderMode,
                            activeHighlightColor = activeHighlightMode,
                            onTextSelected = { text, bounds ->
                                if (text == "DELETE") {
                                    val annId = bounds.toIntOrNull() ?: return@TextViewer
                                    viewModel.deleteAnnotation(com.infer.inferead.data.Annotation(id = annId, fileId = 0, cfiRange = "", colorHex = "")) // only ID matters
                                } else {
                                    if (!activeHighlightMode.isNullOrEmpty()) {
                                        viewModel.insertAnnotation(
                                            com.infer.inferead.data.Annotation(
                                                fileId = file.id,
                                                selectedText = text,
                                                cfiRange = bounds,
                                                colorHex = activeHighlightMode ?: "#c25d5d",
                                                timestamp = System.currentTimeMillis()
                                            )
                                        )
                                        viewModel.setActiveHighlightMode(null)
                                    }
                                }
                            },
                            annotations = pageAnns
                        )
                        "IMAGE" -> ImageViewer(file.filePath, isNoir = settings.isNoir, isNegative = settings.isNegative, onTap = toggleReaderMode)
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
                            annotations = pageAnns
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
                    if (textSelectionData != null && file.format == "EPUB") {
                        val sel = textSelectionData!!
                        val density = LocalDensity.current.density
                        // Determine whether to show above or below based on position
                        val screenHeightPx = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * density
                        val isTopHalf = sel.top < screenHeightPx / 2
                        val menuY = if (isTopHalf) (sel.bottom / density).dp + 10.dp else (sel.top / density).dp - 60.dp
                        
                        Box(modifier = Modifier.fillMaxSize()) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = menuY)
                                    .shadow(8.dp, RoundedCornerShape(8.dp)),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (showHighlightColorsForSelection) {
                                        val colors = listOf("#FFFF00", "#00FF00", "#00FFFF", "#FF00FF", "#c25d5d")
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
                                        if (sel.annId != null) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clickable {
                                                        viewModel.deleteAnnotation(
                                                            com.infer.inferead.data.Annotation(id = sel.annId!!, fileId = 0, cfiRange = "", colorHex = "")
                                                        )
                                                        showHighlightColorsForSelection = false
                                                        textSelectionData = null
                                                    }
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.Gray.copy(alpha = 0.5f)))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(onClick = { showHighlightColorsForSelection = false }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close Colors")
                                        }
                                    } else {
                                        TextButton(onClick = { 
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("InfeRead Copied Text", sel.text)
                                            clipboard.setPrimaryClip(clip)
                                            textSelectionData = null
                                        }) {
                                            Text("Copy", color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.Gray.copy(alpha = 0.5f)))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        
                                        TextButton(onClick = { showHighlightColorsForSelection = true }) {
                                            Text("Highlight", color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.Gray.copy(alpha = 0.5f)))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        
                                        TextButton(onClick = { 
                                            commentingSelectionData = sel
                                            showCommentDialogForSelection = true
                                            textSelectionData = null
                                        }) {
                                            Text("Comment", color = MaterialTheme.colorScheme.onSurface)
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
                            title = { Text("Highlight Options") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "\"${editingHighlight!!.selectedText.trim()}\"",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val colors = listOf("#FFFF00", "#00FF00", "#00FFFF", "#FF00FF", "#c25d5d")
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
                            title = { Text("Edit Annotation") },
                            text = {
                                Column {
                                    Text("Selected: \"${editingAnnotation!!.selectedText.take(50)}${if (editingAnnotation!!.selectedText.length > 50) "..." else ""}\"", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = commentText,
                                        onValueChange = { if (it.length <= 500) commentText = it },
                                        modifier = Modifier.fillMaxWidth().height(120.dp),
                                        placeholder = { Text("Enter your comment (max 500 chars)") },
                                        maxLines = 5
                                    )
                                    Text("${commentText.length}/500", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
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
                            title = { Text("Add Comment") },
                            text = {
                                Column {
                                    Text("Selected: \"${sel.text.take(50)}${if (sel.text.length > 50) "..." else ""}\"", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = commentText,
                                        onValueChange = { if (it.length <= 500) commentText = it },
                                        modifier = Modifier.fillMaxWidth().height(120.dp),
                                        placeholder = { Text("Enter your comment (max 500 chars)") },
                                        maxLines = 5
                                    )
                                    Text("${commentText.length}/500", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
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
                // Bottom left scrubber button (not shown for IMAGE files)
                AnimatedVisibility(
                    visible = !settings.isReaderModeActive && currentFile?.format != "IMAGE",
                    enter = if (settings.contrastMode == ContrastMode.EInk) EnterTransition.None else fadeIn(),
                    exit = if (settings.contrastMode == ContrastMode.EInk) ExitTransition.None else fadeOut(),
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
                    enter = if (settings.contrastMode == ContrastMode.EInk) EnterTransition.None else fadeIn(),
                    exit = if (settings.contrastMode == ContrastMode.EInk) ExitTransition.None else fadeOut(),
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
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut(),
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

            // PDF Page Scrubber Overlay Dialog
            if (showScrubber && currentFile != null && (currentFile!!.format == "PDF" || currentFile!!.format == "CBZ" || currentFile!!.format == "EPUB") && currentFile!!.totalPages > 0) {
                var scrubbingPage by remember(showScrubber, currentFile?.currentPage) {
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
                                thumbColor = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary,
                                activeTrackColor = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = textColor.copy(alpha = 0.24f)
                            ),
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

            // TXT Page Scrubber Overlay (Slider only) when in horizontal scroll
            if (showScrubber && currentFile != null && currentFile!!.format == "TXT" && settings.isHorizontalScroll && currentFile!!.totalPages > 0) {
                var scrubbingPage by remember(showScrubber, currentFile?.currentPage) {
                    mutableFloatStateOf(currentFile?.currentPage?.toFloat() ?: 1f)
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

                    Slider(
                        value = scrubbingPage,
                        onValueChange = { pageVal ->
                            scrubbingPage = pageVal
                        },
                        onValueChangeFinished = {
                            viewModel.updateCurrentPage(scrubbingPage.roundToInt())
                        },
                        valueRange = 1f..currentFile!!.totalPages.toFloat(),
                        steps = if (currentFile!!.totalPages > 2) currentFile!!.totalPages - 2 else 0,
                        colors = SliderDefaults.colors(
                            thumbColor = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary,
                            activeTrackColor = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = textColor.copy(alpha = 0.24f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .height(32.dp)
                    )
                }
            }

            // TXT Scroll Minimap Overlay
            if (showScrubber && currentFile != null && currentFile!!.format == "TXT" && !settings.isHorizontalScroll) {
                val scope = rememberCoroutineScope()
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .width(60.dp)
                        .height(300.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(barColor.copy(alpha = 0.4f))
                        .border(
                            width = 1.dp,
                            color = textColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .pointerInput(textScrollState.maxValue) {
                            detectTapGestures { offset ->
                                val ratio = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                scope.launch {
                                    textScrollState.scrollTo((ratio * textScrollState.maxValue).roundToInt())
                                }
                            }
                        }
                        .pointerInput(textScrollState.maxValue) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val ratio = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                scope.launch {
                                    textScrollState.scrollTo((ratio * textScrollState.maxValue).roundToInt())
                                }
                            }
                        }
                ) {
                    val totalHeight = 300.dp
                    val viewportHeight = 60.dp
                    val maxOffset = totalHeight - viewportHeight
                    val progress = if (textScrollState.maxValue > 0) {
                        textScrollState.value.toFloat() / textScrollState.maxValue
                    } else {
                        0f
                    }
                    val offsetVal = (progress * maxOffset.value).dp

                    // Draw simulated text lines
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val lineSpacing = 6f
                        val lineHeight = 3f
                        var y = 10f
                        val random = java.util.Random(42)
                        while (y < canvasHeight - 10f) {
                            val wordCount = random.nextInt(3) + 2
                            var x = 10f
                            for (i in 0 until wordCount) {
                                val wordWidth = random.nextFloat() * (canvasWidth / 4f) + 8f
                                if (x + wordWidth > canvasWidth - 8f) break
                                drawRoundRect(
                                    color = textColor.copy(alpha = 0.15f),
                                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                                    size = androidx.compose.ui.geometry.Size(wordWidth, lineHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f)
                                )
                                x += wordWidth + 3f
                            }
                            y += lineHeight + lineSpacing
                        }
                    }

                    // Rectangular viewport frame
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(viewportHeight)
                            .offset(y = offsetVal)
                            .border(
                                width = 2.dp,
                                color = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .background(textColor.copy(alpha = 0.08f))
                    )
                }
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
                        }) { Icon(Icons.Default.Search, contentDescription = "Search Annotations", tint = textColor) }
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        val fonts = listOf(
                            "SansSerif" to FontFamily.SansSerif,
                            "Google Sans" to FontFamily(androidx.compose.ui.text.font.Font("fonts/google_sans.ttf", context.assets)),
                            "Literata" to FontFamily(androidx.compose.ui.text.font.Font("fonts/literata.ttf", context.assets)),
                            "Serif" to FontFamily.Serif,
                            "Monospace" to FontFamily.Monospace
                        )
                        fonts.forEach { (name, font) ->
                            val isSelected = settings.fontFamily == name
                            Column(
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
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

                } else if (formatGroup == "TXT_DOC_DOCX") {
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { 
                            viewModel.setActiveHighlightMode("#c25d5d")
                            showSettingsSheet = false
                        }) { Icon(Icons.Default.Highlight, contentDescription = "Highlight", tint = textColor) }
                        IconButton(onClick = { 
                            showSettingsSheet = false
                            viewModel.setShowAnnotationManager(true) 
                        }) { Icon(Icons.Default.Search, contentDescription = "Search Annotations", tint = textColor) }
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        val fonts = listOf(
                            "SansSerif" to FontFamily.SansSerif,
                            "Serif" to FontFamily.Serif,
                            "Monospace" to FontFamily.Monospace,
                            "Google Sans" to FontFamily(androidx.compose.ui.text.font.Font("fonts/google_sans.ttf", context.assets)),
                            "Literata" to FontFamily(androidx.compose.ui.text.font.Font("fonts/literata.ttf", context.assets))
                        )
                        fonts.forEach { (name, font) ->
                            val isSelected = settings.fontFamily == name
                            Surface(
                                onClick = { viewModel.setFontFamily(name) },
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary).copy(alpha = 0.15f) else Color.Transparent,
                                border = BorderStroke(1.dp, if (isSelected) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else textColor.copy(alpha = 0.2f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = "Aa", fontFamily = font, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else textColor, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = name.replace(" ", ""), fontFamily = FontFamily.Default, color = if (isSelected) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else textColor, fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Scrolling Style", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, textColor.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)).background(if (!settings.isHorizontalScroll) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).clickable { viewModel.setHorizontalScroll(false) },
                                    contentAlignment = Alignment.Center
                                ) { Text("Vertical", color = if (!settings.isHorizontalScroll) MaterialTheme.colorScheme.onPrimaryContainer else textColor, style = MaterialTheme.typography.labelMedium, fontWeight = if (!settings.isHorizontalScroll) FontWeight.Bold else FontWeight.Normal) }
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)).background(if (settings.isHorizontalScroll) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).clickable { viewModel.setHorizontalScroll(true) },
                                    contentAlignment = Alignment.Center
                                ) { Text("Horizontal", color = if (settings.isHorizontalScroll) MaterialTheme.colorScheme.onPrimaryContainer else textColor, style = MaterialTheme.typography.labelMedium, fontWeight = if (settings.isHorizontalScroll) FontWeight.Bold else FontWeight.Normal) }
                            }
                        }
                    }
                    
                } else if (formatGroup == "PDF") {
                    
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            IconButton(
                                onClick = { viewModel.setReaderModeActive(!settings.isReaderModeActive) },
                                modifier = Modifier.border(width = if (settings.isReaderModeActive) 2.dp else 0.dp, color = if (settings.isReaderModeActive) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else Color.Transparent, shape = RoundedCornerShape(8.dp)).padding(4.dp)
                            ) { Icon(imageVector = Icons.Default.Fullscreen, contentDescription = "Immersive", tint = if (settings.isReaderModeActive) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else textColor) }
                            IconButton(
                                onClick = { viewModel.setWarmFilterActive(!settings.isWarmFilterActive) },
                                modifier = Modifier.border(width = if (settings.isWarmFilterActive) 2.dp else 0.dp, color = if (settings.isWarmFilterActive) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else Color.Transparent, shape = RoundedCornerShape(8.dp)).padding(4.dp)
                            ) { Icon(imageVector = Icons.Default.WbSunny, contentDescription = "Warm Filter", tint = if (settings.isWarmFilterActive) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else textColor) }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Scrolling Styles", style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.7f), modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp).background(textColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp).background(if (!settings.isHorizontalScroll) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary).copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp)).clickable { viewModel.setHorizontalScroll(false) }, contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(imageVector = Icons.Default.SwapVert, contentDescription = "Vertical", tint = if (!settings.isHorizontalScroll) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else textColor); Text("Vertical", color = if (!settings.isHorizontalScroll) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp).background(if (settings.isHorizontalScroll) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary).copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp)).clickable { viewModel.setHorizontalScroll(true) }, contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = "Horizontal", tint = if (settings.isHorizontalScroll) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else textColor); Text("Horizontal", color = if (settings.isHorizontalScroll) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }

                } else if (formatGroup == "CBZ_CBR_CB7") {
                    
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
                        }) { Icon(Icons.Default.Search, contentDescription = "Search Comments", tint = textColor) }
                    }
                    
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            IconButton(
                                onClick = { viewModel.setReaderModeActive(!settings.isReaderModeActive) },
                                modifier = Modifier.border(width = if (settings.isReaderModeActive) 2.dp else 0.dp, color = if (settings.isReaderModeActive) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else Color.Transparent, shape = RoundedCornerShape(8.dp)).padding(4.dp)
                            ) { Icon(imageVector = Icons.Default.Fullscreen, contentDescription = "Immersive", tint = if (settings.isReaderModeActive) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else textColor) }
                            IconButton(
                                onClick = { viewModel.setWarmFilterActive(!settings.isWarmFilterActive) },
                                modifier = Modifier.border(width = if (settings.isWarmFilterActive) 2.dp else 0.dp, color = if (settings.isWarmFilterActive) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else Color.Transparent, shape = RoundedCornerShape(8.dp)).padding(4.dp)
                            ) { Icon(imageVector = Icons.Default.WbSunny, contentDescription = "Warm Filter", tint = if (settings.isWarmFilterActive) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else textColor) }
                            IconButton(
                                onClick = { viewModel.setNegative(!settings.isNegative) },
                                modifier = Modifier.border(width = if (settings.isNegative) 2.dp else 0.dp, color = if (settings.isNegative) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else Color.Transparent, shape = RoundedCornerShape(8.dp)).padding(4.dp)
                            ) { Icon(imageVector = Icons.Default.NightsStay, contentDescription = "Negative Filter", tint = if (settings.isNegative) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else textColor) }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Scrolling Styles", style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.7f), modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp).background(textColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp).background(if (!settings.isHorizontalScroll) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary).copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp)).clickable { viewModel.setHorizontalScroll(false) }, contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(imageVector = Icons.Default.SwapVert, contentDescription = "Vertical", tint = if (!settings.isHorizontalScroll) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else textColor); Text("Vertical", color = if (!settings.isHorizontalScroll) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp).background(if (settings.isHorizontalScroll) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary).copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp)).clickable { viewModel.setHorizontalScroll(true) }, contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = "Horizontal", tint = if (settings.isHorizontalScroll) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else textColor); Text("Horizontal", color = if (settings.isHorizontalScroll) (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary) else textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
    }

    val showAnnotationManager by viewModel.showAnnotationManager.collectAsState()
    val allAnns by viewModel.getAnnotationsForFile(fileId).collectAsState(initial = emptyList<com.infer.inferead.data.Annotation>())
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

}
}

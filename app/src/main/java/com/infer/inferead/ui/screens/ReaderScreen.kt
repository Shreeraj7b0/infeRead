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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.withStyle
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.zIndex
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

fun splitTextIntoChunks(text: String, maxLength: Int = 2000): List<String> {
    val chunks = mutableListOf<String>()
    var currentIndex = 0
    while (currentIndex < text.length) {
        val endIndex = minOf(currentIndex + maxLength, text.length)
        var chunk = text.substring(currentIndex, endIndex)
        if (endIndex < text.length) {
            val lastSpace = chunk.lastIndexOf(' ')
            if (lastSpace > maxLength * 0.8) {
                chunk = text.substring(currentIndex, currentIndex + lastSpace)
                currentIndex += lastSpace + 1
            } else {
                currentIndex = endIndex
            }
        } else {
            currentIndex = endIndex
        }
        if (chunk.trim().isNotEmpty()) {
            chunks.add(chunk)
        }
    }
    return chunks
}

fun detectLanguageOfText(text: String): String {
    val lowercase = text.lowercase()
    if (lowercase.any { it.code in 0x0900..0x097F }) {
        return "DEVANAGARI"
    }
    if (lowercase.any { it.code in 0x3040..0x30FF || it.code in 0x4E00..0x9FFF }) {
        if (lowercase.any { it.code in 0x3040..0x30FF }) return "JA"
        return "ZH"
    }
    
    val words = lowercase.split(Regex("\\s+")).take(100)
    var enCount = 0
    var esCount = 0
    var frCount = 0
    var deCount = 0
    var ptCount = 0
    var itCount = 0
    
    for (w in words) {
        when (w) {
            "the", "and", "of", "to", "is", "in", "that", "it" -> enCount++
            "el", "la", "de", "que", "en", "un", "y", "los" -> esCount++
            "le", "la", "de", "et", "un", "est", "en", "les" -> frCount++
            "der", "die", "das", "und", "ist", "in", "zu", "den" -> deCount++
            "o", "a", "de", "que", "e", "do", "da", "em", "para" -> ptCount++
            "il", "la", "di", "che", "e", "in", "un", "per", "con" -> itCount++
        }
    }
    
    val counts = mapOf("EN" to enCount, "ES" to esCount, "FR" to frCount, "DE" to deCount, "PT" to ptCount, "IT" to itCount)
    val best = counts.maxByOrNull { it.value }
    if (best != null && best.value > 0) {
        return best.key
    }
    return "EN"
}

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
    var showFileSearch by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
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
                    if (it.filePath.startsWith("content://")) {
                        try {
                            context.contentResolver.openFileDescriptor(android.net.Uri.parse(it.filePath), "r")?.use { pfd -> pfd.statSize } ?: 0L
                        } catch (e: Exception) { 0L }
                    } else {
                        java.io.File(it.filePath).length()
                    }
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
    
    val searchResults by viewModel.searchResults.collectAsState()
    val searchJumpIndex by viewModel.searchJumpIndex.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentSearchIdx by viewModel.currentSearchResultIndex.collectAsState()

    // State for Top/Bottom Bars visibility    
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showCustomiseTopBar by remember { mutableStateOf(false) }
    var showHiddenMenu by remember { mutableStateOf(false) }
    var showWordSpacingDialog by remember { mutableStateOf(false) }
    var showLineSpacingDialog by remember { mutableStateOf(false) }
    var showFontColorDialog by remember { mutableStateOf(false) }

    val window = (context as? android.app.Activity)?.window
    androidx.compose.runtime.LaunchedEffect(settings.readingBrightness) {
        window?.let {
            val lp = it.attributes
            lp.screenBrightness = settings.readingBrightness
            it.attributes = lp
        }
    }
    var chapterPreviews by remember { mutableStateOf<List<String>?>(null) }
    var targetScrollAnnId by remember { mutableStateOf<Int?>(null) }
    var showPageAnnotationManager by remember { mutableStateOf(false) }
    var showScrubber by remember { mutableStateOf(false) }
    var showBrowserPreview by remember { mutableStateOf(false) }
    var showRawMarkdown by remember(currentFile) { mutableStateOf(false) }
    LaunchedEffect(showRawMarkdown) {
        if (showRawMarkdown) {
            viewModel.setReaderModeActive(false)
        }
    }
    var showPageCommentsDialog by remember { mutableStateOf(false) }
    var isTitleExpanded by remember { mutableStateOf(false) }
    val textScrollState = rememberScrollState()
    val bookmarkedPages by viewModel.bookmarkedPages.collectAsState()

    var epubSpineFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var ttsInitialized by remember { mutableStateOf(false) }
    var isTtsActive by remember { mutableStateOf(false) }
    var ttsVolume by remember { mutableStateOf(1f) }
    var ttsSpeed by remember { mutableStateOf(1f) }
    var selectedLocale by remember { mutableStateOf(java.util.Locale.US) }
    var selectedGender by remember { mutableStateOf("Female") }
    var showTtsSubPage by remember { mutableStateOf(false) }
    var latestTotalChunks by remember { mutableStateOf(0) }
    // Thread-safe chunk tracker - readable from TTS background callbacks
    val currentPlayingChunkRef = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    // State version to force re-read of voice settings without resetting chunk position
    var ttsVoiceSettingsVersion by remember { mutableStateOf(0) }
    // The parsed text chunks for the current chapter - persisted so voice changes don't re-read file
    var currentChapterChunks by remember { mutableStateOf<List<String>>(emptyList()) }

    val prefs = remember { context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE) }
    var isOfflineMode by remember { mutableStateOf(prefs.getBoolean("is_offline_mode", false)) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == "is_offline_mode") {
                isOfflineMode = sp.getBoolean("is_offline_mode", false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    DisposableEffect(context) {
        var instance: android.speech.tts.TextToSpeech? = null
        try {
            instance = android.speech.tts.TextToSpeech(context, { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    ttsInitialized = true
                }
            }, "com.google.android.tts")
        } catch (e: Exception) {
            instance = android.speech.tts.TextToSpeech(context) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    ttsInitialized = true
                }
            }
        }
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        try { instance.setAudioAttributes(audioAttributes) } catch (e: Exception) {}
        tts = instance
        onDispose {
            instance.stop()
            instance.shutdown()
        }
    }

    // Helper: apply current voice settings to TTS engine
    fun applyVoiceSettings(engine: android.speech.tts.TextToSpeech, locale: java.util.Locale, gender: String, speed: Float) {
        try { engine.setLanguage(locale) } catch (e: Exception) {}
        try { engine.setSpeechRate(speed) } catch (e: Exception) {}
        val voices = try {
            engine.voices
                ?.filter {
                    it.locale.language == locale.language &&
                    (locale.country.isEmpty() || it.locale.country == locale.country)
                }
                ?.sortedBy { it.name }
                ?: emptyList()
        } catch (e: Exception) { emptyList<android.speech.tts.Voice>() }
        if (voices.isEmpty()) return

        // Extract Google TTS variant code: "en-us-x-iof-local" → "iof"
        fun variantCode(v: android.speech.tts.Voice): String {
            val name = v.name.lowercase()
            return Regex("""-x-([a-z0-9]+)""").find(name)?.groupValues?.get(1) ?: name
        }

        // Gender signal from the variant code's last (or second-to-last) char
        // Google naming: en-us-x-iof → 'f' = Female, en-us-x-iom → 'm' = Male
        // en-in-x-enf → 'f' = Female, en-gb-x-gbg/gbf → 'f' = Female, else ambiguous
        fun genderFromCode(code: String): String? {
            val name = code.lowercase()
            if (name.contains("female")) return "Female"
            if (name.contains("male") && !name.contains("female")) return "Male"
            val last = name.lastOrNull()
            if (last == 'f') return "Female"
            if (last == 'm') return "Male"
            if (name.length >= 2) {
                val sl = name[name.length - 2]
                if (sl == 'f') return "Female"
                if (sl == 'm') return "Male"
            }
            return null
        }

        // Group voices by unique variant code — eliminates -local/-network duplicates
        val byVariant = voices.groupBy { variantCode(it) }
        // Within each variant group, prefer local (non-network) voice
        fun bestVoiceInGroup(group: List<android.speech.tts.Voice>) =
            group.minByOrNull { if (it.name.contains("network")) 1 else 0 }

        // Sort unique variant codes alphabetically for deterministic assignment
        val sortedCodes = byVariant.keys.sorted()

        // Map each variant code to a gender
        val femaleCodes = sortedCodes.filter { genderFromCode(it) == "Female" }
        val maleCodes   = sortedCodes.filter { genderFromCode(it) == "Male" }
        val ambigCodes  = sortedCodes.filter { genderFromCode(it) == null }

        // For ambiguous codes: first half → Female, rest → Male (deterministic split)
        val ambigFemale = ambigCodes.take((ambigCodes.size + 1) / 2)
        val ambigMale   = ambigCodes.drop((ambigCodes.size + 1) / 2)

        val allFemaleCodes = femaleCodes + ambigFemale
        val allMaleCodes   = maleCodes   + ambigMale

        val targetCodes = if (gender == "Female") allFemaleCodes else allMaleCodes
        val fallbackCodes = if (gender == "Female") allMaleCodes else allFemaleCodes

        val voice = targetCodes.firstNotNullOfOrNull { code -> bestVoiceInGroup(byVariant[code] ?: emptyList()) }
            ?: fallbackCodes.firstNotNullOfOrNull { code -> bestVoiceInGroup(byVariant[code] ?: emptyList()) }
            ?: voices.firstOrNull()

        if (voice != null) try { engine.voice = voice } catch (e: Exception) {}
    }

    // Helper: queue chunks starting from a given index
    fun queueChunksFrom(engine: android.speech.tts.TextToSpeech, chunks: List<String>, startIdx: Int, volume: Float) {
        val safeStart = startIdx.coerceIn(0, maxOf(0, chunks.size - 1))
        chunks.drop(safeStart).forEachIndexed { dropIdx, chunk ->
            val idx = safeStart + dropIdx
            val queueMode = if (idx == safeStart) android.speech.tts.TextToSpeech.QUEUE_FLUSH else android.speech.tts.TextToSpeech.QUEUE_ADD
            val utteranceId = "epub_tts_${idx}"
            val params = android.os.Bundle().apply {
                putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
                putString(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            engine.speak(chunk, queueMode, params, utteranceId)
        }
    }

    // Register utterance progress listener once
    LaunchedEffect(tts, ttsInitialized) {
        if (ttsInitialized && tts != null) {
            tts!!.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId != null && utteranceId.startsWith("epub_tts_")) {
                        val chunkIdx = utteranceId.removePrefix("epub_tts_").toIntOrNull()
                        if (chunkIdx != null) {
                            currentPlayingChunkRef.set(chunkIdx)
                        }
                    }
                }
                override fun onDone(utteranceId: String?) {
                    if (utteranceId != null && utteranceId.startsWith("epub_tts_")) {
                        val chunkIdx = utteranceId.removePrefix("epub_tts_").toIntOrNull()
                        if (chunkIdx != null) {
                            scope.launch {
                                if (isTtsActive && currentFile != null && chunkIdx == latestTotalChunks - 1) {
                                    if (currentFile!!.currentPage < currentFile!!.totalPages) {
                                        viewModel.updateCurrentPage(currentFile!!.currentPage + 1)
                                    } else {
                                        isTtsActive = false
                                    }
                                }
                            }
                        }
                    }
                }
                override fun onError(utteranceId: String?) {}
            })
        }
    }

    // PAGE CHANGE: re-parse text, reset chunk index, and start from beginning
    LaunchedEffect(currentFile?.currentPage, isTtsActive, epubSpineFiles) {
        if (isTtsActive && currentFile != null && currentFile?.format == "EPUB") {
            val path = epubSpineFiles.getOrNull(currentFile!!.currentPage - 1)
            if (path != null) {
                try {
                    val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        org.jsoup.Jsoup.parse(java.io.File(path), "UTF-8").text()
                    }
                    val chunks = splitTextIntoChunks(text)
                    currentChapterChunks = chunks
                    latestTotalChunks = chunks.size
                    currentPlayingChunkRef.set(0)
                    if (ttsInitialized && tts != null) {
                        applyVoiceSettings(tts!!, selectedLocale, selectedGender, ttsSpeed)
                        queueChunksFrom(tts!!, chunks, 0, ttsVolume)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    // VOICE SETTINGS CHANGE: stop, apply new voice, resume from current chunk (no re-parse)
    LaunchedEffect(ttsVoiceSettingsVersion) {
        if (ttsVoiceSettingsVersion > 0 && isTtsActive && ttsInitialized && tts != null && currentChapterChunks.isNotEmpty()) {
            val resumeFrom = currentPlayingChunkRef.get()
            tts!!.stop()
            applyVoiceSettings(tts!!, selectedLocale, selectedGender, ttsSpeed)
            queueChunksFrom(tts!!, currentChapterChunks, resumeFrom, ttsVolume)
        }
    }

    // STOP when TTS toggled off
    LaunchedEffect(isTtsActive) {
        if (!isTtsActive) {
            tts?.stop()
            currentPlayingChunkRef.set(0)
            currentChapterChunks = emptyList()
        }
    }
    
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
                    com.infer.inferead.ui.components.SoundtrackPlayerBar(
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
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
                            val displayFormat = if ((currentFile?.filePath ?: "").startsWith("content://")) {
                                currentFile?.format
                            } else {
                                java.io.File(currentFile?.filePath ?: "").extension.uppercase().takeIf { it.isNotEmpty() } ?: currentFile?.format
                            }
                            displayFormat?.let { format ->
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
                            val shouldShowBookmark = currentFile?.format != "IMAGE" && (currentFile?.format !in listOf("TXT", "CODING") || settings.isHorizontalScroll)
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
                            Icon(Icons.Default.Create, contentDescription = "Annotation Manager")
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
                            onScrollProgress = { p -> verticalScrollProgress = p },
                            searchResults = searchResults,
                            searchJumpIndex = searchJumpIndex
                        )
                        "TXT", "CODING" -> TXTReader(
                            filePath = file.filePath,
                            format = file.format,
                            settings = settings,
                            chapterIndex = file.currentPage,
                            onPageChanged = { page -> viewModel.updateCurrentPage(page) },
                            onTotalPagesLoaded = { total, _ -> viewModel.updateTotalPages(total) },
                            onTap = toggleReaderMode,
                            onTextSelected = { text, top, bottom, cfiRange -> textSelectionData = com.infer.inferead.ui.screens.TextSelectionData(text, top, bottom, cfiRange) },
                            onSelectionFinished = { text, top, bottom, cfiRange -> textSelectionData = com.infer.inferead.ui.screens.TextSelectionData(text, top, bottom, cfiRange); true },
                            onTextSelectionCleared = { textSelectionData = null },
                            onAnnotationClicked = { id, top, bottom -> /* handle ann click */ },
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
                            searchResults = searchResults,
                            searchJumpIndex = searchJumpIndex,
                            searchQuery = searchQuery,
                            showRawMarkdown = showRawMarkdown
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
                            viewModel = viewModel,
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
                            },
                            onSpineLoaded = { files ->
                                epubSpineFiles = files
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
                    Column(
                        modifier = Modifier.padding(bottom = 16.dp, start = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val fileExt = java.io.File(currentFile?.filePath ?: "").extension.lowercase()
                        val isHtmlJsCss = fileExt == "html" || fileExt == "htm" || fileExt == "css" || fileExt == "js" || fileExt == "md"
                        val isWebCode = currentFile?.format == "CODING" && isHtmlJsCss
                        val isMd = fileExt == "md"
                        
                        if (isWebCode || isMd) {
                            FloatingActionButton(
                                onClick = { 
                                    if (isMd) {
                                        showRawMarkdown = !showRawMarkdown
                                    } else {
                                        showBrowserPreview = true 
                                    }
                                },
                                containerColor = barColor,
                                contentColor = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ) {
                                Icon(com.infer.inferead.ui.components.code_xml, contentDescription = if (isMd) "Toggle Raw Code" else "Browser Preview")
                            }
                        }

                        if (isTtsActive) {
                            FloatingActionButton(
                                onClick = { 
                                    isTtsActive = false
                                    tts?.stop()
                                },
                                containerColor = barColor,
                                contentColor = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Stop TTS"
                                )
                            }
                        }

                        FloatingActionButton(
                            onClick = { showScrubber = !showScrubber },
                            containerColor = barColor,
                            contentColor = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.List, contentDescription = "Scrubber")
                        }
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
                                    val previewText = if (currentFile!!.format == "EPUB") {
                                        chapterPreviews?.getOrNull(index) ?: "Chapter ${index + 1}"
                                    } else {
                                        "Page ${index + 1}"
                                    }
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).clip(RoundedCornerShape(8.dp)).padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Text(
                                                text = previewText,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center
                                            )
                                            if (currentFile!!.format == "EPUB") {
                                                Text(
                                                    text = "${index + 1} / ${currentFile!!.totalPages}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        }
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


            }
        }
        
        if (showGoalCelebration) {
            StarAnimationOverlay(onAnimationFinished = { showGoalCelebration = false })
        }

        if (showBrowserPreview && currentFile != null) {
            val file = currentFile!!
            val context = androidx.compose.ui.platform.LocalContext.current
            var isDesktopMode by remember { mutableStateOf(false) }
            var isBrowserDarkMode by remember { mutableStateOf(false) }
            var contentLoaded by remember { mutableStateOf(false) }
            var fileContent by remember { mutableStateOf<String?>(null) }
            var defaultUserAgent by remember { mutableStateOf<String?>(null) }
            val fileExt = remember(file.filePath) {
                java.io.File(file.filePath).extension.lowercase()
            }
            val isHtml = fileExt == "html" || fileExt == "htm"

            // For HTML files, avoid loading into memory to prevent OOM on large files
            LaunchedEffect(file.filePath) {
                if (!isHtml) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            fileContent = if (file.filePath.startsWith("content://")) {
                                val uri = android.net.Uri.parse(file.filePath)
                                context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                            } else {
                                java.io.File(file.filePath).readText()
                            }
                        } catch (e: Throwable) {
                            fileContent = "// Error loading file: ${e.message}"
                        }
                    }
                } else {
                    fileContent = "HTML_LOADED_VIA_URL"
                }
            }

            val previewClient = remember(isOfflineMode) {
                object : android.webkit.WebViewClient() {
                    var isDarkMode = false
                    var isDesktop = false

                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url
                        if (isOfflineMode && uri != null && (uri.scheme?.startsWith("http", ignoreCase = true) == true || uri.scheme?.startsWith("ws", ignoreCase = true) == true)) {
                            view?.post {
                                android.widget.Toast.makeText(
                                    context,
                                    "Offline Mode Enabled: Network request to ${uri.host ?: uri} is blocked.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            return true
                        }
                        return super.shouldOverrideUrlLoading(view, request)
                    }

                    override fun shouldInterceptRequest(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        val uri = request?.url
                        if (isOfflineMode && uri != null && (uri.scheme?.startsWith("http", ignoreCase = true) == true || uri.scheme?.startsWith("ws", ignoreCase = true) == true)) {
                            view?.post {
                                android.widget.Toast.makeText(
                                    context,
                                    "Offline Mode: Blocked online resource (${uri.host ?: uri})",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                view.evaluateJavascript("""
                                    console.error("Offline Mode Error: Blocked online resource: ${uri}");
                                """.trimIndent(), null)
                            }
                            val errorBody = """{"error": "Offline Mode Enabled", "message": "Online fetching is disabled because Offline Mode is on in app settings."}"""
                            val stream = java.io.ByteArrayInputStream(errorBody.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
                            return android.webkit.WebResourceResponse("application/json", "UTF-8", 403, "Forbidden - Offline Mode Enabled", mapOf("Access-Control-Allow-Origin" to "*"), stream)
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (isOfflineMode) {
                            view?.evaluateJavascript("""
                                (function() {
                                    if (window.__offlineGuardInjected) return;
                                    window.__offlineGuardInjected = true;
                                    var origFetch = window.fetch;
                                    window.fetch = function(input, init) {
                                        var reqUrl = (typeof input === 'string') ? input : (input && input.url) ? input.url : '';
                                        if (reqUrl.startsWith('http://') || reqUrl.startsWith('https://') || reqUrl.startsWith('//')) {
                                            var err = new Error('Offline Mode Error: fetch to "' + reqUrl + '" blocked because Offline Mode is enabled in settings.');
                                            console.error(err.message);
                                            return Promise.reject(err);
                                        }
                                        return origFetch ? origFetch.apply(this, arguments) : Promise.reject(new Error('Fetch not supported'));
                                    };
                                    var origXhrOpen = XMLHttpRequest.prototype.open;
                                    XMLHttpRequest.prototype.open = function(method, url) {
                                        this.__reqUrl = url;
                                        if (typeof url === 'string' && (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('//'))) {
                                            this.__isBlocked = true;
                                        }
                                        return origXhrOpen.apply(this, arguments);
                                    };
                                    var origXhrSend = XMLHttpRequest.prototype.send;
                                    XMLHttpRequest.prototype.send = function(body) {
                                        if (this.__isBlocked) {
                                            var err = 'Offline Mode Error: XMLHttpRequest to "' + this.__reqUrl + '" blocked because Offline Mode is enabled in settings.';
                                            console.error(err);
                                            if (this.onerror) {
                                                this.onerror(new ProgressEvent('error'));
                                            }
                                            return;
                                        }
                                        return origXhrSend.apply(this, arguments);
                                    };
                                    var origWs = window.WebSocket;
                                    if (origWs) {
                                        window.WebSocket = function(url, protocols) {
                                            var err = 'Offline Mode Error: WebSocket to "' + url + '" blocked because Offline Mode is enabled in settings.';
                                            console.error(err);
                                            throw new Error(err);
                                        };
                                    }
                                })();
                            """.trimIndent(), null)
                        }
                        if (isDarkMode) {
                            view?.evaluateJavascript("""
                                (function() {
                                    var style = document.getElementById('browser-dark-mode-style');
                                    if (!style) {
                                        style = document.createElement('style');
                                        style.id = 'browser-dark-mode-style';
                                        style.innerHTML = 'html, body { background-color: #121212 !important; color: #E0E0E0 !important; } a { color: #BB86FC !important; }';
                                        document.head.appendChild(style);
                                    }
                                })();
                            """.trimIndent(), null)
                        } else {
                            view?.evaluateJavascript("""
                                (function() {
                                    var style = document.getElementById('browser-dark-mode-style');
                                    if (style) style.remove();
                                })();
                            """.trimIndent(), null)
                        }
                        view?.evaluateJavascript("""
                            (function() {
                                var meta = document.querySelector('meta[name="viewport"]');
                                if (!meta) {
                                    meta = document.createElement('meta');
                                    meta.name = "viewport";
                                    document.head.appendChild(meta);
                                }
                                meta.content = "width=" + (""" + (if (isDesktop) "true" else "false") + """ ? "1024" : "device-width") + ", initial-scale=1";
                                
                                var style = document.getElementById('browser-centering-style');
                                if (!style) {
                                    style = document.createElement('style');
                                    style.id = 'browser-centering-style';
                                    style.innerHTML = 'html, body { min-height: 100vh; margin: 0; background-color: transparent; } canvas { display: block; margin: 0 auto; }';
                                    document.head.appendChild(style);
                                }
                            })();
                        """.trimIndent(), null)
                    }
                }
            }
            previewClient.isDarkMode = isBrowserDarkMode
            previewClient.isDesktop = isDesktopMode

            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                if (fileContent == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Text(
                            text = "Loading preview...",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.webkit.WebView(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                this.settings.javaScriptEnabled = true
                                this.settings.domStorageEnabled = true
                                this.settings.allowFileAccess = true
                                @Suppress("DEPRECATION")
                                this.settings.allowFileAccessFromFileURLs = true
                                @Suppress("DEPRECATION")
                                this.settings.allowUniversalAccessFromFileURLs = true
                                this.settings.allowContentAccess = true
                                this.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                this.settings.setSupportZoom(true)
                                this.settings.builtInZoomControls = true
                                this.settings.displayZoomControls = false
                                webChromeClient = android.webkit.WebChromeClient()
                                webViewClient = previewClient
                                defaultUserAgent = this.settings.userAgentString
                            }
                        },
                        update = { webView ->
                            val currentTag = webView.tag as? String ?: ""
                            val newTag = "${if (isDesktopMode) "desktop" else "mobile"}_${if (isBrowserDarkMode) "dark" else "light"}_${file.filePath.hashCode()}"
                            if (currentTag != newTag) {
                                webView.tag = newTag
                                
                                val wasDesktopMode = currentTag.startsWith("desktop")
                                val shouldReloadContent = currentTag == "" || currentTag == "loaded" || (isDesktopMode != wasDesktopMode) || !currentTag.endsWith("_${file.filePath.hashCode()}")
                                
                                if (shouldReloadContent) {
                                    if (isDesktopMode) {
                                        webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                                        webView.settings.useWideViewPort = true
                                        webView.settings.loadWithOverviewMode = true
                                    } else {
                                        if (defaultUserAgent != null) webView.settings.userAgentString = defaultUserAgent
                                        webView.settings.useWideViewPort = true
                                        webView.settings.loadWithOverviewMode = true
                                    }
                                    // Actually apply the changes by reloading
                                    if (isHtml) {
                                        val fullUrl = if (file.filePath.startsWith("content://")) file.filePath else "file://${file.filePath}"
                                        webView.post { webView.loadUrl(fullUrl) }
                                    } else {
                                        // For loadDataWithBaseURL, reload() is unreliable. Re-load the content!
                                        val isCss = fileExt == "css"
                                        val isJs = fileExt == "js"
                                        val isMd = fileExt == "md"
                                        val htmlData = when {
                                            isCss -> "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"${if (isDesktopMode) "width=1024" else "width=device-width, initial-scale=1"}\"><style>body { font-family: sans-serif; padding: 16px; }\n$fileContent\n</style></head><body><div class=\"preview-container\"><h1>CSS Preview</h1><p>This is a sample paragraph to demonstrate the CSS styles.</p><button>Sample Button</button><ul><li>Item 1</li><li>Item 2</li></ul></div></body></html>"
                                            isJs -> {
                                                val offlineScript = if (isOfflineMode) {
                                                    """
                                                    (function(){
                                                        var origFetch = window.fetch;
                                                        window.fetch = function(input, init) {
                                                            var reqUrl = (typeof input === 'string') ? input : (input && input.url) ? input.url : '';
                                                            if (reqUrl.startsWith('http://') || reqUrl.startsWith('https://') || reqUrl.startsWith('//')) {
                                                                var err = new Error('Offline Mode Error: fetch to "' + reqUrl + '" blocked because Offline Mode is enabled.');
                                                                console.error(err.message);
                                                                return Promise.reject(err);
                                                            }
                                                            return origFetch ? origFetch.apply(this, arguments) : Promise.reject(new Error('Fetch not supported'));
                                                        };
                                                        var origXhrOpen = XMLHttpRequest.prototype.open;
                                                        XMLHttpRequest.prototype.open = function(method, url) {
                                                            this.__reqUrl = url;
                                                            if (typeof url === 'string' && (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('//'))) {
                                                                this.__isBlocked = true;
                                                            }
                                                            return origXhrOpen.apply(this, arguments);
                                                        };
                                                        var origXhrSend = XMLHttpRequest.prototype.send;
                                                        XMLHttpRequest.prototype.send = function(body) {
                                                            if (this.__isBlocked) {
                                                                var err = 'Offline Mode Error: XMLHttpRequest to "' + this.__reqUrl + '" blocked because Offline Mode is enabled.';
                                                                console.error(err);
                                                                if (this.onerror) {
                                                                    this.onerror(new ProgressEvent('error'));
                                                                }
                                                                return;
                                                            }
                                                            return origXhrSend.apply(this, arguments);
                                                        };
                                                        var origWs = window.WebSocket;
                                                        if (origWs) {
                                                            window.WebSocket = function(url, protocols) {
                                                                var err = 'Offline Mode Error: WebSocket to "' + url + '" blocked because Offline Mode is enabled.';
                                                                console.error(err);
                                                                throw new Error(err);
                                                            };
                                                        }
                                                    })();
                                                    """.trimIndent()
                                                } else ""
                                                "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"${if (isDesktopMode) "width=1024" else "width=device-width, initial-scale=1"}\"><style>body { font-family: monospace; background: #1e1e1e; color: #d4d4d4; padding: 16px; margin: 0; } #console { white-space: pre-wrap; word-wrap: break-word; } .log { color: #d4d4d4; } .error { color: #f48771; font-weight: bold; } .warn { color: #cca700; }</style></head><body><div id=\"console\"></div><script>(function(){var cons=document.getElementById('console');function logMsg(type,args){var msg=Array.from(args).map(function(a){return (typeof a === 'object' && a !== null) ? JSON.stringify(a) : String(a);}).join(' ');cons.innerHTML+='<div class=\"'+type+'\">'+msg+'</div>';}var oldLog=console.log;console.log=function(){logMsg('log',arguments);oldLog.apply(console,arguments);};var oldErr=console.error;console.error=function(){logMsg('error',arguments);oldErr.apply(console,arguments);};var oldWarn=console.warn;console.warn=function(){logMsg('warn',arguments);oldWarn.apply(console,arguments);};})();</script><script>$offlineScript</script><script>try{$fileContent}catch(e){console.error(e.message || e);}</script></body></html>"
                                            }
                                            isMd -> "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"${if (isDesktopMode) "width=1024" else "width=device-width, initial-scale=1"}\"><script src=\"file:///android_asset/marked.min.js\"></script><style>body { font-family: sans-serif; padding: 16px; background: ${if (isBrowserDarkMode) "#121212" else "#ffffff"}; color: ${if (isBrowserDarkMode) "#e0e0e0" else "#000000"}; line-height: 1.6; } img { max-width: 100%; height: auto; } blockquote { border-left: 4px solid #ccc; margin: 0; padding-left: 16px; color: #888; } pre { background: ${if (isBrowserDarkMode) "#1e1e1e" else "#f4f4f4"}; padding: 12px; overflow-x: auto; border-radius: 4px; } code { font-family: monospace; background: ${if (isBrowserDarkMode) "#2a2a2a" else "#eaeaea"}; padding: 2px 4px; border-radius: 2px; } pre code { background: transparent; padding: 0; }</style></head><body><div id=\"content\"></div><script>document.getElementById('content').innerHTML = marked.parse(decodeURIComponent(\"${java.net.URLEncoder.encode(fileContent ?: "", "UTF-8").replace("+", "%20")}\"));</script></body></html>"
                                            else -> fileContent ?: "Empty"
                                        }
                                        val parentFolder = if (file.filePath.startsWith("content://")) {
                                            file.filePath
                                        } else {
                                            val parent = java.io.File(file.filePath).parentFile?.absolutePath ?: ""
                                            if (parent.isNotEmpty()) "file://$parent/" else "file:///"
                                        }
                                        webView.post { webView.loadDataWithBaseURL(parentFolder, htmlData, "text/html", "UTF-8", null) }
                                    }
                                } else {
                                            previewClient.isDarkMode = isBrowserDarkMode
                                            previewClient.isDesktop = isDesktopMode
                                            
                                            // Re-evaluate JS styles explicitly if we aren't reloading
                                            if (isBrowserDarkMode) {
                                                webView.evaluateJavascript("""
                                                    (function() {
                                                        var style = document.getElementById('browser-dark-mode-style');
                                                        if (!style) {
                                                            style = document.createElement('style');
                                                            style.id = 'browser-dark-mode-style';
                                                            style.innerHTML = 'html, body { background-color: #121212 !important; color: #E0E0E0 !important; } a { color: #BB86FC !important; }';
                                                            document.head.appendChild(style);
                                                        }
                                                    })();
                                                """.trimIndent(), null)
                                            } else {
                                                webView.evaluateJavascript("""
                                                    (function() {
                                                        var style = document.getElementById('browser-dark-mode-style');
                                                        if (style) style.remove();
                                                    })();
                                                """.trimIndent(), null)
                                            }
                                        }
                            }
                        },
                        modifier = Modifier.fillMaxSize() // Fullscreen without top padding
                    )
                }

                // Top Left FAB: Open Nav Pane
                FloatingActionButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp).size(40.dp),
                    shape = CircleShape,
                    containerColor = barColor,
                    contentColor = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Open Nav Pane", modifier = Modifier.size(20.dp))
                }

                // Top Right Pill: Desktop Mode, Dark Mode Toggle, and Return Button
                Surface(
                    shape = CircleShape,
                    color = barColor,
                    shadowElevation = 2.dp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isOfflineMode) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                modifier = Modifier.clickable {
                                    android.widget.Toast.makeText(context, "Offline Mode: Online requests in coding files are blocked.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("📴", fontSize = 12.sp)
                                    Text("Offline", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        IconButton(
                            onClick = { isBrowserDarkMode = !isBrowserDarkMode },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("🌓", fontSize = 16.sp)
                        }

                        IconButton(
                            onClick = { isDesktopMode = !isDesktopMode },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                androidx.compose.material.icons.Icons.Default.DesktopMac,
                                contentDescription = "Desktop Mode",
                                modifier = Modifier.size(20.dp),
                                tint = if (isDesktopMode) MaterialTheme.colorScheme.primary else (if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary)
                            )
                        }

                        IconButton(
                            onClick = { showBrowserPreview = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Return",
                                modifier = Modifier.size(20.dp),
                                tint = if (settings.contrastMode == ContrastMode.Dark) Color(0xFF9AB0E6) else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }



    // Settings Bottom Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { 
                showSettingsSheet = false
                showTtsSubPage = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = barColor,
            dragHandle = { BottomSheetDefaults.DragHandle(color = textColor.copy(alpha = 0.4f)) }
        ) {
            if (showTtsSubPage) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showTtsSubPage = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textColor)
                        }
                        Text(
                            text = "Text-to-Speech Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Reading", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = textColor)
                        androidx.compose.material3.Switch(
                            checked = isTtsActive,
                            onCheckedChange = { isTtsActive = it }
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Volume (${(ttsVolume * 100).roundToInt()}%)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = textColor.copy(alpha = 0.8f))
                        Slider(
                            value = ttsVolume,
                            onValueChange = { ttsVolume = it },
                            onValueChangeFinished = { ttsVoiceSettingsVersion++ },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Reading Speed (${String.format(java.util.Locale.US, "%.1fx", ttsSpeed)})", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = textColor.copy(alpha = 0.8f))
                        Slider(
                            value = ttsSpeed,
                            onValueChange = { ttsSpeed = it },
                            onValueChangeFinished = { ttsVoiceSettingsVersion++ },
                            valueRange = 0.5f..2.5f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Voice Gender", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = textColor)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Female", "Male").forEach { gender ->
                                val isSel = selectedGender == gender
                                Button(
                                    onClick = {
                                        selectedGender = gender
                                        ttsVoiceSettingsVersion++
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else textColor
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(gender)
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Language & Accent", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = textColor)
                        
                        val detectedLang = remember(currentFile?.currentPage, epubSpineFiles) {
                            if (currentFile != null) {
                                val path = epubSpineFiles.getOrNull(currentFile!!.currentPage - 1)
                                if (path != null) {
                                    try {
                                        val sampleText = java.io.File(path).readText().take(5000)
                                        val plain = org.jsoup.Jsoup.parse(sampleText).text()
                                        detectLanguageOfText(plain)
                                    } catch (e: Exception) { "EN" }
                                } else "EN"
                            } else "EN"
                        }

                        val ttsLanguages = when (detectedLang) {
                            "DEVANAGARI" -> listOf(
                                "Hindi" to java.util.Locale("hi", "IN"),
                                "Marathi" to java.util.Locale("mr", "IN")
                            )
                            "ES" -> listOf(
                                "Spanish (Spain)" to java.util.Locale("es", "ES"),
                                "Spanish (Mexico)" to java.util.Locale("es", "MX")
                            )
                            "FR" -> listOf(
                                "French (France)" to java.util.Locale.FRANCE,
                                "French (Canada)" to java.util.Locale.CANADA_FRENCH
                            )
                            "DE" -> listOf(
                                "German" to java.util.Locale.GERMANY
                            )
                            "PT" -> listOf(
                                "Portuguese (Brazil)" to java.util.Locale("pt", "BR"),
                                "Portuguese (Portugal)" to java.util.Locale("pt", "PT")
                            )
                            "IT" -> listOf(
                                "Italian" to java.util.Locale.ITALY
                            )
                            "JA" -> listOf(
                                "Japanese" to java.util.Locale.JAPAN
                            )
                            "ZH" -> listOf(
                                "Chinese (China)" to java.util.Locale.CHINA,
                                "Chinese (Taiwan)" to java.util.Locale.TAIWAN
                            )
                            else -> listOf(
                                "English (USA)" to java.util.Locale.US,
                                "English (India)" to java.util.Locale("en", "IN"),
                                "English (UK)" to java.util.Locale.UK,
                                "English (Australia)" to java.util.Locale("en", "AU")
                            )
                        }

                        LaunchedEffect(detectedLang) {
                            val hasCurrent = ttsLanguages.any { it.second == selectedLocale }
                            if (!hasCurrent) {
                                selectedLocale = ttsLanguages.firstOrNull()?.second ?: java.util.Locale.US
                            }
                        }

                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 3
                        ) {
                            ttsLanguages.forEach { (name, locale) ->
                                val isSel = selectedLocale == locale
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            selectedLocale = locale
                                            ttsVoiceSettingsVersion++
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = name,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else textColor,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (formatGroup != "IMAGE" && formatGroup != "PDF" && formatGroup != "CBZ_CBR_CB7") {
                        IconButton(onClick = { showCustomiseTopBar = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Customise Top Bar", tint = textColor)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (formatGroup == "IMAGE") "Image Settings" else "Reader Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        textAlign = TextAlign.Center
                    )
                    if (formatGroup != "IMAGE" && formatGroup != "PDF" && formatGroup != "CBZ_CBR_CB7") {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { showFileSearch = true; showSettingsSheet = false }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Search, contentDescription = "Search in File", tint = textColor)
                        }
                    }
                }
                




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
                    val activeButtons = settings.topBarButtons.toList()
                    if (activeButtons.isNotEmpty()) {
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            maxItemsInEachRow = 5
                        ) {
                            activeButtons.forEach { btnName ->
                                when (btnName) {
                                    "Highlight" -> IconButton(onClick = { 
                                        viewModel.setActiveHighlightMode("#c25d5d")
                                        showSettingsSheet = false
                                        showCommentOptions = false 
                                    }) { Icon(Icons.Default.Highlight, contentDescription = "Highlight", tint = textColor) }
                                    "Comment" -> IconButton(onClick = { showCommentOptions = !showCommentOptions }) { Icon(Icons.Default.Comment, contentDescription = "Comment", tint = textColor) }
                                    "Search Annotations" -> IconButton(onClick = { 
                                        showSettingsSheet = false
                                        viewModel.setShowAnnotationManager(true) 
                                    }) { Icon(Icons.Default.Create, contentDescription = "Edit Annotations", tint = textColor) }
                                    "Search File" -> IconButton(onClick = { 
                                        showFileSearch = true; showSettingsSheet = false 
                                    }) { Icon(Icons.Default.Search, contentDescription = "Search File", tint = textColor) }
                                    "More Settings" -> IconButton(onClick = { showHiddenMenu = true }) { Icon(Icons.Default.ViewColumn, contentDescription = "More Settings", tint = textColor) }
                                    "Justify Text" -> IconButton(onClick = { viewModel.setJustifyText(!settings.justifyText) }) { Icon(Icons.Default.FormatAlignJustify, contentDescription = "Justify Text", tint = if (settings.justifyText) MaterialTheme.colorScheme.primary else textColor) }
                                    "Word Spacing" -> IconButton(onClick = { showWordSpacingDialog = true }) { Icon(Icons.Default.SpaceBar, contentDescription = "Word Spacing", tint = textColor) }
                                    "Line Spacing" -> IconButton(onClick = { showLineSpacingDialog = true }) { Icon(Icons.Default.FormatLineSpacing, contentDescription = "Line Spacing", tint = textColor) }
                                    "Font Color" -> IconButton(onClick = { showFontColorDialog = true }) { Icon(Icons.Default.Palette, contentDescription = "Font Color", tint = textColor) }
                                    "Reading Mode" -> IconButton(onClick = { viewModel.setWarmFilterActive(!settings.isWarmFilterActive) }) { Icon(Icons.Default.MenuBook, contentDescription = "Reading Mode", tint = if (settings.isWarmFilterActive) Color(0xFFCC7722) else textColor) }
                                    "Immersive Mode" -> IconButton(onClick = { 
                                        viewModel.setReaderModeActive(!settings.isReaderModeActive)
                                        val activity = context as? android.app.Activity
                                        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(activity!!.window, activity.window.decorView)
                                        if (!settings.isReaderModeActive) { windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()); windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE } 
                                        else { windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
                                    }) { Icon(imageVector = if (settings.isReaderModeActive) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = "Immersive Mode", tint = if (settings.isReaderModeActive) MaterialTheme.colorScheme.primary else textColor) }
                                    "Mark Finished" -> IconButton(onClick = { 
                                        viewModel.markFinished(currentFile!!.id, true)
                                        showSettingsSheet = false 
                                    }) { Icon(Icons.Default.DoneAll, contentDescription = "Mark Finished", tint = textColor) }
                                }
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
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                                Text("Reading Brightness", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f))
                                androidx.compose.material3.Slider(
                                    value = if (settings.readingBrightness < 0) 0.5f else settings.readingBrightness,
                                    onValueChange = { viewModel.setReadingBrightness(it) },
                                    valueRange = 0.05f..1f,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                )
                                if (settings.readingBrightness >= 0) {
                                    TextButton(onClick = { viewModel.setReadingBrightness(-1f) }) {
                                        Text("System Default", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            if (currentFile?.format == "EPUB") {
                                androidx.compose.material3.Divider(color = textColor.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { isTtsActive = !isTtsActive },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isTtsActive) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = "Read Aloud",
                                            tint = if (isTtsActive) MaterialTheme.colorScheme.primary else textColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isTtsActive) "Stop" else "Read Aloud",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { showTtsSubPage = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "TTS Settings",
                                            tint = textColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
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

                    val scriptType by viewModel.epubScriptType.collectAsState()

                    val latinFonts = listOf(
                        "SansSerif" to FontFamily.SansSerif,
                        "Google Sans" to FontFamily(androidx.compose.ui.text.font.Font("fonts/google_sans.ttf", context.assets)),
                        "Literata" to FontFamily(androidx.compose.ui.text.font.Font("fonts/literata.ttf", context.assets)),
                        "Serif" to FontFamily.Serif,
                        "Monospace" to FontFamily.Monospace,
                        "Chelsea Market" to FontFamily(androidx.compose.ui.text.font.Font("fonts/chelsea_market.ttf", context.assets)),
                        "Libre Baskerville" to FontFamily(androidx.compose.ui.text.font.Font("fonts/libre_baskerville.ttf", context.assets)),
                        "Lora" to FontFamily(androidx.compose.ui.text.font.Font("fonts/lora.ttf", context.assets)),
                        "Nunito" to FontFamily(androidx.compose.ui.text.font.Font("fonts/nunito.ttf", context.assets)),
                        "Playfair Display" to FontFamily(androidx.compose.ui.text.font.Font("fonts/playfair_display.ttf", context.assets))
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
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.Top
                        ) {
                            fonts.forEach { (name, font) ->
                                val isSelected = settings.fontFamily == name
                                Column(
                                    modifier = Modifier.width(72.dp).padding(horizontal = 4.dp),
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
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        maxItemsInEachRow = 5
                    ) {
                        val activeButtons = settings.topBarButtons.toList().filter { it in listOf("Justify Text", "Font Color", "Word Spacing", "Line Spacing") }
                        if (activeButtons.isNotEmpty()) {
                            activeButtons.forEach { btnName ->
                                when (btnName) {
                                    "Justify Text" -> IconButton(onClick = { viewModel.setJustifyText(!settings.justifyText) }) { Icon(Icons.Default.FormatAlignJustify, contentDescription = "Justify Text", tint = if (settings.justifyText) MaterialTheme.colorScheme.primary else textColor) }
                                    "Word Spacing" -> IconButton(onClick = { showWordSpacingDialog = true }) { Icon(Icons.Default.SpaceBar, contentDescription = "Word Spacing", tint = textColor) }
                                    "Line Spacing" -> IconButton(onClick = { showLineSpacingDialog = true }) { Icon(Icons.Default.FormatLineSpacing, contentDescription = "Line Spacing", tint = textColor) }
                                    "Font Color" -> IconButton(onClick = { showFontColorDialog = true }) { Icon(Icons.Default.Palette, contentDescription = "Font Color", tint = textColor) }
                                }
                            }
                        }
                    }
                    
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
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                                Text("Reading Brightness", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f))
                                androidx.compose.material3.Slider(
                                    value = if (settings.readingBrightness < 0) 0.5f else settings.readingBrightness,
                                    onValueChange = { viewModel.setReadingBrightness(it) },
                                    valueRange = 0.05f..1f,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                )
                                if (settings.readingBrightness >= 0) {
                                    TextButton(onClick = { viewModel.setReadingBrightness(-1f) }) {
                                        Text("System Default", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            IconButton(onClick = { showHiddenMenu = true }) { Icon(Icons.Default.ViewColumn, contentDescription = "More Settings", tint = textColor) }
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
                        "Monospace" to FontFamily.Monospace,
                        "Chelsea Market" to FontFamily(androidx.compose.ui.text.font.Font("fonts/chelsea_market.ttf", context.assets)),
                        "Libre Baskerville" to FontFamily(androidx.compose.ui.text.font.Font("fonts/libre_baskerville.ttf", context.assets)),
                        "Lora" to FontFamily(androidx.compose.ui.text.font.Font("fonts/lora.ttf", context.assets)),
                        "Nunito" to FontFamily(androidx.compose.ui.text.font.Font("fonts/nunito.ttf", context.assets)),
                        "Playfair Display" to FontFamily(androidx.compose.ui.text.font.Font("fonts/playfair_display.ttf", context.assets))
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
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.Top
                        ) {
                            fonts.forEach { (name, font) ->
                                val isSelected = settings.fontFamily == name
                                Column(
                                    modifier = Modifier.width(72.dp).padding(horizontal = 4.dp),
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
                        }) { Icon(Icons.Default.Edit, contentDescription = "Edit Annotations", tint = textColor) }
                        if (formatGroup == "PDF") {
                            IconButton(onClick = { 
                                showFileSearch = true
                                showSettingsSheet = false 
                            }) { Icon(Icons.Default.Search, contentDescription = "Search File", tint = textColor) }
                        }
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

    if (showFileSearch) {
        var isDropdownVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
        val focusRequester = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        val activity = context as? android.app.Activity
        val windowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars
        val topPadding = windowInsets.asPaddingValues().calculateTopPadding()
        
        androidx.compose.runtime.LaunchedEffect(Unit) {
            viewModel.setReaderModeActive(true)
            activity?.let {
                val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView)
                windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            kotlinx.coroutines.delay(300)
            focusRequester.requestFocus()
            keyboardController?.show()
        }

        if (isDropdownVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(9f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { 
                                focusManager.clearFocus() 
                                isDropdownVisible = false
                            }
                        )
                    }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding + 16.dp)
                .zIndex(10f),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(0.95f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(barColor)
                        .border(1.dp, textColor.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { 
                            viewModel.setSearchQuery(it)
                            isDropdownVisible = true 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (it.isFocused) isDropdownVisible = true else isDropdownVisible = false },
                        placeholder = { Text("Search...", color = textColor.copy(alpha = 0.6f)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = textColor) },
                        trailingIcon = {
                            IconButton(onClick = { 
                                viewModel.setSearchQuery("")
                                showFileSearch = false 
                                viewModel.setReaderModeActive(false)
                                activity?.let {
                                    val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView)
                                    windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                                }
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Close", tint = textColor)
                            }
                        },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = { keyboardController?.hide() }
                        )
                    )
                }

                if (searchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(barColor)
                            .border(1.dp, textColor.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val displayIndex = if (currentSearchIdx in searchResults.indices) currentSearchIdx else 0
                        Text(
                            text = "${displayIndex + 1} / ${searchResults.size}",
                            color = textColor,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(onClick = { 
                            val prevIndex = if (displayIndex > 0) displayIndex - 1 else searchResults.size - 1
                            viewModel.triggerSearchJump(prevIndex)
                            isDropdownVisible = false
                            focusManager.clearFocus()
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous", tint = textColor)
                        }
                        IconButton(onClick = { 
                            val nextIndex = if (displayIndex < searchResults.size - 1) displayIndex + 1 else 0
                            viewModel.triggerSearchJump(nextIndex)
                            isDropdownVisible = false
                            focusManager.clearFocus()
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next", tint = textColor)
                        }
                    }
                }
                } // This closes the parent Row!

                if (isDropdownVisible && searchResults.isNotEmpty() && searchQuery.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(barColor)
                            .border(1.dp, textColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    ) {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(searchResults.size) { index ->
                                val result = searchResults[index]
                                val isSelected = index == currentSearchIdx
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            viewModel.triggerSearchJump(index) 
                                            isDropdownVisible = false
                                            keyboardController?.hide()
                                        }
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.snippet.trim().replace("\\n", " "),
                                            color = textColor,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val contextText = if (currentFile?.format == "EPUB") {
                                            "Chapter ${result.chapterIndex + 1}"
                                        } else {
                                            "Page ${result.pageNumber ?: (result.chapterIndex + 1)}"
                                        }
                                        Text(
                                            text = contextText,
                                            color = textColor.copy(alpha = 0.6f),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                if (index < searchResults.size - 1) {
                                    androidx.compose.material3.Divider(color = textColor.copy(alpha = 0.1f))
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    if (showCustomiseTopBar) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showCustomiseTopBar = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1E1E1E))
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Select Top Bar Buttons", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
                    
                    val formatGroup = when (currentFile?.format) {
                        "EPUB" -> "EPUB"
                        "TXT", "DOC", "DOCX" -> "TXT_DOC_DOCX"
                        "CODING" -> "CODING"
                        "CBZ", "CBR", "CB7" -> "CBZ_CBR_CB7"
                        "PDF" -> "PDF"
                        "IMAGE" -> "IMAGE"
                        else -> "OTHER"
                    }

                    val allButtons = if (formatGroup == "EPUB") {
                        listOf(
                            "Highlight", "Comment", "Search Annotations", 
                            "Search File", "More Settings", "Justify Text", 
                            "Word Spacing", "Line Spacing", "Font Color", 
                            "Reading Mode", "Immersive Mode", "Mark Finished"
                        )
                    } else if (formatGroup == "TXT_DOC_DOCX" || formatGroup == "CODING") {
                        listOf(
                            "Justify Text", "Font Color", "Word Spacing", "Line Spacing"
                        )
                    } else {
                        emptyList()
                    }
                    
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(allButtons.size) { i ->
                            val btnName = allButtons[i]
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                                val newSet = settings.topBarButtons.toMutableSet()
                                if (newSet.contains(btnName)) newSet.remove(btnName) else newSet.add(btnName)
                                viewModel.setTopBarButtons(newSet)
                            }.padding(vertical = 12.dp)) {
                                androidx.compose.material3.Checkbox(checked = settings.topBarButtons.contains(btnName), onCheckedChange = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(btnName, color = Color.White, fontSize = 16.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showCustomiseTopBar = false }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
        }
    }

    if (showHiddenMenu) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showHiddenMenu = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1A1A1A)) // Visibly darker box
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("More Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
                    
                    // Justify Text
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FormatAlignJustify, contentDescription = "Justify", tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Justify Text", color = Color.White, fontSize = 16.sp)
                        }
                        androidx.compose.material3.Switch(checked = settings.justifyText, onCheckedChange = { viewModel.setJustifyText(it) })
                    }
                    
                    // Word Spacing
                    Row(modifier = Modifier.fillMaxWidth().clickable { showWordSpacingDialog = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SpaceBar, contentDescription = "Word Spacing", tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Word Spacing", color = Color.White, fontSize = 16.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Edit", tint = Color.White)
                    }

                    // Line Spacing
                    Row(modifier = Modifier.fillMaxWidth().clickable { showLineSpacingDialog = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FormatLineSpacing, contentDescription = "Line Spacing", tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Line Spacing", color = Color.White, fontSize = 16.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Edit", tint = Color.White)
                    }

                    // Font Color
                    Row(modifier = Modifier.fillMaxWidth().clickable { showFontColorDialog = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = "Font Color", tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Font Color", color = Color.White, fontSize = 16.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Edit", tint = Color.White)
                    }
                    if (currentFile != null && currentFile!!.format == "EPUB") {
                        Row(modifier = Modifier.fillMaxWidth().clickable { 
                            viewModel.markFinished(currentFile!!.id, true)
                            showHiddenMenu = false 
                        }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DoneAll, contentDescription = "Mark Finished", tint = Color.White, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Mark Finished", color = Color.White, fontSize = 16.sp)
                            }
                        }
                    }
                }
        }
    }
    }

    if (showWordSpacingDialog) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showWordSpacingDialog = false }) {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2A2A)).padding(20.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Word Spacing", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { viewModel.setWordSpacing((settings.wordSpacingMultiplier - 0.1f).coerceIn(0.5f, 3.0f)) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Remove, contentDescription = "-", tint = Color.White) }
                            androidx.compose.material3.Slider(value = settings.wordSpacingMultiplier, onValueChange = { viewModel.setWordSpacing(it) }, valueRange = 0.5f..3.0f, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.setWordSpacing((settings.wordSpacingMultiplier + 0.1f).coerceIn(0.5f, 3.0f)) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, contentDescription = "+", tint = Color.White) }
                            Text("${(settings.wordSpacingMultiplier * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White, modifier = Modifier.width(48.dp).clickable { viewModel.setWordSpacing(1.0f) }, textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }

        if (showLineSpacingDialog) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showLineSpacingDialog = false }) {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2A2A)).padding(20.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Line Spacing", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { viewModel.setLineSpacing((settings.lineSpacingMultiplier - 0.1f).coerceIn(0.5f, 3.0f)) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Remove, contentDescription = "-", tint = Color.White) }
                            androidx.compose.material3.Slider(value = settings.lineSpacingMultiplier, onValueChange = { viewModel.setLineSpacing(it) }, valueRange = 0.5f..3.0f, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.setLineSpacing((settings.lineSpacingMultiplier + 0.1f).coerceIn(0.5f, 3.0f)) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, contentDescription = "+", tint = Color.White) }
                            Text("${(settings.lineSpacingMultiplier * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White, modifier = Modifier.width(48.dp).clickable { viewModel.setLineSpacing(1.0f) }, textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }

        if (showFontColorDialog) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showFontColorDialog = false }) {
                var customHexInput by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(settings.customFontColor ?: "") }
                var r by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(255f) }
                var g by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(255f) }
                var b by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(255f) }
                
                androidx.compose.runtime.LaunchedEffect(settings.customFontColor) {
                    if (settings.customFontColor != null) {
                        try {
                            val colStr = if (settings.customFontColor!!.startsWith("#")) settings.customFontColor!! else "#${settings.customFontColor}"
                            val color = android.graphics.Color.parseColor(colStr)
                            r = android.graphics.Color.red(color).toFloat()
                            g = android.graphics.Color.green(color).toFloat()
                            b = android.graphics.Color.blue(color).toFloat()
                            customHexInput = String.format("#%02X%02X%02X", r.toInt(), g.toInt(), b.toInt())
                        } catch (e: Exception) {}
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2A2A)).padding(20.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Font Color", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(r / 255f, g / 255f, b / 255f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val updateColorFromSliders = {
                            val hexStr = String.format("#%02X%02X%02X", r.toInt(), g.toInt(), b.toInt())
                            customHexInput = hexStr
                            viewModel.setCustomFontColor(hexStr)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("R", color = Color.Red, modifier = Modifier.width(24.dp)); androidx.compose.material3.Slider(value = r, onValueChange = { r = it; updateColorFromSliders() }, valueRange = 0f..255f, modifier = Modifier.weight(1f)) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("G", color = Color.Green, modifier = Modifier.width(24.dp)); androidx.compose.material3.Slider(value = g, onValueChange = { g = it; updateColorFromSliders() }, valueRange = 0f..255f, modifier = Modifier.weight(1f)) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("B", color = Color.Blue, modifier = Modifier.width(24.dp)); androidx.compose.material3.Slider(value = b, onValueChange = { b = it; updateColorFromSliders() }, valueRange = 0f..255f, modifier = Modifier.weight(1f)) }
                        
                        androidx.compose.material3.OutlinedTextField(
                            value = customHexInput,
                            onValueChange = { 
                                customHexInput = it
                                try {
                                    val colStr = if (it.startsWith("#")) it else "#$it"
                                    if (colStr.length == 7 || colStr.length == 9) {
                                        val color = android.graphics.Color.parseColor(colStr)
                                        r = android.graphics.Color.red(color).toFloat()
                                        g = android.graphics.Color.green(color).toFloat()
                                        b = android.graphics.Color.blue(color).toFloat()
                                        viewModel.setCustomFontColor(colStr)
                                    }
                                } catch (e: Exception) {}
                            },
                            label = { Text("Hex Color Code", color = Color.White.copy(alpha = 0.7f)) },
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            singleLine = true
                        )
                        
                        if (settings.customFontColor != null) {
                            TextButton(onClick = { 
                                viewModel.setCustomFontColor(null) 
                                customHexInput = ""
                            }, modifier = Modifier.align(Alignment.End)) { Text("Reset to Default", color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
        }
    }

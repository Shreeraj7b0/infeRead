package com.infer.inferead.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infer.inferead.data.InfeReadDatabase
import com.infer.inferead.data.LibraryFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.util.Calendar

data class SearchResult(
    val snippet: String,
    val chapterIndex: Int,
    val matchIndex: Int = 0, // Used to disambiguate multiple matches in the same chapter
    val pageNumber: Int? = null, // For paginated formats like PDF
    val rects: List<android.graphics.RectF>? = null, // Bounding boxes for highlighting
    val textIndex: Int? = null // For text-based formats
)

fun getFileGroup(format: String?, filePath: String?): String {
    val fmt = format?.uppercase() ?: filePath?.substringAfterLast('.')?.uppercase() ?: ""
    return when {
        fmt == "EPUB" -> "epub"
        fmt in listOf("PDF", "CBZ", "CBR", "CB7") -> "pdf_cbz"
        fmt in listOf("TXT", "DOC", "DOCX") -> "txt_doc"
        fmt == "CODING" -> "coding"
        fmt in listOf("IMAGE", "JPG", "JPEG", "PNG", "WEBP", "BMP", "GIF") -> "img"
        else -> "other"
    }
}

enum class ContrastMode {
    Normal,
    Dark,
    HighContrastLight,
    HighContrastDark,
    EInk
}

data class ReaderSettings(
    val contrastMode: ContrastMode = ContrastMode.Normal,
    val fontSizeMultiplier: Float = 1.0f,
    val wordSpacingMultiplier: Float = 1.0f,
    val lineSpacingMultiplier: Float = 1.0f,
    val fontFamily: String = "SansSerif",
    val fontBold: Boolean = false,
    val isReaderModeActive: Boolean = false,
    val isWarmFilterActive: Boolean = false,
    val isHorizontalScroll: Boolean = false,
    val isNoir: Boolean = false,
    val isNegative: Boolean = false,
    val vignetteStrength: Float = 0f,
    val topBarButtons: Set<String> = setOf("Highlight", "Comment", "Search Annotations"),
    val customFontColor: String? = null,
    val readingBrightness: Float = -1f,
    val justifyText: Boolean = false
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = InfeReadDatabase.getDatabase(application).infeReadDao()
    private val prefs = application.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)

    private val _currentFile = MutableStateFlow<LibraryFile?>(null)
    val currentFile: StateFlow<LibraryFile?> = _currentFile.asStateFlow()

    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private val _bookmarkedPages = MutableStateFlow<Set<Int>>(emptySet())
    val bookmarkedPages: StateFlow<Set<Int>> = _bookmarkedPages.asStateFlow()

    private val _liveMinutes = MutableStateFlow(0)
    val liveMinutes: StateFlow<Int> = _liveMinutes.asStateFlow()

    private val _initialTodayMinutes = MutableStateFlow(0)
    val initialTodayMinutes: StateFlow<Int> = _initialTodayMinutes.asStateFlow()

    private val _goalMinutes = MutableStateFlow(15)
    val goalMinutes: StateFlow<Int> = _goalMinutes.asStateFlow()

    private val _activeHighlightMode = MutableStateFlow<String?>(null)
    val activeHighlightMode: StateFlow<String?> = _activeHighlightMode.asStateFlow()

    private val _showAnnotationManager = MutableStateFlow(false)
    val showAnnotationManager: StateFlow<Boolean> = _showAnnotationManager.asStateFlow()

    private val _pageCommentTrigger = MutableStateFlow(0)
    val pageCommentTrigger: StateFlow<Int> = _pageCommentTrigger.asStateFlow()

    fun setActiveHighlightMode(colorHex: String?) {
        _activeHighlightMode.value = colorHex
    }

    fun setShowAnnotationManager(show: Boolean) {
        _showAnnotationManager.value = show
    }

    fun triggerPageComment() {
        _pageCommentTrigger.value += 1
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _searchJumpIndex = MutableStateFlow<SearchResult?>(null)
    val searchJumpIndex: StateFlow<SearchResult?> = _searchJumpIndex.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null

    fun setSearchQuery(query: String) { 
        _searchQuery.value = query 
        val file = _currentFile.value ?: return
        val formatGroup = getFileGroup(file.format, file.filePath)
        if (formatGroup == "epub") return // EPUB search is handled in FormatRenderers.kt
        
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        
        searchJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(500)
            performSearch(getApplication<Application>(), query, file)
        }
    }

    private suspend fun performSearch(context: Context, query: String, file: LibraryFile) {
        try {
            val results = mutableListOf<SearchResult>()
            val formatGroup = getFileGroup(file.format, file.filePath)
            
            if (formatGroup == "pdf_cbz" && file.format == "PDF") {
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
                val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(java.io.File(file.filePath))
                val stripper = PDFSearchStripper(query)
                
                for (i in 1..document.numberOfPages) {
                    if (!kotlinx.coroutines.currentCoroutineContext().isActive) break
                    stripper.startPage = i
                    stripper.endPage = i
                    stripper.matches.clear()
                    val text = stripper.getText(document) // This runs processPage and findMatches
                    
                    var matchIdx = 0
                    for (rects in stripper.matches) {
                        // Find the snippet using standard index (since stripper.matches maps 1:1)
                        var idx = text.indexOf(query, ignoreCase = true)
                        var currentMatch = 0
                        while (idx >= 0 && currentMatch < matchIdx) {
                            idx = text.indexOf(query, idx + query.length, ignoreCase = true)
                            currentMatch++
                        }
                        
                        val snippet = if (idx >= 0) {
                            val start = maxOf(0, idx - 40)
                            val end = minOf(text.length, idx + query.length + 40)
                            (if(start>0)"..."else"") + text.substring(start, end).replace("\n", " ") + (if(end<text.length)"..."else"")
                        } else "..."
                        
                        results.add(SearchResult(snippet, i - 1, matchIdx, i, rects))
                        matchIdx++
                        if (results.size >= 150) break
                    }
                    if (results.size >= 150) break
                }
                document.close()
            } else if (formatGroup == "txt_doc" || formatGroup == "coding") {
                val text = java.io.File(file.filePath).readText()
                var idx = text.indexOf(query, ignoreCase = true)
                var matchIdx = 0
                while (idx >= 0 && results.size < 150) {
                    if (!kotlinx.coroutines.currentCoroutineContext().isActive) break
                    val start = maxOf(0, idx - 40)
                    val end = minOf(text.length, idx + query.length + 40)
                    val snippet = (if(start>0)"..."else"") + text.substring(start, end).replace("\n", " ") + (if(end<text.length)"..."else"")
                    results.add(SearchResult(snippet, 0, matchIdx, null, null, idx)) // matchIndex is used for highlighting
                    matchIdx++
                    idx = text.indexOf(query, idx + query.length, ignoreCase = true)
                }
            }
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _searchResults.value = results
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setSearchResults(results: List<SearchResult>) { _searchResults.value = results }
    fun triggerSearchJump(result: SearchResult) { _searchJumpIndex.value = result }
    fun clearSearchJump() { _searchJumpIndex.value = null }

    private var sessionStartTime: Long = 0
    private var currentDayOfYear: Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

    init {
        loadSettings()
        loadGoalAndStats()
    }

    private fun loadGoalAndStats() {
        val settingsPrefs = getApplication<Application>().getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        _goalMinutes.value = settingsPrefs.getInt("reading_goal_minutes", 15)

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val calendar = Calendar.getInstance()
            currentDayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = calendar.timeInMillis
            
            try {
                val sessions = dao.getAllReadingSessions().firstOrNull() ?: emptyList()
                _initialTodayMinutes.value = sessions.filter { it.date >= startOfDay }.sumOf { it.durationMinutes }
            } catch (e: Exception) {}
        }
    }

    fun incrementLiveMinutes() {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (today != currentDayOfYear) {
            val durationSoFar = ((System.currentTimeMillis() - sessionStartTime) / (1000 * 60)).toInt()
            if (durationSoFar > 0) {
                viewModelScope.launch {
                    val session = com.infer.inferead.data.ReadingSession(date = System.currentTimeMillis(), durationMinutes = durationSoFar)
                    dao.insertReadingSession(session)
                }
            }
            sessionStartTime = System.currentTimeMillis()
            _liveMinutes.value = 0
            loadGoalAndStats()
        }
        _liveMinutes.value += 1
    }

    private fun loadSettings(fileGroup: String = "epub") {
        val g = "_$fileGroup"
        _settings.value = ReaderSettings(
            contrastMode = ContrastMode.valueOf(prefs.getString("contrastMode$g", ContrastMode.Normal.name) ?: ContrastMode.Normal.name),
            fontSizeMultiplier = prefs.getFloat("fontSizeMultiplier$g", 1.0f),
            wordSpacingMultiplier = prefs.getFloat("wordSpacingMultiplier$g", 1.0f),
            lineSpacingMultiplier = prefs.getFloat("lineSpacingMultiplier$g", 1.0f),
            fontFamily = prefs.getString("fontFamily$g", "SansSerif") ?: "SansSerif",
            fontBold = prefs.getBoolean("fontBold$g", false),
            isReaderModeActive = prefs.getBoolean("isReaderModeActive$g", false),
            isWarmFilterActive = prefs.getBoolean("isWarmFilterActive$g", false),
            isHorizontalScroll = prefs.getBoolean("isHorizontalScroll$g", false),
            isNoir = prefs.getBoolean("isNoir$g", false),
            isNegative = prefs.getBoolean("isNegative$g", false),
            vignetteStrength = prefs.getFloat("vignetteStrength$g", 0f),
            topBarButtons = prefs.getStringSet("topBarButtons$g", setOf("Highlight", "Comment", "Search Annotations")) ?: setOf("Highlight", "Comment", "Search Annotations"),
            customFontColor = prefs.getString("customFontColor$g", null),
            readingBrightness = prefs.getFloat("readingBrightness$g", -1f),
            justifyText = prefs.getBoolean("justifyText$g", false)
        )
    }

    private fun saveSettings() {
        val fileGroup = getFileGroup(_currentFile.value?.format, _currentFile.value?.filePath)
        val g = "_$fileGroup"
        val s = _settings.value
        prefs.edit().apply {
            putString("contrastMode$g", s.contrastMode.name)
            putFloat("fontSizeMultiplier$g", s.fontSizeMultiplier)
            putFloat("wordSpacingMultiplier$g", s.wordSpacingMultiplier)
            putFloat("lineSpacingMultiplier$g", s.lineSpacingMultiplier)
            putString("fontFamily$g", s.fontFamily)
            putBoolean("fontBold$g", s.fontBold)
            putBoolean("isReaderModeActive$g", s.isReaderModeActive)
            putBoolean("isWarmFilterActive$g", s.isWarmFilterActive)
            putBoolean("isHorizontalScroll$g", s.isHorizontalScroll)
            putBoolean("isNoir$g", s.isNoir)
            putBoolean("isNegative$g", s.isNegative)
            putFloat("vignetteStrength$g", s.vignetteStrength)
            putStringSet("topBarButtons$g", s.topBarButtons)
            if (s.customFontColor != null) putString("customFontColor$g", s.customFontColor) else remove("customFontColor$g")
            putFloat("readingBrightness$g", s.readingBrightness)
            putBoolean("justifyText$g", s.justifyText)
            apply()
        }
    }

    fun setContrastMode(contrastMode: ContrastMode) {
        _settings.value = _settings.value.copy(contrastMode = contrastMode)
        saveSettings()
    }

    fun setFontSizeMultiplier(multiplier: Float) {
        val clampedValue = multiplier.coerceIn(0.5f, 3.0f)
        _settings.value = _settings.value.copy(fontSizeMultiplier = clampedValue)
        saveSettings()
    }
    
    fun setWordSpacing(multiplier: Float) {
        _settings.value = _settings.value.copy(wordSpacingMultiplier = multiplier)
        saveSettings()
    }
    
    fun setLineSpacing(multiplier: Float) {
        _settings.value = _settings.value.copy(lineSpacingMultiplier = multiplier)
        saveSettings()
    }

    fun setFontFamily(fontFamily: String) {
        _settings.value = _settings.value.copy(fontFamily = fontFamily)
        saveSettings()
    }

    fun setFontBold(fontBold: Boolean) {
        _settings.value = _settings.value.copy(fontBold = fontBold)
        saveSettings()
    }

    fun setReaderModeActive(isReaderModeActive: Boolean) {
        _settings.value = _settings.value.copy(isReaderModeActive = isReaderModeActive)
        saveSettings()
    }

    fun setWarmFilterActive(isWarmFilterActive: Boolean) {
        _settings.value = _settings.value.copy(isWarmFilterActive = isWarmFilterActive)
        saveSettings()
    }

    fun setNoir(isNoir: Boolean) {
        _settings.value = _settings.value.copy(isNoir = isNoir, isNegative = false)
        saveSettings()
    }

    fun setNegative(isNegative: Boolean) {
        _settings.value = _settings.value.copy(isNegative = isNegative, isNoir = false)
        saveSettings()
    }
    
    fun markFinished(fileId: Int, isFinished: Boolean) {
        viewModelScope.launch {
            val finishedAt = if (isFinished) System.currentTimeMillis() else 0L
            dao.markFinished(fileId, isFinished, finishedAt)
        }
    }

    fun setVignetteStrength(strength: Float) {
        _settings.value = _settings.value.copy(vignetteStrength = strength)
        saveSettings()
    }

    fun setHorizontalScroll(isHorizontal: Boolean) {
        _settings.value = _settings.value.copy(isHorizontalScroll = isHorizontal)
        saveSettings()
    }

    fun setTopBarButtons(buttons: Set<String>) {
        _settings.value = _settings.value.copy(topBarButtons = buttons)
        saveSettings()
    }

    fun setCustomFontColor(colorHex: String?) {
        _settings.value = _settings.value.copy(customFontColor = colorHex)
        saveSettings()
    }

    fun setReadingBrightness(brightness: Float) {
        _settings.value = _settings.value.copy(readingBrightness = brightness)
        saveSettings()
    }

    fun setJustifyText(justify: Boolean) {
        _settings.value = _settings.value.copy(justifyText = justify)
        saveSettings()
    }

    fun loadFile(fileId: Int) {
        viewModelScope.launch {
            val file = dao.getLibraryFileById(fileId)
            _currentFile.value = file
            if (file != null) {
                loadSettings(getFileGroup(file.format, file.filePath))
                // Load per-page bookmarks
                val savedPages = prefs.getStringSet("bookmarked_pages_${file.id}", emptySet()) ?: emptySet()
                _bookmarkedPages.value = savedPages.mapNotNull { it.toIntOrNull() }.toSet()
            }
        }
    }

    fun toggleBookmark() {
        val file = _currentFile.value ?: return
        val page = file.currentPage
        val current = _bookmarkedPages.value.toMutableSet()
        if (current.contains(page)) {
            current.remove(page)
        } else {
            current.add(page)
        }
        _bookmarkedPages.value = current
        prefs.edit().putStringSet("bookmarked_pages_${file.id}", current.map { it.toString() }.toSet()).apply()
        // Keep DB isBookmarked in sync: true if any page is bookmarked
        val anyBookmarked = current.isNotEmpty()
        _currentFile.value = file.copy(isBookmarked = anyBookmarked)
        viewModelScope.launch {
            dao.toggleBookmark(file.id, anyBookmarked)
        }
    }

    fun clearBookmarks() {
        val file = _currentFile.value ?: return
        _bookmarkedPages.value = emptySet()
        prefs.edit().remove("bookmarked_pages_${file.id}").apply()
        _currentFile.value = file.copy(isBookmarked = false)
        viewModelScope.launch {
            dao.clearBookmarks(file.id)
        }
    }

    fun updateCurrentPage(currentPage: Int) {
        val file = _currentFile.value ?: return
        if (file.currentPage != currentPage) {
            _currentFile.value = file.copy(currentPage = currentPage)
            viewModelScope.launch {
                dao.updatePageProgress(file.id, currentPage, file.totalPages)
            }
        }
    }

    fun updateTotalPages(totalPages: Int) {
        val file = _currentFile.value ?: return
        if (file.totalPages != totalPages) {
            _currentFile.value = file.copy(totalPages = totalPages)
            viewModelScope.launch {
                dao.updatePageProgress(file.id, file.currentPage, totalPages)
            }
        }
    }

    fun startReadingSession() {
        sessionStartTime = System.currentTimeMillis()
    }

    fun endReadingSession() {
        if (sessionStartTime > 0) {
            val durationMinutes = ((System.currentTimeMillis() - sessionStartTime) / (1000 * 60)).toInt()
            if (durationMinutes > 0) {
                viewModelScope.launch {
                    val session = com.infer.inferead.data.ReadingSession(date = System.currentTimeMillis(), durationMinutes = durationMinutes)
                    dao.insertReadingSession(session)
                }
            }
            sessionStartTime = 0
        }
        
        // Clear extracted temp files from cache
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cacheDir = getApplication<Application>().cacheDir
                val files = cacheDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.name.startsWith("comic_cache_") || file.name.startsWith("epub_cache_") || file.name.startsWith("temp_comic_") || file.name.startsWith("temp_import_")) {
                            file.deleteRecursively()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Annotations
    fun getAnnotationsForFile(fileId: Int): kotlinx.coroutines.flow.Flow<List<com.infer.inferead.data.Annotation>> {
        return dao.getAnnotations(fileId)
    }

    fun insertAnnotation(annotation: com.infer.inferead.data.Annotation) {
        viewModelScope.launch {
            dao.insertAnnotation(annotation)
        }
    }

    fun deleteAnnotation(annotation: com.infer.inferead.data.Annotation) {
        viewModelScope.launch {
            dao.deleteAnnotation(annotation.id)
        }
    }

    fun deleteHighlightsForFile(fileId: Int) {
        viewModelScope.launch {
            val anns = dao.getAnnotations(fileId).firstOrNull() ?: emptyList()
            anns.filter { it.colorHex != "" && !it.cfiRange.contains("|PAGE") }.forEach {
                dao.deleteAnnotation(it.id)
            }
        }
    }

    fun deleteCommentsForFile(fileId: Int) {
        viewModelScope.launch {
            val anns = dao.getAnnotations(fileId).firstOrNull() ?: emptyList()
            anns.filter { it.colorHex == "" || it.cfiRange.contains("|PAGE") }.forEach {
                dao.deleteAnnotation(it.id)
            }
        }
    }

    fun exportFile(context: Context, file: LibraryFile, modified: Boolean) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val infeReadDir = java.io.File(downloadsDir, "infeRead")
                if (!infeReadDir.exists()) infeReadDir.mkdirs()

                val ext = file.filePath.substringAfterLast('.', "")
                val baseName = file.title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
                val originalFile = java.io.File(file.filePath)
                
                if (!originalFile.exists()) return@launch

                if (modified && file.format == "IMAGE") {
                    val isNoir = prefs.getBoolean("is_noir", false)
                    val isNegative = prefs.getBoolean("is_negative", false)
                    
                    if (isNoir || isNegative) {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(file.filePath)
                        if (bitmap != null) {
                            val outBitmap = android.graphics.Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(outBitmap)
                            val paint = android.graphics.Paint()
                            
                            val cm = android.graphics.ColorMatrix()
                            if (isNegative) {
                                cm.set(floatArrayOf(
                                    -1f, 0f, 0f, 0f, 255f,
                                    0f, -1f, 0f, 0f, 255f,
                                    0f, 0f, -1f, 0f, 255f,
                                    0f, 0f, 0f, 1f, 0f
                                ))
                            }
                            if (isNoir) {
                                val noirCm = android.graphics.ColorMatrix()
                                noirCm.setSaturation(0f)
                                val contrastCm = android.graphics.ColorMatrix()
                                val scale = 1.2f
                                val translate = (-.5f * scale + .5f) * 255f
                                contrastCm.set(floatArrayOf(
                                    scale, 0f, 0f, 0f, translate,
                                    0f, scale, 0f, 0f, translate,
                                    0f, 0f, scale, 0f, translate,
                                    0f, 0f, 0f, 1f, 0f
                                ))
                                noirCm.postConcat(contrastCm)
                                if (isNegative) cm.postConcat(noirCm) else cm.set(noirCm)
                            }
                            paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
                            canvas.drawBitmap(bitmap, 0f, 0f, paint)
                            
                            val outFile = java.io.File(infeReadDir, "${baseName}_modified.jpg")
                            java.io.FileOutputStream(outFile).use { fos ->
                                outBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, fos)
                            }
                            bitmap.recycle()
                            outBitmap.recycle()
                            
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Exported to Downloads/infeRead", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }
                    }
                }

                // Default copy
                val outFile = java.io.File(infeReadDir, "$baseName${if (ext.isNotEmpty()) ".$ext" else ""}")
                originalFile.copyTo(outFile, overwrite = true)
                
                // If modified and EPUB, export annotations to TXT
                if (modified && file.format == "EPUB") {
                    val annotations = dao.getAnnotations(file.id).firstOrNull()
                    if (!annotations.isNullOrEmpty()) {
                        val txtFile = java.io.File(infeReadDir, "${baseName}_annotations.txt")
                        txtFile.bufferedWriter().use { writer ->
                            writer.write("Annotations for ${file.title}\n\n")
                            annotations.forEach { ann ->
                                writer.write("Location: ${ann.cfiRange}\n")
                                if (ann.colorHex != "") {
                                    writer.write("Type: Highlight (${ann.colorHex})\n")
                                } else {
                                    writer.write("Type: Comment/Bookmark\n")
                                }
                                if (!ann.textComment.isNullOrEmpty()) {
                                    writer.write("Note: ${ann.textComment}\n")
                                }
                                writer.write("\n")
                            }
                        }
                    }
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Exported to Downloads/infeRead", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Export Failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun updateAnnotationColor(annotation: com.infer.inferead.data.Annotation, newColor: String) {
        viewModelScope.launch {
            dao.updateAnnotation(annotation.copy(colorHex = newColor))
        }
    }
}

class PDFSearchStripper(private val query: String) : com.tom_roush.pdfbox.text.PDFTextStripper() {
    val matches = mutableListOf<List<android.graphics.RectF>>()
    private val currentText = java.lang.StringBuilder()
    private val currentPositions = mutableListOf<com.tom_roush.pdfbox.text.TextPosition>()

    override fun writeString(text: String, textPositions: List<com.tom_roush.pdfbox.text.TextPosition>) {
        currentText.append(text)
        currentPositions.addAll(textPositions)
        super.writeString(text, textPositions)
    }

    override fun processPage(page: com.tom_roush.pdfbox.pdmodel.PDPage?) {
        currentText.clear()
        currentPositions.clear()
        super.processPage(page)
        findMatches()
    }

    private fun findMatches() {
        val builder = java.lang.StringBuilder()
        val indexMap = mutableListOf<Int>()
        for (i in currentPositions.indices) {
            val unicode = currentPositions[i].unicode
            if (unicode != null) {
                for (char in unicode) {
                    if (char.isLetterOrDigit()) {
                        builder.append(char)
                        indexMap.add(i)
                    }
                }
            }
        }
        val searchableText = builder.toString()
        val searchableQuery = query.filter { it.isLetterOrDigit() }
        if (searchableQuery.isEmpty()) return

        var idx = searchableText.indexOf(searchableQuery, ignoreCase = true)
        while (idx >= 0) {
            val rects = mutableListOf<android.graphics.RectF>()
            val endIdx = minOf(idx + searchableQuery.length, indexMap.size) - 1
            if (idx < indexMap.size && endIdx >= 0 && endIdx < indexMap.size) {
                val startPosIdx = indexMap[idx]
                val endPosIdx = indexMap[endIdx]
                for (i in startPosIdx..endPosIdx) {
                    val pos = currentPositions[i]
                    val x = pos.xDirAdj
                    val y = pos.yDirAdj - pos.heightDir
                    val w = pos.widthDirAdj
                    val h = pos.heightDir
                    rects.add(android.graphics.RectF(x, y, x + w, y + h))
                }
                if (rects.isNotEmpty()) {
                    matches.add(rects)
                }
            }
            idx = searchableText.indexOf(searchableQuery, idx + searchableQuery.length, ignoreCase = true)
        }
    }
}

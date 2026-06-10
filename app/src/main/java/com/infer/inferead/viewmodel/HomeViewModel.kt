package com.infer.inferead.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infer.inferead.data.FileRepository
import com.infer.inferead.data.InfeReadDatabase
import com.infer.inferead.data.LibraryFile
import com.infer.inferead.data.User
import com.infer.inferead.data.Checklist
import com.infer.inferead.data.ChecklistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = InfeReadDatabase.getDatabase(application).infeReadDao()
    private val repository = FileRepository(application, dao)
    private val prefs = application.getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE)

    init {
        viewModelScope.launch {
            com.infer.inferead.network.AppDownloadManager.completedDownloads.collect { path ->
                val file = java.io.File(path)
                if (file.exists()) {
                    repository.linkFile(path)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val infeReadDir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "infeRead")
                if (infeReadDir.exists() && infeReadDir.isDirectory) {
                    val existingFiles = dao.getAllLibraryFiles().first()
                    infeReadDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val ext = file.extension.uppercase()
                            val format = when (ext) {
                                "PDF" -> "PDF"
                                "MD", "PY", "C", "JAVA", "JS", "CSS" -> "CODING"
                                "TXT", "DOC", "DOCX" -> "TXT"
                                "JPG", "JPEG", "PNG", "WEBP", "SVG", "BMP", "HEIC", "HEIF" -> "IMAGE"
                                "EPUB" -> "EPUB"
                                "CBZ", "ZIP" -> "CBZ"
                                "CBR", "RAR" -> "CBR"
                                "CB7", "7Z" -> "CB7"
                                else -> "UNKNOWN"
                            }
                            val isDuplicate = existingFiles.any { it.title == file.nameWithoutExtension && it.format == format }
                            if (!isDuplicate) {
                                val isSupported = ext.lowercase() in listOf("pdf", "epub", "cbz", "cbr", "cb7", "txt", "doc", "docx", "md", "py", "c", "java", "js", "css", "jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "svg")
                                if (isSupported) {
                                    repository.linkFile(file.absolutePath)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val libraryFiles: StateFlow<List<LibraryFile>> = dao.getAllLibraryFiles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val checklists: StateFlow<List<Checklist>> = dao.getAllChecklists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val bookshelves: StateFlow<List<com.infer.inferead.data.Bookshelf>> = repository.getAllBookshelves()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val bookshelfItems: StateFlow<List<com.infer.inferead.data.BookshelfItem>> = repository.getAllBookshelfItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val currentUser: StateFlow<User?> = dao.getUser()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val readingSessions: StateFlow<List<com.infer.inferead.data.ReadingSession>> = dao.getAllReadingSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _conversionProgress = MutableStateFlow(0)
    val conversionProgress = _conversionProgress.asStateFlow()

    private val _isConverting = MutableStateFlow(false)
    val isConverting = _isConverting.asStateFlow()

    private val _isConversionPaused = MutableStateFlow(false)
    val isConversionPaused = _isConversionPaused.asStateFlow()

    private val _convertingFileName = MutableStateFlow<String?>(null)
    val convertingFileName = _convertingFileName.asStateFlow()

    private val _massImportProgress = MutableStateFlow(0)
    val massImportProgress = _massImportProgress.asStateFlow()

    private val _massImportTotal = MutableStateFlow(0)
    val massImportTotal = _massImportTotal.asStateFlow()

    private val _isMassImporting = MutableStateFlow(false)
    val isMassImporting = _isMassImporting.asStateFlow()

    private val _isMassImportPaused = MutableStateFlow(false)
    val isMassImportPaused = _isMassImportPaused.asStateFlow()

    private var massImportJob: Job? = null

    private var conversionJob: Job? = null

    suspend fun importFile(uri: Uri): Int? {
        val fileId = repository.importFile(uri)
        return fileId?.toInt()
    }

    /**
     * Primary entry point for the + FAB and pendingUri (files shared into the app).
     * Attempts to link via a persistent content:// URI (zero disk copy, survives file moves).
     * Falls back to a full import (copy into sandbox) for cloud or non-persistable URIs.
     * Shows a toast so the user can tell which path was taken.
     */
    fun linkOrImportFile(uri: Uri): Job = viewModelScope.launch(Dispatchers.IO) {
        val app = getApplication<android.app.Application>()
        val canPersist = try {
            app.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (e: SecurityException) {
            false
        }

        if (canPersist) {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(app, "Linking File…", android.widget.Toast.LENGTH_SHORT).show()
            }
            repository.linkFileFromUri(uri)
        } else {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(app, "Importing File…", android.widget.Toast.LENGTH_SHORT).show()
            }
            repository.importFile(uri)
        }
    }


    fun massImportFolder(treeUri: Uri, context: android.content.Context, targetBookshelfId: Int?) {
        if (_isMassImporting.value) return
        _isMassImporting.value = true
        _massImportProgress.value = 0
        _isMassImportPaused.value = false
        
        massImportJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                val uris = mutableListOf<Uri>()
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                    null, null, null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val documentId = cursor.getString(0)
                        val fileUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                        uris.add(fileUri)
                    }
                }
                
                _massImportTotal.value = uris.size
                for ((index, uri) in uris.withIndex()) {
                    while (_isMassImportPaused.value && isActive) {
                        delay(500)
                    }
                    if (!isActive) break
                    
                    val importedId = repository.importFile(uri)?.toInt()
                    if (importedId != null && targetBookshelfId != null) {
                        repository.addFileToBookshelf(targetBookshelfId, importedId)
                    }
                    _massImportProgress.value = index + 1
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isMassImporting.value = false
            }
        }
    }

    fun scanFolderForNewFiles(treeUri: Uri, context: android.content.Context, selectedExtensions: List<String>) {
        if (_isMassImporting.value) return
        _isMassImporting.value = true
        _massImportProgress.value = 0
        _isMassImportPaused.value = false
        
        massImportJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                if (documentFile != null && documentFile.isDirectory) {
                    val existingFiles = dao.getAllLibraryFiles().first()
                    val filesToScan = mutableListOf<androidx.documentfile.provider.DocumentFile>()
                    
                    fun traverse(dir: androidx.documentfile.provider.DocumentFile) {
                        dir.listFiles().forEach { file ->
                            if (file.isDirectory) traverse(file)
                            else if (file.isFile && file.name != null) {
                                val fileName = file.name!!
                                val ext = fileName.substringAfterLast(".", "").uppercase()
                                val nameWithoutExtension = fileName.substringBeforeLast(".")
                                val format = when (ext) {
                                    "PDF" -> "PDF"
                                    "MD", "PY", "C", "JAVA", "JS", "CSS" -> "CODING"
                                    "TXT", "DOC", "DOCX" -> "TXT"
                                    "JPG", "JPEG", "PNG", "WEBP", "SVG", "BMP", "HEIC", "HEIF" -> "IMAGE"
                                    "EPUB" -> "EPUB"
                                    "CBZ", "ZIP" -> "CBZ"
                                    "CBR", "RAR" -> "CBR"
                                    "CB7", "7Z" -> "CB7"
                                    else -> "UNKNOWN"
                                }
                                val isDuplicate = existingFiles.any { it.title == nameWithoutExtension && it.format == format }
                                if (!isDuplicate && selectedExtensions.contains(ext)) {
                                    filesToScan.add(file)
                                }
                            }
                        }
                    }
                    traverse(documentFile)
                    
                    _massImportTotal.value = filesToScan.size
                    for ((index, file) in filesToScan.withIndex()) {
                        while (_isMassImportPaused.value && isActive) {
                            delay(500)
                        }
                        if (!isActive) break
                        
                        // We need the absolute path for repository.linkFile
                        // Content URIs can be converted to physical paths if they are from standard storage
                        val childPathStr = file.uri.path ?: ""
                        val physicalPath = if (childPathStr.contains("primary:")) {
                            "/storage/emulated/0/" + childPathStr.substringAfter("primary:")
                        } else null
                        
                        if (physicalPath != null) {
                            repository.linkFile(physicalPath)
                        }
                        _massImportProgress.value = index + 1
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isMassImporting.value = false
            }
        }
    }

    fun massImportFiles(uris: List<Uri>, targetBookshelfId: Int?) {
        if (_isMassImporting.value) return
        _isMassImporting.value = true
        _massImportProgress.value = 0
        _massImportTotal.value = uris.size
        _isMassImportPaused.value = false

        massImportJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                for ((index, uri) in uris.withIndex()) {
                    while (_isMassImportPaused.value && isActive) {
                        delay(500)
                    }
                    if (!isActive) break

                    // Try to link the file via persistent content:// URI first.
                    // This avoids copying and survives file moves on device.
                    val canPersist = try {
                        getApplication<android.app.Application>().contentResolver
                            .takePersistableUriPermission(
                                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        true
                    } catch (e: SecurityException) {
                        false
                    }

                    val fileId: Int?
                    if (canPersist) {
                        // Show "Linking File..." toast
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                getApplication(),
                                "Linking File...",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        fileId = repository.linkFileFromUri(uri)?.toInt()
                    } else {
                        // Fallback: cloud or inaccessible URIs — copy into sandbox
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                getApplication(),
                                "Importing File...",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        fileId = repository.importFile(uri)?.toInt()
                    }

                    if (fileId != null && targetBookshelfId != null) {
                        repository.addFileToBookshelf(targetBookshelfId, fileId)
                    }
                    _massImportProgress.value = index + 1
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isMassImporting.value = false
            }
        }
    }
    
    fun pauseMassImport() { _isMassImportPaused.value = true }
    fun resumeMassImport() { _isMassImportPaused.value = false }
    fun cancelMassImport() { 
        massImportJob?.cancel() 
        _isMassImporting.value = false
    }

    fun updateThumbnail(fileId: Int, imageUri: Uri) {
        viewModelScope.launch {
            repository.saveThumbnail(fileId, imageUri)
        }
    }

    fun deleteFile(fileId: Int) {
        viewModelScope.launch {
            repository.deleteFile(fileId)
        }
    }

    fun renameFile(fileId: Int, newTitle: String) {
        viewModelScope.launch {
            repository.renameFile(fileId, newTitle)
        }
    }

    fun relinkFile(fileId: Int, newUri: Uri) {
        viewModelScope.launch {
            repository.relinkFile(fileId, newUri)
        }
    }

    fun markFinished(fileId: Int, isFinished: Boolean) {
        viewModelScope.launch {
            val finishedAt = if (isFinished) System.currentTimeMillis() else 0L
            repository.markFinished(fileId, isFinished, finishedAt)
        }
    }

    fun updateRating(fileId: Int, rating: Int) {
        viewModelScope.launch {
            repository.updateRating(fileId, rating)
        }
    }

    fun markToRead(fileId: Int, isToRead: Boolean) {
        viewModelScope.launch {
            dao.markToRead(fileId, isToRead)
        }
    }

    fun clearBookmarks(fileId: Int) {
        viewModelScope.launch {
            dao.clearBookmarks(fileId)
        }
    }

    // Checklist management
    fun createChecklist(name: String) {
        viewModelScope.launch {
            dao.insertChecklist(Checklist(name = name))
        }
    }

    fun renameChecklist(id: Int, newName: String) {
        viewModelScope.launch {
            dao.renameChecklist(id, newName)
        }
    }

    fun markChecklistFinished(checklistId: Int, isFinished: Boolean) {
        viewModelScope.launch {
            dao.markAllChecklistItemsCompletion(checklistId, isFinished)
        }
    }
    
    fun markAllChecklistItemsDone(checklistId: Int, isDone: Boolean) {
        viewModelScope.launch {
            dao.markAllChecklistItemsCompletion(checklistId, isDone)
        }
    }

    fun clearChecklist(checklistId: Int) {
        viewModelScope.launch {
            dao.clearChecklistItems(checklistId)
        }
    }

    fun deleteChecklist(checklistId: Int) {
        viewModelScope.launch {
            dao.deleteChecklist(checklistId)
            dao.clearChecklistItems(checklistId)
        }
    }

    fun getChecklistItems(checklistId: Int): kotlinx.coroutines.flow.Flow<List<ChecklistItem>> {
        return dao.getChecklistItems(checklistId)
    }

    fun addChecklistItem(checklistId: Int, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            dao.insertChecklistItem(ChecklistItem(checklistId = checklistId, title = title.trim()))
        }
    }

    fun toggleChecklistItemCompletion(item: ChecklistItem) {
        viewModelScope.launch {
            dao.updateChecklistItem(item.copy(isCompleted = !item.isCompleted))
        }
    }
    
    fun convertPdfToEpub(file: LibraryFile) {
        if (_isConverting.value) return
        
        _isConverting.value = true
        _conversionProgress.value = 0
        _isConversionPaused.value = false
        _convertingFileName.value = file.title
        
        conversionJob = viewModelScope.launch {
            try {
                val inputPdf = java.io.File(file.filePath)
                val newEpubName = "${inputPdf.nameWithoutExtension}.epub"
                val outputEpub = java.io.File(inputPdf.parentFile, newEpubName)
                
                val success = com.infer.inferead.utils.PdfToEpubConverter.convert(
                    getApplication<Application>(), 
                    inputPdf, 
                    outputEpub,
                    onProgress = { progress -> _conversionProgress.value = progress },
                    checkPause = {
                        while (_isConversionPaused.value && isActive) {
                            delay(500)
                        }
                    },
                    checkCancel = { !isActive }
                )
                
                if (success) {
                    val newFile = LibraryFile(
                        title = "${file.title} (EPUB)",
                        filePath = outputEpub.absolutePath,
                        format = "EPUB",
                        thumbnailUri = file.thumbnailUri,
                        addedAt = System.currentTimeMillis()
                    )
                    dao.insertLibraryFile(newFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isConverting.value = false
                _convertingFileName.value = null
            }
        }
    }

    fun pauseConversion() {
        _isConversionPaused.value = true
    }

    fun resumeConversion() {
        _isConversionPaused.value = false
    }

    fun cancelConversion() {
        conversionJob?.cancel()
        _isConverting.value = false
        _convertingFileName.value = null
    }

    fun deleteChecklistItem(itemId: Int) {
        viewModelScope.launch {
            dao.deleteChecklistItem(itemId)
        }
    }

    fun updateChecklistItem(item: ChecklistItem) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateChecklistItem(item)
        }
    }

    fun updateChecklistItemIndent(id: Int, newIndentLevel: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = dao.getChecklistItemById(id)
            if (item != null) {
                dao.updateChecklistItem(item.copy(indentLevel = newIndentLevel))
            }
        }
    }

    fun updateChecklistColor(id: Int, colorHex: String) {
        viewModelScope.launch {
            dao.updateChecklistColor(id, colorHex)
        }
    }

    fun convertChecklistToPdf(checklistId: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val checklist = dao.getAllChecklists().first().find { it.id == checklistId } ?: return@launch
                val items = dao.getChecklistItems(checklistId).first()
                
                val context = getApplication<Application>()
                val pdfDocument = android.graphics.pdf.PdfDocument()
                val pageWidth = 595
                val pageHeight = 842
                val margin = 50f
                var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                }
                
                val titleTextPaint = android.text.TextPaint(paint).apply {
                    textSize = 24f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }
                
                val itemTextPaint = android.text.TextPaint(paint).apply {
                    textSize = 16f
                    typeface = android.graphics.Typeface.DEFAULT
                }
                
                var y = margin
                
                canvas.drawText(checklist.name, margin, y + titleTextPaint.textSize, titleTextPaint)
                y += titleTextPaint.textSize * 2
                
                for (item in items) {
                    val indent = item.indentLevel * 20f
                    val checkbox = if (item.isCompleted) "[X]" else "[ ]"
                    val text = "$checkbox ${item.title}"
                    
                    val textWidth = (pageWidth - margin * 2 - indent).toInt()
                    val staticLayout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, itemTextPaint, textWidth)
                        .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1f)
                        .setIncludePad(false)
                        .build()
                        
                    val itemHeight = staticLayout.height
                    if (y + itemHeight > pageHeight - margin) {
                        pdfDocument.finishPage(page)
                        pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        y = margin
                    }
                    
                    canvas.save()
                    canvas.translate(margin + indent, y)
                    staticLayout.draw(canvas)
                    canvas.restore()
                    
                    y += itemHeight + 10f
                }
                pdfDocument.finishPage(page)
                
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val infeReadDir = java.io.File(downloadsDir, "infeRead")
                if (!infeReadDir.exists()) infeReadDir.mkdirs()
                val file = java.io.File(infeReadDir, "${checklist.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.pdf")
                val out = java.io.FileOutputStream(file)
                pdfDocument.writeTo(out)
                pdfDocument.close()
                out.close()
                
                // Show toast
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Saved to Downloads: ${file.name}", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun shareChecklistAsPdf(checklistId: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val checklist = dao.getAllChecklists().first().find { it.id == checklistId } ?: return@launch
                val items = dao.getChecklistItems(checklistId).first()
                
                val context = getApplication<Application>()
                val pdfDocument = android.graphics.pdf.PdfDocument()
                val pageWidth = 595
                val pageHeight = 842
                val margin = 50f
                var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                }
                
                val titleTextPaint = android.text.TextPaint(paint).apply {
                    textSize = 24f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }
                
                val itemTextPaint = android.text.TextPaint(paint).apply {
                    textSize = 16f
                    typeface = android.graphics.Typeface.DEFAULT
                }
                
                var y = margin
                
                canvas.drawText(checklist.name, margin, y + titleTextPaint.textSize, titleTextPaint)
                y += titleTextPaint.textSize * 2
                
                for (item in items) {
                    val indent = item.indentLevel * 20f
                    val checkbox = if (item.isCompleted) "[X]" else "[ ]"
                    val text = "$checkbox ${item.title}"
                    
                    val textWidth = (pageWidth - margin * 2 - indent).toInt()
                    val staticLayout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, itemTextPaint, textWidth)
                        .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1f)
                        .setIncludePad(false)
                        .build()
                        
                    val itemHeight = staticLayout.height
                    if (y + itemHeight > pageHeight - margin) {
                        pdfDocument.finishPage(page)
                        pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        y = margin
                    }
                    
                    canvas.save()
                    canvas.translate(margin + indent, y)
                    staticLayout.draw(canvas)
                    canvas.restore()
                    
                    y += itemHeight + 10f
                }
                pdfDocument.finishPage(page)
                
                val file = java.io.File(context.cacheDir, "${checklist.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.pdf")
                val out = java.io.FileOutputStream(file)
                pdfDocument.writeTo(out)
                pdfDocument.close()
                out.close()
                
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share Checklist").apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    fun convertChecklistToTxt(checklistId: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val checklist = dao.getAllChecklists().first().find { it.id == checklistId } ?: return@launch
                val items = dao.getChecklistItems(checklistId).first()
                
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val infeReadDir = java.io.File(downloadsDir, "infeRead")
                if (!infeReadDir.exists()) infeReadDir.mkdirs()
                
                val file = java.io.File(infeReadDir, "${checklist.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.txt")
                
                file.bufferedWriter().use { writer ->
                    writer.write("${checklist.name}\n\n")
                    for (item in items) {
                        val indent = "  ".repeat(item.indentLevel)
                        val checkbox = if (item.isCompleted) "[X]" else "[ ]"
                        writer.write("$indent$checkbox ${item.title}\n")
                    }
                }
                
                val context = getApplication<Application>()
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Saved to Downloads: ${file.name}", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun shareChecklistAsTxt(checklistId: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val checklist = dao.getAllChecklists().first().find { it.id == checklistId } ?: return@launch
                val items = dao.getChecklistItems(checklistId).first()
                val context = getApplication<Application>()
                
                val file = java.io.File(context.cacheDir, "${checklist.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.txt")
                
                file.bufferedWriter().use { writer ->
                    writer.write("${checklist.name}\n\n")
                    for (item in items) {
                        val indent = "  ".repeat(item.indentLevel)
                        val checkbox = if (item.isCompleted) "[X]" else "[ ]"
                        writer.write("$indent$checkbox ${item.title}\n")
                    }
                }
                
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share Checklist").apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun exportFile(context: android.content.Context, file: LibraryFile, modified: Boolean) {
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

    fun convertCodeFile(context: android.content.Context, file: LibraryFile, format: String, isShare: Boolean) {
        if (_isConverting.value) return
        
        _isConverting.value = true
        _conversionProgress.value = 0
        _isConversionPaused.value = false
        _convertingFileName.value = file.title
        
        conversionJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val text = java.io.File(file.filePath).readText()
                val originalExt = file.filePath.substringAfterLast('.', "txt")
                // Format baseName per user request: sample(py)
                val baseName = "${file.title}($originalExt)"
                
                val outputFiles = mutableListOf<java.io.File>()
                
                val outDir = if (isShare) context.cacheDir else {
                    val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "infeRead")
                    if (!dir.exists()) dir.mkdirs()
                    dir
                }
                
                val onProgress: (Int) -> Unit = { p -> _conversionProgress.value = p }
                val checkPause: suspend () -> Unit = {
                    while (_isConversionPaused.value && kotlinx.coroutines.currentCoroutineContext().isActive) {
                        kotlinx.coroutines.delay(500)
                    }
                }
                
                when (format) {
                    "PDF" -> {
                        val pdfFile = java.io.File(outDir, "$baseName.pdf")
                        com.infer.inferead.utils.CodeConverter.convertToPdf(context, text, pdfFile, onProgress, checkPause)
                        outputFiles.add(pdfFile)
                    }
                    "TXT" -> {
                        val txtFile = java.io.File(outDir, "$baseName.txt")
                        java.io.File(file.filePath).copyTo(txtFile, overwrite = true)
                        onProgress(100)
                        outputFiles.add(txtFile)
                    }
                    "IMG(JPG)" -> {
                        val files = com.infer.inferead.utils.CodeConverter.convertToImages(context, text, outDir, baseName, onProgress, checkPause)
                        outputFiles.addAll(files)
                    }
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (isShare && outputFiles.isNotEmpty()) {
                        val uris = outputFiles.map { 
                            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) 
                        }
                        val intent = if (uris.size == 1) {
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = if (format == "PDF") "application/pdf" else if (format == "TXT") "text/plain" else "image/jpeg"
                                putExtra(android.content.Intent.EXTRA_STREAM, uris.first())
                            }
                        } else {
                            android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "image/jpeg"
                                putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
                            }
                        }
                        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(android.content.Intent.createChooser(intent, "Share File").apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } else if (!isShare && outputFiles.isNotEmpty()) {
                        android.widget.Toast.makeText(context, "Saved to Downloads/infeRead", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    e.printStackTrace()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Conversion Failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                _isConverting.value = false
                _convertingFileName.value = null
            }
        }
    }

    // Bookshelf Operations
    fun createBookshelf(name: String, colorHex: String, autoAddFileId: Int? = null) {
        viewModelScope.launch {
            val id = repository.createBookshelf(name, colorHex)
            if (autoAddFileId != null) {
                repository.addFileToBookshelf(id.toInt(), autoAddFileId)
            }
        }
    }

    fun renameBookshelf(id: Int, name: String) {
        viewModelScope.launch {
            repository.renameBookshelf(id, name)
        }
    }

    fun updateBookshelfColor(id: Int, colorHex: String) {
        viewModelScope.launch {
            repository.updateBookshelfColor(id, colorHex)
        }
    }

    fun deleteBookshelf(id: Int) {
        viewModelScope.launch {
            repository.deleteBookshelf(id)
        }
    }

    fun updateBookshelfMinimised(id: Int, isMinimised: Boolean) {
        viewModelScope.launch {
            repository.updateBookshelfMinimised(id, isMinimised)
        }
    }

    fun setAllBookshelvesMinimised(isMinimised: Boolean) {
        viewModelScope.launch {
            bookshelves.value.forEach { shelf ->
                repository.updateBookshelfMinimised(shelf.id, isMinimised)
            }
        }
    }

    fun updateBookshelvesOrder(bookshelves: List<com.infer.inferead.data.Bookshelf>) {
        viewModelScope.launch {
            repository.updateBookshelvesOrder(bookshelves)
        }
    }

    fun addFileToBookshelf(bookshelfId: Int, fileId: Int) {
        viewModelScope.launch {
            repository.addFileToBookshelf(bookshelfId, fileId)
        }
    }

    fun removeFileFromBookshelf(bookshelfId: Int, fileId: Int) {
        viewModelScope.launch {
            repository.removeFileFromBookshelf(bookshelfId, fileId)
        }
    }

    fun updateBookshelfItemsOrder(items: List<com.infer.inferead.data.BookshelfItem>) {
        viewModelScope.launch {
            repository.updateBookshelfItemsOrder(items)
        }
    }

    suspend fun getFileInfo(fileId: Int): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            val file = dao.getLibraryFileById(fileId) ?: return@withContext emptyMap()
            val annotations = dao.getAnnotations(fileId).firstOrNull() ?: emptyList()
            
            val hasHighlights = annotations.any { it.colorHex.isNotEmpty() }
            val hasComments = annotations.any { it.textComment.isNotEmpty() }
            val bookmarkCount = if (file.isBookmarked) 1 else 0
            
            mapOf(
                "title" to file.title,
                "format" to file.format,
                "path" to file.filePath,
                "addedAt" to file.addedAt,
                "hasHighlights" to hasHighlights,
                "hasComments" to hasComments,
                "bookmarks" to bookmarkCount,
                "isToRead" to file.isToRead,
                "isFinished" to file.isFinished,
                "fileSize" to java.io.File(file.filePath).length()
            )
        }
    }
}

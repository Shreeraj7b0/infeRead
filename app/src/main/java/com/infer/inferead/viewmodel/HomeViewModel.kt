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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = InfeReadDatabase.getDatabase(application).infeReadDao()
    private val repository = FileRepository(application, dao)

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

    private var conversionJob: Job? = null

    suspend fun importFile(uri: Uri): Int? {
        val fileId = repository.importFile(uri)
        return fileId?.toInt()
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
            repository.markFinished(fileId, isFinished)
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
        viewModelScope.launch {
            dao.updateChecklistItem(item)
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
                val file = java.io.File(downloadsDir, "${checklist.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.pdf")
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
}

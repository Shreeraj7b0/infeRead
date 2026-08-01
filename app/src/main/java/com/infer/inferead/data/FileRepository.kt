package com.infer.inferead.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream

class FileRepository(private val context: Context, private val dao: InfeReadDao) {
    suspend fun linkFile(filePath: String): Long? = withContext(Dispatchers.IO) {
        try {
            val permanentFile = File(filePath)
            if (!permanentFile.exists()) return@withContext null

            val fileName = permanentFile.name
            var format = when {
                fileName.endsWith(".pdf", true) -> "PDF"
                fileName.endsWith(".md", true) || fileName.endsWith(".py", true) || fileName.endsWith(".c", true) || fileName.endsWith(".cpp", true) || fileName.endsWith(".java", true) || fileName.endsWith(".js", true) || fileName.endsWith(".css", true) || fileName.endsWith(".html", true) || fileName.endsWith(".xml", true) || fileName.endsWith(".json", true) -> "CODING"
                fileName.endsWith(".txt", true) || fileName.endsWith(".doc", true) || fileName.endsWith(".docx", true) -> "TXT"
                fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) || fileName.endsWith(".png", true) || fileName.endsWith(".heic", true) || fileName.endsWith(".heif", true) || fileName.endsWith(".webp", true) || fileName.endsWith(".svg", true) -> "IMAGE"
                fileName.endsWith(".epub", true) -> "EPUB"
                fileName.endsWith(".cbz", true) || fileName.endsWith(".zip", true) -> "CBZ"
                fileName.endsWith(".cbr", true) || fileName.endsWith(".rar", true) -> "CBR"
                fileName.endsWith(".cb7", true) || fileName.endsWith(".7z", true) -> "CB7"
                else -> "UNKNOWN"
            }

            val autoThumbnailUri = com.infer.inferead.utils.ThumbnailGenerator.generateThumbnail(context, format, physicalFile = permanentFile)

            val libraryFile = LibraryFile(
                title = fileName.substringBeforeLast("."),
                filePath = permanentFile.absolutePath,
                format = format,
                thumbnailUri = autoThumbnailUri,
                addedAt = System.currentTimeMillis()
            )
            return@withContext dao.insertLibraryFile(libraryFile)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }



    suspend fun importFile(uri: Uri): Long? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            var fileName = "Unknown Document"
            
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }

            val mimeType = contentResolver.getType(uri) ?: ""
            var format = when {
                mimeType.contains("pdf") || fileName.endsWith(".pdf", true) -> "PDF"
                fileName.endsWith(".md", true) || fileName.endsWith(".py", true) || fileName.endsWith(".c", true) || fileName.endsWith(".cpp", true) || fileName.endsWith(".java", true) || fileName.endsWith(".js", true) || fileName.endsWith(".css", true) || fileName.endsWith(".html", true) || fileName.endsWith(".xml", true) || fileName.endsWith(".json", true) -> "CODING"
                mimeType.contains("text") || fileName.endsWith(".txt", true) || fileName.endsWith(".doc", true) || fileName.endsWith(".docx", true) || mimeType.contains("msword") || mimeType.contains("wordprocessingml") -> "TXT"
                (mimeType.contains("image") && !fileName.endsWith(".cbz", true)) || fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) || fileName.endsWith(".png", true) || fileName.endsWith(".heic", true) || fileName.endsWith(".heif", true) || fileName.endsWith(".webp", true) || fileName.endsWith(".bmp", true) || fileName.endsWith(".svg", true) -> "IMAGE"
                mimeType.contains("epub") || fileName.endsWith(".epub", true) -> "EPUB"
                mimeType.contains("cbz") || mimeType.contains("zip") || fileName.endsWith(".cbz", true) || fileName.endsWith(".zip", true) -> "CBZ"
                fileName.endsWith(".cbr", true) || fileName.endsWith(".rar", true) -> "CBR"
                fileName.endsWith(".cb7", true) || fileName.endsWith(".7z", true) -> "CB7"
                else -> "UNKNOWN"
            }

            var permanentFile: File? = null
            var isLinked = false
            
            if (uri.scheme == "file") {
                val file = File(uri.path!!)
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val infeReadDir = File(downloadsDir, "infeRead")
                if (file.absolutePath.startsWith(infeReadDir.absolutePath)) {
                    permanentFile = file
                    isLinked = true
                }
            }
            
            if (!isLinked) {
                val internalLibDir = File(context.filesDir, "library")
                if (!internalLibDir.exists()) internalLibDir.mkdirs()
                permanentFile = File(internalLibDir, "${System.currentTimeMillis()}_$fileName")
                
                val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
                val outputStream = FileOutputStream(permanentFile)
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            // permanentFile is guaranteed to be non-null here
            val targetFile = permanentFile!!

            if (format == "UNKNOWN" || format == "CBZ") {
                try {
                    var hasContainerXml = false
                    java.util.zip.ZipInputStream(java.io.FileInputStream(targetFile)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name.equals("META-INF/container.xml", true)) {
                                hasContainerXml = true
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                    if (hasContainerXml) {
                        format = "EPUB"
                    } else if (format == "UNKNOWN") {
                        var hasImages = false
                        java.util.zip.ZipInputStream(java.io.FileInputStream(permanentFile)).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory && (entry.name.endsWith(".jpg", true) || entry.name.endsWith(".jpeg", true) || entry.name.endsWith(".png", true) || entry.name.endsWith(".webp", true))) {
                                    hasImages = true
                                    break
                                }
                                entry = zis.nextEntry
                            }
                        }
                        if (hasImages) format = "CBZ"
                    }
                } catch (e: Exception) {
                    // Not a zip file or unreadable, leave format as is
                }
            }

            val autoThumbnailUri = com.infer.inferead.utils.ThumbnailGenerator.generateThumbnail(context, format, physicalFile = targetFile)

            val libraryFile = LibraryFile(
                title = fileName.substringBeforeLast("."),
                filePath = targetFile.absolutePath,
                format = format,
                thumbnailUri = autoThumbnailUri,
                addedAt = System.currentTimeMillis()
            )
            return@withContext dao.insertLibraryFile(libraryFile)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun saveThumbnail(fileId: Int, imageUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(imageUri) ?: return@withContext null
            val internalDir = File(context.filesDir, "thumbnails")
            if (!internalDir.exists()) internalDir.mkdirs()
            
            val destFile = File(internalDir, "thumb_${fileId}_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(destFile)
            
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            
            val uriStr = destFile.absolutePath
            dao.updateThumbnail(fileId, uriStr)
            return@withContext uriStr
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun deleteFile(fileId: Int) = withContext(Dispatchers.IO) {
        val file = dao.getLibraryFileById(fileId)
        if (file != null) {
            try {
                // Only delete the physical file if it lives inside the app's internal sandbox
                val internalLibDir = File(context.filesDir, "library").canonicalPath
                val isInternalFile = !file.filePath.startsWith("content://") &&
                    File(file.filePath).canonicalPath.startsWith(internalLibDir)
                if (isInternalFile) {
                    val physicalFile = File(file.filePath)
                    if (physicalFile.exists()) {
                        physicalFile.delete()
                    }
                }
                // Always clean up the thumbnail (it's always internal)
                if (file.thumbnailUri != null) {
                    val thumbFile = File(file.thumbnailUri)
                    if (thumbFile.exists()) {
                        thumbFile.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        dao.deleteFile(fileId)
    }

    /**
     * Links a file using a persistent content:// URI granted by the system.
     * No file is copied — the URI itself is stored as the file path.
     * Android tracks the document via document ID, so the link survives
     * even if the user moves the file within the same storage provider.
     */
    suspend fun linkFileFromUri(uri: Uri): Long? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            // Persist the permission so it survives app restarts
            try {
                contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // May already be persisted, or not grantable — continue anyway
            }

            var fileName = "Unknown Document"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                }
            }

            val mimeType = contentResolver.getType(uri) ?: ""
            val format = when {
                mimeType.contains("pdf") || fileName.endsWith(".pdf", true) -> "PDF"
                fileName.endsWith(".md", true) || fileName.endsWith(".py", true) ||
                    fileName.endsWith(".c", true) || fileName.endsWith(".cpp", true) || fileName.endsWith(".java", true) ||
                    fileName.endsWith(".js", true) || fileName.endsWith(".css", true) || fileName.endsWith(".html", true) ||
                    fileName.endsWith(".xml", true) || fileName.endsWith(".json", true) -> "CODING"
                mimeType.contains("text") || fileName.endsWith(".txt", true) ||
                    fileName.endsWith(".doc", true) || fileName.endsWith(".docx", true) ||
                    mimeType.contains("msword") || mimeType.contains("wordprocessingml") -> "TXT"
                (mimeType.contains("image") && !fileName.endsWith(".cbz", true)) ||
                    fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) ||
                    fileName.endsWith(".png", true) || fileName.endsWith(".webp", true) ||
                    fileName.endsWith(".heic", true) || fileName.endsWith(".heif", true) ||
                    fileName.endsWith(".bmp", true) || fileName.endsWith(".svg", true) -> "IMAGE"
                mimeType.contains("epub") || fileName.endsWith(".epub", true) -> "EPUB"
                mimeType.contains("cbz") || mimeType.contains("zip") ||
                    fileName.endsWith(".cbz", true) || fileName.endsWith(".zip", true) -> "CBZ"
                fileName.endsWith(".cbr", true) || fileName.endsWith(".rar", true) -> "CBR"
                fileName.endsWith(".cb7", true) || fileName.endsWith(".7z", true) -> "CB7"
                else -> "UNKNOWN"
            }

            // Generate thumbnail by streaming from the URI
            val autoThumbnailUri = com.infer.inferead.utils.ThumbnailGenerator.generateThumbnail(context, format, fileUri = uri)

            val libraryFile = LibraryFile(
                title = fileName.substringBeforeLast("."),
                filePath = uri.toString(), // Store the content:// URI string
                format = format,
                thumbnailUri = autoThumbnailUri,
                addedAt = System.currentTimeMillis()
            )
            return@withContext dao.insertLibraryFile(libraryFile)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun renameFile(fileId: Int, newTitle: String) = withContext(Dispatchers.IO) {
        val file = dao.getLibraryFileById(fileId) ?: return@withContext
        val oldFile = File(file.filePath)
        if (oldFile.exists()) {
            val extension = oldFile.extension
            val parent = oldFile.parentFile
            val prefix = oldFile.name.substringBefore("_")
            val newName = if (prefix.toLongOrNull() != null) {
                "${prefix}_$newTitle" + if (extension.isNotEmpty()) ".$extension" else ""
            } else {
                newTitle + if (extension.isNotEmpty()) ".$extension" else ""
            }
            val newFile = File(parent, newName)
            if (oldFile.renameTo(newFile)) {
                dao.updateFilePath(fileId, newFile.absolutePath)
            }
        }
        dao.renameFile(fileId, newTitle)
    }

    suspend fun relinkFile(fileId: Int, newUri: Uri) = withContext(Dispatchers.IO) {
        val file = dao.getLibraryFileById(fileId) ?: return@withContext
        val format = file.format
        var autoThumbnailUri: String? = null
        val contentResolver = context.contentResolver

        val canPersist = try {
            contentResolver.takePersistableUriPermission(
                newUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (e: SecurityException) {
            false
        }

        val finalPath: String

        if (canPersist) {
            finalPath = newUri.toString()
            autoThumbnailUri = com.infer.inferead.utils.ThumbnailGenerator.generateThumbnail(context, format, fileUri = newUri)
        } else {
            val internalLibDir = File(context.filesDir, "library")
            if (!internalLibDir.exists()) internalLibDir.mkdirs()
            
            val extension = when(format) {
                "PDF" -> ".pdf"
                "EPUB" -> ".epub"
                "TXT" -> ".txt"
                "CODING" -> ".txt"
                "CBZ" -> ".cbz"
                "CBR" -> ".cbr"
                "CB7" -> ".cb7"
                "IMAGE" -> ".jpg"
                else -> ""
            }
            val permanentFile = File(internalLibDir, "${System.currentTimeMillis()}_${file.title.replace(" ", "_")}$extension")

            try {
                contentResolver.openInputStream(newUri)?.use { input ->
                    FileOutputStream(permanentFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                autoThumbnailUri = com.infer.inferead.utils.ThumbnailGenerator.generateThumbnail(context, format, physicalFile = permanentFile)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            finalPath = permanentFile.absolutePath
        }

        dao.updateFilePath(fileId, finalPath)
        if (autoThumbnailUri != null) {
            dao.updateThumbnail(fileId, autoThumbnailUri)
        }
    }

    suspend fun markFinished(fileId: Int, isFinished: Boolean, finishedAt: Long) = withContext(Dispatchers.IO) {
        dao.markFinished(fileId, isFinished, finishedAt)
    }

    suspend fun updateRating(fileId: Int, rating: Int) = withContext(Dispatchers.IO) {
        dao.updateRating(fileId, rating)
    }

    suspend fun logReadingSession(durationMinutes: Int) = withContext(Dispatchers.IO) {
        val session = ReadingSession(date = System.currentTimeMillis(), durationMinutes = durationMinutes)
        dao.insertReadingSession(session)
    }

    

    

    // Bookshelf operations
    fun getAllBookshelves() = dao.getAllBookshelves()
    fun getAllBookshelfItems() = dao.getAllBookshelfItems()

    suspend fun createBookshelf(name: String, colorHex: String): Long = withContext(Dispatchers.IO) {
        val count = dao.getAllBookshelves().first().size
        dao.insertBookshelf(Bookshelf(name = name, colorHex = colorHex, sortOrder = count))
    }

    suspend fun renameBookshelf(id: Int, name: String) = withContext(Dispatchers.IO) {
        dao.renameBookshelf(id, name)
    }

    suspend fun updateBookshelfColor(id: Int, colorHex: String) = withContext(Dispatchers.IO) {
        dao.updateBookshelfColor(id, colorHex)
    }

    suspend fun deleteBookshelf(id: Int) = withContext(Dispatchers.IO) {
        dao.deleteBookshelf(id)
        dao.clearBookshelfItems(id)
    }

    suspend fun updateBookshelfMinimised(id: Int, isMinimised: Boolean) = withContext(Dispatchers.IO) {
        dao.updateBookshelfMinimised(id, isMinimised)
    }

    suspend fun updateBookshelvesOrder(bookshelves: List<Bookshelf>) = withContext(Dispatchers.IO) {
        bookshelves.forEachIndexed { index, shelf ->
            dao.updateBookshelfSortOrder(shelf.id, index)
        }
    }

    suspend fun addFileToBookshelf(bookshelfId: Int, fileId: Int) = withContext(Dispatchers.IO) {
        val items = dao.getAllBookshelfItems().first().filter { it.bookshelfId == bookshelfId }
        val maxSort = items.maxOfOrNull { it.sortOrder } ?: -1
        dao.insertBookshelfItem(BookshelfItem(bookshelfId = bookshelfId, fileId = fileId, sortOrder = maxSort + 1))
    }

    suspend fun removeFileFromBookshelf(bookshelfId: Int, fileId: Int) = withContext(Dispatchers.IO) {
        dao.deleteBookshelfItemByFile(bookshelfId, fileId)
    }

    suspend fun updateBookshelfItemsOrder(items: List<BookshelfItem>) = withContext(Dispatchers.IO) {
        items.forEachIndexed { index, item ->
            dao.updateBookshelfItemSortOrder(item.id, index)
        }
    }
}

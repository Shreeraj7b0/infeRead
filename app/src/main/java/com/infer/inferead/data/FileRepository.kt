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
                fileName.endsWith(".md", true) || fileName.endsWith(".py", true) || fileName.endsWith(".c", true) || fileName.endsWith(".java", true) || fileName.endsWith(".js", true) || fileName.endsWith(".css", true) -> "CODING"
                mimeType.contains("text") || fileName.endsWith(".txt", true) || fileName.endsWith(".doc", true) || fileName.endsWith(".docx", true) || mimeType.contains("msword") || mimeType.contains("wordprocessingml") -> "TXT"
                (mimeType.contains("image") && !fileName.endsWith(".cbz", true)) || fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) || fileName.endsWith(".png", true) || fileName.endsWith(".heic", true) || fileName.endsWith(".heif", true) || fileName.endsWith(".webp", true) || fileName.endsWith(".bmp", true) || fileName.endsWith(".svg", true) -> "IMAGE"
                mimeType.contains("epub") || fileName.endsWith(".epub", true) -> "EPUB"
                mimeType.contains("cbz") || mimeType.contains("zip") || fileName.endsWith(".cbz", true) || fileName.endsWith(".zip", true) -> "CBZ"
                fileName.endsWith(".cbr", true) || fileName.endsWith(".rar", true) -> "CBR"
                fileName.endsWith(".cb7", true) || fileName.endsWith(".7z", true) -> "CB7"
                else -> "UNKNOWN"
            }

            val internalLibDir = File(context.filesDir, "library")
            if (!internalLibDir.exists()) internalLibDir.mkdirs()
            val permanentFile = File(internalLibDir, "${System.currentTimeMillis()}_$fileName")
            
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
            val outputStream = FileOutputStream(permanentFile)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            if (format == "UNKNOWN" || format == "CBZ") {
                try {
                    var hasContainerXml = false
                    java.util.zip.ZipInputStream(java.io.FileInputStream(permanentFile)).use { zis ->
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

            var autoThumbnailUri: String? = null
            try {
                if (format == "IMAGE") {
                    val pfd = contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val thumbDir = File(context.filesDir, "thumbnails")
                        if (!thumbDir.exists()) thumbDir.mkdirs()
                        val thumbFile = File(thumbDir, "thumb_${System.currentTimeMillis()}.jpg")
                        val options = android.graphics.BitmapFactory.Options()
                        options.inJustDecodeBounds = true
                        android.graphics.BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
                        options.inSampleSize = calculateInSampleSize(options, 300, 400)
                        options.inJustDecodeBounds = false
                        val bitmap = android.graphics.BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
                        if (bitmap != null) {
                            FileOutputStream(thumbFile).use { out ->
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                            }
                            autoThumbnailUri = thumbFile.absolutePath
                        }
                        pfd.close()
                    }
                } else if (format == "PDF") {
                    val pfd = android.os.ParcelFileDescriptor.open(permanentFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = android.graphics.pdf.PdfRenderer(pfd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val bitmap = android.graphics.Bitmap.createBitmap(
                            (page.width * 1.5f).toInt().coerceAtMost(800),
                            (page.height * 1.5f).toInt().coerceAtMost(1200),
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        renderer.close()
                        pfd.close()
                        
                        val thumbDir = File(context.filesDir, "thumbnails")
                        if (!thumbDir.exists()) thumbDir.mkdirs()
                        val thumbFile = File(thumbDir, "thumb_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(thumbFile).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                        }
                        autoThumbnailUri = thumbFile.absolutePath
                    }
                } else if (format == "EPUB") {
                    val extractDir = File(context.cacheDir, "epub_${System.currentTimeMillis()}")
                    if (com.infer.inferead.utils.ArchiveExtractor.extractArchive(permanentFile.absolutePath, extractDir, "EPUB")) {
                        val epubBook = com.infer.inferead.utils.EpubParser.parseEpub(extractDir)
                        if (epubBook?.coverImagePath != null) {
                            val thumbDir = File(context.filesDir, "thumbnails")
                            if (!thumbDir.exists()) thumbDir.mkdirs()
                            val thumbFile = File(thumbDir, "thumb_${System.currentTimeMillis()}.jpg")
                            
                            val options = android.graphics.BitmapFactory.Options()
                            options.inJustDecodeBounds = true
                            android.graphics.BitmapFactory.decodeFile(epubBook.coverImagePath, options)
                            options.inSampleSize = calculateInSampleSize(options, 300, 400)
                            options.inJustDecodeBounds = false
                            val bitmap = android.graphics.BitmapFactory.decodeFile(epubBook.coverImagePath, options)
                            if (bitmap != null) {
                                FileOutputStream(thumbFile).use { out ->
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                                }
                                autoThumbnailUri = thumbFile.absolutePath
                            }
                        }
                    }
                } else if (format == "CBZ" || format == "CBR" || format == "CB7") {
                    val extractDir = File(context.cacheDir, "comic_${System.currentTimeMillis()}")
                    if (com.infer.inferead.utils.ArchiveExtractor.extractArchive(permanentFile.absolutePath, extractDir, format)) {
                        val images = extractDir.walkTopDown().filter { it.isFile && (it.extension.equals("jpg", true) || it.extension.equals("jpeg", true) || it.extension.equals("png", true) || it.extension.equals("webp", true)) }.sortedBy { it.name }.toList()
                        if (images.isNotEmpty()) {
                            for (image in images) {
                                val coverImagePath = image.absolutePath
                                val thumbDir = File(context.filesDir, "thumbnails")
                                if (!thumbDir.exists()) thumbDir.mkdirs()
                                val thumbFile = File(thumbDir, "thumb_${System.currentTimeMillis()}.jpg")
                                
                                val options = android.graphics.BitmapFactory.Options()
                                options.inJustDecodeBounds = true
                                android.graphics.BitmapFactory.decodeFile(coverImagePath, options)
                                
                                if (options.outWidth > 0 && options.outHeight > 0) {
                                    options.inSampleSize = calculateInSampleSize(options, 300, 400)
                                    options.inJustDecodeBounds = false
                                    val bitmap = android.graphics.BitmapFactory.decodeFile(coverImagePath, options)
                                    if (bitmap != null) {
                                        FileOutputStream(thumbFile).use { out ->
                                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                                        }
                                        autoThumbnailUri = thumbFile.absolutePath
                                        break // Successfully created thumbnail
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

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
        dao.deleteFile(fileId)
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

            if (format == "IMAGE") {
                val pfd = contentResolver.openFileDescriptor(newUri, "r")
                if (pfd != null) {
                    val thumbDir = File(context.filesDir, "thumbnails")
                    if (!thumbDir.exists()) thumbDir.mkdirs()
                    val thumbFile = File(thumbDir, "thumb_${System.currentTimeMillis()}.jpg")
                    val options = android.graphics.BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    android.graphics.BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
                    options.inSampleSize = calculateInSampleSize(options, 300, 400)
                    options.inJustDecodeBounds = false
                    val bitmap = android.graphics.BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
                    if (bitmap != null) {
                        FileOutputStream(thumbFile).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                        }
                        autoThumbnailUri = thumbFile.absolutePath
                    }
                    pfd.close()
                }
            } else if (format == "PDF" || format == "EPUB" || format == "CBZ" || format == "CBR" || format == "CB7") {
                if (format == "PDF") {
                    val pfd = android.os.ParcelFileDescriptor.open(permanentFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = android.graphics.pdf.PdfRenderer(pfd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val bitmap = android.graphics.Bitmap.createBitmap(
                            (page.width * 1.5f).toInt().coerceAtMost(800),
                            (page.height * 1.5f).toInt().coerceAtMost(1200),
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        renderer.close()
                        pfd.close()
                        
                        val thumbDir = File(context.filesDir, "thumbnails")
                        if (!thumbDir.exists()) thumbDir.mkdirs()
                        val thumbFile = File(thumbDir, "thumb_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(thumbFile).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                        }
                        autoThumbnailUri = thumbFile.absolutePath
                    }
                } else if (format == "EPUB") {
                    val extractDir = File(context.cacheDir, "epub_${System.currentTimeMillis()}")
                    if (com.infer.inferead.utils.ArchiveExtractor.extractArchive(permanentFile.absolutePath, extractDir, "EPUB")) {
                        val epubBook = com.infer.inferead.utils.EpubParser.parseEpub(extractDir)
                        if (epubBook?.coverImagePath != null) {
                            val thumbDir = File(context.filesDir, "thumbnails")
                            if (!thumbDir.exists()) thumbDir.mkdirs()
                            val thumbFile = File(thumbDir, "thumb_${System.currentTimeMillis()}.jpg")
                            
                            val options = android.graphics.BitmapFactory.Options()
                            options.inJustDecodeBounds = true
                            android.graphics.BitmapFactory.decodeFile(epubBook.coverImagePath, options)
                            options.inSampleSize = calculateInSampleSize(options, 300, 400)
                            options.inJustDecodeBounds = false
                            val bitmap = android.graphics.BitmapFactory.decodeFile(epubBook.coverImagePath, options)
                            if (bitmap != null) {
                                FileOutputStream(thumbFile).use { out ->
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                                }
                                autoThumbnailUri = thumbFile.absolutePath
                            }
                        }
                    }
                } else {
                    val extractDir = File(context.cacheDir, "comic_${System.currentTimeMillis()}")
                    if (com.infer.inferead.utils.ArchiveExtractor.extractArchive(permanentFile.absolutePath, extractDir, format)) {
                        val images = extractDir.walkTopDown().filter { it.isFile && (it.extension.equals("jpg", true) || it.extension.equals("jpeg", true) || it.extension.equals("png", true) || it.extension.equals("webp", true)) }.sortedBy { it.name }.toList()
                        if (images.isNotEmpty()) {
                            val coverImagePath = images.first().absolutePath
                            val thumbDir = File(context.filesDir, "thumbnails")
                            if (!thumbDir.exists()) thumbDir.mkdirs()
                            val thumbFile = File(thumbDir, "thumb_${System.currentTimeMillis()}.jpg")
                            
                            val options = android.graphics.BitmapFactory.Options()
                            options.inJustDecodeBounds = true
                            android.graphics.BitmapFactory.decodeFile(coverImagePath, options)
                            
                            if (options.outWidth > 0 && options.outHeight > 0) {
                                options.inSampleSize = calculateInSampleSize(options, 300, 400)
                                options.inJustDecodeBounds = false
                                val bitmap = android.graphics.BitmapFactory.decodeFile(coverImagePath, options)
                                if (bitmap != null) {
                                    FileOutputStream(thumbFile).use { out ->
                                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                                    }
                                    autoThumbnailUri = thumbFile.absolutePath
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        dao.updateFilePath(fileId, permanentFile.absolutePath)
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

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
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

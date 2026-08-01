package com.infer.inferead.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import com.github.junrar.Archive
import org.apache.commons.compress.archivers.sevenz.SevenZFile

object ThumbnailGenerator {

    private const val MAX_THUMB_WIDTH = 400
    private const val MAX_THUMB_HEIGHT = 600

    suspend fun generateThumbnail(context: Context, format: String, fileUri: Uri? = null, physicalFile: File? = null): String? {
        try {
            return when (format) {
                "EPUB" -> generateEpubThumbnail(context, fileUri, physicalFile)
                "PDF" -> generatePdfThumbnail(context, fileUri, physicalFile)
                "CBZ", "CBR", "CB7" -> generateComicThumbnail(context, format, fileUri, physicalFile)
                "IMAGE" -> generateImageThumbnail(context, fileUri, physicalFile)
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private suspend fun generateEpubThumbnail(context: Context, fileUri: Uri?, physicalFile: File?): String? {
        val extractDir = File(context.cacheDir, "epub_thumb_${System.currentTimeMillis()}")
        var tempEpub: File? = null
        try {
            val sourcePath = if (physicalFile != null) {
                physicalFile.absolutePath
            } else if (fileUri != null) {
                tempEpub = File(context.cacheDir, "tmp_link_${System.currentTimeMillis()}.epub")
                context.contentResolver.openInputStream(fileUri)?.use { input ->
                    FileOutputStream(tempEpub).use { out -> input.copyTo(out) }
                }
                tempEpub.absolutePath
            } else return null

            if (ArchiveExtractor.extractArchive(sourcePath, extractDir, "EPUB")) {
                val epubBook = EpubParser.parseEpub(extractDir)
                if (epubBook?.coverImagePath != null) {
                    return compressAndSave(context, epubBook.coverImagePath)
                }
            }
        } finally {
            extractDir.deleteRecursively()
            tempEpub?.delete()
        }
        return null
    }

    private suspend fun generatePdfThumbnail(context: Context, fileUri: Uri?, physicalFile: File?): String? {
        val pfd = if (physicalFile != null) {
            ParcelFileDescriptor.open(physicalFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } else if (fileUri != null) {
            context.contentResolver.openFileDescriptor(fileUri, "r")
        } else return null

        pfd?.use { fd ->
            val renderer = PdfRenderer(fd)
            if (renderer.pageCount > 0) {
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(
                        (page.width * 1.5f).toInt().coerceAtMost(MAX_THUMB_WIDTH),
                        (page.height * 1.5f).toInt().coerceAtMost(MAX_THUMB_HEIGHT),
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    return saveBitmap(context, bitmap)
                }
            }
        }
        return null
    }

    private suspend fun generateComicThumbnail(context: Context, format: String, fileUri: Uri?, physicalFile: File?): String? {
        var tempComic: File? = null
        val targetFile = if (physicalFile != null) {
            physicalFile
        } else if (fileUri != null) {
            tempComic = File(context.cacheDir, "tmp_comic_${System.currentTimeMillis()}.${format.lowercase()}")
            context.contentResolver.openInputStream(fileUri)?.use { input ->
                FileOutputStream(tempComic).use { out -> input.copyTo(out) }
            }
            tempComic
        } else return null

        try {
            val images = mutableListOf<String>()
            when (format) {
                "CBZ" -> {
                    ZipFile(targetFile).use { zip ->
                        val e = zip.entries()
                        while (e.hasMoreElements()) {
                            val entry = e.nextElement()
                            if (!entry.isDirectory && isImageFile(entry.name)) {
                                images.add(entry.name)
                            }
                        }
                    }
                }
                "CBR" -> {
                    Archive(targetFile).use { archive ->
                        var header = archive.nextFileHeader()
                        while (header != null) {
                            if (!header.isDirectory && isImageFile(header.fileNameString)) {
                                images.add(header.fileNameString.trim())
                            }
                            header = archive.nextFileHeader()
                        }
                    }
                }
                "CB7" -> {
                    SevenZFile(targetFile).use { sevenZFile ->
                        var entry = sevenZFile.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && isImageFile(entry.name)) {
                                images.add(entry.name)
                            }
                            entry = sevenZFile.nextEntry
                        }
                    }
                }
            }
            images.sort()

            if (images.isNotEmpty()) {
                val imagePath = images.first()
                var bytes: ByteArray? = null
                when (format) {
                    "CBZ" -> bytes = ArchiveStreamer.getEntryBytes(targetFile, imagePath)
                    "CBR" -> {
                        Archive(targetFile).use { archive ->
                            var header = archive.nextFileHeader()
                            while (header != null) {
                                if (header.fileNameString.trim() == imagePath) {
                                    val out = java.io.ByteArrayOutputStream()
                                    archive.extractFile(header, out)
                                    bytes = out.toByteArray()
                                    break
                                }
                                header = archive.nextFileHeader()
                            }
                        }
                    }
                    "CB7" -> {
                        SevenZFile(targetFile).use { sevenZFile ->
                            var entry = sevenZFile.nextEntry
                            while (entry != null) {
                                if (entry.name == imagePath) {
                                    val content = ByteArray(entry.size.toInt())
                                    sevenZFile.read(content)
                                    bytes = content
                                    break
                                }
                                entry = sevenZFile.nextEntry
                            }
                        }
                    }
                }

                if (bytes != null) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        options.inSampleSize = calculateInSampleSize(options, MAX_THUMB_WIDTH, MAX_THUMB_HEIGHT)
                        options.inJustDecodeBounds = false
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        if (bitmap != null) {
                            return saveBitmap(context, bitmap)
                        }
                    }
                }
            }
        } finally {
            tempComic?.delete()
        }
        return null
    }

    private suspend fun generateImageThumbnail(context: Context, fileUri: Uri?, physicalFile: File?): String? {
        val pfd = if (physicalFile != null) {
            ParcelFileDescriptor.open(physicalFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } else if (fileUri != null) {
            context.contentResolver.openFileDescriptor(fileUri, "r")
        } else return null

        pfd?.use { fd ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(fd.fileDescriptor, null, options)
            options.inSampleSize = calculateInSampleSize(options, MAX_THUMB_WIDTH, MAX_THUMB_HEIGHT)
            options.inJustDecodeBounds = false
            val bitmap = BitmapFactory.decodeFileDescriptor(fd.fileDescriptor, null, options)
            if (bitmap != null) {
                return saveBitmap(context, bitmap)
            }
        }
        return null
    }

    private fun compressAndSave(context: Context, imagePath: String): String? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, options)
        options.inSampleSize = calculateInSampleSize(options, MAX_THUMB_WIDTH, MAX_THUMB_HEIGHT)
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeFile(imagePath, options) ?: return null
        return saveBitmap(context, bitmap)
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap): String {
        val thumbDir = File(context.filesDir, "thumbnails")
        if (!thumbDir.exists()) thumbDir.mkdirs()
        val thumbFile = File(thumbDir, "thumb_${System.currentTimeMillis()}.jpg")
        FileOutputStream(thumbFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        return thumbFile.absolutePath
    }

    private fun isImageFile(name: String): Boolean {
        return name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) || 
               name.endsWith(".png", true) || name.endsWith(".webp", true)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
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
}

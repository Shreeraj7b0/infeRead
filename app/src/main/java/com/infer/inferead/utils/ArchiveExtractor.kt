package com.infer.inferead.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import com.github.junrar.Archive
import org.apache.commons.compress.archivers.sevenz.SevenZFile

object ArchiveExtractor {
    suspend fun extractZip(zipFilePath: String, outputDir: File, extractMedia: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            } else {
                if (outputDir.listFiles()?.isNotEmpty() == true) {
                    return@withContext true // Already extracted
                }
            }

            ZipFile(zipFilePath).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val destFile = File(outputDir, entry.name)
                    val destDirPath = outputDir.canonicalPath
                    val destFilePath = destFile.canonicalPath
                    if (!destFilePath.startsWith(destDirPath + File.separator)) {
                        continue // Prevent Zip Slip vulnerability
                    }
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        val isMedia = destFile.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "mp3", "mp4", "m4a", "webm", "svg", "ttf", "otf", "woff", "woff2")
                        if (!extractMedia && isMedia) {
                            continue
                        }
                        destFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            BufferedOutputStream(FileOutputStream(destFile)).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun extractArchive(archivePath: String, outputDir: File, format: String, extractMedia: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        if (format == "EPUB" || format == "CBZ") return@withContext extractZip(archivePath, outputDir, extractMedia)
        try {
            if (!outputDir.exists()) outputDir.mkdirs()
            else if (outputDir.listFiles()?.isNotEmpty() == true) return@withContext true

            when (format) {
                "CBR" -> {
                    Archive(File(archivePath)).use { archive ->
                        var header = archive.nextFileHeader()
                        while (header != null) {
                            val destFile = File(outputDir, header.fileNameString.trim())
                                if (header.isDirectory) {
                                    destFile.mkdirs()
                                } else {
                                    val isMedia = destFile.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "mp3", "mp4", "m4a", "webm", "svg", "ttf", "otf", "woff", "woff2")
                                    if (!extractMedia && isMedia) {
                                        header = archive.nextFileHeader()
                                        continue
                                    }
                                    destFile.parentFile?.mkdirs()
                                    FileOutputStream(destFile).use { output ->
                                        archive.extractFile(header, output)
                                    }
                                }
                            header = archive.nextFileHeader()
                        }
                    }
                    true
                }
                "CB7" -> {
                    SevenZFile(File(archivePath)).use { sevenZFile ->
                        var entry = sevenZFile.nextEntry
                        while (entry != null) {
                            val destFile = File(outputDir, entry.name)
                            if (entry.isDirectory) {
                                destFile.mkdirs()
                            } else {
                                val isMedia = destFile.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "mp3", "mp4", "m4a", "webm", "svg", "ttf", "otf", "woff", "woff2")
                                if (!extractMedia && isMedia) {
                                    entry = sevenZFile.nextEntry
                                    continue
                                }
                                destFile.parentFile?.mkdirs()
                                val content = ByteArray(entry.size.toInt())
                                sevenZFile.read(content, 0, content.size)
                                FileOutputStream(destFile).use { it.write(content) }
                            }
                            entry = sevenZFile.nextEntry
                        }
                    }
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

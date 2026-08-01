package com.infer.inferead.utils

import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

object ArchiveStreamer {
    fun getEntryStream(archiveFile: File, entryPath: String): InputStream? {
        if (!archiveFile.exists() || !archiveFile.isFile) return null
        return try {
            val zipFile = ZipFile(archiveFile)
            val entry = zipFile.getEntry(entryPath)
            if (entry != null) {
                // Return a wrapped stream that closes the ZipFile when the stream is closed
                object : java.io.FilterInputStream(zipFile.getInputStream(entry)) {
                    override fun close() {
                        super.close()
                        zipFile.close()
                    }
                }
            } else {
                zipFile.close()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getEntryBytes(archiveFile: File, entryPath: String): ByteArray? {
        return getEntryStream(archiveFile, entryPath)?.use { it.readBytes() }
    }

    fun readTextEntry(archiveFile: File, entryPath: String): String? {
        return getEntryStream(archiveFile, entryPath)?.use { it.bufferedReader().readText() }
    }
}

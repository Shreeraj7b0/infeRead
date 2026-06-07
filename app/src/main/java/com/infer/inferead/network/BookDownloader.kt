package com.infer.inferead.network

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object BookDownloader {

    suspend fun downloadBook(context: Context, downloadUrl: String, fileName: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e("BookDownloader", "Server returned HTTP ${connection.responseCode}")
                    return@withContext null
                }

                // Make sure we have a valid documents directory for the app
                val dir = File(context.filesDir, "downloads")
                if (!dir.exists()) {
                    dir.mkdirs()
                }

                val targetFile = File(dir, fileName)
                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(targetFile)

                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                outputStream.close()
                inputStream.close()

                targetFile
            } catch (e: Exception) {
                Log.e("BookDownloader", "Failed to download book", e)
                null
            }
        }
    }
}

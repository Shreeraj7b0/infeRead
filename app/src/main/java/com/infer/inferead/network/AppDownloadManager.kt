package com.infer.inferead.network

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object AppDownloadManager {
    
    data class DownloadInfo(
        val id: Long,
        val url: String,
        val fileName: String,
        val progress: Int,
        val status: Int // DownloadManager.STATUS_*
    )

    private val _activeDownloads = MutableStateFlow<Map<Long, DownloadInfo>>(emptyMap())
    val activeDownloads: StateFlow<Map<Long, DownloadInfo>> = _activeDownloads.asStateFlow()

    private val _completedDownloads = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val completedDownloads: kotlinx.coroutines.flow.SharedFlow<String> = _completedDownloads.asSharedFlow()

    private var isPolling = false

    private var receiverRegistered = false

    fun startDownload(context: Context, url: String, fileName: String): Long {
        if (!receiverRegistered) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                        if (id != -1L) {
                            val downloadManager = context?.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                            val query = DownloadManager.Query().setFilterById(id)
                            val cursor = downloadManager?.query(query)
                            if (cursor != null && cursor.moveToFirst()) {
                                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                                if (statusIndex != -1 && uriIndex != -1) {
                                    val status = cursor.getInt(statusIndex)
                                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                        val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                                        if (titleIndex != -1) {
                                            val title = cursor.getString(titleIndex)
                                            val path = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "infeRead/$title").absolutePath
                                            GlobalScope.launch {
                                                _completedDownloads.emit(path)
                                            }
                                        }
                                    }
                                }
                                cursor.close()
                            }
                        }
                    }
                }
            }
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )
            receiverRegistered = true
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri).apply {
            setTitle(fileName)
            setDescription("Downloading book...")
            addRequestHeader("User-Agent", "Wget/1.21.2")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            // Save to Downloads/infeRead
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "infeRead/$fileName")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val downloadId = downloadManager.enqueue(request)
        
        val currentDownloads = _activeDownloads.value.toMutableMap()
        currentDownloads[downloadId] = DownloadInfo(downloadId, url, fileName, 0, DownloadManager.STATUS_PENDING)
        _activeDownloads.value = currentDownloads

        startPolling(context)
        return downloadId
    }

    fun cancelDownload(context: Context, downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.remove(downloadId)
        val currentDownloads = _activeDownloads.value.toMutableMap()
        currentDownloads.remove(downloadId)
        _activeDownloads.value = currentDownloads
    }

    private fun startPolling(context: Context) {
        if (isPolling) return
        isPolling = true
        
        GlobalScope.launch(Dispatchers.IO) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            while (_activeDownloads.value.isNotEmpty()) {
                val currentDownloads = _activeDownloads.value.toMutableMap()
                val ids = currentDownloads.keys.toLongArray()
                
                val query = DownloadManager.Query().setFilterById(*ids)
                val cursor = downloadManager.query(query)
                
                if (cursor != null) {
                    val toRemove = mutableListOf<Long>()
                    while (cursor.moveToNext()) {
                        val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        
                        if (idIndex != -1 && statusIndex != -1) {
                            val id = cursor.getLong(idIndex)
                            val status = cursor.getInt(statusIndex)
                            
                            var progress = 0
                            if (downloadedIndex != -1 && totalIndex != -1) {
                                val downloaded = cursor.getLong(downloadedIndex)
                                val total = cursor.getLong(totalIndex)
                                if (total > 0) {
                                    progress = ((downloaded * 100) / total).toInt()
                                }
                            }
                            
                            val info = currentDownloads[id]
                            if (info != null) {
                                currentDownloads[id] = info.copy(progress = progress, status = status)
                            }
                            
                            if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                                toRemove.add(id)
                            }
                        }
                    }
                    cursor.close()
                    
                    toRemove.forEach { 
                        currentDownloads.remove(it)
                    }
                }
                
                _activeDownloads.value = currentDownloads
                delay(1000)
            }
            isPolling = false
        }
    }
}

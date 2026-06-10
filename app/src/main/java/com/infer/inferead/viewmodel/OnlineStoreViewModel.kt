package com.infer.inferead.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infer.inferead.network.AppDownloadManager
import kotlinx.coroutines.flow.collectLatest
import com.infer.inferead.network.GutenbergApi
import com.infer.inferead.network.GutenbergBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnlineStoreViewModel(application: Application) : AndroidViewModel(application) {

    private val _books = MutableStateFlow<List<GutenbergBook>>(emptyList())
    val books = _books.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _currentSource = MutableStateFlow<String?>(null)
    val currentSource = _currentSource.asStateFlow()
    
    private val _hideWebUi = MutableStateFlow(false)
    val hideWebUi = _hideWebUi.asStateFlow()

    fun setHideWebUi(hide: Boolean) {
        _hideWebUi.value = hide
    }

    val activeDownloads = AppDownloadManager.activeDownloads

    private val _annasArchiveHostname = MutableStateFlow("https://annas-archive.gl/")
    val annasArchiveHostname = _annasArchiveHostname.asStateFlow()

    private val _isAnnasArchiveDefunct = MutableStateFlow(false)
    val isAnnasArchiveDefunct = _isAnnasArchiveDefunct.asStateFlow()

    init {
        searchBooks("")
        loadPrefs()
    }

    private fun loadPrefs() {
        val prefs = getApplication<Application>().getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
        _annasArchiveHostname.value = prefs.getString("annas_archive_host", "https://annas-archive.gl/") ?: "https://annas-archive.gl/"
        _isAnnasArchiveDefunct.value = prefs.getBoolean("is_annas_archive_defunct", false)
    }

    fun setAnnasArchiveHostname(hostname: String) {
        _annasArchiveHostname.value = hostname
        getApplication<Application>().getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
            .edit().putString("annas_archive_host", hostname).apply()
    }

    fun setAnnasArchiveDefunct(isDefunct: Boolean) {
        _isAnnasArchiveDefunct.value = isDefunct
        getApplication<Application>().getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("is_annas_archive_defunct", isDefunct).apply()
    }

    fun setSource(source: String?) {
        _currentSource.value = source
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun searchBooks(query: String) {
        _searchQuery.value = query
        if (_currentSource.value != "Project Gutenberg") return
        
        viewModelScope.launch {
            _isLoading.value = true
            _books.value = GutenbergApi.searchBooks(query)
            _isLoading.value = false
        }
    }

    fun downloadBook(book: GutenbergBook, homeViewModel: HomeViewModel) {
        val url = book.downloadUrl ?: return
        val fileName = book.title.replace(Regex("[^a-zA-Z0-9.\\-]"), "_") + ".epub"
        AppDownloadManager.startDownload(getApplication(), url, fileName)
    }

    fun downloadUrl(url: String, fileName: String, homeViewModel: HomeViewModel) {
        AppDownloadManager.startDownload(getApplication(), url, fileName)
    }
}

package com.infer.inferead.ui.screens

import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.infer.inferead.viewmodel.HomeViewModel
import com.infer.inferead.viewmodel.OnlineStoreViewModel
import com.infer.inferead.network.AppDownloadManager
import androidx.compose.material.icons.filled.Close
import android.app.DownloadManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineStoreTab(
    viewModel: OnlineStoreViewModel,
    homeViewModel: HomeViewModel
) {
    val context = LocalContext.current
    val currentSource by viewModel.currentSource.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val books by viewModel.books.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val hideWebUi by viewModel.hideWebUi.collectAsState()

    var expanded by remember { mutableStateOf(false) }
    val sources = listOf("Project Gutenberg", "Anna's Archive", "Internet Archive")
    val isWebViewSource = currentSource == "Anna's Archive" || currentSource == "Internet Archive"

    var isScrolling by remember { mutableStateOf(false) }
    var scrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()

    var showHostErrorDialog by remember { mutableStateOf(false) }
    var tempHostname by remember { mutableStateOf("") }
    val annasArchiveHostname by viewModel.annasArchiveHostname.collectAsState()
    val isAnnasArchiveDefunct by viewModel.isAnnasArchiveDefunct.collectAsState()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    if (showHostErrorDialog) {
        AlertDialog(
            onDismissRequest = { showHostErrorDialog = false },
            title = { Text("Host Unavailable") },
            text = {
                Column {
                    Text("Could not reach Anna's Archive. The hostname might have changed. Please enter the new hostname if you know it.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempHostname,
                        onValueChange = { tempHostname = it },
                        label = { Text("Hostname URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setAnnasArchiveHostname(tempHostname)
                    showHostErrorDialog = false
                    webViewRef?.loadUrl(tempHostname)
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showHostErrorDialog = false }) { Text("Cancel") }
                    TextButton(onClick = { 
                        viewModel.setAnnasArchiveDefunct(true)
                        viewModel.setSource("Project Gutenberg")
                        showHostErrorDialog = false
                    }) { Text("Host Unavailable", color = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentSource == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select a Source",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                sources.forEach { source ->
                    val isDefunct = source == "Anna's Archive" && isAnnasArchiveDefunct
                    val icon = when(source) {
                        "Project Gutenberg" -> androidx.compose.material.icons.Icons.Default.Search
                        "Anna's Archive" -> androidx.compose.material.icons.Icons.Default.Search
                        "Internet Archive" -> androidx.compose.material.icons.Icons.Default.Language
                        else -> androidx.compose.material.icons.Icons.Default.Language
                    }
                    @OptIn(ExperimentalMaterial3Api::class)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .height(120.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDefunct) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        onClick = {
                            if (!isDefunct) {
                                viewModel.setSource(source)
                                if (source == "Project Gutenberg") {
                                    viewModel.searchBooks(searchQuery)
                                }
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = if (isDefunct) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = source,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDefunct) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                            )
                            if (isDefunct) {
                                Text(
                                    text = "Currently Unavailable",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
            if (!isWebViewSource) {
                // Top Bar / Controls
                if (activeDownloads.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Active Downloads", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            activeDownloads.values.forEach { download ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(download.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                        LinearProgressIndicator(
                                            progress = download.progress / 100f,
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                        )
                                    }
                                    IconButton(onClick = { AppDownloadManager.cancelDownload(context, download.id) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search Books") },
                        modifier = Modifier
                            .weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        trailingIcon = {
                            IconButton(onClick = { 
                                if (currentSource == "Project Gutenberg") {
                                    viewModel.searchBooks(searchQuery)
                                } 
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { 
                            if (currentSource == "Project Gutenberg") {
                                viewModel.searchBooks(searchQuery)
                            } 
                        }),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box {
                        IconButton(
                            onClick = { expanded = true },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = "Source", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .width(200.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        ) {
                            sources.forEach { selectionOption ->
                                val isDefunct = selectionOption == "Anna's Archive" && isAnnasArchiveDefunct
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            text = selectionOption, 
                                            maxLines = 1, 
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (currentSource == selectionOption) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isDefunct) Color.Gray else if (currentSource == selectionOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        ) 
                                    },
                                    enabled = !isDefunct,
                                    onClick = {
                                        viewModel.setSource(selectionOption)
                                        expanded = false
                                        if (selectionOption == "Project Gutenberg") {
                                            viewModel.searchBooks(searchQuery)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            } // end of if

            if (isWebViewSource) {
                AndroidView(
                    factory = {
                        WebView(it).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            webViewClient = object : WebViewClient() {
                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        tempHostname = annasArchiveHostname
                                        showHostErrorDialog = true
                                    }
                                }
                            }
                            webChromeClient = WebChromeClient()
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                                Toast.makeText(it, "Downloading Book...", Toast.LENGTH_SHORT).show()
                                val fileName = "AnnasArchiveBook_" + System.currentTimeMillis() + 
                                    if (url.contains(".epub", ignoreCase = true)) ".epub" 
                                    else if (url.contains(".pdf", ignoreCase = true)) ".pdf" else ".epub"
                                viewModel.downloadUrl(url, fileName, homeViewModel)
                            }
                            
                            setOnScrollChangeListener { _, _, _, _, _ ->
                                isScrolling = true
                                scrollJob?.cancel()
                                scrollJob = scope.launch {
                                    kotlinx.coroutines.delay(500)
                                    isScrolling = false
                                }
                            }
                            
                            webViewRef = this
                            tag = currentSource
                            if (currentSource == "Anna's Archive") {
                                loadUrl(annasArchiveHostname)
                            } else if (currentSource == "Internet Archive") {
                                loadUrl("https://archive.org/details/texts")
                            }
                        }
                    },
                    update = { view ->
                        if (currentSource == "Anna's Archive" && view.tag != "Anna's Archive") {
                            view.tag = "Anna's Archive"
                            view.loadUrl(annasArchiveHostname)
                        } else if (currentSource == "Internet Archive" && view.tag != "Internet Archive") {
                            view.tag = "Internet Archive"
                            view.loadUrl("https://archive.org/details/texts")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Gutenberg Grid UI
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading...", style = MaterialTheme.typography.titleMedium)
                    }
                } else if (books.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No books found.")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(books) { book ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    if (book.coverUrl != null) {
                                        AsyncImage(
                                            model = book.coverUrl,
                                            contentDescription = "Cover",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                                .background(Color.Gray),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No Cover", color = Color.White)
                                        }
                                    }
                                    
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = book.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = book.author,
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        if (book.downloadUrl != null) {
                                            val isDownloading = activeDownloads.values.any { it.url == book.downloadUrl }
                                            Button(
                                                onClick = {
                                                    Toast.makeText(context, "Downloading ${book.title}...", Toast.LENGTH_SHORT).show()
                                                    viewModel.downloadBook(book, homeViewModel)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                contentPadding = PaddingValues(4.dp),
                                                enabled = !isDownloading
                                            ) {
                                                if (isDownloading) {
                                                    val progress = activeDownloads.values.find { it.url == book.downloadUrl }?.progress ?: 0
                                                    Text("Downloading ($progress%)...", fontSize = 12.sp, color = Color.White)
                                                } else {
                                                    Icon(Icons.Default.Download, contentDescription = "Download", modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Download", fontSize = 12.sp)
                                                }
                                            }
                                        } else {
                                            Text("No valid format", fontSize = 12.sp, color = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } // End of Column

        if (isWebViewSource) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !isScrolling && !hideWebUi,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                // Floating Back/Home Pill
                Surface(
                    modifier = Modifier.padding(top = 20.dp, start = 16.dp).height(48.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { webViewRef?.let { if (it.canGoBack()) it.goBack() } },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Go Back", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { 
                                webViewRef?.let { 
                                    if (currentSource == "Anna's Archive") {
                                        it.loadUrl(annasArchiveHostname)
                                    } else if (currentSource == "Internet Archive") {
                                        it.loadUrl("https://archive.org/details/texts")
                                    }
                                } 
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !isScrolling && !hideWebUi,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Box {
                    // Floating Source Button
                    FloatingActionButton(
                        onClick = { expanded = true },
                        modifier = Modifier.padding(top = 20.dp, end = 16.dp),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.Language, contentDescription = "Change Source")
                    }
        
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    ) {
                        sources.forEach { selectionOption ->
                            val isDefunct = selectionOption == "Anna's Archive" && isAnnasArchiveDefunct
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = selectionOption, 
                                        fontWeight = if (currentSource == selectionOption) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isDefunct) Color.Gray else if (currentSource == selectionOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    ) 
                                },
                                enabled = !isDefunct,
                                onClick = {
                                    viewModel.setSource(selectionOption)
                                    expanded = false
                                    if (selectionOption == "Project Gutenberg") {
                                        viewModel.searchBooks(searchQuery)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            }
        }
    } // End of Box
}

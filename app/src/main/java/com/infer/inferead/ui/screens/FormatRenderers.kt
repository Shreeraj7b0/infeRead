@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.infer.inferead.ui.screens

import com.infer.inferead.viewmodel.ContrastMode
import com.infer.inferead.viewmodel.ReaderSettings
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.Comment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.foundation.lazy.*

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import android.util.LruCache
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior

import androidx.compose.ui.composed
import kotlinx.coroutines.launch

fun Modifier.zoomable(
    onTransform: (Float, androidx.compose.ui.geometry.Offset, Boolean) -> Unit
) = composed {
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val animX = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val animY = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    var scale by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { 
            androidx.compose.ui.geometry.Offset(animX.value, animY.value) 
        }.collect { offset ->
            if (animX.isRunning || animY.isRunning) {
                onTransform(scale, offset, scale > 1.01f)
            }
        }
    }

    this.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            coroutineScope.launch { 
                animX.stop()
                animY.stop() 
            }
            val velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
            do {
                val event = awaitPointerEvent()
                val pointers = event.changes.size
                
                if (pointers > 1) {
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val centroid = event.calculateCentroid(useCurrent = false)
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    val isZoomed = scale > 1.01f
                    val size = this.size
                    val pivot = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                    val dX = centroid.x - pivot.x - animX.value
                    val dY = centroid.y - pivot.y - animY.value
                    
                    var newX = animX.value + pan.x - dX * (zoom - 1f)
                    var newY = animY.value + pan.y - dY * (zoom - 1f)
                    
                    val maxX = (size.width * (scale - 1f)) / 2f
                    val maxY = (size.height * (scale - 1f)) / 2f
                    
                    if (scale <= 1.0f) {
                        newX = 0f
                        newY = 0f
                    } else {
                        newX = newX.coerceIn(-maxX, maxX)
                        newY = newY.coerceIn(-maxY, maxY)
                    }
                    
                    coroutineScope.launch {
                        animX.snapTo(newX)
                        animY.snapTo(newY)
                        animX.updateBounds(-maxX, maxX)
                        animY.updateBounds(-maxY, maxY)
                    }
                    onTransform(scale, androidx.compose.ui.geometry.Offset(newX, newY), isZoomed)
                    event.changes.forEach { it.consume() }
                } else if (scale > 1.01f && pointers == 1) {
                    val change = event.changes.first()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    val pan = event.calculatePan()
                    
                    val size = this.size
                    val maxX = (size.width * (scale - 1f)) / 2f
                    val maxY = (size.height * (scale - 1f)) / 2f
                    
                    val newX = (animX.value + pan.x).coerceIn(-maxX, maxX)
                    val newY = (animY.value + pan.y).coerceIn(-maxY, maxY)
                    
                    coroutineScope.launch {
                        animX.snapTo(newX)
                        animY.snapTo(newY)
                        animX.updateBounds(-maxX, maxX)
                        animY.updateBounds(-maxY, maxY)
                    }
                    onTransform(scale, androidx.compose.ui.geometry.Offset(newX, newY), true)
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
            
            if (scale > 1.01f) {
                val velocity = velocityTracker.calculateVelocity()
                val decay = androidx.compose.animation.splineBasedDecay<Float>(this@pointerInput)
                if (velocity.x != 0f) {
                    coroutineScope.launch { animX.animateDecay(velocity.x, decay) }
                }
                if (velocity.y != 0f) {
                    coroutineScope.launch { animY.animateDecay(velocity.y, decay) }
                }
            }
        }
    }
}

fun concatColorMatrices(m2: ColorMatrix, m1: ColorMatrix): ColorMatrix {
    val a = m2.values
    val b = m1.values
    val result = FloatArray(20)
    for (row in 0 until 4) {
        val rowOffset = row * 5
        for (col in 0 until 4) {
            result[rowOffset + col] = 
                a[rowOffset + 0] * b[col] +
                a[rowOffset + 1] * b[5 + col] +
                a[rowOffset + 2] * b[10 + col] +
                a[rowOffset + 3] * b[15 + col]
        }
        result[rowOffset + 4] = 
            a[rowOffset + 0] * b[4] +
            a[rowOffset + 1] * b[9] +
            a[rowOffset + 2] * b[14] +
            a[rowOffset + 3] * b[19] +
            a[rowOffset + 4]
    }
    return ColorMatrix(result)
}

@Composable
fun PdfViewer(
    filePath: String,
    contrastMode: ContrastMode = ContrastMode.Normal,
    isWarmFilterActive: Boolean = false,
    isHorizontalScroll: Boolean = false,
    isReaderModeActive: Boolean = false,
    isNegative: Boolean = false,
    currentPage: Int = 1,
    onPageChanged: (Int) -> Unit = {},
    onTotalPages: (Int) -> Unit = {},
    onTap: () -> Unit = {}
) {
    val file = File(filePath)
    if (!file.exists()) {
        Text("File not found")
        return
    }

    val parcelFileDescriptor = remember {
        try { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) } catch (e: Exception) { null }
    }
    val pdfRenderer = remember {
        parcelFileDescriptor?.let {
            try { PdfRenderer(it) } catch (e: Exception) { null }
        }
    }
    if (pdfRenderer == null) {
        Text("Cannot open PDF. It may be corrupted or password protected.")
        return
    }

    val mutex = remember { Mutex() }
    val bitmapCache = remember { android.util.LruCache<Int, Bitmap>(6) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            try { pdfRenderer.close() } catch (e: Exception) {}
            try { parcelFileDescriptor?.close() } catch (e: Exception) {}
        }
    }

    LaunchedEffect(pdfRenderer.pageCount) {
        onTotalPages(pdfRenderer.pageCount)
    }

    // Helper: build color filter for a page bitmap
    @Composable
    fun pageColorFilter(): ColorFilter? {
        return remember(contrastMode, isWarmFilterActive, isNegative) {
            var matrix = when (contrastMode) {
                ContrastMode.Dark, ContrastMode.HighContrastDark -> ColorMatrix(floatArrayOf(-1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f))
                ContrastMode.HighContrastLight -> { val c=1.4f; val t=(-0.5f*c+0.5f)*255f; ColorMatrix(floatArrayOf(c,0f,0f,0f,t, 0f,c,0f,0f,t, 0f,0f,c,0f,t, 0f,0f,0f,1f,0f)) }
                ContrastMode.EInk -> { val c=2f; val t=(-0.5f*c+0.5f)*255f; ColorMatrix(floatArrayOf(0.213f*c,0.715f*c,0.072f*c,0f,t, 0.213f*c,0.715f*c,0.072f*c,0f,t, 0.213f*c,0.715f*c,0.072f*c,0f,t, 0f,0f,0f,1f,0f)) }
                else -> null
            }
            if (isNegative) {
                val negMatrix = ColorMatrix(floatArrayOf(
                    -1f, 0f,  0f,  0f, 255f,
                    0f,  -1f, 0f,  0f, 255f,
                    0f,  0f,  -1f, 0f, 255f,
                    0f,  0f,  0f,  1f, 0f
                ))
                matrix = if (matrix != null) concatColorMatrices(negMatrix, matrix) else negMatrix
            }
            if (isWarmFilterActive && contrastMode != ContrastMode.EInk) {
                val warmMatrix = ColorMatrix(floatArrayOf(0.957f,0f,0f,0f,0f, 0f,0.925f,0f,0f,0f, 0f,0f,0.847f,0f,0f, 0f,0f,0f,1f,0f))
                matrix = if (matrix != null) concatColorMatrices(warmMatrix, matrix) else warmMatrix
            }
            matrix?.let { ColorFilter.colorMatrix(it) }
        }
    }

    // Shared function to render a page to bitmap
    suspend fun renderPage(index: Int, density: Float): Bitmap? {
        return bitmapCache.get(index) ?: mutex.withLock {
            bitmapCache.get(index) ?: run {
                var page: PdfRenderer.Page? = null
                try {
                    page = pdfRenderer.openPage(index)
                    var renderWidth = (page.width * density * 1.5f)
                    var renderHeight = (page.height * density * 1.5f)
                    val maxDim = 2048f
                    if (renderWidth > maxDim || renderHeight > maxDim) {
                        val scale = maxDim / maxOf(renderWidth, renderHeight)
                        renderWidth *= scale
                        renderHeight *= scale
                    }
                    val bmp = Bitmap.createBitmap(renderWidth.toInt(), renderHeight.toInt(), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmapCache.put(index, bmp)
                    bmp
                } catch (e: Throwable) { null }
                finally { try { page?.close() } catch (e: Exception) {} }
            }
        }
    }

    if (isHorizontalScroll) {
        // Pagination mode: HorizontalPager — one page at a time, gallery style
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = (currentPage - 1).coerceIn(0, maxOf(0, pdfRenderer.pageCount - 1)),
            pageCount = { pdfRenderer.pageCount }
        )
        var internalPage by remember { mutableIntStateOf(pagerState.currentPage) }

        LaunchedEffect(pagerState.currentPage) {
            if (internalPage != pagerState.currentPage) {
                internalPage = pagerState.currentPage
                onPageChanged(pagerState.currentPage + 1)
            }
        }
        LaunchedEffect(currentPage) {
            val target = currentPage - 1
            if (target != internalPage && target in 0 until pdfRenderer.pageCount) {
                internalPage = target
                pagerState.scrollToPage(target)
            }
        }

        var isZoomed by remember { mutableStateOf(false) }

        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            var bitmap by remember { mutableStateOf<Bitmap?>(bitmapCache.get(page)) }
            val density = LocalDensity.current.density
            val cf = pageColorFilter()

            LaunchedEffect(page) {
                if (bitmap == null) {
                    bitmap = withContext(Dispatchers.IO) { renderPage(page, density) }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zoomable { newScale, newOffset, zoomed ->
                        scale = newScale
                        offset = newOffset
                        isZoomed = zoomed
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { onTap() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Page ${page + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp)
                            .graphicsLayer {
                                scaleX = scale; scaleY = scale
                                translationX = offset.x; translationY = offset.y
                            },
                        contentScale = ContentScale.Fit,
                        colorFilter = cf
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(0.70f)
                            .background(androidx.compose.ui.graphics.Color.White),
                        contentAlignment = Alignment.Center
                    ) { Text("Loading...", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    } else {
        // Vertical scroll mode: free-scroll LazyColumn (no snapping)
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentPage - 1).coerceIn(0, maxOf(0, pdfRenderer.pageCount - 1)))
        var lastTargetPage by remember { mutableIntStateOf(-1) }

        val firstVisibleItem by remember { derivedStateOf { listState.firstVisibleItemIndex } }
        LaunchedEffect(firstVisibleItem) {
            onPageChanged(firstVisibleItem + 1)
        }
        LaunchedEffect(currentPage) {
            if (currentPage > 0 && currentPage <= pdfRenderer.pageCount
                && !listState.isScrollInProgress
                && currentPage != listState.firstVisibleItemIndex + 1
                && currentPage != lastTargetPage) {
                lastTargetPage = currentPage
                listState.scrollToItem(currentPage - 1)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
                // No flingBehavior override = default free scroll, no snapping
            ) {
                items(pdfRenderer.pageCount, key = { it }) { index ->
                    var bitmap by remember { mutableStateOf<Bitmap?>(bitmapCache.get(index)) }
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }
                    val density = LocalDensity.current.density
                    val cf = pageColorFilter()

                    LaunchedEffect(index) {
                        if (bitmap == null) {
                            bitmap = withContext(Dispatchers.IO) { renderPage(index, density) }
                        }
                    }

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = "Page $index",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .graphicsLayer {
                                    scaleX = scale; scaleY = scale
                                    translationX = offset.x; translationY = offset.y
                                }
                                .zoomable { newScale, newOffset, zoomed ->
                                    scale = newScale
                                    offset = newOffset
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(onDoubleTap = { onTap() })
                                },
                            contentScale = ContentScale.FillWidth,
                            colorFilter = cf
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.70f)
                                .padding(vertical = 4.dp)
                                .background(androidx.compose.ui.graphics.Color.White),
                            contentAlignment = Alignment.Center
                        ) { Text("Loading...", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }

            // Back to top FAB
            val coroutineScope = rememberCoroutineScope()
            androidx.compose.animation.AnimatedVisibility(
                visible = listState.isScrollInProgress || !isReaderModeActive,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                        end = 16.dp
                    )
            ) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = { coroutineScope.launch { listState.scrollToItem(0) } },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.size(40.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.KeyboardArrowUp,
                        contentDescription = "Back to Top",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ZoomIndicator(scaleProvider: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "${(scaleProvider() * 100).roundToInt()}%",
            color = MaterialTheme.colorScheme.background,
            style = MaterialTheme.typography.labelLarge
        )
    }
}


fun segmentTextIntoPages(text: String, pageSize: Int = 1500): List<String> {
    if (text.isEmpty()) return listOf("")
    val pages = mutableListOf<String>()
    var startIndex = 0
    while (startIndex < text.length) {
        val endIndex = startIndex + pageSize
        if (endIndex >= text.length) {
            pages.add(text.substring(startIndex))
            break
        }
        
        var boundary = endIndex
        while (boundary > startIndex && boundary > endIndex - 100 && !text[boundary].isWhitespace()) {
            boundary--
        }
        
        if (boundary == startIndex) {
            boundary = endIndex
            while (boundary < text.length && !text[boundary].isWhitespace()) {
                boundary++
            }
        }
        
        pages.add(text.substring(startIndex, boundary).trim())
        startIndex = boundary
    }
    return pages
}

@Composable
fun TXTReader(
    filePath: String,
    format: String = "TXT",
    settings: com.infer.inferead.viewmodel.ReaderSettings,
    chapterIndex: Int,
    onPageChanged: (Int) -> Unit,
    onTotalPagesLoaded: (Int, List<String>?) -> Unit,
    onTap: () -> Unit = {},
    onTextSelected: (String, Float, Float, String) -> Unit = { _, _, _, _ -> },
    onSelectionFinished: (String, Float, Float, String) -> Boolean = { _, _, _, _ -> false },
    onTextSelectionCleared: () -> Unit = {},
    onAnnotationClicked: (Int, Float, Float) -> Unit = { _, _, _ -> },
    targetVerticalProgress: Float? = null,
    onScrollProgress: (Float) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var loadedText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var isLoading by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    var errorMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    val textScrollState = androidx.compose.foundation.rememberScrollState()
    val horizontalScrollState = androidx.compose.foundation.rememberScrollState()

    androidx.compose.runtime.LaunchedEffect(filePath) {
        isLoading = true
        val file = java.io.File(filePath)
        loadedText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (file.exists()) {
                val ext = file.extension.lowercase()
                if (ext == "docx") {
                    try {
                        val zip = java.util.zip.ZipFile(file)
                        val entry = zip.getEntry("word/document.xml")
                        if (entry != null) {
                            val parser = android.util.Xml.newPullParser()
                            parser.setInput(zip.getInputStream(entry), "utf-8")
                            val sb = java.lang.StringBuilder()
                            var eventType = parser.eventType
                            var isTextNode = false
                            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && (parser.name == "w:t" || parser.name == "t")) {
                                    isTextNode = true
                                } else if (eventType == org.xmlpull.v1.XmlPullParser.TEXT && isTextNode) {
                                    sb.append(parser.text)
                                } else if (eventType == org.xmlpull.v1.XmlPullParser.END_TAG) {
                                    if (parser.name == "w:t" || parser.name == "t") {
                                        isTextNode = false
                                    } else if (parser.name == "w:p" || parser.name == "p") {
                                        sb.append("\n")
                                    }
                                }
                                eventType = parser.next()
                            }
                            sb.toString()
                        } else {
                            "Could not parse DOCX: word/document.xml not found."
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        "Error parsing DOCX: ${e.message}"
                    }
                } else if (ext == "doc") {
                    "DOC parsing not fully supported. Please convert to DOCX or TXT."
                } else {
                    file.readText()
                }
            } else null
        }
        
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            if (loadedText == null) {
                errorMessage = "Failed to load file."
            }
            isLoading = false
        }
    }

    if (isLoading) {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text("Loading...", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
        }
    } else if (loadedText == null) {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text(errorMessage ?: "Failed to load file.", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
        }
    } else {
        val bgColor = when (settings.contrastMode) {
            com.infer.inferead.viewmodel.ContrastMode.Dark -> androidx.compose.ui.graphics.Color(0xFF1A1A1A)
            com.infer.inferead.viewmodel.ContrastMode.HighContrastDark -> androidx.compose.ui.graphics.Color.Black
            com.infer.inferead.viewmodel.ContrastMode.HighContrastLight -> androidx.compose.ui.graphics.Color.White
            com.infer.inferead.viewmodel.ContrastMode.EInk -> androidx.compose.ui.graphics.Color(0xFFF0F0F0)
            com.infer.inferead.viewmodel.ContrastMode.Normal -> if (settings.isWarmFilterActive) androidx.compose.ui.graphics.Color(0xFFF4ECD8) else androidx.compose.ui.graphics.Color(0xFFF5F5F5)
        }
        val textColor = when (settings.contrastMode) {
            com.infer.inferead.viewmodel.ContrastMode.Dark -> androidx.compose.ui.graphics.Color.White
            com.infer.inferead.viewmodel.ContrastMode.HighContrastDark -> androidx.compose.ui.graphics.Color.White
            com.infer.inferead.viewmodel.ContrastMode.HighContrastLight, com.infer.inferead.viewmodel.ContrastMode.EInk -> androidx.compose.ui.graphics.Color.Black
            com.infer.inferead.viewmodel.ContrastMode.Normal -> androidx.compose.ui.graphics.Color.Black
        }
        val tintColor = if (settings.isWarmFilterActive && settings.contrastMode == com.infer.inferead.viewmodel.ContrastMode.Normal) androidx.compose.ui.graphics.Color(0xFFF4ECD8) else bgColor
        
        val fontFamily = when (settings.fontFamily) {
            "SansSerif" -> androidx.compose.ui.text.font.FontFamily.SansSerif
            "Serif" -> androidx.compose.ui.text.font.FontFamily.Serif
            "Monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
            "Google Sans" -> androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font("fonts/google_sans.ttf", context.assets))
            "Literata" -> androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font("fonts/literata.ttf", context.assets))
            else -> androidx.compose.ui.text.font.FontFamily.SansSerif
        }

        @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.fillMaxSize().background(tintColor)
        ) {
            if (settings.isHorizontalScroll) {
                var webViewRef by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.webkit.WebView?>(null) }
                val htmlContent = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                    <style>
                        @font-face { font-family: 'Google Sans'; src: url('file:///android_asset/fonts/google_sans.ttf'); }
                        @font-face { font-family: 'Literata'; src: url('file:///android_asset/fonts/literata.ttf'); }
                        body {
                            margin: 0;
                            padding: 16px;
                            padding-top: 60px;
                            padding-bottom: 60px;
                            box-sizing: border-box;
                            width: 100vw;
                            height: 100vh;
                            column-width: calc(100vw - 32px);
                            column-gap: 32px;
                            overflow-y: hidden;
                            overflow-x: hidden; /* We handle scroll via JS to snap */
                            color: ${String.format("#%06X", 0xFFFFFF and textColor.toArgb())};
                            font-family: ${
                                when(settings.fontFamily) {
                                    "Monospace" -> "monospace"
                                    "Serif" -> "serif"
                                    "SansSerif" -> "sans-serif"
                                    "Google Sans" -> "\"Google Sans\", sans-serif"
                                    "Literata" -> "\"Literata\", serif"
                                    else -> "\"Google Sans\", sans-serif"
                                }
                            };
                            font-size: ${16 * settings.fontSizeMultiplier}px;
                            font-weight: ${if (settings.fontBold) "bold" else "normal"};
                            line-height: ${1.6f * settings.lineSpacingMultiplier};
                            letter-spacing: ${(settings.wordSpacingMultiplier - 1.0f) * 0.1f}em;
                            word-wrap: break-word;
                            white-space: pre-wrap;
                        }
                    </style>
                    </head>
                    <body>${loadedText!!.replace("<", "&lt;").replace(">", "&gt;")}</body>
                    <script>
                        function reportPages() {
                            var totalPages = Math.ceil(document.body.scrollWidth / window.innerWidth);
                            if (totalPages === 0) totalPages = 1;
                            Android.reportTotalPages(totalPages);
                        }
                        window.onload = reportPages;
                        window.addEventListener('resize', reportPages);
                        
                        var startX = 0;
                        var scrollStartX = 0;
                        var lastTapTime = 0;
                        window.addEventListener('touchstart', function(e) {
                            startX = e.touches[0].clientX;
                            scrollStartX = window.scrollX;
                        }, { passive: true });
                        window.addEventListener('touchmove', function(e) {
                            var deltaX = startX - e.touches[0].clientX;
                            window.scrollTo(scrollStartX + deltaX, 0);
                            e.preventDefault();
                        }, { passive: false });
                        window.addEventListener('touchend', function(e) {
                            var currentTime = new Date().getTime();
                            if (currentTime - lastTapTime < 300) {
                                Android.triggerTap();
                                lastTapTime = 0;
                            } else {
                                lastTapTime = currentTime;
                            }
                            var deltaX = startX - e.changedTouches[0].clientX;
                            var page = Math.round(scrollStartX / window.innerWidth);
                            if (deltaX > 40) page++;
                            else if (deltaX < -40) page--;
                            var maxPage = Math.ceil(document.body.scrollWidth / window.innerWidth) - 1;
                            if (page < 0) page = 0;
                            if (page > maxPage) page = maxPage;
                            scrollToPage(page);
                        }, { passive: true });

                        function scrollToPage(pageIndex) {
                            var targetX = pageIndex * window.innerWidth;
                            var startX = window.scrollX;
                            var distance = targetX - startX;
                            var duration = 200; // snappy speed
                            var start = null;

                            function step(timestamp) {
                                if (!start) start = timestamp;
                                var progress = (timestamp - start) / duration;
                                if (progress > 1) progress = 1;
                                
                                // Cubic ease-out
                                var easeProgress = 1 - Math.pow(1 - progress, 3);
                                window.scrollTo(startX + distance * easeProgress, 0);
                                
                                if (progress < 1) {
                                    window.requestAnimationFrame(step);
                                } else {
                                    Android.reportCurrentPage(pageIndex + 1);
                                }
                            }
                            window.requestAnimationFrame(step);
                        }
                    </script>
                    </html>
                """.trimIndent()

                androidx.compose.ui.viewinterop.AndroidView(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { onTap() })
                        },
                    factory = { ctx ->
                        android.webkit.WebView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            webViewRef = this
                            this.settings.javaScriptEnabled = true
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                            
                            addJavascriptInterface(object {
                                @android.webkit.JavascriptInterface
                                fun reportTotalPages(total: Int) {
                                    onTotalPagesLoaded(total, null)
                                }
                                @android.webkit.JavascriptInterface
                                fun reportCurrentPage(page: Int) {
                                    onPageChanged(page)
                                }
                                @android.webkit.JavascriptInterface
                                fun triggerTap() {
                                    onTap()
                                }
                            }, "Android")

                            loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        webViewRef = webView
                        val textColorHex = String.format("#%06X", 0xFFFFFF and textColor.toArgb())
                        val fontFam = when(settings.fontFamily) {
                            "Monospace" -> "monospace"
                            "Serif" -> "serif"
                            "SansSerif" -> "sans-serif"
                            "Google Sans" -> "\"Google Sans\", sans-serif"
                            "Literata" -> "\"Literata\", serif"
                            else -> "\"Google Sans\", sans-serif"
                        }
                        val js = """
                            document.body.style.color = '$textColorHex';
                            document.body.style.fontFamily = '$fontFam';
                            document.body.style.fontSize = '${16 * settings.fontSizeMultiplier}px';
                            document.body.style.fontWeight = '${if (settings.fontBold) "bold" else "normal"}';
                            document.body.style.lineHeight = '${1.6f * settings.lineSpacingMultiplier}';
                            document.body.style.letterSpacing = '${(settings.wordSpacingMultiplier - 1.0f) * 0.1f}em';
                            reportPages();
                        """.trimIndent()
                        webView.evaluateJavascript(js, null)
                    }
                )
                
                androidx.compose.runtime.LaunchedEffect(chapterIndex) {
                    webViewRef?.evaluateJavascript("scrollToPage(${chapterIndex - 1});", null)
                }
            } else {
                androidx.compose.runtime.LaunchedEffect(settings.isHorizontalScroll, loadedText) {
                    if (!settings.isHorizontalScroll && loadedText != null) {
                        onTotalPagesLoaded(1, null)
                        onPageChanged(1)
                    }
                }
                
                androidx.compose.runtime.LaunchedEffect(textScrollState.value, textScrollState.maxValue) {
                    if (textScrollState.maxValue > 0) {
                        onScrollProgress(textScrollState.value.toFloat() / textScrollState.maxValue.toFloat())
                    }
                }
                
                androidx.compose.runtime.LaunchedEffect(targetVerticalProgress) {
                    if (targetVerticalProgress != null && textScrollState.maxValue > 0) {
                        textScrollState.scrollTo((targetVerticalProgress * textScrollState.maxValue).toInt())
                    }
                }
                
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { onTap() })
                        }
                        .verticalScroll(textScrollState)
                        .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 26.dp)
                ) {
                    androidx.compose.material3.Text(
                        text = loadedText!!,
                        color = textColor,
                        fontSize = (16 * settings.fontSizeMultiplier).sp,
                        fontFamily = fontFamily,
                        fontWeight = if (settings.fontBold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                        lineHeight = (16 * settings.fontSizeMultiplier * 1.6f * settings.lineSpacingMultiplier).sp,
                        letterSpacing = ((settings.wordSpacingMultiplier - 1.0f) * 0.1f).em,
                        modifier = androidx.compose.ui.Modifier.padding(top = 60.dp, bottom = 60.dp)
                    )
                }
            }
            
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
            androidx.compose.animation.AnimatedVisibility(
                visible = textScrollState.isScrollInProgress && textScrollState.value > 0 && !settings.isHorizontalScroll,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
                modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(top = 70.dp, end = 16.dp)
            ) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = { coroutineScope.launch { textScrollState.scrollTo(0) } },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = androidx.compose.ui.Modifier.size(40.dp),
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move to Top",
                        modifier = androidx.compose.ui.Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}





@Composable
fun ImageViewer(filePath: String, isNoir: Boolean = false, isNegative: Boolean = false, vignetteStrength: Float = 0f, onTap: () -> Unit = {}) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    val colorFilter = remember(isNoir, isNegative) {
        when {
            isNoir -> {
                // Grayscale: luminance weights
                val m = ColorMatrix(floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f
                ))
                ColorFilter.colorMatrix(m)
            }
            isNegative -> {
                // Invert: multiply by -1, add 255
                val m = ColorMatrix(floatArrayOf(
                    -1f, 0f,  0f,  0f, 255f,
                    0f,  -1f, 0f,  0f, 255f,
                    0f,  0f,  -1f, 0f, 255f,
                    0f,  0f,  0f,  1f, 0f
                ))
                ColorFilter.colorMatrix(m)
            }
            else -> null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onTap() }
                )
            }
            .zoomable { newScale, newOffset, _ ->
                scale = newScale
                offset = newOffset
            }
    ) {
        AsyncImage(
            model = File(filePath),
            contentDescription = "Image",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter
        )
        if (vignetteStrength != 0f) {
            val vColor = if (vignetteStrength > 0) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
            val vAlpha = Math.abs(vignetteStrength)
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = kotlin.math.max(size.width, size.height) / 1.2f
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(androidx.compose.ui.graphics.Color.Transparent, vColor.copy(alpha = vAlpha)),
                        center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f),
                        radius = radius
                    )
                )
            }
        }
    }
}

@Composable
fun PdfPagePreview(
    filePath: String,
    pageIndex: Int,
    contrastMode: ContrastMode,
    isWarmFilterActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    var previewBitmap by remember(filePath, pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(filePath, pageIndex) {
        withContext(Dispatchers.IO) {
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            var page: PdfRenderer.Page? = null
            try {
                val file = File(filePath)
                if (file.exists()) {
                    pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(pfd)
                    if (pageIndex in 0 until renderer.pageCount) {
                        page = renderer.openPage(pageIndex)
                        val previewWidth = 150
                        val aspect = page.height.toFloat() / page.width.toFloat()
                        val previewHeight = (previewWidth * aspect).toInt()

                        val bitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        
                        previewBitmap = bitmap
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { page?.close() } catch (e: Exception) {}
                try { renderer?.close() } catch (e: Exception) {}
                try { pfd?.close() } catch (e: Exception) {}
            }
        }
    }

    Box(
        modifier = modifier
            .background(androidx.compose.ui.graphics.Color.LightGray, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = previewBitmap
        if (bitmap != null) {
            val colorFilter = remember(contrastMode, isWarmFilterActive) {
                val baseMatrix = when (contrastMode) {
                    ContrastMode.Dark, ContrastMode.HighContrastDark -> {
                        ColorMatrix(
                            floatArrayOf(
                                -1f, 0f, 0f, 0f, 255f,
                                0f, -1f, 0f, 0f, 255f,
                                0f, 0f, -1f, 0f, 255f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    }
                    ContrastMode.HighContrastLight -> {
                        val contrast = 1.4f
                        val translate = (-0.5f * contrast + 0.5f) * 255f
                        ColorMatrix(
                            floatArrayOf(
                                contrast, 0f, 0f, 0f, translate,
                                0f, contrast, 0f, 0f, translate,
                                0f, 0f, contrast, 0f, translate,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    }
                    ContrastMode.EInk -> {
                        val contrast = 2.0f
                        val translate = (-0.5f * contrast + 0.5f) * 255f
                        ColorMatrix(
                            floatArrayOf(
                                0.213f * contrast, 0.715f * contrast, 0.072f * contrast, 0f, translate,
                                0.213f * contrast, 0.715f * contrast, 0.072f * contrast, 0f, translate,
                                0.213f * contrast, 0.715f * contrast, 0.072f * contrast, 0f, translate,
                                0f,                0f,                0f,                1f, 0f
                            )
                        )
                    }
                    else -> null
                }

                val warmMatrix = if (isWarmFilterActive && contrastMode != ContrastMode.EInk) {
                    ColorMatrix(
                        floatArrayOf(
                            0.957f, 0f,     0f,     0f, 0f,
                            0f,     0.925f, 0f,     0f, 0f,
                            0f,     0f,     0.847f, 0f, 0f,
                            0f,     0f,     0f,     1f, 0f
                        )
                    )
                } else {
                    null
                }

                val finalMatrix = if (baseMatrix != null && warmMatrix != null) {
                    concatColorMatrices(warmMatrix, baseMatrix)
                } else baseMatrix ?: warmMatrix

                finalMatrix?.let { ColorFilter.colorMatrix(it) }
            }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Preview Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter
            )
        } else {
            Text(
                text = "...",
                color = androidx.compose.ui.graphics.Color.DarkGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun ComicArchiveViewer(
    filePath: String,
    format: String,
    settings: ReaderSettings,
    currentPage: Int,
    onPageChanged: (Int) -> Unit,
    onTotalPagesLoaded: (Int) -> Unit,
    onTap: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var images by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(filePath) {
        isLoading = true
        val extractDir = File(context.cacheDir, "comic_cache_${filePath.hashCode()}")
        
        var tempFile: File? = null
        val pathForExtraction = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var tempPath = filePath
            if (filePath.startsWith("content://")) {
                val uri = android.net.Uri.parse(filePath)
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val file = File(context.cacheDir, "temp_import_${filePath.hashCode()}")
                    tempFile = file
                    val outputStream = java.io.FileOutputStream(file)
                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempPath = file.absolutePath
                }
            }
            tempPath
        }

        val success = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.infer.inferead.utils.ArchiveExtractor.extractArchive(pathForExtraction, extractDir, format)
        }
        try { tempFile?.delete() } catch (e: Exception) {}

        if (success) {
            images = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                extractDir.walkTopDown().filter { it.isFile && (it.extension.equals("jpg", true) || it.extension.equals("jpeg", true) || it.extension.equals("png", true) || it.extension.equals("webp", true)) }.sortedBy { it.name }.toList()
            }
            onTotalPagesLoaded(images.size)
        }
        isLoading = false
    }

    val colorFilter = remember(settings.isNoir, settings.isNegative) {
        when {
            settings.isNoir -> {
                val m = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f
                ))
                androidx.compose.ui.graphics.ColorFilter.colorMatrix(m)
            }
            settings.isNegative -> {
                val m = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                    -1f, 0f,  0f,  0f, 255f,
                    0f,  -1f, 0f,  0f, 255f,
                    0f,  0f,  -1f, 0f, 255f,
                    0f,  0f,  0f,  1f, 0f
                ))
                androidx.compose.ui.graphics.ColorFilter.colorMatrix(m)
            }
            else -> null
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading...", color = MaterialTheme.colorScheme.onBackground)
        }
    } else if (images.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No images found in Archive", color = MaterialTheme.colorScheme.onBackground)
        }
    } else {
        if (settings.isHorizontalScroll) {
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = (currentPage - 1).coerceIn(0, maxOf(0, images.size - 1)), pageCount = { images.size })
            var internalPage by remember { mutableIntStateOf(pagerState.currentPage) }
            LaunchedEffect(pagerState.currentPage) {
                if (internalPage != pagerState.currentPage) {
                    internalPage = pagerState.currentPage
                    onPageChanged(pagerState.currentPage + 1)
                }
            }
            LaunchedEffect(currentPage) {
                val target = currentPage - 1
                if (target != internalPage && target in images.indices) {
                    internalPage = target
                    pagerState.scrollToPage(target)
                }
            }
            var isZoomed by remember { mutableStateOf(false) }
            androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                
                Box(modifier = Modifier
                    .fillMaxSize()
                    .zoomable { newScale, newOffset, zoomed ->
                        scale = newScale
                        offset = newOffset
                        isZoomed = zoomed
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { onTap() },
                            onTap = { tapOffset ->
                                if (!isZoomed) {
                                    val width = size.width
                                    val height = size.height
                                    if (tapOffset.y > height * 0.15f && tapOffset.y < height * 0.85f) {
                                        if (tapOffset.x < width * 0.3f) {
                                            scope.launch {
                                                if (pagerState.targetPage > 0) {
                                                    pagerState.animateScrollToPage(pagerState.targetPage - 1)
                                                }
                                            }
                                        } else if (tapOffset.x > width * 0.7f) {
                                            scope.launch {
                                                if (pagerState.targetPage < images.size - 1) {
                                                    pagerState.animateScrollToPage(pagerState.targetPage + 1)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                ) {
                    AsyncImage(
                        model = images[page],
                        contentDescription = "Page ${page + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                    contentScale = ContentScale.Fit,
                    colorFilter = colorFilter
                )
                if (settings.vignetteStrength != 0f) {
                    val vColor = if (settings.vignetteStrength > 0) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
                    val vAlpha = Math.abs(settings.vignetteStrength)
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val radius = kotlin.math.max(size.width, size.height) / 1.2f
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(androidx.compose.ui.graphics.Color.Transparent, vColor.copy(alpha = vAlpha)),
                                center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f),
                                radius = radius
                            )
                        )
                    }
                }
            }
        }
        } else {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentPage - 1).coerceIn(0, maxOf(0, images.size - 1)))
            var internalPage by remember { mutableIntStateOf(listState.firstVisibleItemIndex) }
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
                    if (internalPage != index) {
                        internalPage = index
                        onPageChanged(index + 1)
                    }
                }
            }
            LaunchedEffect(currentPage) {
                val target = currentPage - 1
                if (target != internalPage && target in images.indices) {
                    internalPage = target
                    listState.scrollToItem(target)
                }
            }
            var isZoomed by remember { mutableStateOf(false) }
            // No flingBehavior override = default free scroll, no page snapping
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(images) { image ->
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .zoomable { newScale, newOffset, zoomed ->
                            scale = newScale
                            offset = newOffset
                            isZoomed = zoomed
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { onTap() }
                            )
                        }
                    ) {
                        AsyncImage(
                            model = image,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                ),
                            contentScale = ContentScale.FillWidth,
                            colorFilter = colorFilter
                        )
                        if (settings.vignetteStrength != 0f) {
                            val vColor = if (settings.vignetteStrength > 0) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
                            val vAlpha = Math.abs(settings.vignetteStrength)
                            androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                                val radius = kotlin.math.max(size.width, size.height) / 1.2f
                                drawRect(
                                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                        colors = listOf(androidx.compose.ui.graphics.Color.Transparent, vColor.copy(alpha = vAlpha)),
                                        center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f),
                                        radius = radius
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class WebAppInterface(
    private val onTapCallback: () -> Unit,
    private val onNextChapter: () -> Unit = {},
    private val onPrevChapter: () -> Unit = {},
    private val onScrollStateChanged: (Boolean) -> Unit = {},
    private val onTextSelectedCallback: (String, Float, Float, String) -> Unit = { _, _, _, _ -> },
    private val onTextSelectionClearedCallback: () -> Unit = {},
    private val onAnnotationClickedCallback: (Int, Float, Float) -> Unit = { _, _, _ -> },
    private val onSelectionFinishedCallback: (String, Float, Float, String) -> Boolean = { _, _, _, _ -> false },
    private val onScrollProgressCallback: (Float) -> Unit = {},
    private val onAnnotationPositionsCallback: (String) -> Unit = {}
) {
    @android.webkit.JavascriptInterface
    fun onTap() { onTapCallback() }
    
    @android.webkit.JavascriptInterface
    fun nextChapter() { onNextChapter() }
    
    @android.webkit.JavascriptInterface
    fun prevChapter() { onPrevChapter() }
    
    @android.webkit.JavascriptInterface
    fun reportScroll(isScrolling: Boolean) { onScrollStateChanged(isScrolling) }

    @android.webkit.JavascriptInterface
    fun reportScrollProgress(progress: Float) { onScrollProgressCallback(progress) }

    @android.webkit.JavascriptInterface
    fun reportAnnotationPositions(positions: String) { onAnnotationPositionsCallback(positions) }

    @android.webkit.JavascriptInterface
    fun onTextSelected(text: String, top: Float, bottom: Float, cfiRange: String) { 
        onTextSelectedCallback(text, top, bottom, cfiRange) 
    }

    @android.webkit.JavascriptInterface
    fun onSelectionFinished(text: String, top: Float, bottom: Float, cfiRange: String): Boolean { 
        return onSelectionFinishedCallback(text, top, bottom, cfiRange) 
    }
    
    @android.webkit.JavascriptInterface
    fun onTextSelectionCleared() { onTextSelectionClearedCallback() }
    
    @android.webkit.JavascriptInterface
    fun onAnnotationClicked(annId: Int, top: Float, bottom: Float) { onAnnotationClickedCallback(annId, top, bottom) }
}

@Composable
fun EPUBReader(
    filePath: String,
    settings: com.infer.inferead.viewmodel.ReaderSettings,
    chapterIndex: Int,
    onPageChanged: (Int) -> Unit,
    onTotalPagesLoaded: (Int, List<String>?) -> Unit,
    onTap: () -> Unit = {},
    onTextSelected: (String, Float, Float, String) -> Unit = { _, _, _, _ -> },
    onSelectionFinished: (String, Float, Float, String) -> Boolean = { _, _, _, _ -> false },
    onTextSelectionCleared: () -> Unit = {},
    onAnnotationClicked: (Int, Float, Float) -> Unit = { _, _, _ -> },
    annotations: List<com.infer.inferead.data.Annotation> = emptyList(),
    targetScrollAnnId: Int? = null,
    targetVerticalProgress: Float? = null,
    onScrollProgress: (Float) -> Unit = {},
    onAnnotationPositions: (List<Pair<Int, Float>>) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var epubBook by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.infer.inferead.utils.EpubBook?>(null) }
    var isLoading by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    var errorMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(filePath) {
        isLoading = true
        val extractDir = java.io.File(context.cacheDir, "epub_cache_${filePath.hashCode()}")
        
        var tempFile: java.io.File? = null
        val pathForExtraction = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var tempPath = filePath
            if (filePath.startsWith("content://")) {
                val uri = android.net.Uri.parse(filePath)
                tempFile = java.io.File(context.cacheDir, "temp_epub_${System.currentTimeMillis()}.epub")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile!!.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempPath = tempFile!!.absolutePath
            }
            tempPath
        }

        val success = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.infer.inferead.utils.ArchiveExtractor.extractArchive(pathForExtraction, extractDir, "EPUB")
        }
        val parsed = if (success) com.infer.inferead.utils.EpubParser.parseEpub(extractDir) else null
        tempFile?.delete()
        
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            if (parsed != null && parsed.spineFiles.isNotEmpty()) {
                epubBook = parsed
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val previews = parsed.spineFiles.map { htmlFile ->
                        val htmlContent = java.io.File(htmlFile).readText()
                        val bodyStart = htmlContent.indexOf("<body", ignoreCase = true)
                        val bodyStr = if (bodyStart != -1) htmlContent.substring(bodyStart) else htmlContent
                        val text = bodyStr.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
                        text.take(150)
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onTotalPagesLoaded(parsed.spineFiles.size, previews)
                    }
                }
            } else {
                errorMessage = "Failed to parse EPUB structure. The file might be corrupted or in an unsupported layout."
            }
        }
        isLoading = false
    }

    if (isLoading) {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text("Loading...", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
        }
    } else if (epubBook == null || epubBook!!.spineFiles.isEmpty()) {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text("Failed to parse EPUB or empty spine.", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
        }
    } else {
        val chapterIndexSafe = (chapterIndex - 1).coerceIn(0, maxOf(0, epubBook!!.spineFiles.size - 1))
        val chapterPath = epubBook!!.spineFiles.getOrNull(chapterIndexSafe)
        
        if (chapterPath != null) {
            val htmlUrl = android.net.Uri.fromFile(java.io.File(chapterPath)).toString()
                
                val bgColor = when (settings.contrastMode) {
                    com.infer.inferead.viewmodel.ContrastMode.Dark -> "#1A1A1A"
                    com.infer.inferead.viewmodel.ContrastMode.HighContrastDark -> "#000000"
                    com.infer.inferead.viewmodel.ContrastMode.HighContrastLight -> "#FFFFFF"
                    com.infer.inferead.viewmodel.ContrastMode.EInk -> "#F0F0F0"
                    com.infer.inferead.viewmodel.ContrastMode.Normal -> if (settings.isWarmFilterActive) "#F4ECD8" else "#F5F5F5"
                }
                val textColor = when (settings.contrastMode) {
                    com.infer.inferead.viewmodel.ContrastMode.Dark -> "#E0E0E0"
                    com.infer.inferead.viewmodel.ContrastMode.HighContrastDark -> "#FFFFFF"
                    com.infer.inferead.viewmodel.ContrastMode.HighContrastLight, com.infer.inferead.viewmodel.ContrastMode.EInk -> "#000000"
                    com.infer.inferead.viewmodel.ContrastMode.Normal -> if (settings.isWarmFilterActive) "#5C4033" else "#1C1C1E"
                }
                val tintColor = if (settings.isWarmFilterActive && settings.contrastMode == com.infer.inferead.viewmodel.ContrastMode.Normal) "#F4ECD8" else bgColor
                val actualBg = tintColor
                val fontFamily = when (settings.fontFamily) {
                    "SansSerif" -> "sans-serif"
                    "Serif" -> "serif"
                    "Monospace" -> "monospace"
                    "Google Sans" -> "'Google Sans'"
                    "Literata" -> "'Literata'"
                    else -> "sans-serif"
                }
                val fontSize = (16 * settings.fontSizeMultiplier).toInt()
                val boldRule = if (settings.fontBold) "font-weight: bold !important;" else "font-weight: normal !important;"
                val isHorizontal = settings.isHorizontalScroll
                val isFixedLayout = epubBook!!.isFixedLayout
                val isArchiveOrg = epubBook!!.isArchiveOrg
                
                val js = androidx.compose.runtime.remember(settings.contrastMode, settings.fontFamily, settings.fontSizeMultiplier, settings.fontBold, settings.lineSpacingMultiplier, settings.wordSpacingMultiplier, isHorizontal, isFixedLayout, isArchiveOrg) {
                    val fontFaces = """
                        @font-face {
                            font-family: 'Google Sans';
                            src: url('file:///android_asset/fonts/google_sans.ttf');
                        }
                        @font-face {
                            font-family: 'Literata';
                            src: url('file:///android_asset/fonts/literata.ttf');
                        }
                    """
                    val layoutCss = if (isFixedLayout) """
                        html, body {
                            margin: 0 !important;
                            padding: 0 !important;
                            height: 100% !important;
                            width: 100% !important;
                            overflow: hidden !important;
                            background-color: transparent !important;
                        }
                        img {
                            max-width: 100% !important;
                            max-height: 100% !important;
                            object-fit: contain !important;
                            display: block !important;
                            margin: auto !important;
                        }
                    """ else if (isHorizontal) """
                        html {
                            overflow: hidden !important;
                            height: 100% !important;
                            width: 100% !important;
                            background-color: transparent !important;
                        }
                        ::-webkit-scrollbar { display: none !important; }
                        body {
                            margin: 0 !important;
                            padding: 0 !important;
                            height: 100vh !important;
                            width: 100vw !important;
                            overflow: hidden !important;
                            box-sizing: border-box !important;
                            word-wrap: break-word !important;
                            word-break: break-word !important;
                            white-space: normal !important;
                        }
                        img {
                            max-width: 100vw !important;
                            max-height: 100vh !important;
                            object-fit: contain !important;
                            display: block !important;
                            margin: 0 auto !important;
                        }
                    """ else """
                        html, body {
                            background-color: transparent !important;
                            margin: 0 !important;
                            overflow-x: hidden !important;
                            overflow-y: visible !important;
                            height: auto !important;
                        }
                        body {
                            padding: 60px 24px 60px 16px !important;
                            box-sizing: border-box !important;
                            word-wrap: break-word !important;
                            word-break: break-word !important;
                            white-space: normal !important;
                        }
                        img {
                            max-width: 100% !important;
                            height: auto !important;
                            display: block !important;
                            margin: 0 auto !important;
                        }
                    """
                    
                    val positionOverride = if (isArchiveOrg && !isFixedLayout) "position: static !important;" else ""
                    val ocrHideCss = if (isFixedLayout) """
                        p, div, span, h1, h2, h3, h4, h5, h6, a {
                            color: transparent !important;
                            background-color: transparent !important;
                            font-size: 0px !important;
                        }
                    """ else ""
                    
                    val einkFilter = if (settings.contrastMode == com.infer.inferead.viewmodel.ContrastMode.EInk) "filter: grayscale(100%) !important;" else ""

                    """
                        javascript:(function() {
                            var oldStyle = document.getElementById('infe-custom-styles');
                              if(oldStyle) oldStyle.remove();
                              var style = document.createElement('style');
                              style.id = 'infe-custom-styles';
                            style.innerHTML = `
                                $fontFaces
                                $layoutCss
                                html {
                                    $einkFilter
                                    -webkit-touch-callout: none;
                                    -webkit-user-select: text;
                                    user-select: text;
                                }
                                body {
                                    color: $textColor !important;
                                    font-family: $fontFamily, sans-serif !important;
                                    font-size: ${fontSize}px !important;
                                    background-color: transparent !important;
                                    line-height: ${1.6f * settings.lineSpacingMultiplier} !important;
                                    letter-spacing: ${(settings.wordSpacingMultiplier - 1.0f) * 0.1f}em !important;
                                    $boldRule
                                }
                                p, div, span:not(.inferead-annotation), a, li, blockquote, h1, h2, h3, h4, h5, h6 {
                                    color: $textColor !important;
                                    font-family: $fontFamily, sans-serif !important;
                                    background-color: transparent !important;
                                    $boldRule
                                    $positionOverride
                                }
                                .inferead-annotation {
                                    color: $textColor !important;
                                    font-family: $fontFamily, sans-serif !important;
                                    $boldRule
                                }
                                $ocrHideCss
                            `;
                            document.head.appendChild(style);
                            
                            var imgs = document.getElementsByTagName('img').length;
                            var textLen = document.body.innerText.trim().length;
                            var isManga = ($isFixedLayout || (imgs > 0 && textLen < 150));
                            
                            if (isManga) {
                                var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
                                var nodes = [];
                                while (walker.nextNode()) nodes.push(walker.currentNode);
                                for (var i = 0; i < nodes.length; i++) nodes[i].nodeValue = '';
                                document.body.style.backgroundColor = 'transparent';
                                document.documentElement.style.backgroundColor = 'transparent';
                            } else if ($isHorizontal) {
                                if (!document.getElementById('infe-wrapper')) {
                                    var wrap = document.createElement('div');
                                    wrap.id = 'infe-wrapper';
                                    wrap.style.columnWidth = '100vw';
                                    wrap.style.WebkitColumnWidth = '100vw';
                                    wrap.style.columnGap = '0px';
                                    wrap.style.WebkitColumnGap = '0px';
                                    wrap.style.height = '100vh';
                                    wrap.style.width = '100vw';
                                    wrap.style.overflowX = 'auto';
                                    wrap.style.overflowY = 'hidden';
                                    wrap.style.boxSizing = 'border-box';
                                    wrap.style.padding = '20px 16px';
                                    wrap.style.margin = '0';
                                    while (document.body.firstChild) {
                                        wrap.appendChild(document.body.firstChild);
                                    }
                                    document.body.appendChild(wrap);
                                }
                            }
                            
                            var scrollTimeout;
                            function handleScroll() {
                                Android.reportScroll(true);
                                
                                if (!$isHorizontal && !isManga) {
                                    var maxScroll = document.documentElement.scrollHeight - window.innerHeight;
                                    var progress = maxScroll > 0 ? window.scrollY / maxScroll : 0;
                                    Android.reportScrollProgress(progress);
                                }
                                
                                clearTimeout(scrollTimeout);
                                scrollTimeout = setTimeout(function() {
                                    Android.reportScroll(false);
                                }, 500);
                            }
                            window.addEventListener('scroll', handleScroll, {passive: true});
                            var wrapEl = document.getElementById('infe-wrapper');
                            if (wrapEl) { wrapEl.addEventListener('scroll', handleScroll, {passive: true}); }
                            
                            window.scrollToProgress = function(progress) {
                                if (!$isHorizontal && !isManga) {
                                    var maxScroll = document.documentElement.scrollHeight - window.innerHeight;
                                    window.scrollTo(0, maxScroll * progress);
                                }
                            };
                            
                            if (!document.getElementById('epub-next-btn')) {
                                var btnContainer = document.createElement('div');
                                btnContainer.id = 'epub-next-btn';
                                btnContainer.style.textAlign = 'center';
                                btnContainer.style.padding = '40px';
                                btnContainer.style.borderTop = '1px solid gray';
                                btnContainer.style.marginTop = '40px';
                                btnContainer.style.marginBottom = '20px';
                                
                                var nextBtn = document.createElement('div');
                                nextBtn.innerHTML = 'Load Next Chapter';
                                nextBtn.style.fontWeight = 'bold';
                                nextBtn.style.fontSize = '18px';
                                nextBtn.style.color = 'inherit';
                                nextBtn.style.marginBottom = '20px';
                                nextBtn.onclick = function() { Android.nextChapter(); };
                                
                                btnContainer.appendChild(nextBtn);
                                document.body.appendChild(btnContainer);
                            }
                            
                            var touchStartX = 0;
                            var touchStartY = 0;
                            document.addEventListener('touchstart', function(e) {
                                touchStartX = e.changedTouches[0].screenX;
                                touchStartY = e.changedTouches[0].screenY;
                            }, {passive: true});
                            
                            document.addEventListener('touchend', function(e) {
                                var touchEndX = e.changedTouches[0].screenX;
                                var touchEndY = e.changedTouches[0].screenY;
                                var dx = touchEndX - touchStartX;
                                var dy = touchEndY - touchStartY;

                                if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy)) {
                                    var wrap = document.getElementById('infe-wrapper');
                                    if (wrap) {
                                        if (dx < 0) {
                                            if (wrap.scrollLeft + wrap.clientWidth >= wrap.scrollWidth - 10) { Android.nextChapter(); } 
                                            else { wrap.scrollLeft += wrap.clientWidth; }
                                        } else {
                                            if (wrap.scrollLeft <= 10) { Android.prevChapter(); } 
                                            else { wrap.scrollLeft -= wrap.clientWidth; }
                                        }
                                    } else {
                                        if (dx < 0) { Android.nextChapter(); } else { Android.prevChapter(); }
                                    }
                                }
                            }, {passive: true});
                            
                            var lastTapTime = 0;
                            document.addEventListener('click', function(e) {
                                var isLink = e.target.tagName === 'A' || (e.target.closest && e.target.closest('a'));
                                var isBtn = e.target.id === 'epub-next-btn' || (e.target.closest && e.target.closest('#epub-next-btn'));
                                if (!isLink && !isBtn) {
                                    var width = window.innerWidth;
                                    var height = window.innerHeight;
                                    
                                    if (e.clientX < width * 0.15) { 
                                        var wrap = document.getElementById('infe-wrapper');
                                        if (wrap && wrap.scrollLeft > 10) { wrap.scrollLeft -= wrap.clientWidth; }
                                        else { Android.prevChapter(); }
                                    } 
                                    else if (e.clientX > width * 0.85) { 
                                        var wrap = document.getElementById('infe-wrapper');
                                        if (wrap && wrap.scrollLeft + wrap.clientWidth < wrap.scrollWidth - 10) { wrap.scrollLeft += wrap.clientWidth; }
                                        else { Android.nextChapter(); }
                                    } 
                                    else {
                                        var currentTime = new Date().getTime();
                                        if (currentTime - lastTapTime < 300) { Android.onTap(); lastTapTime = 0; } 
                                        else { lastTapTime = currentTime; }
                                    }
                                }
                            });
                            
                            // Prevent native Android context menu
                            document.addEventListener('contextmenu', function(e) {
                                e.preventDefault();
                            });
                            function getRangeOffsets(range) {
                                try {
                                    var preSelectionRange = range.cloneRange();
                                    preSelectionRange.selectNodeContents(document.body);
                                    preSelectionRange.setEnd(range.startContainer, range.startOffset);
                                    var start = preSelectionRange.toString().length;
                                    var end = start + range.toString().length;
                                    return start + "," + end;
                                } catch(e) { return ""; }
                            }

                            var selectionTimeout = null;
                            document.addEventListener('selectionchange', function() {
                                var sel = window.getSelection();
                                if (!sel || sel.isCollapsed) {
                                    Android.onTextSelectionCleared();
                                    clearTimeout(selectionTimeout);
                                } else {
                                    var text = sel.toString();
                                    if (text.length > 0) {
                                        try {
                                            var range = sel.getRangeAt(0);
                                            var rect = range.getBoundingClientRect();
                                            var cfi = getRangeOffsets(range);
                                            Android.onTextSelected(text, rect.top, rect.bottom, cfi);
                                            
                                            clearTimeout(selectionTimeout);
                                            selectionTimeout = setTimeout(function() {
                                                var consumed = Android.onSelectionFinished(text, rect.top, rect.bottom, cfi);
                                                if (consumed) {
                                                    window.getSelection().removeAllRanges();
                                                }
                                            }, 600);
                                        } catch (e) {}
                                    }
                                }
                            });
                            
                            // Render annotations
                            window.renderAnnotations = function(annotationsStr) {
                                try {
                                    var anns = JSON.parse(annotationsStr);
                                    
                                    var marks = document.querySelectorAll('.inferead-annotation');
                                    marks.forEach(function(mark) {
                                        var parent = mark.parentNode;
                                        while(mark.firstChild) parent.insertBefore(mark.firstChild, mark);
                                        parent.removeChild(mark);
                                    });
                                    document.body.normalize(); 
                                    
                                    anns.forEach(function(a) {
                                        var parts = a.cfiRange.split(',');
                                        a.start = parseInt(parts[0]);
                                        a.end = parseInt(parts[1]);
                                    });
                                    anns.sort(function(a, b) { return b.start - a.start; });
                                    
                                    anns.forEach(function(ann) {
                                        if(isNaN(ann.start) || isNaN(ann.end)) return;
                                        var start = ann.start;
                                        var end = ann.end;
                                        if(end - start <= 0) return;
                                        
                                        var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
                                        var currentOffset = 0;
                                        var node;
                                        
                                        var nodesToWrap = [];
                                        while(node = walker.nextNode()) {
                                            var nodeLen = node.nodeValue.length;
                                            if(currentOffset + nodeLen > start && currentOffset < end) {
                                                var overlapStart = Math.max(0, start - currentOffset);
                                                var overlapEnd = Math.min(nodeLen, end - currentOffset);
                                                nodesToWrap.push({
                                                    node: node,
                                                    start: overlapStart,
                                                    end: overlapEnd
                                                });
                                            }
                                            currentOffset += nodeLen;
                                            if(currentOffset >= end) break;
                                        }
                                        
                                        for(var i = nodesToWrap.length - 1; i >= 0; i--) {
                                            var item = nodesToWrap[i];
                                            var textNode = item.node;
                                            
                                            if (item.end < textNode.nodeValue.length) {
                                                textNode.splitText(item.end);
                                            }
                                            var targetNode = textNode;
                                            if (item.start > 0) {
                                                targetNode = textNode.splitText(item.start);
                                            }
                                            
                                            var span = document.createElement('span');
                                            span.id = 'ann-' + ann.id;
                                            span.className = 'inferead-annotation';
                                            if (ann.note) {
                                                span.style.borderBottom = '2px dotted ' + (ann.colorHex || '#c25d5d');
                                                span.style.backgroundColor = 'transparent';
                                            } else {
                                                var col = ann.colorHex || '#c25d5d';
                                                span.style.backgroundColor = col + '80';
                                            }
                                            span.onclick = function(e) {
                                                e.stopPropagation();
                                                var rect = span.getBoundingClientRect();
                                                Android.onAnnotationClicked(ann.id, rect.top, rect.bottom);
                                            };
                                            targetNode.parentNode.insertBefore(span, targetNode);
                                            span.appendChild(targetNode);
                                        }
                                    });
                                    
                                    // Report annotation vertical positions
                                    if (!$isHorizontal && !isManga) {
                                        var maxScroll = document.documentElement.scrollHeight - window.innerHeight;
                                        if (maxScroll > 0) {
                                            var posStr = "";
                                            var seen = {};
                                            var allMarks = document.querySelectorAll('.inferead-annotation');
                                            allMarks.forEach(function(mark) {
                                                var id = mark.id.replace('ann-', '');
                                                if (!seen[id]) {
                                                    seen[id] = true;
                                                    var top = mark.getBoundingClientRect().top + window.scrollY;
                                                    var progress = top / maxScroll;
                                                    if (posStr !== "") posStr += ",";
                                                    posStr += id + ":" + progress;
                                                }
                                            });
                                            Android.reportAnnotationPositions(posStr);
                                        }
                                    }
                                } catch(e) {}
                            };
                            
                            function reportPositions() {
                                if (!$isHorizontal && !isManga) {
                                    var maxScroll = document.documentElement.scrollHeight - window.innerHeight;
                                    if (maxScroll > 0) {
                                        var posStr = "";
                                        var seen = {};
                                        var allMarks = document.querySelectorAll('.inferead-annotation');
                                        allMarks.forEach(function(mark) {
                                            var id = mark.id.replace('ann-', '');
                                            if (!seen[id]) {
                                                seen[id] = true;
                                                var top = mark.getBoundingClientRect().top + window.scrollY;
                                                var progress = top / maxScroll;
                                                if (posStr !== "") posStr += ",";
                                                posStr += id + ":" + progress;
                                            }
                                        });
                                        Android.reportAnnotationPositions(posStr);
                                    }
                                }
                            }
                            
                            window.scrollToAnnotation = function(id) {
                                var target = document.getElementById('ann-' + id);
                                if (target) {
                                    if (!$isHorizontal && !isManga) {
                                        var maxScroll = document.documentElement.scrollHeight - window.innerHeight;
                                        var top = target.getBoundingClientRect().top + window.scrollY;
                                        window.scrollTo(0, top - window.innerHeight / 3);
                                    } else {
                                        target.scrollIntoView({behavior: "smooth", block: "center"});
                                    }
                                }
                            };
                        })()
                    """
                }
                
                val annotationsJson = androidx.compose.runtime.remember(annotations) {
                    val list = annotations.map {
                        val cfi = if (it.cfiRange.contains("|")) it.cfiRange.substringAfter("|") else it.cfiRange
                        "{\"id\":${it.id}, \"cfiRange\":\"${cfi}\", \"colorHex\":\"${it.colorHex}\", \"note\":${if (!it.textComment.isNullOrEmpty()) "\"${it.textComment!!.replace("\"", "\\\"").replace("\n", "\\n")}\"" else "null"}}"
                    }
                    "[${list.joinToString(",")}]"
                }
                
                // Track the latest chapterIndex and callbacks for the JS interface
                val latestChapterIndex = androidx.compose.runtime.rememberUpdatedState(chapterIndex)
                val latestOnPageChanged = androidx.compose.runtime.rememberUpdatedState(onPageChanged)
                val latestOnTap = androidx.compose.runtime.rememberUpdatedState(onTap)
                val latestOnTextSelected = androidx.compose.runtime.rememberUpdatedState(onTextSelected)
                val latestOnSelectionFinished = androidx.compose.runtime.rememberUpdatedState(onSelectionFinished)
                val latestOnTextSelectionCleared = androidx.compose.runtime.rememberUpdatedState(onTextSelectionCleared)
                val latestOnAnnotationClicked = androidx.compose.runtime.rememberUpdatedState(onAnnotationClicked)

                val latestOnAnnotationPositions = androidx.compose.runtime.rememberUpdatedState(onAnnotationPositions)
                val latestOnScrollProgress = androidx.compose.runtime.rememberUpdatedState(onScrollProgress)

                var isScrolled by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                var scrollJob by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null) }
                val viewCoroutineScope = androidx.compose.runtime.rememberCoroutineScope()
                var webViewRef by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.webkit.WebView?>(null) }
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(actualBg)))
                ) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        factory = { ctx ->
                            com.infer.inferead.ui.screens.CustomWebView(ctx).apply {
                                webViewRef = this
                                this.settings.javaScriptEnabled = true
                                this.settings.allowFileAccess = true
                                this.settings.allowContentAccess = true
                                this.settings.allowFileAccessFromFileURLs = true
                                this.settings.allowUniversalAccessFromFileURLs = true
                                
                                this.isVerticalScrollBarEnabled = false
                                this.isHorizontalScrollBarEnabled = false
                                
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                    setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                                        if (scrollY != oldScrollY) {
                                            isScrolled = true
                                            scrollJob?.cancel()
                                            scrollJob = viewCoroutineScope.launch {
                                                kotlinx.coroutines.delay(1000)
                                                isScrolled = false
                                            }
                                        }
                                    }
                                }

                                addJavascriptInterface(WebAppInterface(
                                    onTapCallback = { latestOnTap.value() },
                                    onNextChapter = {
                                        if (latestChapterIndex.value < epubBook!!.spineFiles.size) {
                                            latestOnPageChanged.value(latestChapterIndex.value + 1)
                                        }
                                    },
                                    onPrevChapter = {
                                        if (latestChapterIndex.value > 1) {
                                            latestOnPageChanged.value(latestChapterIndex.value - 1)
                                        }
                                    },
                                    onTextSelectedCallback = { text, top, bottom, cfiRange ->
                                        latestOnTextSelected.value(text, top, bottom, cfiRange)
                                    },
                                    onSelectionFinishedCallback = { text, top, bottom, cfiRange ->
                                        latestOnSelectionFinished.value(text, top, bottom, cfiRange)
                                    },
                                    onTextSelectionClearedCallback = {
                                        latestOnTextSelectionCleared.value()
                                    },
                                    onAnnotationClickedCallback = { annId, top, bottom ->
                                        latestOnAnnotationClicked.value(annId, top, bottom)
                                    },
                                    onScrollProgressCallback = { progress ->
                                        latestOnScrollProgress.value(progress)
                                    },
                                    onAnnotationPositionsCallback = { positionsStr ->
                                        if (positionsStr.isNotBlank()) {
                                            val posMap = positionsStr.split(",").mapNotNull {
                                                val parts = it.split(":")
                                                if (parts.size == 2) {
                                                    parts[0].toIntOrNull()?.let { id -> Pair(id, parts[1].toFloatOrNull() ?: 0f) }
                                                } else null
                                            }
                                            latestOnAnnotationPositions.value(posMap)
                                        }
                                    }
                                ), "Android")
                                webViewClient = object : android.webkit.WebViewClient() {
                                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                        val tagData = view?.tag as? Pair<*, *>
                                        val anns = (tagData?.first as? String) ?: ""
                                        val targetId = tagData?.second as? Int
                                        view?.evaluateJavascript(js, null)
                                        view?.evaluateJavascript("if(window.renderAnnotations) { window.renderAnnotations('${anns.replace("'", "\\'")}'); setTimeout(function(){ reportPositions(); }, 500); }", null)
                                        if (targetId != null) {
                                            view?.evaluateJavascript("javascript:scrollToAnnotation($targetId);", null)
                                        }
                                    }
                                }
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        update = { webView ->
                            webView.tag = Pair(annotationsJson, targetScrollAnnId)
                            val currentUrl = webView.url
                            if (currentUrl != htmlUrl) {
                                webView.loadUrl(htmlUrl)
                            } else {
                                webView.evaluateJavascript(js, null)
                                webView.evaluateJavascript(
                                    "document.querySelectorAll('.inferead-annotation').forEach(function(e){ e.outerHTML = e.innerHTML; });", null
                                )
                                webView.evaluateJavascript(
                                    "if(window.renderAnnotations) { window.renderAnnotations('${annotationsJson.replace("'", "\\'")}'); setTimeout(function(){ reportPositions(); }, 500); }", null
                                )
                                if (targetScrollAnnId != null) {
                                    webView.evaluateJavascript("javascript:scrollToAnnotation($targetScrollAnnId);", null)
                                }
                            }
                        }
                    )
                    
                    androidx.compose.runtime.LaunchedEffect(targetVerticalProgress) {
                        if (targetVerticalProgress != null) {
                            webViewRef?.evaluateJavascript("javascript:scrollToProgress($targetVerticalProgress);", null)
                        }
                    }

                    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isScrolled && (webViewRef?.scrollY ?: 0) > 0 && !settings.isHorizontalScroll,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
                        modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(top = 70.dp, end = 16.dp)
                    ) {
                        androidx.compose.material3.FloatingActionButton(
                            onClick = { webViewRef?.scrollTo(0, 0) },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            modifier = androidx.compose.ui.Modifier.size(40.dp),
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move to Top",
                                modifier = androidx.compose.ui.Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
    }
}

@Composable
fun CbzPagePreview(filePath: String, pageIndex: Int, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var imagePath by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(filePath, pageIndex) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val extractDir = java.io.File(context.cacheDir, "comic_cache_${filePath.hashCode()}")
            if (extractDir.exists()) {
                val images = extractDir.walkTopDown().filter { it.isFile && (it.extension.equals("jpg", true) || it.extension.equals("jpeg", true) || it.extension.equals("png", true) || it.extension.equals("webp", true)) }.sortedBy { it.name }.toList()
                if (pageIndex >= 0 && pageIndex < images.size) {
                    imagePath = images[pageIndex].absolutePath
                }
            }
        }
    }
    
    if (imagePath != null) {
        coil.compose.AsyncImage(
            model = imagePath,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier
        )
    } else {
        androidx.compose.foundation.layout.Box(modifier = modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant))
    }
}

class CustomWebView(context: android.content.Context) : android.webkit.WebView(context) {
    var activeHighlightMode: String? = null

    override fun startActionMode(callback: android.view.ActionMode.Callback, type: Int): android.view.ActionMode? {
        return suppressActionMode(callback, type)
    }

    override fun startActionMode(callback: android.view.ActionMode.Callback): android.view.ActionMode? {
        return suppressActionMode(callback, -1)
    }
    
    private fun suppressActionMode(callback: android.view.ActionMode.Callback, type: Int): android.view.ActionMode? {
        val dummyCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                menu?.clear()
                return false // Returning false prevents the action mode from starting
            }
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                return false
            }
            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean {
                return false
            }
            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
        }
        return if (type == -1) super.startActionMode(dummyCallback) else super.startActionMode(dummyCallback, type)
    }
}

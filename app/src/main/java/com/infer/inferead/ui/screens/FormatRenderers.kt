@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.infer.inferead.ui.screens

import com.infer.inferead.viewmodel.ContrastMode
import com.infer.inferead.viewmodel.ReaderSettings
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

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

fun Modifier.zoomable(
    scale: Float,
    offset: androidx.compose.ui.geometry.Offset,
    onTransform: (Float, androidx.compose.ui.geometry.Offset, Boolean) -> Unit
) = this.pointerInput(Unit) {
    val size = this.size
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val pointers = event.changes.size
            if (pointers > 1) {
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = false)
                val newScale = (scale * zoom).coerceIn(1f, 5f)
                val isZoomed = newScale > 1.05f
                val pivot = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                val d = centroid - pivot
                val newOffset = offset + pan - d * (newScale / scale - 1f)
                val maxX = (size.width * (newScale - 1f)) / 2f
                val maxY = (size.height * (newScale - 1f)) / 2f
                val clampedOffset = if (newScale <= 1.05f) androidx.compose.ui.geometry.Offset.Zero else androidx.compose.ui.geometry.Offset(newOffset.x.coerceIn(-maxX, maxX), newOffset.y.coerceIn(-maxY, maxY))
                onTransform(if (newScale <= 1.05f) 1f else newScale, clampedOffset, isZoomed)
                event.changes.forEach { it.consume() }
            } else if (scale > 1.05f && pointers == 1) {
                val pan = event.calculatePan()
                val newOffset = offset + pan
                val maxX = (size.width * (scale - 1f)) / 2f
                val maxY = (size.height * (scale - 1f)) / 2f
                val clampedOffset = androidx.compose.ui.geometry.Offset(newOffset.x.coerceIn(-maxX, maxX), newOffset.y.coerceIn(-maxY, maxY))
                onTransform(scale, clampedOffset, true)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
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
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    val pdfRenderer = remember { PdfRenderer(parcelFileDescriptor) }
    val mutex = remember { Mutex() }
    val bitmapCache = remember { android.util.LruCache<Int, Bitmap>(6) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            pdfRenderer.close()
            parcelFileDescriptor.close()
        }
    }

    LaunchedEffect(pdfRenderer.pageCount) {
        onTotalPages(pdfRenderer.pageCount)
    }

    // Helper: build color filter for a page bitmap
    @Composable
    fun pageColorFilter(): ColorFilter? {
        return remember(contrastMode, isWarmFilterActive) {
            val baseMatrix = when (contrastMode) {
                ContrastMode.Dark, ContrastMode.HighContrastDark -> ColorMatrix(floatArrayOf(-1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f))
                ContrastMode.HighContrastLight -> { val c=1.4f; val t=(-0.5f*c+0.5f)*255f; ColorMatrix(floatArrayOf(c,0f,0f,0f,t, 0f,c,0f,0f,t, 0f,0f,c,0f,t, 0f,0f,0f,1f,0f)) }
                ContrastMode.EInk -> { val c=2f; val t=(-0.5f*c+0.5f)*255f; ColorMatrix(floatArrayOf(0.213f*c,0.715f*c,0.072f*c,0f,t, 0.213f*c,0.715f*c,0.072f*c,0f,t, 0.213f*c,0.715f*c,0.072f*c,0f,t, 0f,0f,0f,1f,0f)) }
                else -> null
            }
            val warmMatrix = if (isWarmFilterActive && contrastMode != ContrastMode.EInk) ColorMatrix(floatArrayOf(0.957f,0f,0f,0f,0f, 0f,0.925f,0f,0f,0f, 0f,0f,0.847f,0f,0f, 0f,0f,0f,1f,0f)) else null
            val finalMatrix = if (baseMatrix != null && warmMatrix != null) concatColorMatrices(warmMatrix, baseMatrix) else baseMatrix ?: warmMatrix
            finalMatrix?.let { ColorFilter.colorMatrix(it) }
        }
    }

    // Shared function to render a page to bitmap
    suspend fun renderPage(index: Int, density: Float): Bitmap? {
        return bitmapCache.get(index) ?: mutex.withLock {
            bitmapCache.get(index) ?: run {
                var page: PdfRenderer.Page? = null
                try {
                    page = pdfRenderer.openPage(index)
                    val renderWidth = (page.width * density * 1.5f).toInt()
                    val renderHeight = (page.height * density * 1.5f).toInt()
                    val bmp = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmapCache.put(index, bmp)
                    bmp
                } catch (e: Exception) { null }
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
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isZoomed
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
                    .zoomable(scale, offset) { newScale, newOffset, zoomed ->
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
                                .zoomable(scale, offset) { newScale, newOffset, zoomed ->
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
                    onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
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
fun TextViewer(
    filePath: String,
    settings: ReaderSettings = ReaderSettings(),
    isReaderModeActive: Boolean = false,
    currentPage: Int = 1,
    scrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState(),
    onPageChanged: (Int) -> Unit = {},
    onTotalPages: (Int) -> Unit = {},
    onTap: () -> Unit = {},
    activeHighlightColor: String? = null,
    onTextSelected: (String, String) -> Unit = { _, _ -> },
    annotations: List<com.infer.inferead.data.Annotation> = emptyList()
) {
    var fontScale by remember { mutableFloatStateOf(1f) }
    var isZooming by remember { mutableStateOf(false) }
    var textContent by remember { mutableStateOf("Loading text...") }
    
    val file = File(filePath)

    val pages = remember(textContent) {
        segmentTextIntoPages(textContent)
    }
    
    val horizontalListState = rememberLazyListState()
    
    LaunchedEffect(pages.size, settings.isHorizontalScroll) {
        if (settings.isHorizontalScroll && pages.isNotEmpty()) {
            onTotalPages(pages.size)
        } else {
            onTotalPages(0)
        }
    }
    
    val firstVisibleItem by remember { derivedStateOf { horizontalListState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleItem, settings.isHorizontalScroll) {
        if (settings.isHorizontalScroll && pages.isNotEmpty()) {
            onPageChanged(firstVisibleItem + 1)
        }
    }
    
    var lastTargetPage by remember { mutableIntStateOf(-1) }
    LaunchedEffect(currentPage, pages.size, settings.isHorizontalScroll) {
        if (settings.isHorizontalScroll && currentPage > 0 && currentPage <= pages.size && !horizontalListState.isScrollInProgress && currentPage != horizontalListState.firstVisibleItemIndex + 1 && currentPage != lastTargetPage) {
            lastTargetPage = currentPage
            horizontalListState.scrollToItem(currentPage - 1)
        }
    }

    LaunchedEffect(scrollState.maxValue, settings.isHorizontalScroll) {
        if (!settings.isHorizontalScroll && scrollState.maxValue > 0) {
            onTotalPages(100)
        }
    }

    val isScrollInProgress = scrollState.isScrollInProgress
    LaunchedEffect(scrollState.value, settings.isHorizontalScroll, isScrollInProgress) {
        // Disabled for LazyColumn
    }

    var lastTargetPageVertical by remember { mutableIntStateOf(-1) }
    LaunchedEffect(currentPage, settings.isHorizontalScroll) {
        // Handled by LazyColumn now
    }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            if (file.exists()) {
                val ext = file.extension.lowercase()
                textContent = if (ext == "docx") {
                    try {
                        val zip = java.util.zip.ZipFile(file)
                        val entry = zip.getEntry("word/document.xml")
                        if (entry != null) {
                            val xml = zip.getInputStream(entry).reader().readText()
                            xml.replace(Regex("</w:p>"), "\n").replace(Regex("<.*?>"), "")
                        } else {
                            "Could not parse DOCX: word/document.xml not found."
                        }
                    } catch (e: Exception) {
                        "Error parsing DOCX: ${e.message}"
                    }
                } else if (ext == "doc") {
                    "Legacy .doc format is not supported. Please convert to .docx or .txt."
                } else {
                    file.readText()
                }
            } else {
                textContent = "File not found."
            }
        }
    }

    val backgroundColor = remember(settings.contrastMode, settings.isWarmFilterActive) {
        when (settings.contrastMode) {
            ContrastMode.Dark -> androidx.compose.ui.graphics.Color(0xFF1C1C1E)
            ContrastMode.HighContrastDark -> androidx.compose.ui.graphics.Color(0xFF000000)
            ContrastMode.HighContrastLight, ContrastMode.EInk -> androidx.compose.ui.graphics.Color(0xFFFFFFFF)
            ContrastMode.Normal -> {
                if (settings.isWarmFilterActive) {
                    androidx.compose.ui.graphics.Color(0xFFF4ECD8)
                } else {
                    androidx.compose.ui.graphics.Color(0xFFFDFCF7)
                }
            }
        }
    }

    val textColor = remember(settings.contrastMode, settings.isWarmFilterActive) {
        when (settings.contrastMode) {
            ContrastMode.Dark -> androidx.compose.ui.graphics.Color(0xFFE5E5EA)
            ContrastMode.HighContrastDark -> androidx.compose.ui.graphics.Color(0xFFFFFFFF)
            ContrastMode.HighContrastLight, ContrastMode.EInk -> androidx.compose.ui.graphics.Color(0xFF000000)
            ContrastMode.Normal -> {
                if (settings.isWarmFilterActive) {
                    androidx.compose.ui.graphics.Color(0xFF5C4033)
                } else {
                    androidx.compose.ui.graphics.Color(0xFF1C1C1E)
                }
            }
        }
    }

    val finalBgModifier = if (backgroundColor != androidx.compose.ui.graphics.Color.Unspecified) {
        Modifier.background(backgroundColor)
    } else {
        Modifier
    }

    val finalTextColor = if (textColor != androidx.compose.ui.graphics.Color.Unspecified) {
        textColor
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val fontFamily = remember(settings.fontFamily, context) {
        when (settings.fontFamily) {
            "Serif", "Bookerly" -> androidx.compose.ui.text.font.FontFamily.Serif
            "Monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
            "Google Sans" -> androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(path = "fonts/google_sans.ttf", assetManager = context.assets))
            "Literata" -> androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(path = "fonts/literata.ttf", assetManager = context.assets))
            else -> androidx.compose.ui.text.font.FontFamily.SansSerif
        }
    }

    val fontWeight = if (settings.contrastMode == ContrastMode.EInk) {
        androidx.compose.ui.text.font.FontWeight.Black
    } else if (settings.fontBold) {
        androidx.compose.ui.text.font.FontWeight.Bold
    } else {
        androidx.compose.ui.text.font.FontWeight.Normal
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(finalBgModifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onTap()
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointersCount = event.changes.size
                        if (pointersCount > 1) {
                            val zoomChange = event.calculateZoom()
                            val oldScale = fontScale
                            fontScale = (fontScale * zoomChange).coerceIn(0.5f, 6f)
                            if (fontScale != oldScale) {
                                isZooming = true
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        if (settings.isHorizontalScroll) {
            val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = horizontalListState)
            LazyRow(
                state = horizontalListState,
                flingBehavior = snapFlingBehavior,
                modifier = Modifier.fillMaxSize()
            ) {
                items(pages.size, key = { it }) { index ->
                    Box(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .fillParentMaxHeight()
                            .padding(16.dp)
                    ) {
                        var textLayoutResult by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                        var selectionStart by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(-1) }
                        var selectionEnd by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(-1) }

                        val annotatedText = androidx.compose.ui.text.buildAnnotatedString {
                            append(pages[index])
                            
                            annotations.forEach { ann ->
                                if (ann.cfiRange.startsWith("TXT_PAGE_${index}_")) {
                                    val parts = ann.cfiRange.split("_")
                                    if (parts.size >= 5) {
                                        val min = parts[3].toIntOrNull() ?: return@forEach
                                        val max = parts[4].toIntOrNull() ?: return@forEach
                                        val colorInt = try { android.graphics.Color.parseColor(ann.colorHex) } catch (e: Exception) { android.graphics.Color.YELLOW }
                                        addStyle(
                                            style = androidx.compose.ui.text.SpanStyle(background = androidx.compose.ui.graphics.Color(colorInt).copy(alpha = 0.4f)),
                                            start = min.coerceIn(0, pages[index].length),
                                            end = max.coerceIn(0, pages[index].length)
                                        )
                                    }
                                }
                            }
                            
                            if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                                val min = minOf(selectionStart, selectionEnd).coerceIn(0, pages[index].length)
                                val max = maxOf(selectionStart, selectionEnd).coerceIn(0, pages[index].length)
                                val colorInt = try { android.graphics.Color.parseColor(activeHighlightColor ?: "#c25d5d") } catch (e: Exception) { android.graphics.Color.RED }
                                addStyle(
                                    style = androidx.compose.ui.text.SpanStyle(background = androidx.compose.ui.graphics.Color(colorInt).copy(alpha = 0.4f)),
                                    start = min,
                                    end = max
                                )
                            }
                        }

                        androidx.compose.material3.Text(
                            text = annotatedText,
                            onTextLayout = { textLayoutResult = it },
                            fontSize = (16 * fontScale * settings.fontSizeMultiplier).sp,
                            color = finalTextColor,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight,
                            lineHeight = (24 * fontScale * settings.fontSizeMultiplier * settings.lineSpacingMultiplier).sp,
                            letterSpacing = ((settings.wordSpacingMultiplier - 1.0f) * 0.1f).em,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(activeHighlightColor, annotations) {
                                    if (!activeHighlightColor.isNullOrEmpty()) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                textLayoutResult?.let { layout ->
                                                    val pos = layout.getOffsetForPosition(offset).coerceIn(0, pages[index].length)
                                                    selectionStart = pos
                                                    selectionEnd = pos
                                                }
                                            },
                                            onDragEnd = {
                                                if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                                                    val min = minOf(selectionStart, selectionEnd).coerceIn(0, pages[index].length)
                                                    val max = maxOf(selectionStart, selectionEnd).coerceIn(0, pages[index].length)
                                                    if (min < max) {
                                                        val selectedText = pages[index].substring(min, max)
                                                        val bounds = "TXT_PAGE_${index}_${min}_${max}"
                                                        onTextSelected(selectedText, bounds)
                                                    }
                                                    selectionStart = -1
                                                    selectionEnd = -1
                                                }
                                            }
                                        ) { change, _ ->
                                            textLayoutResult?.let { layout ->
                                                selectionEnd = layout.getOffsetForPosition(change.position).coerceIn(0, pages[index].length)
                                            }
                                        }
                                    } else {
                                        // Tap to delete existing highlight
                                        detectTapGestures(
                                            onTap = { offset ->
                                                textLayoutResult?.let { layout ->
                                                    val pos = layout.getOffsetForPosition(offset).coerceIn(0, pages[index].length)
                                                    annotations.forEach { ann ->
                                                        if (ann.cfiRange.startsWith("TXT_PAGE_${index}_")) {
                                                            val parts = ann.cfiRange.split("_")
                                                            if (parts.size >= 5) {
                                                                val min = parts[3].toIntOrNull() ?: return@forEach
                                                                val max = parts[4].toIntOrNull() ?: return@forEach
                                                                if (pos in min..max) {
                                                                    // We could trigger a delete here, but passing it through onTextSelected is tricky.
                                                                    // Let's pass a special bounds to delete
                                                                    onTextSelected("DELETE", ann.id.toString())
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                onTap()
                                            }
                                        )
                                    }
                                }
                        )
                    }
                }
            }
        } else {
            val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = horizontalListState)
            LazyColumn(
                state = horizontalListState,
                flingBehavior = snapFlingBehavior,
                modifier = Modifier.fillMaxSize()
            ) {
                items(pages.size, key = { it }) { index ->
                    Box(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .fillParentMaxHeight()
                            .padding(16.dp)
                    ) {
                        var textLayoutResult by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                        var selectionStart by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(-1) }
                        var selectionEnd by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(-1) }

                        val annotatedText = androidx.compose.ui.text.buildAnnotatedString {
                            append(pages[index])
                            
                            annotations.forEach { ann ->
                                if (ann.cfiRange.startsWith("TXT_PAGE_${index}_")) {
                                    val parts = ann.cfiRange.split("_")
                                    if (parts.size >= 5) {
                                        val min = parts[3].toIntOrNull() ?: return@forEach
                                        val max = parts[4].toIntOrNull() ?: return@forEach
                                        val colorInt = try { android.graphics.Color.parseColor(ann.colorHex) } catch (e: Exception) { android.graphics.Color.YELLOW }
                                        addStyle(
                                            style = androidx.compose.ui.text.SpanStyle(background = androidx.compose.ui.graphics.Color(colorInt).copy(alpha = 0.4f)),
                                            start = min.coerceIn(0, pages[index].length),
                                            end = max.coerceIn(0, pages[index].length)
                                        )
                                    }
                                }
                            }
                            
                            if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                                val min = minOf(selectionStart, selectionEnd).coerceIn(0, pages[index].length)
                                val max = maxOf(selectionStart, selectionEnd).coerceIn(0, pages[index].length)
                                val colorInt = try { android.graphics.Color.parseColor(activeHighlightColor ?: "#c25d5d") } catch (e: Exception) { android.graphics.Color.RED }
                                addStyle(
                                    style = androidx.compose.ui.text.SpanStyle(background = androidx.compose.ui.graphics.Color(colorInt).copy(alpha = 0.4f)),
                                    start = min,
                                    end = max
                                )
                            }
                        }

                        androidx.compose.material3.Text(
                            text = annotatedText,
                            onTextLayout = { textLayoutResult = it },
                            fontSize = (16 * fontScale * settings.fontSizeMultiplier).sp,
                            color = finalTextColor,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight,
                            lineHeight = (24 * fontScale * settings.fontSizeMultiplier * settings.lineSpacingMultiplier).sp,
                            letterSpacing = ((settings.wordSpacingMultiplier - 1.0f) * 0.1f).em,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(activeHighlightColor, annotations) {
                                    if (!activeHighlightColor.isNullOrEmpty()) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                textLayoutResult?.let { layout ->
                                                    val pos = layout.getOffsetForPosition(offset).coerceIn(0, pages[index].length)
                                                    selectionStart = pos
                                                    selectionEnd = pos
                                                }
                                            },
                                            onDragEnd = {
                                                if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                                                    val min = minOf(selectionStart, selectionEnd).coerceIn(0, pages[index].length)
                                                    val max = maxOf(selectionStart, selectionEnd).coerceIn(0, pages[index].length)
                                                    if (min < max) {
                                                        val selectedText = pages[index].substring(min, max)
                                                        val bounds = "TXT_PAGE_${index}_${min}_${max}"
                                                        onTextSelected(selectedText, bounds)
                                                    }
                                                    selectionStart = -1
                                                    selectionEnd = -1
                                                }
                                            }
                                        ) { change, _ ->
                                            textLayoutResult?.let { layout ->
                                                selectionEnd = layout.getOffsetForPosition(change.position).coerceIn(0, pages[index].length)
                                            }
                                        }
                                    } else {
                                        // Tap to delete existing highlight
                                        detectTapGestures(
                                            onTap = { offset ->
                                                textLayoutResult?.let { layout ->
                                                    val pos = layout.getOffsetForPosition(offset).coerceIn(0, pages[index].length)
                                                    annotations.forEach { ann ->
                                                        if (ann.cfiRange.startsWith("TXT_PAGE_${index}_")) {
                                                            val parts = ann.cfiRange.split("_")
                                                            if (parts.size >= 5) {
                                                                val min = parts[3].toIntOrNull() ?: return@forEach
                                                                val max = parts[4].toIntOrNull() ?: return@forEach
                                                                if (pos in min..max) {
                                                                    // We could trigger a delete here, but passing it through onTextSelected is tricky.
                                                                    // Let's pass a special bounds to delete
                                                                    onTextSelected("DELETE", ann.id.toString())
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                onTap()
                                            }
                                        )
                                    }
                                }
                        )
                    }
                }
            }
        }

        // Zoom Indicator Overlay
        if (isZooming) {
            LaunchedEffect(fontScale) {
                kotlinx.coroutines.delay(1000)
                isZooming = false
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Font Size: ${(fontScale * settings.fontSizeMultiplier * 100).roundToInt()}%",
                    color = MaterialTheme.colorScheme.background,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        
        if (!settings.isHorizontalScroll) {
            val coroutineScope = rememberCoroutineScope()
            androidx.compose.animation.AnimatedVisibility(
                visible = scrollState.isScrollInProgress || !isReaderModeActive,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp, end = 16.dp)
            ) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = {
                        coroutineScope.launch { scrollState.animateScrollTo(0) }
                    },
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
fun ImageViewer(filePath: String, isNoir: Boolean = false, isNegative: Boolean = false, onTap: () -> Unit = {}) {
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
            .zoomable(scale, offset) { newScale, newOffset, _ ->
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
            androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), userScrollEnabled = !isZoomed) { page ->
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                
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
                        )
                        .zoomable(scale, offset) { newScale, newOffset, zoomed ->
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
                                            } else {
                                                onTap()
                                            }
                                        } else {
                                            if (tapOffset.x > width * 0.2f && tapOffset.x < width * 0.8f) {
                                                onTap()
                                            }
                                        }
                                    }
                                }
                            )
                        },
                    contentScale = ContentScale.Fit
                )
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
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), userScrollEnabled = !isZoomed) {
                items(images) { image ->
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                    
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
                            )
                            .zoomable(scale, offset) { newScale, newOffset, zoomed ->
                                scale = newScale
                                offset = newOffset
                                isZoomed = zoomed
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = { onTap() }
                                )
                            },
                        contentScale = ContentScale.FillWidth
                    )
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
    private val onSelectionFinishedCallback: (String, Float, Float, String) -> Unit = { _, _, _, _ -> }
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
    fun onTextSelected(text: String, top: Float, bottom: Float, cfiRange: String) { 
        onTextSelectedCallback(text, top, bottom, cfiRange) 
    }

    @android.webkit.JavascriptInterface
    fun onSelectionFinished(text: String, top: Float, bottom: Float, cfiRange: String) { 
        onSelectionFinishedCallback(text, top, bottom, cfiRange) 
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
    onSelectionFinished: (String, Float, Float, String) -> Unit = { _, _, _, _ -> },
    onTextSelectionCleared: () -> Unit = {},
    onAnnotationClicked: (Int, Float, Float) -> Unit = { _, _, _ -> },
    annotations: List<com.infer.inferead.data.Annotation> = emptyList(),
    targetScrollAnnId: Int? = null
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
                            padding: 60px 16px !important;
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
                                clearTimeout(scrollTimeout);
                                scrollTimeout = setTimeout(function() {
                                    Android.reportScroll(false);
                                }, 500);
                            }
                            window.addEventListener('scroll', handleScroll, {passive: true});
                            var wrapEl = document.getElementById('infe-wrapper');
                            if (wrapEl) { wrapEl.addEventListener('scroll', handleScroll, {passive: true}); }
                            
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

                            document.addEventListener('selectionchange', function() {
                                var sel = window.getSelection();
                                if (!sel || sel.isCollapsed) {
                                    Android.onTextSelectionCleared();
                                } else {
                                    var text = sel.toString();
                                    if (text.length > 0) {
                                        try {
                                            var range = sel.getRangeAt(0);
                                            var rect = range.getBoundingClientRect();
                                            var cfi = getRangeOffsets(range);
                                            Android.onTextSelected(text, rect.top, rect.bottom, cfi);
                                        } catch (e) {}
                                    }
                                }
                            });
                            
                            function notifySelectionFinished() {
                                var sel = window.getSelection();
                                if (sel && !sel.isCollapsed) {
                                    try {
                                        var range = sel.getRangeAt(0);
                                        var rect = range.getBoundingClientRect();
                                        var text = sel.toString();
                                        var cfi = getRangeOffsets(range);
                                        Android.onSelectionFinished(text, rect.top, rect.bottom, cfi);
                                    } catch(e) {}
                                }
                            }
                            document.addEventListener('mouseup', notifySelectionFinished);
                            document.addEventListener('touchend', function(e) {
                                // Small delay so the selection is committed before we read it
                                setTimeout(notifySelectionFinished, 100);
                            }, {passive: true});
                            
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
                                } catch(e) {}
                            };
                            
                            window.scrollToAnnotation = function(id) {
                                var target = document.getElementById('ann-' + id);
                                if (target) {
                                    target.scrollIntoView({behavior: "smooth", block: "center"});
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

                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(actualBg)))
                ) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        factory = { ctx ->
                            com.infer.inferead.ui.screens.CustomWebView(ctx).apply {
                                this.settings.javaScriptEnabled = true
                                this.settings.allowFileAccess = true
                                this.settings.allowContentAccess = true
                                this.settings.allowFileAccessFromFileURLs = true
                                this.settings.allowUniversalAccessFromFileURLs = true
                                
                                this.isVerticalScrollBarEnabled = false
                                this.isHorizontalScrollBarEnabled = false

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
                                    }
                                ), "Android")
                                webViewClient = object : android.webkit.WebViewClient() {
                                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                        val anns = (view?.tag as? String) ?: ""
                                        view?.evaluateJavascript(js, null)
                                        view?.evaluateJavascript("if(window.renderAnnotations) { window.renderAnnotations('${anns.replace("'", "\\'")}'); }", null)
                                    }
                                }
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        update = { webView ->
                            webView.tag = annotationsJson
                            val currentUrl = webView.url
                            if (currentUrl != htmlUrl) {
                                webView.loadUrl(htmlUrl)
                            } else {
                                webView.evaluateJavascript(js, null)
                                webView.evaluateJavascript(
                                    "document.querySelectorAll('.inferead-annotation').forEach(function(e){ e.outerHTML = e.innerHTML; });", null
                                )
                                webView.evaluateJavascript(
                                    "if(window.renderAnnotations) { window.renderAnnotations('${annotationsJson.replace("'", "\\'")}'); }", null
                                )
                                if (targetScrollAnnId != null) {
                                    webView.evaluateJavascript("javascript:scrollToAnnotation($targetScrollAnnId);", null)
                                }
                            }
                        }
                    )
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

fun TXTReader(
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
    targetScrollAnnId: Int? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var txtHtml by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var isLoading by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    var errorMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(filePath) {
        isLoading = true
        val file = java.io.File(filePath)
        val loadedText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (file.exists()) {
                val ext = file.extension.lowercase()
                if (ext == "docx") {
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
                    "Legacy .doc format is not supported."
                } else {
                    file.readText()
                }
            } else null
        }
        
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            if (loadedText != null) {
                txtHtml = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0\"></head><body><div id='txt-content' style='white-space: pre-wrap;'>" + loadedText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</div></body></html>"
                onTotalPagesLoaded(100, null)
            } else {
                errorMessage = "Failed to load file."
            }
            isLoading = false
        }
    }

    if (isLoading) {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text("Loading...", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
        }
    } else if (txtHtml == null) {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text(errorMessage ?: "Failed to load file.", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
        }
    } else {

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
                val isFixedLayout = false
                val isArchiveOrg = false
                
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
                            var isManga = false;
                            
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
                            
                            if (!document.getElementById('txt-next-btn')) {
                                var btnContainer = document.createElement('div');
                                btnContainer.id = 'txt-next-btn';
                                btnContainer.style.textAlign = 'center';
                                btnContainer.style.padding = '40px';
                                btnContainer.style.borderTop = '1px solid gray';
                                btnContainer.style.marginTop = '40px';
                                btnContainer.style.marginBottom = '20px';
                                
                                var nextBtn = document.createElement('div');
                                nextBtn.innerHTML = '';
                                nextBtn.style.fontWeight = 'bold';
                                nextBtn.style.fontSize = '18px';
                                nextBtn.style.color = 'inherit';
                                nextBtn.style.marginBottom = '20px';
                                nextBtn.onclick = function() { Android.nextChapter(); };
                                
                                
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
                                var isBtn = e.target.id === 'txt-next-btn' || (e.target.closest && e.target.closest('#txt-next-btn'));
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

                                addJavascriptInterface(TxtWebAppInterface(
                                    onTapCallback = { latestOnTap.value() },
                                    onNextChapter = {
                                        if (false) {
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
                                        val tagData = view?.tag as? Pair<*, *>
                                        val anns = (tagData?.first as? String) ?: ""
                                        val targetId = tagData?.second as? Int
                                        view?.evaluateJavascript(js, null)
                                        view?.evaluateJavascript("if(window.renderAnnotations) { window.renderAnnotations('${anns.replace("'", "\\'")}'); }", null)
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
                                webView.loadDataWithBaseURL("file:///android_asset/", txtHtml!!, "text/html", "utf-8", null)
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

class TxtWebAppInterface(
    private val onTapCallback: () -> Unit,
    private val onNextChapter: () -> Unit = {},
    private val onPrevChapter: () -> Unit = {},
    private val onScrollStateChanged: (Boolean) -> Unit = {},
    private val onTextSelectedCallback: (String, Float, Float, String) -> Unit = { _, _, _, _ -> },
    private val onTextSelectionClearedCallback: () -> Unit = {},
    private val onAnnotationClickedCallback: (Int, Float, Float) -> Unit = { _, _, _ -> },
    private val onSelectionFinishedCallback: (String, Float, Float, String) -> Boolean = { _, _, _, _ -> false }
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
    fun onSelectionFinished(text: String, top: Float, bottom: Float, cfiRange: String): Boolean { 
        return onSelectionFinishedCallback(text, top, bottom, cfiRange) 
    }
    
    @android.webkit.JavascriptInterface
    fun onTextSelectionCleared() { onTextSelectionClearedCallback() }
    
    @android.webkit.JavascriptInterface
    fun onAnnotationClicked(annId: Int, top: Float, bottom: Float) { onAnnotationClickedCallback(annId, top, bottom) }
}


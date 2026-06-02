import re

with open('app/src/main/java/com/infer/inferead/ui/screens/FormatRenderers.kt', 'r', encoding='utf-8') as f:
    fr_code = f.read()

# 1. Extract EPUBReader and WebAppInterface
epub_match = re.search(r'(fun EPUBReader\(.*?^}\n)\n@Composable\nfun CbzPagePreview', fr_code, re.MULTILINE | re.DOTALL)
epub_code = epub_match.group(1)
web_app = re.search(r'(class WebAppInterface.*?^}\n)', fr_code, re.MULTILINE | re.DOTALL).group(1)

txt_code = epub_code.replace('fun EPUBReader(', 'fun TXTReader(')

# Replace loading
loading_pattern = re.compile(r'    val context = androidx\.compose\.ui\.platform\.LocalContext\.current.*?    if \(isLoading\) \{', re.DOTALL)
txt_loading = '''    val context = androidx.compose.ui.platform.LocalContext.current
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
                            xml.replace(Regex("</w:p>"), "\\\\n").replace(Regex("<.*?>"), "")
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
                txtHtml = "<html><head><meta name=\\"viewport\\" content=\\"width=device-width, initial-scale=1.0, maximum-scale=5.0\\"></head><body><div id='txt-content' style='white-space: pre-wrap;'>" + loadedText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</div></body></html>"
                onTotalPagesLoaded(100, null)
            } else {
                errorMessage = "Failed to load file."
            }
            isLoading = false
        }
    }

    if (isLoading) {'''
txt_code = loading_pattern.sub(txt_loading, txt_code)

# Replace checks
check_pattern = re.compile(r'    if \(isLoading\) \{.*?    \} else \{', re.DOTALL)
txt_check = '''    if (isLoading) {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text("Loading...", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
        }
    } else if (txtHtml == null) {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text(errorMessage ?: "Failed to load file.", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
        }
    } else {'''
txt_code = check_pattern.sub(txt_check, txt_code)

# Remove chapter logic
remove_chapter = re.compile(r'        val chapterIndexSafe =.*?        if \(chapterPath != null\) \{', re.DOTALL)
txt_code = remove_chapter.sub('', txt_code)

txt_code = txt_code.replace('loadUrl(htmlUrl)', 'loadDataWithBaseURL("file:///android_asset/", txtHtml!!, "text/html", "utf-8", null)')

# Remove matching brace for if (chapterPath != null) {
remove_brace = re.compile(r'                    \}\)\n                \}\n            \}\n        \}\n    \}\n\}', re.DOTALL)
txt_code = remove_brace.sub('                    })\n                }\n            }\n        }\n}', txt_code)

# Fix strings
txt_code = txt_code.replace('val isFixedLayout = epubBook!!.isFixedLayout', 'val isFixedLayout = false')
txt_code = txt_code.replace('val isArchiveOrg = epubBook!!.isArchiveOrg', 'val isArchiveOrg = false')
txt_code = txt_code.replace('var isManga = ($isFixedLayout || (imgs > 0 && textLen < 150));', 'var isManga = false;')
txt_code = txt_code.replace('epub-next-btn', 'txt-next-btn')
txt_code = txt_code.replace('Load Next Chapter', '')
txt_code = txt_code.replace('btnContainer.appendChild(nextBtn);', '')
txt_code = txt_code.replace('if (latestChapterIndex.value < epubBook!!.spineFiles.size) {', 'if (false) {')
txt_code = txt_code.replace('WebAppInterface(', 'TxtWebAppInterface(')

web_app_code = web_app.replace('class WebAppInterface', 'class TxtWebAppInterface')

full_txt_code = txt_code + '\n' + web_app_code + '\n\n'

# Replace TextViewer
pattern_tv = re.compile(r'fun TextViewer\(.*?^}$(?=\n\n@Composable\nfun ImageViewer)', re.DOTALL | re.MULTILINE)
new_fr_code = pattern_tv.sub(full_txt_code, fr_code)

with open('app/src/main/java/com/infer/inferead/ui/screens/FormatRenderers.kt', 'w', encoding='utf-8') as f:
    f.write(new_fr_code)


# 2. Patch ReaderScreen.kt
with open('app/src/main/java/com/infer/inferead/ui/screens/ReaderScreen.kt', 'r', encoding='utf-8') as f:
    rs_code = f.read()

txt_reader_block = '''                        "TXT" -> TXTReader(
                            filePath = file.filePath,
                            settings = settings,
                            chapterIndex = file.currentPage,
                            onPageChanged = { page -> viewModel.updateCurrentPage(page) },
                            onTotalPagesLoaded = { total, previews -> 
                                viewModel.updateTotalPages(total)
                                chapterPreviews = previews
                            },
                            onTap = toggleReaderMode,
                            onTextSelected = { textSelected, top, bottom, cfiRange ->
                                val finalCfi = if (file.format == "TXT" || file.format == "EPUB") "${file.currentPage}|$cfiRange" else cfiRange
                                if (activeHighlightMode.isNullOrEmpty()) {
                                    textSelectionData = com.infer.inferead.ui.screens.TextSelectionData(textSelected, top, bottom, finalCfi)
                                }
                            },
                            onSelectionFinished = { textSelected, top, bottom, cfiRange ->
                                val finalCfi = if (file.format == "TXT" || file.format == "EPUB") "${file.currentPage}|$cfiRange" else cfiRange
                                if (!activeHighlightMode.isNullOrEmpty()) {
                                    if (activeHighlightMode == "COMMENT_MODE") {
                                        commentingSelectionData = com.infer.inferead.ui.screens.TextSelectionData(textSelected, top, bottom, finalCfi)
                                        showCommentDialogForSelection = true
                                        viewModel.setActiveHighlightMode("")
                                    } else {
                                        viewModel.insertAnnotation(
                                            com.infer.inferead.data.Annotation(
                                                fileId = file.id,
                                                selectedText = textSelected,
                                                cfiRange = finalCfi,
                                                colorHex = activeHighlightMode ?: "#c25d5d",
                                                timestamp = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                    true
                                } else {
                                    textSelectionData = com.infer.inferead.ui.screens.TextSelectionData(textSelected, top, bottom, finalCfi)
                                    false
                                }
                            },
                            onTextSelectionCleared = {
                                textSelectionData = null
                            },
                            onAnnotationClicked = { annId, top, bottom ->
                                val clickedAnn = pageAnns.find { it.id == annId }
                                if (clickedAnn != null) {
                                    if (clickedAnn.textComment.isNullOrEmpty()) {
                                        editingHighlight = clickedAnn
                                    } else {
                                        editingAnnotation = clickedAnn
                                        commentText = clickedAnn.textComment ?: ""
                                    }
                                }
                            },
                            annotations = pageAnns
                        )'''

pattern_txt_block = re.compile(r'                        "TXT" -> TextViewer\(.*?                        \)', re.DOTALL)
rs_code = pattern_txt_block.sub(txt_reader_block, rs_code)

rs_code = rs_code.replace('textSelectionData != null && file.format == "EPUB"', 'textSelectionData != null && (file.format == "EPUB" || file.format == "TXT")')
rs_code = rs_code.replace('if (file.format == "EPUB" && textSelectionData != null) {', 'if ((file.format == "EPUB" || file.format == "TXT") && textSelectionData != null) {')

with open('app/src/main/java/com/infer/inferead/ui/screens/ReaderScreen.kt', 'w', encoding='utf-8') as f:
    f.write(rs_code)


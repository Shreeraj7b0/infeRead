import re

with open('TXTReader.txt', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('fun EPUBReader(', 'fun TXTReader(')

loading_pattern = re.compile(r'    val context = androidx\.compose\.ui\.platform\.LocalContext\.current.*?    if \(isLoading\) \{', re.DOTALL)
txt_loading = r'''    val context = androidx.compose.ui.platform.LocalContext.current
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
                            xml.replace(Regex("</w:p>"), "\\n").replace(Regex("<.*?>"), "")
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

    if (isLoading) {'''
text = loading_pattern.sub(txt_loading, text)

check_pattern = re.compile(r'    if \(isLoading\) \{.*?    \} else \{', re.DOTALL)
txt_check = r'''    if (isLoading) {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text("Loading...", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
        }
    } else if (txtHtml == null) {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text(errorMessage ?: "Failed to load file.", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
        }
    } else {'''
text = check_pattern.sub(txt_check, text)

# Remove the chapter loading logic:
#         val chapterIndexSafe = (chapterIndex - 1).coerceIn(0, maxOf(0, epubBook!!.spineFiles.size - 1))
#         val chapterPath = epubBook!!.spineFiles.getOrNull(chapterIndexSafe)
#         if (chapterPath != null) {
remove_chapter = re.compile(r'        val chapterIndexSafe =.*?        if \(chapterPath != null\) \{', re.DOTALL)
text = remove_chapter.sub('', text)

text = text.replace('loadUrl(htmlUrl)', 'loadDataWithBaseURL("file:///android_asset/", txtHtml!!, "text/html", "utf-8", null)')

# Remove matching brace for if (chapterPath != null) {
# We will just find `                    })\n                }\n            }\n        }\n    }\n}` at the very end
remove_brace = re.compile(r'                    \}\)\n                \}\n            \}\n        \}\n    \}\n\}', re.DOTALL)
text = remove_brace.sub('                    })\n                }\n            }\n        }\n}', text)

text = text.replace('val isFixedLayout = epubBook!!.isFixedLayout', 'val isFixedLayout = false')
text = text.replace('val isArchiveOrg = epubBook!!.isArchiveOrg', 'val isArchiveOrg = false')
text = text.replace('var isManga = ($isFixedLayout || (imgs > 0 && textLen < 150));', 'var isManga = false;')
text = text.replace('epub-next-btn', 'txt-next-btn')
text = text.replace('Load Next Chapter', '')
text = text.replace('btnContainer.appendChild(nextBtn);', '')
text = text.replace('if (latestChapterIndex.value < epubBook!!.spineFiles.size) {', 'if (false) {')

text = text.replace('WebAppInterface(', 'TxtWebAppInterface(')

# Extract WebAppInterface and modify it
with open('app/src/main/java/com/infer/inferead/ui/screens/FormatRenderers.kt', 'r', encoding='utf-8') as f:
    orig_code = f.read()
web_app = re.search(r'(class WebAppInterface.*?^}\n)', orig_code, re.MULTILINE | re.DOTALL).group(1)
web_app = web_app.replace('class WebAppInterface', 'class TxtWebAppInterface')

text = text + '\n' + web_app + '\n'

with open('TXTReaderFinal.kt', 'w', encoding='utf-8') as f:
    f.write(text)

# Now apply it to FormatRenderers.kt
# We replace TextViewer exactly:
pattern = re.compile(r'fun TextViewer\(.*?^}$(?=\n\n@Composable\nfun ImageViewer)', re.DOTALL | re.MULTILINE)
new_code = pattern.sub(text, orig_code)

with open('app/src/main/java/com/infer/inferead/ui/screens/FormatRenderers.kt', 'w', encoding='utf-8') as f:
    f.write(new_code)

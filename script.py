import re

with open('EPUBReader.txt', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace EPUBReader with TXTReader
text = text.replace('fun EPUBReader(', 'fun TXTReader(')

# Change WebAppInterface to TxtWebAppInterface to keep it isolated
text = text.replace('WebAppInterface(', 'TxtWebAppInterface(')
text = text.replace('android.webkit.WebViewClient', 'android.webkit.WebViewClient') # no change

# Replace EPUB loading logic
loading_logic_pattern = re.compile(r'    var epubBook by.*?(?=    if \(isLoading\))', re.DOTALL)

txt_loading_logic = '''    val context = androidx.compose.ui.platform.LocalContext.current
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
                    "Legacy .doc format is not supported. Please convert to .docx or .txt."
                } else {
                    file.readText()
                }
            } else {
                null
            }
        }
        
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            if (loadedText != null) {
                // Wrap raw text in simple paragraph tags and preserve newlines using CSS white-space: pre-wrap
                txtHtml = "<html><head><meta name=\\"viewport\\" content=\\"width=device-width, initial-scale=1.0, maximum-scale=5.0\\"></head><body><div id='txt-content' style='white-space: pre-wrap;'>" + loadedText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</div></body></html>"
                
                // For a single txt file, total pages is dynamic based on font size and scroll position.
                // We initially set to 100 or calculate dynamically from scroll size later.
                onTotalPagesLoaded(100, null)
            } else {
                errorMessage = "Failed to load file."
            }
            isLoading = false
        }
    }
'''

text = loading_logic_pattern.sub(txt_loading_logic, text)

# Replace the epubBook checks with txtHtml checks
check_pattern = re.compile(r'    if \(isLoading\).*?(?=        if \(chapterPath != null\) {)', re.DOTALL)
txt_check = '''    if (isLoading) {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text("Loading...", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
        }
    } else if (txtHtml == null) {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text(errorMessage ?: "Failed to load file.", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
        }
    } else {
        if (true) {
'''
text = check_pattern.sub(txt_check, text)

# Replace url loading with loadDataWithBaseURL
text = text.replace('loadUrl(htmlUrl)', 'loadDataWithBaseURL("file:///android_asset/", txtHtml!!, "text/html", "utf-8", null)')

# Remove epubBook references in JS injection block
text = text.replace('val isFixedLayout = epubBook!!.isFixedLayout', 'val isFixedLayout = false')
text = text.replace('val isArchiveOrg = epubBook!!.isArchiveOrg', 'val isArchiveOrg = false')
text = text.replace('var isManga = ($isFixedLayout || (imgs > 0 && textLen < 150));', 'var isManga = false;')

# Change epub-next-btn to txt-next-btn and hide it
text = text.replace('epub-next-btn', 'txt-next-btn')
text = text.replace('Load Next Chapter', '')
text = text.replace('btnContainer.appendChild(nextBtn);', '') # Prevent next chapter button rendering

# Remove chapter limit check for onNextChapter (just do nothing for TXT)
text = text.replace('if (latestChapterIndex.value < epubBook!!.spineFiles.size) {', 'if (false) {')
text = text.replace('val chapterPath = epubBook!!.spineFiles.getOrNull(chapterIndexSafe)', '')
text = text.replace('val htmlUrl = android.net.Uri.fromFile(java.io.File(chapterPath)).toString()', '')
text = text.replace('val chapterIndexSafe = (chapterIndex - 1).coerceIn(0, maxOf(0, epubBook!!.spineFiles.size - 1))', '')

with open('TXTReader.kt', 'w', encoding='utf-8') as f:
    f.write(text)

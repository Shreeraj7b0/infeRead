import re

with open('app/src/main/java/com/infer/inferead/ui/screens/FormatRenderers.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Extract EPUBReader (lines 1350 to 1995, 0-indexed: 1349 to 1995)
epub_code = "".join(lines[1349:1995])

# Extract WebAppInterface (lines 1310 to 1347, 0-indexed: 1309 to 1347)
web_app_code = "".join(lines[1309:1347])

# Modify epub_code to txt_code
txt_code = epub_code.replace('fun EPUBReader(', 'fun TXTReader(')

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
                txtHtml = "<html><head><meta name=\\"viewport\\" content=\\"width=device-width, initial-scale=1.0, maximum-scale=5.0\\"></head><body><div id='txt-content' style='white-space: pre-wrap;'>" + loadedText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</div></body></html>"
                onTotalPagesLoaded(100, null)
            } else {
                errorMessage = "Failed to load file."
            }
            isLoading = false
        }
    }'''

txt_code = re.sub(r'    val context = androidx\.compose\.ui\.platform\.LocalContext\.current.*?    if \(isLoading\) \{', txt_loading + '\n\n    if (isLoading) {', txt_code, flags=re.DOTALL)

txt_check = '''    if (isLoading) {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text("Loading...", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
        }
    } else if (txtHtml == null) {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text(errorMessage ?: "Failed to load file.", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
        }
    } else {'''

txt_code = re.sub(r'    if \(isLoading\) \{.*?    \} else \{', txt_check, txt_code, flags=re.DOTALL)

txt_code = re.sub(r'        val chapterIndexSafe = .*?        if \(chapterPath != null\) \{', '', txt_code, flags=re.DOTALL)

txt_code = txt_code.replace('loadUrl(htmlUrl)', 'loadDataWithBaseURL("file:///android_asset/", txtHtml!!, "text/html", "utf-8", null)')

# We need to remove the matching brace for `if (chapterPath != null)`.
txt_code = re.sub(r'                    \}\)\n                \}\n            \}\n        \}\n    \}\n\}', '                    })\n                }\n            }\n        }\n}', txt_code)

txt_code = txt_code.replace('val isFixedLayout = epubBook!!.isFixedLayout', 'val isFixedLayout = false')
txt_code = txt_code.replace('val isArchiveOrg = epubBook!!.isArchiveOrg', 'val isArchiveOrg = false')
txt_code = txt_code.replace('var isManga = ($isFixedLayout || (imgs > 0 && textLen < 150));', 'var isManga = false;')
txt_code = txt_code.replace('epub-next-btn', 'txt-next-btn')
txt_code = txt_code.replace('Load Next Chapter', '')
txt_code = txt_code.replace('btnContainer.appendChild(nextBtn);', '')
txt_code = txt_code.replace('if (latestChapterIndex.value < epubBook!!.spineFiles.size) {', 'if (false) {')

txt_code = txt_code.replace('WebAppInterface(', 'TxtWebAppInterface(')
txt_web_app_code = web_app_code.replace('class WebAppInterface', 'class TxtWebAppInterface')

full_txt_code = txt_code + '\n' + txt_web_app_code + '\n\n'

# Replace TextViewer (lines 463 to 935, 0-indexed: 462 to 935)
new_lines = lines[:462] + [full_txt_code] + lines[935:]

with open('app/src/main/java/com/infer/inferead/ui/screens/FormatRenderers.kt', 'w', encoding='utf-8') as f:
    f.writelines(new_lines)

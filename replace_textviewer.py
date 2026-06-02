import re

with open('TXTReader.kt', 'r', encoding='utf-8') as f:
    txt_reader_code = f.read()

# Make sure to append TxtWebAppInterface
txt_web_app_interface = '''
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
'''

with open('app/src/main/java/com/infer/inferead/ui/screens/FormatRenderers.kt', 'r', encoding='utf-8') as f:
    format_renderers = f.read()

# Regex to find TextViewer block: from "fun TextViewer(" up to the line before "@Composable\nfun ImageViewer"
pattern = re.compile(r'fun TextViewer\(.*?^}$(?=\s*@Composable\nfun ImageViewer)', re.DOTALL | re.MULTILINE)

replacement = txt_reader_code + '\n' + txt_web_app_interface + '\n'

new_format_renderers = pattern.sub(replacement, format_renderers)

with open('app/src/main/java/com/infer/inferead/ui/screens/FormatRenderers.kt', 'w', encoding='utf-8') as f:
    f.write(new_format_renderers)

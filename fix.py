import re

with open('TXTReader.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('val context = androidx.compose.ui.platform.LocalContext.current', '')

# add it back properly
text = text.replace('fun TXTReader(', '''fun TXTReader(
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
    val context = androidx.compose.ui.platform.LocalContext.current''')

# We need to remove the old signature
sig_pattern = re.compile(r'    filePath: String,.*?= null\n\) \{', re.DOTALL)
text = sig_pattern.sub('', text)

# There is a missing closing brace for the `if (true) {` replacement because I didn't replace `if (chapterPath != null) {` properly before.
text = text.replace('if (true) {', '')

# Remove extra braces at the end of the else block.
# We had:
# } // if(chapterPath)
# } // else
# } // EPUBReader
text = text.replace('                    })\n                }\n            }\n        }\n    }\n}', '                    })\n                }\n            }\n    }\n}')

with open('TXTReader.kt', 'w', encoding='utf-8') as f:
    f.write(text)

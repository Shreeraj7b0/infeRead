import re

with open('app/src/main/java/com/infer/inferead/ui/screens/ReaderScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

replacement = '''                        "TXT" -> TXTReader(
                            filePath = file.filePath,
                            settings = settings,
                            chapterIndex = file.currentPage,
                            onPageChanged = { page -> viewModel.updateCurrentPage(page) },
                            onTotalPagesLoaded = { total, _ -> 
                                viewModel.updateTotalPages(total)
                            },
                            onTap = toggleReaderMode,
                            onTextSelected = { textSelected, top, bottom, cfiRange ->
                                val finalCfi = if (file.format == "TXT") "${file.currentPage}|$cfiRange" else cfiRange
                                if (activeHighlightMode.isNullOrEmpty()) {
                                    textSelectionData = com.infer.inferead.ui.screens.TextSelectionData(textSelected, top, bottom, finalCfi)
                                }
                            },
                            onSelectionFinished = { textSelected, top, bottom, cfiRange ->
                                val finalCfi = if (file.format == "TXT") "${file.currentPage}|$cfiRange" else cfiRange
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

pattern = re.compile(r'                        "TXT" -> TXTReader\(.*?                        \)', re.DOTALL)
text = pattern.sub(replacement, text)

# Also fix the textSelectionData usage for TXT
text = text.replace('file.format == "EPUB"', 'file.format == "EPUB" || file.format == "TXT"')

with open('app/src/main/java/com/infer/inferead/ui/screens/ReaderScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text)

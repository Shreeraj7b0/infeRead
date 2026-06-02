import re

with open('app/src/main/java/com/infer/inferead/ui/screens/ReaderScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace the old TextViewer block with the new TXTReader block
pattern = re.compile(r'                        "TXT" -> TextViewer\(.*?                        \)', re.DOTALL)

txt_reader_block = '''                        "TXT" -> TXTReader(
                            filePath = file.filePath,
                            settings = settings,
                            chapterIndex = file.currentPage,
                            onPageChanged = { page -> viewModel.updateCurrentPage(page) },
                            onTotalPagesLoaded = { total, _ -> viewModel.updateTotalPages(total) },
                            onTap = toggleReaderMode,
                            onTextSelected = { textSelected, top, bottom, cfiRange ->
                                if (activeHighlightMode.isNullOrEmpty()) {
                                    textSelectionData = TextSelectionData(textSelected, top, bottom, cfiRange)
                                    showHighlightColorsForSelection = true
                                }
                            },
                            onSelectionFinished = { textSelected, top, bottom, cfiRange ->
                                if (!activeHighlightMode.isNullOrEmpty() && activeHighlightMode != "COMMENT_MODE") {
                                    viewModel.insertAnnotation(
                                        com.infer.inferead.data.Annotation(
                                            fileId = file.id,
                                            selectedText = textSelected,
                                            cfiRange = cfiRange,
                                            colorHex = activeHighlightMode ?: "#c25d5d",
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )
                                    viewModel.setActiveHighlightMode(null)
                                    true
                                } else if (activeHighlightMode == "COMMENT_MODE") {
                                    editingAnnotation = com.infer.inferead.data.Annotation(
                                        fileId = file.id,
                                        selectedText = textSelected,
                                        cfiRange = cfiRange,
                                        colorHex = "",
                                        timestamp = System.currentTimeMillis()
                                    )
                                    commentText = ""
                                    showCommentDialog = true
                                    viewModel.setActiveHighlightMode(null)
                                    true
                                } else false
                            },
                            onTextSelectionCleared = {
                                textSelectionData = null
                                showHighlightColorsForSelection = false
                            },
                            onAnnotationClicked = { annId, top, bottom ->
                                val ann = allAnns.find { it.id == annId }
                                if (ann != null) {
                                    annotationClickData = AnnotationClickData(ann, top, bottom)
                                    showAnnotationClickMenu = true
                                }
                            },
                            annotations = pageAnns,
                            targetScrollAnnId = scrollToAnnId
                        )'''

text = pattern.sub(txt_reader_block, text)

with open('app/src/main/java/com/infer/inferead/ui/screens/ReaderScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text)

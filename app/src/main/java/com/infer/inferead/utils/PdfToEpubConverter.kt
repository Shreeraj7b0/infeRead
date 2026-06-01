package com.infer.inferead.utils

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PdfToEpubConverter {
    suspend fun convert(
        context: Context, 
        pdfFile: File, 
        outputFile: File,
        onProgress: suspend (Int) -> Unit = {},
        checkPause: suspend () -> Unit = {},
        checkCancel: () -> Boolean = { false }
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                PDFBoxResourceLoader.init(context)
                
                val document = PDDocument.load(pdfFile)
                val totalPages = document.numberOfPages
                
                // We'll extract text page by page, or group of pages
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true // Helps with table alignment and columns
                }

                // Chunk into roughly chapters every 15 pages to keep HTML sizes manageable
                val pagesPerChapter = 15
                val chapters = mutableListOf<String>()
                
                for (i in 0 until totalPages step pagesPerChapter) {
                    if (checkCancel()) {
                        return@withContext false
                    }
                    checkPause()
                    
                    val startPage = i + 1
                    val endPage = minOf(i + pagesPerChapter, totalPages)
                    
                    stripper.startPage = startPage
                    stripper.endPage = endPage
                    
                    val text = stripper.getText(document)
                    val escapedText = escapeHtml(text)
                        .replace("\n", "<br/>")
                    
                    val html = """
                        <?xml version="1.0" encoding="utf-8"?>
                        <!DOCTYPE html>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                        <head>
                            <title>Chapter ${i / pagesPerChapter + 1}</title>
                            <style>
                                body { font-family: monospace; white-space: pre-wrap; }
                            </style>
                        </head>
                        <body>
                            <div>$escapedText</div>
                        </body>
                        </html>
                    """.trimIndent()
                    chapters.add(html)
                    
                    // Report progress based on pages processed
                    val processedPages = minOf(i + pagesPerChapter, totalPages)
                    val progress = (processedPages.toFloat() / totalPages * 100).toInt()
                    onProgress(progress)
                }
                
                // Now create the EPUB file structure using ZipOutputStream
               
                document.close()
                
                writeEpubZip(outputFile, chapters, pdfFile.nameWithoutExtension)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
    }

    private fun writeEpubZip(outputFile: File, chapters: List<String>, title: String) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            // 1. mimetype (Must be first, uncompressed)
            val mimeEntry = ZipEntry("mimetype")
            mimeEntry.method = ZipEntry.STORED
            val mimeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            mimeEntry.size = mimeBytes.size.toLong()
            val crc = java.util.zip.CRC32()
            crc.update(mimeBytes)
            mimeEntry.crc = crc.value
            zos.putNextEntry(mimeEntry)
            zos.write(mimeBytes)
            zos.closeEntry()

            // 2. META-INF/container.xml
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            val containerXml = """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent()
            zos.write(containerXml.toByteArray())
            zos.closeEntry()

            // 3. OEBPS/content.opf
            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            val manifestItems = chapters.indices.joinToString("\n") { i ->
                """<item id="chapter$i" href="Text/chapter$i.html" media-type="application/xhtml+xml"/>"""
            }
            val spineItems = chapters.indices.joinToString("\n") { i ->
                """<itemref idref="chapter$i"/>"""
            }
            val contentOpf = """
                <?xml version="1.0" encoding="utf-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>${escapeHtml(title)}</dc:title>
                    <dc:language>en</dc:language>
                    <dc:identifier id="BookId">urn:uuid:infe-${System.currentTimeMillis()}</dc:identifier>
                  </metadata>
                  <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    $manifestItems
                  </manifest>
                  <spine toc="ncx">
                    $spineItems
                  </spine>
                </package>
            """.trimIndent()
            zos.write(contentOpf.toByteArray())
            zos.closeEntry()

            // 4. OEBPS/toc.ncx
            zos.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            val navPoints = chapters.indices.joinToString("\n") { i ->
                """
                <navPoint id="navPoint-$i" playOrder="${i + 1}">
                  <navLabel><text>Part ${i + 1}</text></navLabel>
                  <content src="Text/chapter$i.html"/>
                </navPoint>
                """.trimIndent()
            }
            val tocNcx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                  <head>
                    <meta name="dtb:uid" content="urn:uuid:infe-${System.currentTimeMillis()}"/>
                    <meta name="dtb:depth" content="1"/>
                    <meta name="dtb:totalPageCount" content="0"/>
                    <meta name="dtb:maxPageNumber" content="0"/>
                  </head>
                  <docTitle><text>${escapeHtml(title)}</text></docTitle>
                  <navMap>
                    $navPoints
                  </navMap>
                </ncx>
            """.trimIndent()
            zos.write(tocNcx.toByteArray())
            zos.closeEntry()

            // 5. OEBPS/Text/chapterX.html
            chapters.forEachIndexed { i, html ->
                zos.putNextEntry(ZipEntry("OEBPS/Text/chapter$i.html"))
                zos.write(html.toByteArray())
                zos.closeEntry()
            }
        }
    }
}

package com.infer.inferead.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PdfToEpubConverter {
    // Max width to render each page image at (balance quality vs file size)
    private const val PAGE_RENDER_WIDTH = 1080

    suspend fun convert(
        context: Context,
        pdfInputStream: InputStream,
        outputFile: File,
        bookTitle: String,
        onProgress: suspend (Int) -> Unit = {},
        checkPause: suspend () -> Unit = {},
        checkCancel: () -> Boolean = { false }
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                PDFBoxResourceLoader.init(context)

                // Write the InputStream to a temp file so we can use both
                // PDFTextStripper (text) and Android's PdfRenderer (page images)
                val tempPdf = File.createTempFile("epub_src_", ".pdf", context.cacheDir)
                pdfInputStream.use { it.copyTo(tempPdf.outputStream()) }

                // --- Pass 1: Text extraction via PDFBox ---
                val document = PDDocument.load(tempPdf)
                val totalPages = document.numberOfPages
                val stripper = PDFTextStripper().apply { sortByPosition = true }

                val pageTexts = mutableListOf<String>()
                for (i in 1..totalPages) {
                    stripper.startPage = i
                    stripper.endPage = i
                    pageTexts.add(stripper.getText(document).trim())
                }
                document.close()

                // --- Pass 2: Render each page to a JPEG byte array ---
                // Store image bytes per page — write them as separate files inside the zip
                data class PageImage(val bytes: ByteArray)
                val pageImageBytes = mutableListOf<PageImage>()

                val parcelFd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(parcelFd)

                for (i in 0 until totalPages) {
                    if (checkCancel()) {
                        renderer.close(); parcelFd.close(); tempPdf.delete()
                        return@withContext false
                    }
                    checkPause()

                    val page = renderer.openPage(i)
                    val scale = PAGE_RENDER_WIDTH.toFloat() / page.width
                    val bmpWidth = PAGE_RENDER_WIDTH
                    val bmpHeight = (page.height * scale).toInt()

                    val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                    bitmap.recycle()
                    pageImageBytes.add(PageImage(baos.toByteArray()))

                    onProgress(((i + 1).toFloat() / totalPages * 100).toInt())
                }
                renderer.close()
                parcelFd.close()
                tempPdf.delete()

                // --- Build EPUB zip with separate image files (no base64 inline) ---
                writeEpubZip(outputFile, pageTexts, pageImageBytes.map { it.bytes }, bookTitle, totalPages)
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

    private fun writeEpubZip(
        outputFile: File,
        pageTexts: List<String>,
        pageImages: List<ByteArray>,
        title: String,
        totalPages: Int
    ) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->

            // 1. mimetype (must be first, uncompressed)
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
            zos.write("""
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent().toByteArray())
            zos.closeEntry()

            // 3. OEBPS/Images/pageX.jpg — one image per page
            for (i in 0 until totalPages) {
                zos.putNextEntry(ZipEntry("OEBPS/Images/page$i.jpg"))
                zos.write(pageImages.getOrNull(i) ?: ByteArray(0))
                zos.closeEntry()
            }

            // 4. OEBPS/Text/chapterX.xhtml — one chapter per page
            for (i in 0 until totalPages) {
                val text = pageTexts.getOrNull(i) ?: ""
                val escapedText = escapeHtml(text).replace("\n", "<br/>")
                val html = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>Page ${i + 1}</title>
  <style>
    html, body { margin: 0; padding: 0; background: #ffffff; }
    img.pageimg { width: 100%; height: auto; display: block; }
    div.pagetext { font-family: sans-serif; font-size: 13px; line-height: 1.6; color: #333; padding: 8px; white-space: pre-wrap; }
  </style>
</head>
<body>
  <img class="pageimg" src="../Images/page$i.jpg" alt="Page ${i + 1}"/>
  ${if (escapedText.isNotBlank()) "<div class=\"pagetext\">$escapedText</div>" else ""}
</body>
</html>"""
                zos.putNextEntry(ZipEntry("OEBPS/Text/chapter$i.xhtml"))
                zos.write(html.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            // 5. OEBPS/content.opf
            val manifestImageItems = (0 until totalPages).joinToString("\n    ") { i ->
                """<item id="img$i" href="Images/page$i.jpg" media-type="image/jpeg"/>"""
            }
            val manifestChapterItems = (0 until totalPages).joinToString("\n    ") { i ->
                """<item id="chapter$i" href="Text/chapter$i.xhtml" media-type="application/xhtml+xml"/>"""
            }
            val spineItems = (0 until totalPages).joinToString("\n    ") { i ->
                """<itemref idref="chapter$i"/>"""
            }
            val contentOpf = """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>${escapeHtml(title)}</dc:title>
    <dc:language>en</dc:language>
    <dc:identifier id="BookId">urn:uuid:infe-${System.currentTimeMillis()}</dc:identifier>
  </metadata>
  <manifest>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    $manifestImageItems
    $manifestChapterItems
  </manifest>
  <spine toc="ncx">
    $spineItems
  </spine>
</package>"""
            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write(contentOpf.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 6. OEBPS/toc.ncx
            val navPoints = (0 until totalPages).joinToString("\n") { i ->
                """<navPoint id="navPoint-$i" playOrder="${i + 1}">
  <navLabel><text>Page ${i + 1}</text></navLabel>
  <content src="Text/chapter$i.xhtml"/>
</navPoint>"""
            }
            val tocNcx = """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="urn:uuid:infe-${System.currentTimeMillis()}"/>
    <meta name="dtb:depth" content="1"/>
    <meta name="dtb:totalPageCount" content="$totalPages"/>
    <meta name="dtb:maxPageNumber" content="$totalPages"/>
  </head>
  <docTitle><text>${escapeHtml(title)}</text></docTitle>
  <navMap>
$navPoints
  </navMap>
</ncx>"""
            zos.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            zos.write(tocNcx.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }
}

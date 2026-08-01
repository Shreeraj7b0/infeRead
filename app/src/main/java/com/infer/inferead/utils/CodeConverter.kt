package com.infer.inferead.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileOutputStream

object CodeConverter {

    suspend fun convertToPdf(context: Context, text: String, outputFile: File, onProgress: (Int) -> Unit, checkPause: suspend () -> Unit) = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40
        val contentWidth = pageWidth - 2 * margin
        val contentHeight = pageHeight - 2 * margin

        val textPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }

        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.2f)
            .setIncludePad(false)
            .build()

        var currentLine = 0
        val totalLines = staticLayout.lineCount
        var pageNum = 1

        if (totalLines == 0) {
            onProgress(100)
            return@withContext
        }

        while (currentLine < totalLines) {
            yield() // allow cancellation
            checkPause()

            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            // Find how many lines fit on this page
            var linesOnPage = 0
            var heightSoFar = 0
            while (currentLine + linesOnPage < totalLines) {
                val lh = staticLayout.getLineBottom(currentLine + linesOnPage) - staticLayout.getLineTop(currentLine + linesOnPage)
                if (heightSoFar + lh > contentHeight) {
                    break
                }
                heightSoFar += lh
                linesOnPage++
            }
            if (linesOnPage == 0) linesOnPage = 1 // Prevent infinite loop if one line is taller than page

            // Draw just those lines by translating canvas
            canvas.save()
            canvas.translate(margin.toFloat(), margin.toFloat() - staticLayout.getLineTop(currentLine))
            canvas.clipRect(0, staticLayout.getLineTop(currentLine), contentWidth, staticLayout.getLineBottom(currentLine + linesOnPage - 1))
            staticLayout.draw(canvas)
            canvas.restore()

            document.finishPage(page)
            
            currentLine += linesOnPage
            pageNum++
            
            val progress = ((currentLine.toFloat() / totalLines.toFloat()) * 100).toInt()
            onProgress(progress)
        }

        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()
    }

    suspend fun convertToImages(context: Context, text: String, outputDir: File, baseName: String, onProgress: (Int) -> Unit, checkPause: suspend () -> Unit): List<File> = withContext(Dispatchers.IO) {
        val generatedFiles = mutableListOf<File>()
        
        val pageWidth = 1080
        val pageHeight = 1920
        val margin = 60
        val contentWidth = pageWidth - 2 * margin
        val contentHeight = pageHeight - 2 * margin

        val textPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 32f
            isAntiAlias = true
        }

        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.4f)
            .setIncludePad(false)
            .build()

        var currentLine = 0
        val totalLines = staticLayout.lineCount
        var pageNum = 1

        if (totalLines == 0) {
            onProgress(100)
            return@withContext generatedFiles
        }

        while (currentLine < totalLines) {
            yield()
            checkPause()

            var linesOnPage = 0
            var heightSoFar = 0
            while (currentLine + linesOnPage < totalLines) {
                val lh = staticLayout.getLineBottom(currentLine + linesOnPage) - staticLayout.getLineTop(currentLine + linesOnPage)
                if (heightSoFar + lh > contentHeight) {
                    break
                }
                heightSoFar += lh
                linesOnPage++
            }
            if (linesOnPage == 0) linesOnPage = 1

            val bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            canvas.save()
            canvas.translate(margin.toFloat(), margin.toFloat() - staticLayout.getLineTop(currentLine))
            canvas.clipRect(0, staticLayout.getLineTop(currentLine), contentWidth, staticLayout.getLineBottom(currentLine + linesOnPage - 1))
            staticLayout.draw(canvas)
            canvas.restore()

            val pageName = String.format("%s_%02d.jpg", baseName, pageNum)
            val outFile = File(outputDir, pageName)
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()
            generatedFiles.add(outFile)

            currentLine += linesOnPage
            pageNum++
            
            val progress = ((currentLine.toFloat() / totalLines.toFloat()) * 100).toInt()
            onProgress(progress)
        }
        
        return@withContext generatedFiles
    }
}

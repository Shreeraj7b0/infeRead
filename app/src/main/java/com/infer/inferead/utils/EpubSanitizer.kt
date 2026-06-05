package com.infer.inferead.utils

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.w3c.dom.Element as DomElement
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

object EpubSanitizer {

    fun sanitizeAndSplit(epubBook: EpubBook, opfDoc: org.w3c.dom.Document): EpubBook {
        if (epubBook.isFixedLayout) return epubBook // Don't split fixed layout/manga
        
        try {
            // Find TOC file
            val manifestList = opfDoc.getElementsByTagName("manifest")
            if (manifestList.length == 0) return epubBook
            val manifest = manifestList.item(0) as DomElement
            val items = manifest.getElementsByTagName("item")
            
            var tocFile: File? = null
            for (i in 0 until items.length) {
                val item = items.item(i) as DomElement
                val id = item.getAttribute("id")
                val href = item.getAttribute("href")
                val properties = item.getAttribute("properties")
                val mediaType = item.getAttribute("media-type")
                
                if (id == "ncx" || id == "toc" || properties.contains("nav") || mediaType == "application/x-dtbncx+xml") {
                    val decodedHref = try { java.net.URLDecoder.decode(href, "UTF-8") } catch (e: Exception) { href }
                    val f = File(epubBook.opfDir, decodedHref)
                    if (f.exists()) {
                        tocFile = f
                        break
                    }
                }
            }

            // Extract anchors from TOC
            val anchors = mutableSetOf<String>()
            if (tocFile != null) {
                if (tocFile.name.endsWith(".ncx")) {
                    val factory = DocumentBuilderFactory.newInstance()
                    factory.isNamespaceAware = false
                    val builder = factory.newDocumentBuilder()
                    val tocDoc = builder.parse(tocFile)
                    val contentNodes = tocDoc.getElementsByTagName("content")
                    for (i in 0 until contentNodes.length) {
                        val content = contentNodes.item(i) as DomElement
                        val src = content.getAttribute("src")
                        if (src.contains("#")) {
                            val rawAnchor = src.substringAfter("#")
                            anchors.add(try { java.net.URLDecoder.decode(rawAnchor, "UTF-8") } catch (e: Exception) { rawAnchor })
                        }
                    }
                } else {
                    // It's a nav.xhtml
                    val doc = Jsoup.parse(tocFile, "UTF-8")
                    val links = doc.select("a[href]")
                    for (link in links) {
                        val href = link.attr("href")
                        if (href.contains("#")) {
                            val rawAnchor = href.substringAfter("#")
                            anchors.add(try { java.net.URLDecoder.decode(rawAnchor, "UTF-8") } catch (e: Exception) { rawAnchor })
                        }
                    }
                }
            }

            val newSpineFiles = mutableListOf<String>()
            var chapterCounter = 0

            for (filePath in epubBook.spineFiles) {
                val file = File(filePath)
                if (!file.exists()) continue

                val doc = Jsoup.parse(file, "UTF-8")
                doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml)
                doc.outputSettings().escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
                
                // Sanitize CSS
                doc.select("*").forEach { element ->
                    val style = element.attr("style")
                    if (style.isNotEmpty()) {
                        val cleanedStyle = style.split(";")
                            .filterNot { 
                                it.contains("line-height", true) || 
                                it.contains("font-family", true) || 
                                it.contains("margin", true) 
                            }
                            .joinToString(";")
                        if (cleanedStyle.isNotBlank()) {
                            element.attr("style", cleanedStyle)
                        } else {
                            element.removeAttr("style")
                        }
                    }
                }

                // Enforce break-inside avoid
                doc.select("p, img, h1, h2, h3, h4, h5, h6").forEach { element ->
                    element.attr("style", (element.attr("style") + "; break-inside: avoid;").trim(';'))
                }

                // Find matching anchors in this file
                val fileAnchors = anchors.mapNotNull { id ->
                    try {
                        doc.select("[id=\"$id\"], [name=\"$id\"]").first()
                    } catch (e: Exception) {
                        null
                    }
                }.sortedBy { getPosition(doc, it) }

                if (fileAnchors.size <= 1) {
                    // No need to split, just save sanitized content
                    val outFile = File(file.parentFile, "sanitized_${file.name}")
                    outFile.writeText(doc.outerHtml())
                    newSpineFiles.add(outFile.absolutePath)
                } else {
                    // Split the file!
                    for (i in fileAnchors.indices) {
                        val cloneDoc = doc.clone()
                        val startEl = doc.select("[id=\"${fileAnchors[i].id()}\"], [name=\"${fileAnchors[i].attr("name")}\"]").first()?.let { 
                            cloneDoc.select("[id=\"${it.id()}\"], [name=\"${it.attr("name")}\"]").first() 
                        } ?: continue
                        
                        // Remove everything before this chapter
                        if (i > 0) {
                            removeBefore(startEl)
                        }

                        // Remove everything after this chapter
                        if (i < fileAnchors.size - 1) {
                            val endEl = doc.select("[id=\"${fileAnchors[i+1].id()}\"], [name=\"${fileAnchors[i+1].attr("name")}\"]").first()?.let {
                                cloneDoc.select("[id=\"${it.id()}\"], [name=\"${it.attr("name")}\"]").first()
                            }
                            if (endEl != null) {
                                removeAfter(endEl)
                                endEl.remove() // remove the next anchor element itself
                            }
                        }

                        val outFile = File(file.parentFile, "sanitized_part${chapterCounter++}_${file.name}")
                        outFile.writeText(cloneDoc.outerHtml())
                        newSpineFiles.add(outFile.absolutePath)
                    }
                }
            }

            return epubBook.copy(spineFiles = newSpineFiles)

        } catch (e: Exception) {
            e.printStackTrace()
            return epubBook
        }
    }

    private fun getPosition(root: Element, target: Element): Int {
        var pos = 0
        for (node in root.allElements) {
            if (node == target) return pos
            pos++
        }
        return -1
    }

    private fun removeBefore(node: Node) {
        var current = node
        while (current.parent() != null) {
            var sibling = current.previousSibling()
            while (sibling != null) {
                val prev = sibling.previousSibling()
                if (sibling is Element && (sibling.tagName() == "head" || sibling.tagName() == "title" || sibling.tagName() == "meta" || sibling.tagName() == "link" || sibling.tagName() == "style")) {
                    // keep <head> elements
                } else {
                    sibling.remove()
                }
                sibling = prev
            }
            current = current.parent()!!
        }
    }

    private fun removeAfter(node: Node) {
        var current = node
        while (current.parent() != null) {
            var sibling = current.nextSibling()
            while (sibling != null) {
                val next = sibling.nextSibling()
                sibling.remove()
                sibling = next
            }
            current = current.parent()!!
        }
    }
}

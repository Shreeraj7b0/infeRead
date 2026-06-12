package com.infer.inferead.utils

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

data class EpubBook(
    val title: String,
    val coverImagePath: String?,
    val spineFiles: List<String>,
    val opfDir: String,
    val isFixedLayout: Boolean = false,
    val isArchiveOrg: Boolean = false,
    val hasDevanagari: Boolean = false,
    val hasLatin: Boolean = false
)

object EpubParser {
    fun parseEpub(unzippedDir: File): EpubBook? {
        try {
            val containerFile = File(unzippedDir, "META-INF/container.xml")
            if (!containerFile.exists()) return null

            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false // Simple matching
            val builder = factory.newDocumentBuilder()
            val containerDoc = builder.parse(containerFile)

            val rootfiles = containerDoc.getElementsByTagName("rootfile")
            if (rootfiles.length == 0) return null
            val rootfileElement = rootfiles.item(0) as Element
            val opfPath = rootfileElement.getAttribute("full-path")
            val opfFile = File(unzippedDir, opfPath)
            if (!opfFile.exists()) return null

            val opfDir = opfFile.parentFile ?: unzippedDir

            val opfDoc = builder.parse(opfFile)
            
            // Get Metadata
            var title = "Unknown Title"
            var isFixedLayout = false
            var isArchiveOrg = false
            val metadataList = opfDoc.getElementsByTagName("metadata")
            if (metadataList.length > 0) {
                val metadata = metadataList.item(0) as Element
                val titles = metadata.getElementsByTagName("dc:title")
                if (titles.length > 0) {
                    title = titles.item(0).textContent
                }
                
                val publishers = metadata.getElementsByTagName("dc:publisher")
                for (i in 0 until publishers.length) {
                    if (publishers.item(i).textContent.contains("archive.org", true) || publishers.item(i).textContent.contains("Internet Archive", true)) {
                        isArchiveOrg = true
                    }
                }
                
                val identifiers = metadata.getElementsByTagName("dc:identifier")
                for (i in 0 until identifiers.length) {
                    if (identifiers.item(i).textContent.contains("archive.org", true)) {
                        isArchiveOrg = true
                    }
                }
                
                val metaTags = metadata.getElementsByTagName("meta")
                for (i in 0 until metaTags.length) {
                    val meta = metaTags.item(i) as Element
                    if (meta.getAttribute("property") == "rendition:layout" && meta.textContent.trim() == "pre-paginated") {
                        isFixedLayout = true
                    }
                    if (meta.getAttribute("name") == "fixed-layout" && meta.getAttribute("content") == "true") {
                        isFixedLayout = true
                    }
                }
                
                val types = metadata.getElementsByTagName("dc:type")
                for (i in 0 until types.length) {
                    if (types.item(i).textContent.contains("comic", true) || types.item(i).textContent.contains("manga", true)) {
                        isFixedLayout = true
                    }
                }
                
                val subjects = metadata.getElementsByTagName("dc:subject")
                for (i in 0 until subjects.length) {
                    if (subjects.item(i).textContent.contains("comic", true) || subjects.item(i).textContent.contains("manga", true)) {
                        isFixedLayout = true
                    }
                }
            }

            // Get Manifest
            val manifestList = opfDoc.getElementsByTagName("manifest")
            if (manifestList.length == 0) return null
            val manifest = manifestList.item(0) as Element
            val items = manifest.getElementsByTagName("item")
            
            val itemMap = mutableMapOf<String, String>()
            var coverImageId: String? = null
            
            // Parse items
            for (i in 0 until items.length) {
                val item = items.item(i) as Element
                val id = item.getAttribute("id")
                val href = item.getAttribute("href")
                val properties = item.getAttribute("properties")
                itemMap[id] = href
                
                if (properties.contains("cover-image")) {
                    coverImageId = id
                }
            }
            
            // Try meta tag for cover if not found in properties
            if (coverImageId == null && metadataList.length > 0) {
                val metaTags = (metadataList.item(0) as Element).getElementsByTagName("meta")
                for (i in 0 until metaTags.length) {
                    val meta = metaTags.item(i) as Element
                    if (meta.getAttribute("name") == "cover") {
                        coverImageId = meta.getAttribute("content")
                    }
                }
            }
            
            var coverImagePath = coverImageId?.let { itemMap[it] }?.let {
                try {
                    File(opfDir, java.net.URLDecoder.decode(it, "UTF-8")).absolutePath
                } catch (e: Exception) {
                    File(opfDir, it).absolutePath
                }
            }
            
            if (coverImagePath == null) {
                val fallbackHref = itemMap.values.firstOrNull { it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) || it.endsWith(".png", true) }
                if (fallbackHref != null) {
                    try {
                        val decodedFallback = java.net.URLDecoder.decode(fallbackHref, "UTF-8")
                        val fallbackFile = File(opfDir, decodedFallback)
                        if (fallbackFile.exists()) {
                            coverImagePath = fallbackFile.absolutePath
                        }
                    } catch (e: Exception) {
                        val fallbackFile = File(opfDir, fallbackHref)
                        if (fallbackFile.exists()) {
                            coverImagePath = fallbackFile.absolutePath
                        }
                    }
                }
            }
            
            // Get Spine
            val spineList = opfDoc.getElementsByTagName("spine")
            if (spineList.length == 0) return null
            val spine = spineList.item(0) as Element
            val itemrefs = spine.getElementsByTagName("itemref")
            
            val spineFiles = mutableListOf<String>()
            for (i in 0 until itemrefs.length) {
                val itemref = itemrefs.item(i) as Element
                val idref = itemref.getAttribute("idref")
                val href = itemMap[idref]
                if (href != null) {
                    val cleanHref = href.substringBefore("#")
                    try {
                        val decodedHref = java.net.URLDecoder.decode(cleanHref, "UTF-8")
                        spineFiles.add(File(opfDir, decodedHref).absolutePath)
                    } catch (e: Exception) {
                        spineFiles.add(File(opfDir, cleanHref).absolutePath)
                    }
                }
            }
            
            // Image-based book heuristic
            if (!isFixedLayout && spineFiles.isNotEmpty()) {
                var imageBasedScore = 0
                val sampleSize = minOf(3, spineFiles.size)
                for (i in 0 until sampleSize) {
                    val file = File(spineFiles[i])
                    if (file.exists()) {
                        val content = file.readText()
                        if (content.length < 3000 && (content.contains("<img", true) || content.contains("<image", true) || content.contains("background-image", true))) {
                            imageBasedScore++
                        }
                    }
                }
                if (sampleSize > 0 && imageBasedScore == sampleSize) {
                    isFixedLayout = true
                }
            }
            
            var hasDevanagari = false
            var hasLatin = false
            val devanagariRegex = Regex("[\\u0900-\\u097F]")
            val latinRegex = Regex("[a-zA-Z]")
            
            for (i in 0 until minOf(3, spineFiles.size)) {
                try {
                    val text = File(spineFiles[i]).readText()
                    val stripped = text.replace(Regex("<[^>]*>"), "")
                    if (!hasDevanagari && devanagariRegex.containsMatchIn(stripped)) hasDevanagari = true
                    if (!hasLatin && latinRegex.containsMatchIn(stripped)) hasLatin = true
                    if (hasDevanagari && hasLatin) break
                } catch (e: Exception) {}
            }
            
            val rawEpubBook = EpubBook(title, coverImagePath, spineFiles, opfDir.absolutePath, isFixedLayout, isArchiveOrg, hasDevanagari, hasLatin)
            return EpubSanitizer.sanitizeAndSplit(rawEpubBook, opfDoc)
            
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}

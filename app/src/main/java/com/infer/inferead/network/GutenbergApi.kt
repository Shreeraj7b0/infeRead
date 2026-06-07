package com.infer.inferead.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.util.Log

data class GutenbergBook(
    val id: Int,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val downloadUrl: String?,
    val formats: Map<String, String>
)

object GutenbergApi {
    private const val BASE_URL = "https://gutendex.com/books/"

    suspend fun searchBooks(query: String, page: Int = 1): List<GutenbergBook> {
        return withContext(Dispatchers.IO) {
            val list = mutableListOf<GutenbergBook>()
            try {
                val urlString = if (query.isNotBlank()) {
                    "$BASE_URL?search=${java.net.URLEncoder.encode(query, "UTF-8")}&page=$page"
                } else {
                    "$BASE_URL?page=$page"
                }
                
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val results = json.getJSONArray("results")
                    
                    for (i in 0 until results.length()) {
                        val bookObj = results.getJSONObject(i)
                        val id = bookObj.getInt("id")
                        val title = bookObj.getString("title")
                        
                        var author = "Unknown"
                        val authorsArray = bookObj.optJSONArray("authors")
                        if (authorsArray != null && authorsArray.length() > 0) {
                            author = authorsArray.getJSONObject(0).optString("name", "Unknown")
                        }
                        
                        val formatsObj = bookObj.getJSONObject("formats")
                        val formats = mutableMapOf<String, String>()
                        val keys = formatsObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            formats[key] = formatsObj.getString(key)
                        }
                        
                        // Extract cover and epub url
                        val coverUrl = formats["image/jpeg"]
                        val downloadUrl = formats["application/epub+zip"] ?: formats["application/pdf"]
                        
                        list.add(GutenbergBook(id, title, author, coverUrl, downloadUrl, formats))
                    }
                }
            } catch (e: Exception) {
                Log.e("GutenbergApi", "Error fetching books", e)
            }
            list
        }
    }
}

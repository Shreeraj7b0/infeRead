package com.infer.inferead.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.appwidget.cornerRadius
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.currentState
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.infer.inferead.MainActivity
import com.infer.inferead.data.InfeReadDatabase
import com.infer.inferead.data.LibraryFile
import kotlinx.coroutines.flow.first
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.ContentScale
import android.graphics.BitmapFactory

import android.graphics.Bitmap

class InfeReadWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = InfeReadDatabase.getDatabase(context)
        val allFiles = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { 
            database.infeReadDao().getAllLibraryFiles().first() 
        }

        val prefs = androidx.glance.appwidget.state.getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val mode = prefs[stringPreferencesKey("widget_mode")] ?: "BOOKMARKS"
        
        val displayFiles = if (mode == "BOOKMARKS") {
            allFiles.filter { it.isBookmarked || it.rating > 0 }
        } else {
            allFiles.filter { it.isToRead }
        }.sortedByDescending { it.addedAt }.take(12) // Limit to top 12 to avoid slow loads

        val bitmaps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            displayFiles.associate { file ->
                val bitmap = file.thumbnailUri?.let { uriString ->
                    try {
                        val uri = Uri.parse(uriString)
                        val isFile = uri.scheme == "file" || uriString.startsWith("/")
                        val getStream = {
                            if (isFile) java.io.FileInputStream(java.io.File(uriString.removePrefix("file://")))
                            else context.contentResolver.openInputStream(uri)
                        }
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        getStream()?.use { BitmapFactory.decodeStream(it, null, options) }
                        
                        var inSampleSize = 1
                        val reqWidth = 140
                        val reqHeight = 200
                        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                            val halfHeight = options.outHeight / 2
                            val halfWidth = options.outWidth / 2
                            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                                inSampleSize *= 2
                            }
                        }
                        val finalOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
                        getStream()?.use { BitmapFactory.decodeStream(it, null, finalOptions) }
                    } catch (e: Exception) { null }
                }
                file.id to bitmap
            }
        }

        provideContent {
            val livePrefs = currentState<Preferences>()
            val liveMode = livePrefs[stringPreferencesKey("widget_mode")] ?: "BOOKMARKS"
            val showMenu = livePrefs[androidx.datastore.preferences.core.booleanPreferencesKey("widget_show_menu")] ?: false
            val bgColorInt = livePrefs[intPreferencesKey("widget_bg_color")] ?: android.graphics.Color.parseColor("#E5E5E5")
            val alpha = livePrefs[intPreferencesKey("widget_alpha")] ?: 200
            
            // Reconstruct color with alpha
            val r = android.graphics.Color.red(bgColorInt)
            val g = android.graphics.Color.green(bgColorInt)
            val b = android.graphics.Color.blue(bgColorInt)
            val bgColor = Color(r, g, b, alpha)

            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(bgColor)
                        .cornerRadius(24.dp)
                        .padding(16.dp)
                ) {
                    // Header Dropdown Toggle
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(GlanceTheme.colors.primary)
                            .cornerRadius(16.dp)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (liveMode == "BOOKMARKS") "Bookmarks ▼" else "Reading List ▼",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.onPrimary
                            ),
                            modifier = GlanceModifier.clickable(actionRunCallback<ToggleMenuAction>())
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        
                        val configIntent = Intent(context, InfeReadWidgetConfigActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        Text(
                            text = "Settings",
                            style = TextStyle(fontSize = 16.sp, color = GlanceTheme.colors.onPrimary),
                            modifier = GlanceModifier.clickable(actionStartActivity(configIntent)).padding(4.dp)
                        )
                    }
                    
                    Spacer(modifier = GlanceModifier.height(12.dp))

                    if (showMenu) {
                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(GlanceTheme.colors.surfaceVariant)
                                .cornerRadius(12.dp)
                                .padding(8.dp)
                        ) {
                            val modeKey = ActionParameters.Key<String>("mode")
                            Text("Bookmarks", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 15.sp), modifier = GlanceModifier.fillMaxWidth().padding(8.dp).clickable(actionRunCallback<SelectModeAction>(actionParametersOf(modeKey to "BOOKMARKS"))))
                            Text("Reading List", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 15.sp), modifier = GlanceModifier.fillMaxWidth().padding(8.dp).clickable(actionRunCallback<SelectModeAction>(actionParametersOf(modeKey to "READING_LIST"))))
                        }
                        Spacer(modifier = GlanceModifier.height(12.dp))
                    }

                    // Vertical Scrollable File Grid
                    if (displayFiles.isEmpty()) {
                        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No books found", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp))
                        }
                    } else {
                        androidx.glance.appwidget.lazy.LazyColumn(
                            modifier = GlanceModifier.fillMaxSize()
                        ) {
                            val chunkedFiles: List<List<LibraryFile>> = displayFiles.chunked(3)
                            items(chunkedFiles) { rowFiles: List<LibraryFile> ->
                                Row(
                                    modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    rowFiles.forEach { file ->
                                        Box(
                                            modifier = GlanceModifier.defaultWeight().padding(horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            FileWidgetCard(file = file, bitmap = bitmaps[file.id])
                                        }
                                    }
                                    // Add empty spacers if row has less than 3 items to keep alignment
                                    repeat(3 - rowFiles.size) {
                                        Spacer(modifier = GlanceModifier.defaultWeight().padding(horizontal = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class ToggleMenuAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val showMenu = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("widget_show_menu")] ?: false
            prefs.toMutablePreferences().apply {
                this[androidx.datastore.preferences.core.booleanPreferencesKey("widget_show_menu")] = !showMenu
            }
        }
        InfeReadWidget().update(context, glanceId)
    }
}

class SelectModeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val newMode = parameters[ActionParameters.Key<String>("mode")] ?: "BOOKMARKS"
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[stringPreferencesKey("widget_mode")] = newMode
                this[androidx.datastore.preferences.core.booleanPreferencesKey("widget_show_menu")] = false
            }
        }
        InfeReadWidget().update(context, glanceId)
    }
}

@Composable
fun FileWidgetCard(file: LibraryFile, bitmap: Bitmap?) {
    // Generate Deep Link Intent
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("inferead://reader/${file.id}")
    ).apply {
        setClassName("com.infer.inferead", "com.infer.inferead.MainActivity")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

    Column(
        modifier = GlanceModifier
            .width(80.dp)
            .clickable(actionStartActivity(intent)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = file.title,
                modifier = GlanceModifier.width(70.dp).height(100.dp).cornerRadius(8.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            // Empty Thumbnail
            Box(
                modifier = GlanceModifier
                    .width(70.dp)
                    .height(100.dp)
                    .cornerRadius(8.dp)
                    .background(GlanceTheme.colors.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {}
        }
        
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = file.title,
            maxLines = 2,
            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurface),
            modifier = GlanceModifier.fillMaxWidth()
        )
    }
}

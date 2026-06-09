package com.infer.inferead.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infer.inferead.viewmodel.HomeViewModel
import com.infer.inferead.ui.theme.ThemeManager
import com.infer.inferead.ui.theme.AppThemeBackground
import com.infer.inferead.ui.theme.AppThemeAccent
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.provider.OpenableColumns
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToStats: () -> Unit,
    onOpenFile: (Int) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val libraryFiles by viewModel.libraryFiles.collectAsState()
    val ratedFiles = libraryFiles.filter { it.rating > 0 }
    
    var sortDescending by remember { mutableStateOf(true) }
    
    val sortedRatedFiles = if (sortDescending) {
        ratedFiles.sortedByDescending { it.rating }
    } else {
        ratedFiles.sortedBy { it.rating }
    }

    var ratedFilesExpanded by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE) }
    
    var readingGoalMinutes by remember { mutableStateOf(prefs.getInt("reading_goal_minutes", 15)) }
    var showCustomGoalDialog by remember { mutableStateOf(false) }
    var showFinishedBooksDialog by remember { mutableStateOf(false) }
    var isOfflineMode by remember { mutableStateOf(prefs.getBoolean("is_offline_mode", false)) }


    val appThemeBg by ThemeManager.currentBackground.collectAsState()
    val appThemeAccent by ThemeManager.currentAccent.collectAsState()
    val scope = rememberCoroutineScope()
    
    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    var fileName = "note_${System.currentTimeMillis()}.txt"
                    if (cursor != null && cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        }
                        cursor.close()
                    }
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val notesDir = java.io.File(context.filesDir, "notes")
                        if (!notesDir.exists()) notesDir.mkdirs()
                        val destFile = java.io.File(notesDir, fileName)
                        java.io.FileOutputStream(destFile).use { out ->
                            inputStream.copyTo(out)
                        }
                        viewModel.importFile(android.net.Uri.fromFile(destFile))
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val dbFolder = context.getDatabasePath("infer_read_database").parentFile
                    if (dbFolder != null && dbFolder.exists()) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            java.util.zip.ZipOutputStream(out).use { zos ->
                                dbFolder.listFiles()?.forEach { file ->
                                    if (file.name.startsWith("infer_read_database")) {
                                        zos.putNextEntry(java.util.zip.ZipEntry(file.name))
                                        file.inputStream().use { it.copyTo(zos) }
                                        zos.closeEntry()
                                    }
                                }
                                val prefsFile = java.io.File(context.applicationInfo.dataDir, "shared_prefs/settings_prefs.xml")
                                if (prefsFile.exists()) {
                                    zos.putNextEntry(java.util.zip.ZipEntry("settings_prefs.xml"))
                                    prefsFile.inputStream().use { it.copyTo(zos) }
                                    zos.closeEntry()
                                }
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val dbFolder = context.getDatabasePath("infer_read_database").parentFile
                    val prefsFolder = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
                    if (!prefsFolder.exists()) prefsFolder.mkdirs()
                    
                    if (dbFolder != null) {
                        context.contentResolver.openInputStream(uri)?.use { inp ->
                            java.util.zip.ZipInputStream(inp).use { zis ->
                                var entry = zis.nextEntry
                                while (entry != null) {
                                    val dest = if (entry.name == "settings_prefs.xml") {
                                        java.io.File(prefsFolder, entry.name)
                                    } else {
                                        java.io.File(dbFolder, entry.name)
                                    }
                                    java.io.FileOutputStream(dest).use { out ->
                                        zis.copyTo(out)
                                    }
                                    entry = zis.nextEntry
                                }
                            }
                        }
                        // Kill app to restart and load new DB
                        kotlin.system.exitProcess(0)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun updateGoal(minutes: Int) {
        readingGoalMinutes = minutes
        prefs.edit().putInt("reading_goal_minutes", minutes).apply()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Reading Goal", 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold, 
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Set your daily minutes reading target.", 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        val presets = listOf(10, 15, 20, 30, 45)
                        
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presets.forEach { mins ->
                                FilterChip(
                                    selected = readingGoalMinutes == mins,
                                    onClick = { updateGoal(mins) },
                                    label = { Text("${mins}m") }
                                )
                            }
                            FilterChip(
                                selected = !presets.contains(readingGoalMinutes),
                                onClick = { showCustomGoalDialog = true },
                                label = { 
                                    Text(if (!presets.contains(readingGoalMinutes)) "Custom: ${readingGoalMinutes}m" else "Custom")
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onNavigateToStats,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = "Stats",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Stats",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { showFinishedBooksDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Finished Books",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Finished Books",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "App Theme Settings", 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold, 
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            "Background Theme", 
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bgOptions = listOf(
                                Triple(AppThemeBackground.System, "Auto", Color.Gray),
                                Triple(AppThemeBackground.ModernLight, "Light", Color(0xFFF9F5EB)),
                                Triple(AppThemeBackground.ModernDark, "Dark", Color(0xFF2C2C2C)),
                                Triple(AppThemeBackground.HighContrastLight, "HCLight", Color(0xFFFFFFFF)),
                                Triple(AppThemeBackground.HighContrastDark, "HCDark", Color(0xFF000000))
                            )
                            bgOptions.forEach { (bg, label, color) ->
                                val isSelected = appThemeBg == bg
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { ThemeManager.setBackground(context, bg) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = label, 
                                        style = MaterialTheme.typography.labelSmall, 
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Dynamic Accent Colors",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Use system Material You wallpaper colors",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = appThemeAccent == AppThemeAccent.Dynamic,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        ThemeManager.setAccent(context, AppThemeAccent.Dynamic)
                                    } else {
                                        ThemeManager.setAccent(context, AppThemeAccent.OceanSky)
                                    }
                                }
                            )
                        }

                        if (appThemeAccent != AppThemeAccent.Dynamic) {
                            Text(
                                "Accent Colors", 
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val presets = listOf(
                                    Pair(AppThemeAccent.OceanSky, Color(0xFF0288D1)),
                                    Pair(AppThemeAccent.VioletLavender, Color(0xFF7B1FA2)),
                                    Pair(AppThemeAccent.EmeraldMint, Color(0xFF388E3C)),
                                    Pair(AppThemeAccent.RosePeach, Color(0xFFD32F2F))
                                )
                                presets.forEach { (accent, color) ->
                                    val isSelected = appThemeAccent == accent
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 2.dp else 0.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { ThemeManager.setAccent(context, accent) }
                                    )
                                }
                                
                                val isCustomSelected = appThemeAccent == AppThemeAccent.Custom
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            androidx.compose.ui.graphics.Brush.sweepGradient(
                                                listOf(Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Magenta, Color.Red)
                                            )
                                        )
                                        .border(
                                            width = if (isCustomSelected) 2.dp else 0.dp,
                                            color = if (isCustomSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            val intent = android.content.Intent(context, com.infer.inferead.ui.theme.ThemeColorPickerActivity::class.java)
                                            context.startActivity(intent)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.ColorLens, 
                                        contentDescription = "Custom Color",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Advanced Settings", 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold, 
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Notes & Highlights Storage", 
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Files imported here are physically copied to the app's internal sandbox.", 
                                style = MaterialTheme.typography.labelSmall, 
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("*/*") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Import Notes")
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Data Backup & Restore", 
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Restoring a backup will immediately restart the app.", 
                                style = MaterialTheme.typography.labelSmall, 
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { exportLauncher.launch("infeRead_backup.zip") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Export Backup")
                                }
                                OutlinedButton(
                                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Import Backup")
                                }
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Homescreen Widgets", 
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Customize appearance, opacity, and styles of widgets.", 
                                style = MaterialTheme.typography.labelSmall, 
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    val intent = android.content.Intent(context, com.infer.inferead.widget.InfeReadWidgetConfigActivity::class.java)
                                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Configure Widget Options")
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Offline Mode",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Disable the Online Sources browser tab.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isOfflineMode,
                                onCheckedChange = { isChecked ->
                                    isOfflineMode = isChecked
                                    prefs.edit().putBoolean("is_offline_mode", isChecked).apply()
                                    if (isChecked) {
                                        onNavigateBack()
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
              item {
                  Divider()
                  Spacer(Modifier.height(8.dp))
                  Row(
                      modifier = Modifier
                          .fillMaxWidth()
                          .clickable { ratedFilesExpanded = !ratedFilesExpanded }
                          .padding(vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                      Text("Rated Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                      Row(verticalAlignment = Alignment.CenterVertically) {
                          if (ratedFilesExpanded) {
                              IconButton(onClick = { sortDescending = !sortDescending }) {
                                  Icon(
                                      if (sortDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                      contentDescription = "Sort"
                                  )
                              }
                          }
                          Icon(
                              if (ratedFilesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                              contentDescription = "Toggle"
                          )
                          Spacer(Modifier.width(8.dp))
                      }
                  }
                  if (ratedFilesExpanded && ratedFiles.isEmpty()) {
                      Text("No files rated yet.", color = Color.Gray, modifier = Modifier.padding(top = 16.dp))
                  }
              }
  
              if (ratedFilesExpanded) {
                  items(sortedRatedFiles) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { onOpenFile(file.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(file.format, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row {
                            for (i in 1..5) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (i <= file.rating) Color(0xFFFFC107) else Color.Gray.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
              }
            
            item {
                  Spacer(Modifier.height(24.dp))
                  Divider()
                  Spacer(Modifier.height(16.dp))
                  
                  Text(
                      "Supported Formats",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                      textAlign = androidx.compose.ui.text.style.TextAlign.Center
                  )
                  val sections = listOf(
                      "E-Books" to listOf("EPUB"),
                      "Documents" to listOf("PDF", "TXT", "DOC", "DOCX"),
                      "Comics & Manga" to listOf("CBZ", "CBR", "CB7"),
                      "Source Code" to listOf("MD", "PY", "C", "JAVA", "JS", "CSS"),
                      "Images" to listOf("JPG", "PNG", "WEBP", "HEIC", "HEIF", "BMP", "SVG")
                  )
                  val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { sections.size }, initialPage = 0)
                  
                  androidx.compose.foundation.pager.HorizontalPager(
                      state = pagerState,
                      contentPadding = PaddingValues(horizontal = 64.dp),
                      modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                  ) { page ->
                      val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                      val fraction = 1f - Math.abs(pageOffset).coerceIn(0f, 1f)
                      val scale = 0.85f + (0.15f * fraction)
                      val alpha = 0.5f + (0.5f * fraction)
                      
                      Card(
                          modifier = Modifier
                              .fillMaxWidth()
                              .padding(horizontal = 8.dp)
                              .graphicsLayer {
                                  scaleX = scale
                                  scaleY = scale
                                  this.alpha = alpha
                              },
                          shape = RoundedCornerShape(16.dp),
                          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                          elevation = CardDefaults.cardElevation(defaultElevation = if (page == pagerState.currentPage) 8.dp else 0.dp)
                      ) {
                          Column(
                              modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp).fillMaxWidth(),
                              horizontalAlignment = Alignment.CenterHorizontally
                          ) {
                              val (title, formats) = sections[page]
                              Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                              Spacer(Modifier.height(12.dp))
                              @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                              androidx.compose.foundation.layout.FlowRow(
                                  horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                  verticalArrangement = Arrangement.spacedBy(6.dp),
                                  modifier = Modifier.fillMaxWidth()
                              ) {
                                  formats.forEach { fmt ->
                                      androidx.compose.material3.AssistChip(
                                          onClick = { },
                                          label = { Text(fmt, fontSize = 11.sp, maxLines = 1) },
                                          modifier = Modifier.padding(horizontal = 1.dp),
                                          shape = RoundedCornerShape(6.dp)
                                      )
                                  }
                              }
                          }
                      }
                  }
                  
                  // Page indicator dots
                  Row(
                      modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                      horizontalArrangement = Arrangement.Center
                  ) {
                      repeat(sections.size) { index ->
                          Box(
                              modifier = Modifier
                                  .padding(horizontal = 3.dp)
                                  .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                                  .background(
                                      if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                      else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                      CircleShape
                                  )
                          )
                      }
                  }
                  
                  Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Created and Curated by,",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Shree",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    Text(
                        "Second Edition - v2.0.0",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showCustomGoalDialog) {
        var customInput by remember { mutableStateOf(readingGoalMinutes.toString()) }
        AlertDialog(
            onDismissRequest = { showCustomGoalDialog = false },
            title = { Text("Custom Reading Goal") },
            text = {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it.filter { char -> char.isDigit() } },
                    label = { Text("Minutes") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val parsed = customInput.toIntOrNull()
                    if (parsed != null && parsed > 0) {
                        updateGoal(parsed)
                    }
                    showCustomGoalDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomGoalDialog = false }) { Text("Cancel") }
            }
        )
    }
    
    if (showFinishedBooksDialog) {
        var isGridView by remember { mutableStateOf(false) }
        val finishedFiles = libraryFiles.filter { it.isFinished }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showFinishedBooksDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = { Text("Finished Books") },
                        navigationIcon = {
                            IconButton(onClick = { showFinishedBooksDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        actions = {
                            IconButton(onClick = { isGridView = !isGridView }) {
                                Icon(
                                    if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = "Toggle View"
                                )
                            }
                        }
                    )
                    
                    if (finishedFiles.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No finished books yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val dateFormat = java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.getDefault())
                        
                        if (isGridView) {
                            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(100.dp),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(finishedFiles.size) { index ->
                                    val file = finishedFiles[index]
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.clickable { onOpenFile(file.id) }
                                    ) {
                                        val shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(0.7f)
                                                .clip(shape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            if (file.thumbnailUri != null) {
                                                coil.compose.AsyncImage(
                                                    model = file.thumbnailUri,
                                                    contentDescription = null,
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            file.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        if (file.finishedAt > 0) {
                                            Text(
                                                dateFormat.format(java.util.Date(file.finishedAt)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(finishedFiles) { file ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                            .clickable { onOpenFile(file.id) },
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        val shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        Box(
                                            modifier = Modifier
                                                .width(80.dp)
                                                .aspectRatio(0.7f)
                                                .clip(shape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            if (file.thumbnailUri != null) {
                                                coil.compose.AsyncImage(
                                                    model = file.thumbnailUri,
                                                    contentDescription = null,
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                file.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            if (file.finishedAt > 0) {
                                                Text(
                                                    dateFormat.format(java.util.Date(file.finishedAt)),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
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
    }
}

package com.infer.inferead.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infer.inferead.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val sessions by viewModel.readingSessions.collectAsState()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE) }
    val goalMinutes = prefs.getInt("reading_goal_minutes", 15)

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Daily", "Weekly", "Monthly", "Yearly")

    val libraryFiles by viewModel.libraryFiles.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading Statistics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    val groupedData = remember(sessions, selectedTab) {
                        groupSessions(sessions, selectedTab)
                    }
                    
                    BarChartCard(
                        title = "${tabs[selectedTab]} Reading Time",
                        data = groupedData,
                        goal = goalMinutes
                    )
                }
                
                item {
                    CalendarHeatmapCard(
                        sessions = sessions,
                        goalMinutes = goalMinutes
                    )
                }

                item {
                    ReadingStylesCard(libraryFiles = libraryFiles)
                }
                
                item {
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

private fun groupSessions(sessions: List<com.infer.inferead.data.ReadingSession>, tabIndex: Int): List<Pair<String, Int>> {
    val cal = Calendar.getInstance()
    val sdfDay = SimpleDateFormat("EEE", Locale.getDefault())
    val sdfWeek = SimpleDateFormat("'W'W", Locale.getDefault())
    val sdfMonth = SimpleDateFormat("MMM", Locale.getDefault())
    val sdfYear = SimpleDateFormat("yyyy", Locale.getDefault())

    val grouped = mutableMapOf<String, Int>()
    
    sessions.forEach { session ->
        cal.timeInMillis = session.date
        val key = when (tabIndex) {
            0 -> sdfDay.format(cal.time) // Daily (Mon, Tue)
            1 -> sdfWeek.format(cal.time) // Weekly (W1, W2)
            2 -> sdfMonth.format(cal.time) // Monthly (Jan, Feb)
            3 -> sdfYear.format(cal.time) // Yearly (2025, 2026)
            else -> ""
        }
        grouped[key] = grouped.getOrDefault(key, 0) + session.durationMinutes
    }

    // Fill missing recent periods based on tabIndex for a fixed 7-item lookback
    val result = mutableListOf<Pair<String, Int>>()
    val fillCal = Calendar.getInstance()
    for (i in 6 downTo 0) {
        fillCal.timeInMillis = System.currentTimeMillis()
        when (tabIndex) {
            0 -> fillCal.add(Calendar.DAY_OF_YEAR, -i)
            1 -> fillCal.add(Calendar.WEEK_OF_YEAR, -i)
            2 -> fillCal.add(Calendar.MONTH, -i)
            3 -> fillCal.add(Calendar.YEAR, -i)
        }
        val key = when (tabIndex) {
            0 -> sdfDay.format(fillCal.time)
            1 -> sdfWeek.format(fillCal.time)
            2 -> sdfMonth.format(fillCal.time)
            3 -> sdfYear.format(fillCal.time)
            else -> ""
        }
        result.add(Pair(key, grouped[key] ?: 0))
    }
    return result
}

@Composable
fun BarChartCard(title: String, data: List<Pair<String, Int>>, goal: Int) {
    val textMeasurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            Canvas(modifier = Modifier
                .fillMaxHeight()
                .width((data.size * 50).dp)
            ) {
                val maxVal = maxOf(data.maxOfOrNull { it.second } ?: 1, goal).toFloat()
                val barWidth = 30.dp.toPx()
                val spacing = 20.dp.toPx()
                val heightAvailable = size.height - 40.dp.toPx()
                
                // Draw Goal Line
                val goalY = heightAvailable - (goal / maxVal) * heightAvailable
                drawLine(
                    color = errorColor,
                    start = Offset(0f, goalY),
                    end = Offset(size.width, goalY),
                    strokeWidth = 2.dp.toPx()
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = "Goal",
                    topLeft = Offset(5.dp.toPx(), goalY - 15.dp.toPx()),
                    style = TextStyle(color = errorColor, fontSize = 10.sp)
                )

                // Draw Bars
                data.forEachIndexed { index, pair ->
                    val x = index * (barWidth + spacing) + spacing / 2
                    val h = (pair.second / maxVal) * heightAvailable
                    val y = heightAvailable - h
                    
                    val barColor = if (pair.second >= goal) Color(0xFFFF9800) else primary
                    
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                    
                    drawText(
                        textMeasurer = textMeasurer,
                        text = pair.first,
                        topLeft = Offset(x, heightAvailable + 10.dp.toPx()),
                        style = TextStyle(color = onSurface, fontSize = 12.sp)
                    )
                    
                    if (pair.second > 0) {
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "${pair.second}",
                            topLeft = Offset(x + barWidth/4, y - 20.dp.toPx()),
                            style = TextStyle(color = onSurface, fontSize = 10.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarHeatmapCard(sessions: List<com.infer.inferead.data.ReadingSession>, goalMinutes: Int) {
    val dailyTotals = remember(sessions) {
        val map = mutableMapOf<String, Int>()
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sessions.forEach {
            cal.timeInMillis = it.date
            val key = sdf.format(cal.time)
            map[key] = map.getOrDefault(key, 0) + it.durationMinutes
        }
        map
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        Text("Activity Heatmap (Last 30 Days)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val cal = Calendar.getInstance()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            
            // Generate last 30 days
            cal.add(Calendar.DAY_OF_YEAR, -29)
            val days = mutableListOf<Pair<String, Int>>()
            for (i in 0 until 30) {
                val key = sdf.format(cal.time)
                days.add(Pair(key, dailyTotals[key] ?: 0))
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            
            val columns = days.chunked(7)
            columns.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        val metGoal = day.second >= goalMinutes
                        val color = if (day.second == 0) MaterialTheme.colorScheme.surface
                            else if (metGoal) Color(0xFFFF9800) // Orange if goal met
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFFF9800)))
            Text("Goal Met", fontSize = 12.sp)
            Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
            Text("Read", fontSize = 12.sp)
            Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surface))
        }
    }
}

@Composable
fun ReadingStylesCard(libraryFiles: List<com.infer.inferead.data.LibraryFile>) {
    val formatCounts = remember(libraryFiles) {
        val counts = mutableMapOf<String, Int>()
        libraryFiles.forEach { file ->
            val format = if (file.format == "CODING") "CODE" else file.format
            counts[format] = counts.getOrDefault(format, 0) + 1
        }
        counts.toList().sortedByDescending { it.second }
    }

    val totalFiles = maxOf(libraryFiles.size, 1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        Text("Library Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (formatCounts.isEmpty()) {
            Text("No files in library yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }

        // Progress Bar Chart
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val colors = listOf(
                Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800),
                Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF00BCD4)
            )
            
            formatCounts.forEachIndexed { index, pair ->
                val weight = (pair.second.toFloat() / totalFiles).coerceAtLeast(0.01f)
                Box(
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .background(colors[index % colors.size])
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Legend
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            formatCounts.forEachIndexed { index, pair ->
                val colors = listOf(
                    Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800),
                    Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF00BCD4)
                )
                val percentage = ((pair.second.toFloat() / totalFiles) * 100).toInt()
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(colors[index % colors.size]))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(pair.first, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${pair.second} files ($percentage%)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

package com.infer.inferead.widget

import android.content.Context
import android.content.Intent
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
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.datastore.preferences.core.intPreferencesKey
import com.infer.inferead.MainActivity
import com.infer.inferead.data.InfeReadDatabase
import kotlinx.coroutines.flow.first

// ─── Color palette (Keep-inspired, dark) ─────────────────────────────────────
private val BgCard      = Color(0xFF2D2D2D)
private val BgCardTop   = Color(0xFF383838)
private val TextPrimary = Color(0xFFE8EAED)
private val TextMuted   = Color(0xFF9AA0A6)
private val CheckboxOn  = Color(0xFF8AB4F8)  // Google blue
private val CheckboxOff = Color(0xFF5F6368)
private val Divider     = Color(0xFF3C4043)
private val NavButton   = Color(0xFF3C4043)

class ChecklistWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = InfeReadDatabase.getDatabase(context)
        val allChecklists = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            database.infeReadDao().getAllChecklists().first()
        }

        val prefs = androidx.glance.appwidget.state.getAppWidgetState(
            context, PreferencesGlanceStateDefinition, id
        )
        var currentIndex = prefs[intPreferencesKey("widget_checklist_index")] ?: 0

        if (allChecklists.isEmpty()) {
            provideContent {
                GlanceTheme {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .background(BgCard)
                            .cornerRadius(16.dp)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "📋",
                                style = TextStyle(
                                    color = ColorProvider(day = TextMuted, night = TextMuted),
                                    fontSize = 28.sp
                                )
                            )
                            Spacer(modifier = GlanceModifier.height(8.dp))
                            Text(
                                "No Checklists",
                                style = TextStyle(
                                    color = ColorProvider(day = TextMuted, night = TextMuted),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Spacer(modifier = GlanceModifier.height(12.dp))
                            val createIntent = Intent(context, MainActivity::class.java).apply {
                                action = "com.infer.inferead.NEW_CHECKLIST"
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            Box(
                                modifier = GlanceModifier
                                    .background(CheckboxOn)
                                    .cornerRadius(20.dp)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clickable(actionStartActivity(createIntent))
                            ) {
                                Text(
                                    "New list",
                                    style = TextStyle(
                                        color = ColorProvider(day = Color(0xFF202124), night = Color(0xFF202124)),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
            return
        }

        if (currentIndex >= allChecklists.size || currentIndex < 0) {
            currentIndex = 0
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { mutablePrefs ->
                mutablePrefs.toMutablePreferences().apply {
                    this[intPreferencesKey("widget_checklist_index")] = 0
                }
            }
        }

        val currentChecklist = allChecklists[currentIndex]
        val allItems = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            database.infeReadDao().getChecklistItems(currentChecklist.id).first()
        }
        // Unchecked items first, then checked — mirrors Keep's behavior
        val items = allItems.sortedBy { it.isCompleted }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.infer.inferead.OPEN_CHECKLIST"
            putExtra("checklist_id", currentChecklist.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val checkedCount = items.count { it.isCompleted }
        val totalCount   = items.size

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(BgCard)
                        .cornerRadius(16.dp)
                ) {
                    // ── Header bar ───────────────────────────────────────────
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(BgCardTop)
                            .cornerRadius(16.dp)          // top rounded only (visual trick)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Prev button
                        if (allChecklists.size > 1) {
                            Box(
                                modifier = GlanceModifier
                                    .size(30.dp)
                                    .background(NavButton)
                                    .cornerRadius(15.dp)
                                    .clickable(actionRunCallback<PrevChecklistAction>()),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "‹",
                                    style = TextStyle(
                                        color = ColorProvider(day = TextPrimary, night = TextPrimary),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Spacer(modifier = GlanceModifier.width(8.dp))
                        }

                        // Checklist title — tapping opens in app
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .clickable(actionStartActivity(openIntent))
                        ) {
                            Text(
                                currentChecklist.name,
                                style = TextStyle(
                                    color = ColorProvider(day = TextPrimary, night = TextPrimary),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }

                        // Progress chip  e.g. "3/7"
                        if (totalCount > 0) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Box(
                                modifier = GlanceModifier
                                    .background(NavButton)
                                    .cornerRadius(10.dp)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "$checkedCount/$totalCount",
                                    style = TextStyle(
                                        color = ColorProvider(day = TextMuted, night = TextMuted),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        // Next button
                        if (allChecklists.size > 1) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Box(
                                modifier = GlanceModifier
                                    .size(30.dp)
                                    .background(NavButton)
                                    .cornerRadius(15.dp)
                                    .clickable(actionRunCallback<NextChecklistAction>()),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "›",
                                    style = TextStyle(
                                        color = ColorProvider(day = TextPrimary, night = TextPrimary),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    // ── Items list ───────────────────────────────────────────
                    if (items.isEmpty()) {
                        Box(
                            modifier = GlanceModifier
                                .fillMaxSize()
                                .clickable(actionStartActivity(openIntent)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Tap to add items",
                                style = TextStyle(
                                    color = ColorProvider(day = TextMuted, night = TextMuted),
                                    fontSize = 13.sp,
                                    fontStyle = FontStyle.Italic
                                )
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .defaultWeight()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            items(items) { item ->
                                val toggleParams = actionParametersOf(
                                    ActionParameters.Key<Int>("item_id") to item.id,
                                    ActionParameters.Key<Boolean>("is_completed") to !item.isCompleted
                                )
                                Row(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // ── Checkbox circle ──────────────────────
                                    Box(
                                        modifier = GlanceModifier
                                            .size(20.dp)
                                            .cornerRadius(10.dp)
                                            .background(
                                                if (item.isCompleted) CheckboxOn else Color.Transparent
                                            )
                                            .clickable(
                                                actionRunCallback<ToggleItemAction>(toggleParams)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item.isCompleted) {
                                            Text(
                                                "✓",
                                                style = TextStyle(
                                                    color = ColorProvider(
                                                        day = Color(0xFF202124),
                                                        night = Color(0xFF202124)
                                                    ),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        } else {
                                            // Outlined circle via a slightly smaller inner box
                                            Box(
                                                modifier = GlanceModifier
                                                    .size(18.dp)
                                                    .cornerRadius(9.dp)
                                                    .background(CheckboxOff)
                                            ) {
                                                Box(
                                                    modifier = GlanceModifier
                                                        .size(15.dp)
                                                        .cornerRadius(7.dp)
                                                        .background(BgCard)
                                                ) {}
                                            }
                                        }
                                    }

                                    Spacer(modifier = GlanceModifier.width(10.dp))

                                    // ── Item title ───────────────────────────
                                    Text(
                                        item.title,
                                        style = TextStyle(
                                            color = ColorProvider(
                                                day = if (item.isCompleted) TextMuted else TextPrimary,
                                                night = if (item.isCompleted) TextMuted else TextPrimary
                                            ),
                                            fontSize = 13.sp,
                                            textDecoration = if (item.isCompleted)
                                                TextDecoration.LineThrough
                                            else
                                                TextDecoration.None
                                        ),
                                        maxLines = 1,
                                        modifier = GlanceModifier.defaultWeight()
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

// ─── Action callbacks ─────────────────────────────────────────────────────────

private suspend fun refreshAllChecklistWidgets(context: Context) {
    val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
    val ids = manager.getGlanceIds(ChecklistWidget::class.java)
    val widget = ChecklistWidget()
    ids.forEach { id -> widget.update(context, id) }
}

class NextChecklistAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val database = InfeReadDatabase.getDatabase(context)
        val allChecklists = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            database.infeReadDao().getAllChecklists().first()
        }
        if (allChecklists.isNotEmpty()) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                val currentIndex = prefs[intPreferencesKey("widget_checklist_index")] ?: 0
                prefs.toMutablePreferences().apply {
                    this[intPreferencesKey("widget_checklist_index")] =
                        (currentIndex + 1) % allChecklists.size
                }
            }
            refreshAllChecklistWidgets(context)
        }
    }
}

class PrevChecklistAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val database = InfeReadDatabase.getDatabase(context)
        val allChecklists = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            database.infeReadDao().getAllChecklists().first()
        }
        if (allChecklists.isNotEmpty()) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                val currentIndex = prefs[intPreferencesKey("widget_checklist_index")] ?: 0
                val newIndex = if (currentIndex - 1 < 0) allChecklists.size - 1 else currentIndex - 1
                prefs.toMutablePreferences().apply {
                    this[intPreferencesKey("widget_checklist_index")] = newIndex
                }
            }
            refreshAllChecklistWidgets(context)
        }
    }
}

class ToggleItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val itemId      = parameters[ActionParameters.Key<Int>("item_id")]      ?: return
        val isCompleted = parameters[ActionParameters.Key<Boolean>("is_completed")] ?: false
        val database = InfeReadDatabase.getDatabase(context)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            database.infeReadDao().updateChecklistItemCompletion(itemId, isCompleted)
        }
        refreshAllChecklistWidgets(context)
    }
}


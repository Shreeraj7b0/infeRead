package com.infer.inferead

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.infer.inferead.navigation.AppNavigation
import com.infer.inferead.ui.theme.InfeReadTheme
import com.infer.inferead.ui.theme.ThemeManager
import android.net.Uri
import android.content.Intent

val LocalThemeToggle = staticCompositionLocalOf<() -> Unit> { {} }

class MainActivity : ComponentActivity() {
    private val pendingIntentUri = kotlinx.coroutines.flow.MutableStateFlow<Uri?>(null)
    private val pendingWidgetAction = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val pendingWidgetChecklistId = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        ThemeManager.init(this)
        
        if (intent?.action == "com.infer.inferead.NEW_CHECKLIST" || intent?.action == "com.infer.inferead.OPEN_CHECKLIST") {
            pendingWidgetAction.value = intent?.action
            if (intent?.hasExtra("checklist_id") == true) {
                pendingWidgetChecklistId.value = intent?.getIntExtra("checklist_id", -1)
            }
        }
        
        val uri = intent?.data ?: if (intent?.action == Intent.ACTION_SEND) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
        } else null
        
        uri?.let { 
            if (it.scheme != "inferead") {
                try { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: SecurityException) {}
                pendingIntentUri.value = it 
            }
        }
        
        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by remember { mutableStateOf(systemDark) }

            val pendingUri by pendingIntentUri.collectAsState()
            val widgetAction by pendingWidgetAction.collectAsState()
            val widgetChecklistId by pendingWidgetChecklistId.collectAsState()
            
            CompositionLocalProvider(LocalThemeToggle provides { 
                val currentBg = ThemeManager.currentBackground.value
                val newBg = when (currentBg) {
                    com.infer.inferead.ui.theme.AppThemeBackground.ModernDark, 
                    com.infer.inferead.ui.theme.AppThemeBackground.HighContrastDark -> com.infer.inferead.ui.theme.AppThemeBackground.ModernLight
                    com.infer.inferead.ui.theme.AppThemeBackground.ModernLight, 
                    com.infer.inferead.ui.theme.AppThemeBackground.HighContrastLight -> com.infer.inferead.ui.theme.AppThemeBackground.ModernDark
                    com.infer.inferead.ui.theme.AppThemeBackground.System -> if (systemDark) com.infer.inferead.ui.theme.AppThemeBackground.ModernLight else com.infer.inferead.ui.theme.AppThemeBackground.ModernDark
                }
                ThemeManager.setBackground(this@MainActivity, newBg)
            }) {
                InfeReadTheme(darkTheme = isDarkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation(
                            pendingUri = pendingUri,
                            widgetAction = widgetAction,
                            widgetChecklistId = widgetChecklistId,
                            onUriHandled = { pendingIntentUri.value = null },
                            onWidgetActionHandled = {
                                pendingWidgetAction.value = null
                                pendingWidgetChecklistId.value = null
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        if (intent.action == "com.infer.inferead.NEW_CHECKLIST" || intent.action == "com.infer.inferead.OPEN_CHECKLIST") {
            pendingWidgetAction.value = intent.action
            if (intent.hasExtra("checklist_id")) {
                pendingWidgetChecklistId.value = intent.getIntExtra("checklist_id", -1)
            }
        }
        
        val uri = intent.data ?: if (intent.action == Intent.ACTION_SEND) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
        } else null
        
        uri?.let { 
            if (it.scheme != "inferead") {
                try { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: SecurityException) {}
                pendingIntentUri.value = it 
            }
        }
    }
}

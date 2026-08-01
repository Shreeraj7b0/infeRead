package com.infer.inferead.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedNavPane(
    drawerState: DrawerState,
    drawerReady: Boolean, // To prevent initial flash in HomeScreen
    isResizable: Boolean,
    initialWidth: Dp = 300.dp,
    onWidthChange: (Dp) -> Unit = {},
    headerContent: @Composable () -> Unit,
    topActionItem: @Composable () -> Unit,
    listContent: @Composable () -> Unit,
    bottomBarContent: @Composable () -> Unit
) {
    val isDrawerClosed = drawerState.currentValue == DrawerValue.Closed && drawerState.targetValue == DrawerValue.Closed
    val density = LocalDensity.current

    ModalDrawerSheet(
        modifier = Modifier
            .requiredWidth(initialWidth)
            .alpha(if (!drawerReady || isDrawerClosed) 0f else 1f)
            .padding(end = if (isResizable) 16.dp else 0.dp), // Padding for the drag handle
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = if (isResizable) 8.dp else 0.dp) // Leave space for drag handle visually
            ) {
                headerContent()
                Divider()
                topActionItem()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    listContent()
                }
                bottomBarContent()
            }

            if (isResizable) {
                val currentWidth by rememberUpdatedState(initialWidth)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(24.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                val dragDp = with(density) { dragAmount.toDp() }
                                val newWidth = (currentWidth + dragDp).coerceIn(150.dp, 450.dp)
                                onWidthChange(newWidth)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Two vertical lines as a drag handle
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(2.dp).height(32.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)))
                        Box(modifier = Modifier.width(2.dp).height(32.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)))
                    }
                }
            }
        }
    }
}

fun Modifier.consumeHorizontalNestedScroll() = this.nestedScroll(
    object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
            return androidx.compose.ui.geometry.Offset.Zero
        }
        override fun onPostScroll(
            consumed: androidx.compose.ui.geometry.Offset,
            available: androidx.compose.ui.geometry.Offset,
            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
        ): androidx.compose.ui.geometry.Offset {
            return androidx.compose.ui.geometry.Offset(available.x, 0f)
        }
    }
)

package com.infer.inferead.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun VerticalScrubber(
    progressProvider: () -> Float,
    onProgressChange: (Float) -> Unit,
    annotationPositions: List<Pair<Int, Float>>,
    annotations: List<com.infer.inferead.data.Annotation>,
    isBookmarked: Boolean = false,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val currentProgress = progressProvider()
    val displayProgress = if (isDragging) dragProgress else currentProgress

    // Smooth animated progress for the thumb position
    val animatedProgress by animateFloatAsState(
        targetValue = displayProgress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scrubber_progress"
    )

    AnimatedVisibility(
        visible = visible || isDragging,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            // Preview Bubble (only when dragging)
            AnimatedVisibility(
                visible = isDragging,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Text(
                        text = "${(displayProgress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(14.dp)
                    .fillMaxHeight(0.6f)
            ) {
                val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                val defaultThumbColor = MaterialTheme.colorScheme.primary
                val bookmarkThumbColor = Color(0xFFFFC107) // Yellow
                val thumbColor = if (isBookmarked) bookmarkThumbColor else defaultThumbColor
                val commentColor = MaterialTheme.colorScheme.onSurface

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val newProgress = (offset.y / size.height).coerceIn(0f, 1f)
                                    onProgressChange(newProgress)
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    dragProgress = (offset.y / size.height).coerceIn(0f, 1f)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    dragProgress = (change.position.y / size.height).coerceIn(0f, 1f)
                                    onProgressChange(dragProgress)
                                },
                                onDragEnd = {
                                    isDragging = false
                                },
                                onDragCancel = {
                                    isDragging = false
                                }
                            )
                        }
                ) {
                    val trackWidth = 3.dp.toPx()
                    val thumbRadius = 7.dp.toPx()
                    val xCenter = size.width / 2

                    // Draw Track
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(xCenter - trackWidth / 2, 0f),
                        size = Size(trackWidth, size.height),
                        cornerRadius = CornerRadius(trackWidth / 2, trackWidth / 2)
                    )

                    // Draw Indicators
                    val annMap = annotations.associateBy { it.id }
                    annotationPositions.forEach { (id, yProgress) ->
                        val ann = annMap[id]
                        if (ann != null) {
                            val yPos = (yProgress * size.height).coerceIn(0f, size.height)
                            if (!ann.textComment.isNullOrEmpty()) {
                                // Comment line
                                drawRect(
                                    color = commentColor,
                                    topLeft = Offset(xCenter - trackWidth * 1.2f, yPos - 0.75.dp.toPx()),
                                    size = Size(trackWidth * 2.4f, 1.5.dp.toPx())
                                )
                            } else {
                                // Highlight dot
                                val color = try { Color(android.graphics.Color.parseColor(ann.colorHex)) } catch (e: Exception) { Color.Red }
                                drawCircle(
                                    color = color,
                                    radius = 2.5.dp.toPx(),
                                    center = Offset(xCenter, yPos)
                                )
                            }
                        }
                    }

                    // Draw Thumb
                    val thumbY = (animatedProgress * size.height).coerceIn(thumbRadius, size.height - thumbRadius)
                    drawCircle(
                        color = thumbColor,
                        radius = thumbRadius,
                        center = Offset(xCenter, thumbY)
                    )
                }
            }
        }
    }
}

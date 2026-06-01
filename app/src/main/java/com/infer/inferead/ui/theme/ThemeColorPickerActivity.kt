package com.infer.inferead.ui.theme

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class ThemeColorPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            InfeReadTheme {
                var h by remember { mutableFloatStateOf(0f) }
                var s by remember { mutableFloatStateOf(1f) }
                var v by remember { mutableFloatStateOf(0.8f) }
                
                val context = LocalContext.current
                
                // Initialize HSV values from ThemeManager.customColor
                val currentCustomColor by ThemeManager.customColor.collectAsState()
                LaunchedEffect(Unit) {
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(
                        android.graphics.Color.argb(
                            (currentCustomColor.alpha * 255).toInt(),
                            (currentCustomColor.red * 255).toInt(),
                            (currentCustomColor.green * 255).toInt(),
                            (currentCustomColor.blue * 255).toInt()
                        ),
                        hsv
                    )
                    h = hsv[0]
                    s = hsv[1]
                    v = hsv[2]
                }
                
                val selectedColor = Color.hsv(h, s, v)

                val presets = listOf(
                    Pair("Sepia", Color(0xFFF4ECD8)),
                    Pair("Crimson Red", Color(0xFFDC143C)),
                    Pair("Grey-Brown", Color(0xFF5C5552)),
                    Pair("Lemon Green", Color(0xFFADFF2F))
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)) // Translucent overlay
                        .clickable { finish() },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(16.dp)
                            .clickable(enabled = false) { /* Prevent click propagation */ },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Accent Color Picker",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            // Color Preview Circle
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(selectedColor)
                                    .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
                            )
                            
                            // Hue Slider
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Hue", style = MaterialTheme.typography.bodyMedium)
                                    Text("${h.toInt()}°", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = h,
                                    onValueChange = { h = it },
                                    valueRange = 0f..360f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Saturation Slider
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Saturation", style = MaterialTheme.typography.bodyMedium)
                                    Text("${(s * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = s,
                                    onValueChange = { s = it },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Value (Brightness) Slider
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Brightness", style = MaterialTheme.typography.bodyMedium)
                                    Text("${(v * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = v,
                                    onValueChange = { v = it },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Preset Section
                            Text(
                                "Presets",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                presets.forEach { (name, color) ->
                                    val isColorSelected = selectedColor == color
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.clickable {
                                            val hsv = FloatArray(3)
                                            android.graphics.Color.colorToHSV(
                                                android.graphics.Color.argb(
                                                    (color.alpha * 255).toInt(),
                                                    (color.red * 255).toInt(),
                                                    (color.green * 255).toInt(),
                                                    (color.blue * 255).toInt()
                                                ),
                                                hsv
                                            )
                                            h = hsv[0]
                                            s = hsv[1]
                                            v = hsv[2]
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(
                                                    width = if (isColorSelected) 3.dp else 1.dp,
                                                    color = if (isColorSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                    shape = CircleShape
                                                )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = if (isColorSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TextButton(
                                    onClick = { finish() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        ThemeManager.setCustomColor(context, selectedColor)
                                        ThemeManager.setAccent(context, AppThemeAccent.Custom)
                                        finish()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Apply")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

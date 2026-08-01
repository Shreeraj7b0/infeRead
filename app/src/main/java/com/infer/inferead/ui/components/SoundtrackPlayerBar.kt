package com.infer.inferead.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.infer.inferead.utils.AudioTrack
import com.infer.inferead.utils.SoundtrackManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoundtrackPlayerBar(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Initialize manager
    LaunchedEffect(Unit) {
        SoundtrackManager.initialize(context)
    }

    val isPlaying by SoundtrackManager.isPlaying.collectAsState()
    val currentTrack by SoundtrackManager.currentTrack.collectAsState()
    val tracksList by SoundtrackManager.tracksList.collectAsState()

    var isMarqueeActive by remember { mutableStateOf(false) }
    var showDropdown by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Auto-dismiss error message after 1.5 seconds
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            delay(1500)
            errorMessage = null
        }
    }

    // Custom track picker launcher
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val result = SoundtrackManager.importCustomTrack(context, uri)
            if (result is SoundtrackManager.ImportResult.Error) {
                errorMessage = result.message
            } else {
                Toast.makeText(context, "Track imported successfully!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Render Error dialog if size check fails
    if (errorMessage != null) {
        Dialog(onDismissRequest = { errorMessage = null }) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Artwork (or Gradient)
            if (currentTrack != null && currentTrack!!.thumbnailPath != null) {
                val imageModel: Any = if (currentTrack!!.isCustom) {
                    val file = java.io.File(currentTrack!!.thumbnailPath!!)
                    coil.request.ImageRequest.Builder(context)
                        .data(file)
                        .memoryCacheKey(currentTrack!!.thumbnailPath + "_" + file.lastModified())
                        .build()
                } else {
                    "file:///android_asset/soundtracks/${currentTrack!!.thumbnailPath}"
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            drawRect(Color.Black.copy(alpha = 0.55f))
                        },
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF2C3E50),
                                    Color(0xFF000000)
                                )
                            )
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Music Icon / Volume visual
                Icon(
                    imageVector = if (isPlaying) Icons.Default.VolumeUp else Icons.Default.MusicNote,
                    contentDescription = "Soundtrack status",
                    tint = if (isPlaying) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))

                // Track Title (Long press toggles Marquee)
                val scrollModifier = if (isMarqueeActive) Modifier.basicMarquee() else Modifier
                Text(
                    text = currentTrack?.name ?: "No Soundtrack playing",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .then(scrollModifier)
                        .combinedClickable(
                            onLongClick = {
                                isMarqueeActive = !isMarqueeActive
                            },
                            onClick = {
                                // regular tap does nothing or toggles state
                            }
                        )
                )

                // Controls: Play/Pause
                IconButton(
                    onClick = { SoundtrackManager.togglePlayPause(context) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Switch Track Dropdown
                Box {
                    IconButton(
                        onClick = { showDropdown = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Switch Track / Import",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false },
                        modifier = Modifier
                            .width(200.dp)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        tracksList.forEach { track ->
                            val isSelected = currentTrack?.uri == track.uri
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = track.name,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    showDropdown = false
                                    SoundtrackManager.playTrack(context, track)
                                },
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.VolumeUp,
                                            contentDescription = "Selected",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                        // Import Custom Track Item
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Import Music (<25MB)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                showDropdown = false
                                filePicker.launch("audio/*")
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Import",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

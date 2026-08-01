package com.infer.inferead.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.infer.inferead.services.SoundtrackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class AudioTrack(
    val name: String,
    val uri: String, // Filename for assets, absolute path for custom files
    val isCustom: Boolean,
    val thumbnailPath: String? = null // Asset filename or null/generic for custom
)

object SoundtrackManager {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack

    private val _tracksList = MutableStateFlow<List<AudioTrack>>(emptyList())
    val tracksList: StateFlow<List<AudioTrack>> = _tracksList

    private val defaultTracks = listOf(
        AudioTrack("Morning Birds", "morning_birds.ogg", false, "morning_birds.jpg"),
        AudioTrack("River Stream", "river_stream.ogg", false, "river_stream.jpg"),
        AudioTrack("Night Ambience", "night_ambience.ogg", false, "night_ambience.jpg"),
        AudioTrack("Rain Thunder", "rain_thunder.ogg", false, "rain_thunder.jpg")
    )

    fun initialize(context: Context) {
        if (_currentTrack.value == null) {
            _currentTrack.value = defaultTracks.firstOrNull()
        }
        loadTracks(context)
    }

    fun setPlayingState(playing: Boolean) {
        _isPlaying.value = playing
    }

    private fun loadTracks(context: Context) {
        val list = mutableListOf<AudioTrack>()
        list.addAll(defaultTracks)

        val dir = File(context.filesDir, "soundtracks")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val files = dir.listFiles { _, name ->
            name.endsWith(".mp3", true) || name.endsWith(".wav", true) || name.endsWith(".ogg", true)
        }
        files?.forEach { file ->
            val nameWithoutExt = file.nameWithoutExtension.replace("_", " ").replace("-", " ")
            val parent = file.parentFile
            val baseName = file.nameWithoutExtension
            val imgFile = listOf("jpg", "jpeg", "png")
                .map { ext -> File(parent, "$baseName.$ext") }
                .firstOrNull { it.exists() }
            list.add(AudioTrack(nameWithoutExt, file.absolutePath, true, imgFile?.absolutePath))
        }

        _tracksList.value = list
    }

    fun playTrack(context: Context, track: AudioTrack) {
        _currentTrack.value = track
        val intent = Intent(context, SoundtrackService::class.java).apply {
            action = SoundtrackService.ACTION_CHANGE_TRACK
            putExtra(SoundtrackService.EXTRA_TRACK_NAME, track.name)
            putExtra(SoundtrackService.EXTRA_TRACK_URI, track.uri)
            putExtra(SoundtrackService.EXTRA_IS_CUSTOM, track.isCustom)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun togglePlayPause(context: Context) {
        val intent = Intent(context, SoundtrackService::class.java)
        if (_isPlaying.value) {
            intent.action = SoundtrackService.ACTION_PAUSE
        } else {
            intent.action = SoundtrackService.ACTION_PLAY
        }
        context.startService(intent)
    }

    fun notifyAppClosed(context: Context) {
        val intent = Intent(context, SoundtrackService::class.java).apply {
            action = SoundtrackService.ACTION_APP_CLOSED
        }
        context.startService(intent)
    }

    sealed class ImportResult {
        object Success : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    fun importCustomTrack(context: Context, uri: Uri): ImportResult {
        try {
            val contentResolver = context.contentResolver
            
            // Get size
            var fileSize: Long = 0
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }

            if (fileSize > 25 * 1024 * 1024) {
                return ImportResult.Error("File size should be less than 25MB.")
            }

            // Get file name
            var fileName = "custom_track_${System.currentTimeMillis()}.mp3"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            val extension = fileName.substringAfterLast(".", "").lowercase()
            if (extension != "mp3" && extension != "wav" && extension != "ogg") {
                return ImportResult.Error("Only .mp3, .wav, and .ogg formats are supported.")
            }

            val dir = File(context.filesDir, "soundtracks")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val destFile = File(dir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            loadTracks(context)
            return ImportResult.Success

        } catch (e: Exception) {
            e.printStackTrace()
            return ImportResult.Error("Import failed: ${e.localizedMessage}")
        }
    }

    fun deleteCustomTrack(context: Context, track: AudioTrack) {
        val file = File(track.uri)
        if (file.exists()) {
            file.delete()
        }
        val parent = file.parentFile
        val baseName = file.nameWithoutExtension
        listOf("jpg", "jpeg", "png").forEach { ext ->
            val imgFile = File(parent, "$baseName.$ext")
            if (imgFile.exists()) {
                imgFile.delete()
            }
        }
        loadTracks(context)
        
        if (_currentTrack.value?.uri == track.uri) {
            val intent = Intent(context, SoundtrackService::class.java).apply {
                action = SoundtrackService.ACTION_STOP
            }
            context.startService(intent)
            _currentTrack.value = defaultTracks.firstOrNull()
        }
    }

    fun setCustomTrackThumbnail(context: Context, track: AudioTrack, imageUri: Uri): ImportResult {
        try {
            val contentResolver = context.contentResolver
            
            var fileSize: Long = 0
            contentResolver.query(imageUri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
            
            if (fileSize > 2 * 1024 * 1024) {
                return ImportResult.Error("File size should be less than 2MB.")
            }

            val trackFile = File(track.uri)
            val parent = trackFile.parentFile
            val baseName = trackFile.nameWithoutExtension
            
            listOf("jpg", "jpeg", "png").forEach { ext ->
                val imgFile = File(parent, "$baseName.$ext")
                if (imgFile.exists()) {
                    imgFile.delete()
                }
            }

            var fileName = "image.jpg"
            contentResolver.query(imageUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            val ext = fileName.substringAfterLast(".", "jpg").lowercase()

            val destFile = File(parent, "$baseName.$ext")
            contentResolver.openInputStream(imageUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            loadTracks(context)
            
            if (_currentTrack.value?.uri == track.uri) {
                _currentTrack.value = _currentTrack.value?.copy(thumbnailPath = destFile.absolutePath)
            }

            return ImportResult.Success
        } catch (e: Exception) {
            e.printStackTrace()
            return ImportResult.Error("Failed to set thumbnail: ${e.localizedMessage}")
        }
    }
}

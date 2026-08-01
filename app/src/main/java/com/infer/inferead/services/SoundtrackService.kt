package com.infer.inferead.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.infer.inferead.MainActivity
import com.infer.inferead.utils.SoundtrackManager
import java.io.File

class SoundtrackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val channelId = "soundtrack_playback_channel"
    private val notificationId = 8872

    companion object {
        const val ACTION_PLAY = "com.infer.inferead.action.PLAY"
        const val ACTION_PAUSE = "com.infer.inferead.action.PAUSE"
        const val ACTION_CHANGE_TRACK = "com.infer.inferead.action.CHANGE_TRACK"
        const val ACTION_APP_CLOSED = "com.infer.inferead.action.APP_CLOSED"
        const val ACTION_STOP = "com.infer.inferead.action.STOP"

        const val EXTRA_TRACK_NAME = "extra_track_name"
        const val EXTRA_TRACK_URI = "extra_track_uri"
        const val EXTRA_IS_CUSTOM = "extra_is_custom"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> resumePlayback()
            ACTION_PAUSE -> pausePlayback(false)
            ACTION_APP_CLOSED -> pausePlayback(true)
            ACTION_CHANGE_TRACK -> {
                val name = intent.getStringExtra(EXTRA_TRACK_NAME) ?: ""
                val uri = intent.getStringExtra(EXTRA_TRACK_URI) ?: ""
                val isCustom = intent.getBooleanExtra(EXTRA_IS_CUSTOM, false)
                changeTrack(name, uri, isCustom)
            }
            ACTION_STOP -> stopService()
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Reading Soundtrack"
            val descriptionText = "Controls loopable soothing background music"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        val currentTrack = SoundtrackManager.currentTrack.value
        val trackName = currentTrack?.name ?: "No Track Selected"

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseActionIntent = Intent(this, SoundtrackService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 1, playPauseActionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopActionIntent = Intent(this, SoundtrackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopActionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseLabel = if (isPlaying) "Pause" else "Play"

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Reading Soundtrack")
            .setContentText(trackName)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseIcon, playPauseLabel, playPausePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1))
            .build()
    }

    private fun startForegroundService() {
        val notification = buildNotification(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun changeTrack(name: String, uriStr: String, isCustom: Boolean) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                isLooping = true
            }

            if (isCustom) {
                val file = File(uriStr)
                if (file.exists()) {
                    mediaPlayer?.setDataSource(uriStr)
                } else {
                    SoundtrackManager.setPlayingState(false)
                    return
                }
            } else {
                val assetManager = assets
                val afd = assetManager.openFd("soundtracks/$uriStr")
                mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            }

            mediaPlayer?.prepare()
            mediaPlayer?.start()
            SoundtrackManager.setPlayingState(true)
            startForegroundService()

        } catch (e: Exception) {
            e.printStackTrace()
            SoundtrackManager.setPlayingState(false)
        }
    }

    private fun resumePlayback() {
        try {
            if (mediaPlayer == null) {
                val currentTrack = SoundtrackManager.currentTrack.value
                if (currentTrack != null) {
                    changeTrack(currentTrack.name, currentTrack.uri, currentTrack.isCustom)
                    return
                }
            }
            mediaPlayer?.start()
            SoundtrackManager.setPlayingState(true)
            startForegroundService()
        } catch (e: Exception) {
            e.printStackTrace()
            SoundtrackManager.setPlayingState(false)
        }
    }

    private fun pausePlayback(appClosed: Boolean) {
        try {
            mediaPlayer?.pause()
            SoundtrackManager.setPlayingState(false)
            
            if (appClosed) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(notificationId, buildNotification(false))
            } else {
                val notification = buildNotification(false)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(notificationId, notification)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopService() {
        mediaPlayer?.release()
        mediaPlayer = null
        SoundtrackManager.setPlayingState(false)
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}

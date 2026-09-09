package com.weixu.ueatsmonitor.action

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.weixu.ueatsmonitor.ui.MainActivity
import java.io.File

/**
 * Action. Records the screen in fixed-size segments for the length of a shift.
 *
 * Segments, not one long file: a crash costs at most one segment, the oldest can
 * be dropped when the folder outgrows its budget, and pulling a shift off the
 * phone does not mean moving a single multi-gigabyte file.
 */
class ScreenRecorderService : Service() {

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private lateinit var store: RecordingStore
    private var current: File? = null
    private var startedAtMillis: Long = 0L
    private val clock = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (System.currentTimeMillis() - startedAtMillis >= AUTO_STOP_MILLIS) {
                ServiceJournal.note(this@ScreenRecorderService, "录屏到时自动停止")
                stopSelf()
                return
            }
            goForeground()
            clock.postDelayed(this, TICK_MILLIS)
        }
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "projection stopped by the system")
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        store = RecordingStore(this)
        startedAtMillis = System.currentTimeMillis()
        goForeground()

        // The foreground service must already be running before the projection starts.
        val manager = getSystemService(MediaProjectionManager::class.java)
        val started = runCatching {
            projection = manager.getMediaProjection(resultCode, resultData).also {
                it.registerCallback(projectionCallback, null)
            }
            startSegment()
        }
        if (started.isFailure) {
            Log.w(TAG, "could not start recording: " + started.exceptionOrNull()?.message)
            ServiceJournal.note(this, "录屏启动失败：" + started.exceptionOrNull()?.message)
            stopSelf()
            return START_NOT_STICKY
        }

        RecordingStatus.running.value = true
        ServiceJournal.note(this, "录屏已开始")
        clock.postDelayed(tick, TICK_MILLIS)
        return START_STICKY
    }

    override fun onDestroy() {
        clock.removeCallbacks(tick)
        stopSegment()
        display?.release()
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        RecordingStatus.running.value = false
        ServiceJournal.note(this, "录屏已停止")
        super.onDestroy()
    }

    private fun startSegment() {
        store.pruneTo(BUDGET_BYTES)
        val file = store.nextSegment()
        current = file

        val media = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        media.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(WIDTH, HEIGHT)
            setVideoFrameRate(FPS)
            setVideoEncodingBitRate(BITRATE)
            setOutputFile(file.absolutePath)
            setMaxFileSize(SEGMENT_BYTES)
            setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_APPROACHING) {
                    rollOver()
                }
            }
            prepare()
        }
        recorder = media

        display = projection?.createVirtualDisplay(
            "ueats-recorder",
            WIDTH,
            HEIGHT,
            DENSITY,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            media.surface,
            null,
            null,
        )
        media.start()
        Log.i(TAG, "segment started: " + file.name)
    }

    /** Hands the encoder the next file so the video never stops mid-shift. */
    private fun rollOver() {
        val media = recorder ?: return
        runCatching {
            val next = store.nextSegment()
            current = next
            media.setNextOutputFile(next)
            store.pruneTo(BUDGET_BYTES)
            Log.i(TAG, "rolled over to " + next.name)
        }.onFailure { Log.w(TAG, "roll over failed: " + it.message) }
    }

    private fun stopSegment() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
    }

    private fun goForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "录屏", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = android.app.PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenRecorderService::class.java).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("正在录屏 · " + elapsed())
            .setContentText(sizeSoFar() + " · " + hoursLeft() + " 后自动停")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(
                        this, android.R.drawable.ic_menu_close_clear_cancel
                    ),
                    "停止录屏",
                    stop,
                ).build()
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun elapsed(): String {
        val minutes = (System.currentTimeMillis() - startedAtMillis) / 60_000L
        return if (minutes < 60) "$minutes 分钟" else "${minutes / 60} 小时 ${minutes % 60} 分"
    }

    private fun sizeSoFar(): String {
        val bytes = store.totalBytes()
        return if (bytes >= 1024L * 1024 * 1024) {
            String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
        } else {
            String.format("%.0f MB", bytes / 1024.0 / 1024.0)
        }
    }

    private fun hoursLeft(): String {
        val left = AUTO_STOP_MILLIS - (System.currentTimeMillis() - startedAtMillis)
        val minutes = (left / 60_000L).coerceAtLeast(0)
        return if (minutes < 60) "$minutes 分钟" else "${minutes / 60} 小时"
    }

    companion object {
        private const val TAG = "UEatsMonitor"
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 42

        const val ACTION_STOP = "com.weixu.ueatsmonitor.STOP_RECORDING"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        /** 720x1600 at 8 fps and 800 kbps is about 360 MB an hour and still legible. */
        private const val WIDTH = 720
        private const val HEIGHT = 1600
        private const val DENSITY = 320
        private const val FPS = 8
        private const val BITRATE = 800_000
        private const val SEGMENT_BYTES = 30L * 1024 * 1024
        private const val BUDGET_BYTES = 3L * 1024 * 1024 * 1024

        /** A shift is two or three hours; four is a recording someone forgot about. */
        private const val AUTO_STOP_MILLIS = 4L * 60 * 60 * 1000
        private const val TICK_MILLIS = 60_000L

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenRecorderService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScreenRecorderService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}

package com.weixu.ueatsmonitor.action

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.weixu.ueatsmonitor.R
import com.weixu.ueatsmonitor.domain.OfferParser
import com.weixu.ueatsmonitor.ui.MainActivity

/**
 * Action. Keeps the recorder's process awake and drives its clock.
 *
 * The polling used to live on the accessibility service's main-thread Handler.
 * Android freezes a cached process, and with no foreground component of our own
 * that Handler simply stopped firing - which is how offers came and went leaving
 * hour-long holes with not one screenshot in them.
 *
 * A foreground service is the only thing that reliably stops that. Its clock
 * runs on its own thread, and a partial wake lock keeps the CPU on it.
 */
class CaptureKeeperService : Service() {

    private lateinit var thread: HandlerThread
    private lateinit var clock: Handler
    private var wakeLock: PowerManager.WakeLock? = null
    private var ticks: Long = 0

    private val tick = object : Runnable {
        override fun run() {
            ticks++
            UberScreenService.pokeFromKeeper()
            if (ticks % NOTE_EVERY == 0L) {
                ServiceJournal.note(this@CaptureKeeperService, "采集心跳 " + ticks + " 次")
            }
            clock.postDelayed(this, TICK_MILLIS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground()
        thread = HandlerThread("capture-keeper").apply { start() }
        clock = Handler(thread.looper)
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ueats:keeper")
            ?.apply { setReferenceCounted(false); acquire() }
        clock.post(tick)
        KeeperStatus.running.value = true
        ServiceJournal.note(this, "采集守护已启动")
        Log.i(TAG, "keeper started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Take the notification with it. Leaving the notification up after a
            // deliberate quit says the monitor is running when it is not.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            }
            stopSelf()
            return START_NOT_STICKY
        }
        // Since Android 13 this notification can be swiped away, and once it is
        // there is no way back from the phone. Posting it again here means opening
        // the app brings it back - the driver asking for it, rather than the app
        // putting back something he dismissed.
        goForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        clock.removeCallbacks(tick)
        thread.quitSafely()
        runCatching { wakeLock?.release() }
        KeeperStatus.running.value = false
        ServiceJournal.note(this, "采集守护已停止")
        super.onDestroy()
    }

    private fun goForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "采集守护", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // A custom body, not addAction: an action is hidden until the shade entry
        // is expanded, and these two have to be one tap away while driving.
        val body = RemoteViews(packageName, R.layout.keeper_notification).apply {
            setOnClickPendingIntent(R.id.keeperOpen, activity(Intent(this@CaptureKeeperService, MainActivity::class.java), 1))
            // Android 11 hides other packages unless the manifest names them;
            // without that this comes back null and the button does nothing.
            val uber = packageManager.getLaunchIntentForPackage(OfferParser.UBER_DRIVER_PACKAGE)
            if (uber == null) {
                setViewVisibility(R.id.keeperUber, View.GONE)
            } else {
                setOnClickPendingIntent(R.id.keeperUber, activity(uber, 2))
            }
        }
        val notification: Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentIntent(open)
            .setOngoing(true)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(body)
            .setCustomBigContentView(body)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun activity(target: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            this,
            requestCode,
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val TAG = "UEatsMonitor"
        private const val CHANNEL = "keeper"
        private const val NOTIFICATION_ID = 43
        private const val TICK_MILLIS = 1_000L
        private const val NOTE_EVERY = 300L

        const val ACTION_STOP = "com.weixu.ueatsmonitor.STOP_KEEPER"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CaptureKeeperService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, CaptureKeeperService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}

/** Action. Whether the foreground keeper is alive. */
object KeeperStatus {
    val running = kotlinx.coroutines.flow.MutableStateFlow(false)
}

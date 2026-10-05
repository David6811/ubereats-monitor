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
            holdWake()
            ticks++
            UberScreenService.pokeFromKeeper()
            if (ticks % NOTE_EVERY == 0L) {
                ServiceJournal.note(this@CaptureKeeperService, "采集心跳 " + ticks + " 次")
            }
            repaintIfChanged()
            clock.postDelayed(this, TICK_MILLIS)
        }
    }

    /** What the row is painted from. Re-posted only when one of them turns. */
    private var painted: Pair<Boolean, Boolean>? = null

    private fun repaintIfChanged() {
        val now = VoiceService.isRunning() to stopKnown()
        if (now == painted) return
        painted = now
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification())
        }.onFailure { Log.w(TAG, "keeper: could not repaint the notice", it) }
    }

    /** Whether the map button would have somewhere to send the driver. */
    private fun stopKnown(): Boolean =
        !CurrentStop.trip.empty || UberScreenService.uberScreenNow().isNotEmpty()

    private fun holdWake() {
        val lock = wakeLock ?: return
        if (!lock.isHeld) lock.acquire()
    }


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground()
        thread = HandlerThread("capture-keeper").apply { start() }
        clock = Handler(thread.looper)
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ueats:keeper")
            ?.apply { setReferenceCounted(false) }
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
                // LOW, not MIN. At MIN this sat in the shade's "Silent" pile beside
                // the voice notice, and the phone packed the two of them into a
                // group showing a count - which is where the buttons went the
                // moment the voice was switched on.
                NotificationChannel(CHANNEL, driverWords().keeperChannel, NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(NOTIFICATION_ID, notification())
    }

    /** The notice as it should look right now. Built again whenever a button turns. */
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // A custom body, not addAction: an action is hidden until the shade entry
        // is expanded, and these two have to be one tap away while driving.
        val body = RemoteViews(packageName, R.layout.keeper_notification).apply {
            // The same row of buttons that floats over the map, here in the
            // collapsed notification: one swipe reaches them, and nothing has
            // to be expanded, which at the wheel is a second of eyes off the road.
            val listening = VoiceService.isRunning()
            setInt(R.id.keeperVoice, "setImageResource",
                if (listening) R.drawable.ic_tool_mic else R.drawable.ic_tool_mic_off)
            // Gold when the button has something to do, grey when it has not.
            // Gold, not green: a colour this driver cannot tell from red would
            // be no signal at all, and the microphone keeps its struck-through
            // shape so the state is never the colour alone.
            setInt(R.id.keeperNav, "setColorFilter", LIVE)
            setInt(R.id.keeperMap, "setColorFilter", if (stopKnown()) LIVE else DULL)
            setInt(R.id.keeperVoice, "setColorFilter", if (listening) LIVE else DULL)
            setOnClickPendingIntent(R.id.keeperNav, broadcast(NotificationButtons.STOP_NAVIGATION, 11))
            setOnClickPendingIntent(R.id.keeperMap, broadcast(NotificationButtons.SEND_TO_MAPS, 12))
            setOnClickPendingIntent(R.id.keeperVoice, broadcast(NotificationButtons.TOGGLE_VOICE, 13))
        }
        val notification: Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentIntent(open)
            .setOngoing(true)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(body)
            .setCustomBigContentView(body)
            .build()
        return notification
    }

    private fun broadcast(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            requestCode,
            Intent(action).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

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
        /** The app's gold. Yellow against grey reads for a red-green eye; green would not. */
        private val LIVE = android.graphics.Color.parseColor("#FFE8B64C")

        /** Nothing to do: grey, and dimmer, so the difference is not the hue alone. */
        private val DULL = android.graphics.Color.parseColor("#FF8A8D93")

        /** Both ongoing notices belong to it; the keeper's is its summary. */
        const val GROUP = "com.weixu.ueatsmonitor.ongoing"

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

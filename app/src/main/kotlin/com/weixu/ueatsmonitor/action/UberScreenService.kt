package com.weixu.ueatsmonitor.action

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.weixu.ueatsmonitor.domain.AcceptBand
import com.weixu.ueatsmonitor.domain.AreaCall
import com.weixu.ueatsmonitor.domain.AreaJudge
import com.weixu.ueatsmonitor.domain.OfferParser
import com.weixu.ueatsmonitor.domain.OfferCardReader
import com.weixu.ueatsmonitor.domain.OfferEvaluator
import com.weixu.ueatsmonitor.domain.DropoffScreen
import com.weixu.ueatsmonitor.domain.OfferShape
import com.weixu.ueatsmonitor.domain.PickupScreen
import com.weixu.ueatsmonitor.domain.RuleJudge
import com.weixu.ueatsmonitor.domain.Ruling
import com.weixu.ueatsmonitor.domain.RulingText
import com.weixu.ueatsmonitor.domain.VerdictText
import com.weixu.ueatsmonitor.domain.Suburb
import com.weixu.ueatsmonitor.domain.ChipText
import com.weixu.ueatsmonitor.domain.OfferCard
import com.weixu.ueatsmonitor.domain.RoadIndex
import com.weixu.ueatsmonitor.domain.SuburbIndex
import java.util.concurrent.Executors

/**
 * Action. Records what the Uber apps show, because a foreground offer card never
 * reaches the notification listener.
 *
 * It does not wait to be told. A shift was lost to that: for 28 minutes Android
 * delivered no accessibility event at all, so nothing was recorded even though
 * offers were ringing. This polls the window list on its own clock instead, and
 * treats incoming events only as a reason to look sooner.
 *
 * It only reads. It never taps, never accepts, never declines.
 */
class UberScreenService : AccessibilityService() {

    private val store: CaptureStore by lazy { CaptureStore(this) }
    private val position: CurrentPosition by lazy { CurrentPosition(this) }
    private val chime: Chime by lazy { Chime() }
    private val overlay: OverlayController by lazy { OverlayController(this) }
    private val gazetteer: List<Suburb> by lazy { Gazetteer.suburbs(this) }

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Everything heavy runs here, never on the service's main thread. Walking the
     * node tree, compressing a JPEG and taking OCR callbacks on main made Android
     * unbind this service as unresponsive - which showed up as minute-long holes
     * in the recording, one of them straight through a real offer.
     */
    private val pulse by lazy { PulseController(this) }
    private val worker = HandlerThread("uber-screen").apply { start() }
    private val work = Handler(worker.looper)

    private val poll = object : Runnable {
        override fun run() {
            look()
            work.postDelayed(this, POLL_MILLIS)
        }
    }

    private var lastText: String = ""
    private var lastWindowSignature: String = ""
    private var lastCaptureAtMillis: Long = 0L
    private var lastRungSignature: String = ""
    private var lastRungAtMillis: Long = 0L
    private var lastHeartbeatAtMillis: Long = 0L
    private var lastRoutineAtMillis: Long = 0L
    private var lastEventPostAtMillis: Long = 0L
    private var burstUntilMillis: Long = 0L
    private var lastSawUberAtMillis: Long = 0L

    /**
     * An offer wakes a sleeping phone, and on the lock screen the window list is
     * not ours to read - which is how a real offer left a 109 second hole in a
     * recording. So the screen lighting up is itself a reason to shoot: blind,
     * and regardless of which app the system says is in front.
     */
    private val screenWatcher = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                android.content.Intent.ACTION_SCREEN_ON,
                android.content.Intent.ACTION_USER_PRESENT -> {
                    burstUntilMillis = System.currentTimeMillis() + BURST_MILLIS
                    work.post {
                        ServiceJournal.note(this@UberScreenService, "屏幕亮起，密集截图 " + (BURST_MILLIS / 1000) + " 秒")
                        look(force = true)
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        Log.i(TAG, "accessibility service connected")
        ServiceJournal.note(this, "读屏已连接")
        live = this
        // The node tree is cached by default. Reading it every second returned a
        // stale snapshot: at the moment an offer card was on screen this service
        // still saw the map underneath, while a fresh uiautomator dump saw the card.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { setCacheEnabled(false) }
        }
        CaptureKeeperService.start(this)
        // A megabyte of road table takes a second to read, and reading it when
        // the first card appears puts that second in front of the first verdict
        // of the shift. Read it now instead, on the same thread the judging uses,
        // where nothing is waiting on it.
        work.post { RoadTable.warm(this) }
        work.removeCallbacks(poll)
        work.post(poll)
        runCatching {
            registerReceiver(
                screenWatcher,
                android.content.IntentFilter().apply {
                    addAction(android.content.Intent.ACTION_SCREEN_ON)
                    addAction(android.content.Intent.ACTION_USER_PRESENT)
                },
            )
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.w(TAG, "accessibility service unbound")
        ServiceJournal.note(this, "读屏被断开")
        work.removeCallbacks(poll)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenWatcher) }
        live = null
        worker.quitSafely()
        ServiceJournal.note(this, "读屏被销毁")
        work.removeCallbacks(poll)
        super.onDestroy()
    }

    /**
     * A window appearing is a reason to capture on its own. If the offer card
     * draws its text on a canvas, the accessibility tree never changes and a
     * text-only trigger would record nothing at the one moment that matters.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val fromUber = OfferParser.isUberPackage(event.packageName?.toString().orEmpty())
        val appeared = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (!fromUber && !appeared) return

        // This callback runs on the main thread and fires many times a second
        // across every app on the phone. Walking a node tree, sampling pixels and
        // taking a screenshot here is what made the app stop responding.
        val now = System.currentTimeMillis()
        if (now - lastEventPostAtMillis < EVENT_POST_GAP_MILLIS) return
        lastEventPostAtMillis = now
        work.post { look(force = fromUber && appeared) }
    }

    override fun onInterrupt() = Unit

    private fun look(force: Boolean = false) {
        val bursting = System.currentTimeMillis() < burstUntilMillis
        val all = runCatching { windows.orEmpty().mapNotNull { it.root } }.getOrDefault(emptyList())
        // During a burst take whatever windows are there - on the lock screen that
        // may be none, and a screenshot with no text still shows the offer.
        // The last dependency to remove: an offer that shows over the lock screen,
        // or over another app, may leave no Uber window we can enumerate. While the
        // screen is lit during a shift, shoot regardless of what the system reports.
        // Test mode reads whatever is on screen, so a screenshot of a card opened
        // in any app runs the whole pipeline. Waiting for a real offer to test
        // with means either taking it or refusing it, and both cost something.
        val testing = LiveSettings.current?.testModeEnabled == true
        val onShift = testing ||
            System.currentTimeMillis() - lastSawUberAtMillis < ON_SHIFT_MILLIS
        val screenLit = runCatching {
            getSystemService(android.os.PowerManager::class.java)?.isInteractive == true
        }.getOrDefault(false)
        val shootBlind = bursting || (screenLit && onShift)

        val roots = (if (testing) all else uberRoots()).ifEmpty { if (shootBlind) all else emptyList() }
        heartbeat(all.map { it.packageName?.toString() ?: "null" }, roots.size)

        // One beat per pass of the loop, while screenshots are on. With them off
        // the dot goes too: a dot still beating would say the app is watching.
        if (LiveSettings.current?.timedCaptureEnabled == false) pulse.hide() else pulse.beat(
            when {
                !Permissions.screenReadingGranted(this) -> PulseController.Mood.BROKEN
                all.any { OfferParser.isUberPackage(it.packageName?.toString().orEmpty()) } ->
                    PulseController.Mood.WATCHING
                else -> PulseController.Mood.IDLE
            }
        )
        if (roots.isEmpty() && !shootBlind) return

        // Off means off: no screenshot at all, Uber in front or not. The offer card
        // is only ever read off a screenshot, so with this off no card is judged.
        if (LiveSettings.current?.timedCaptureEnabled == false) return

        // A card that appears half a second after the last capture must not be
        // swallowed by the throttle that exists to stop a moving map spamming files.
        val now = System.currentTimeMillis()
        val gap = if (force || bursting) FORCED_GAP_MILLIS else MIN_GAP_MILLIS
        if (now - lastCaptureAtMillis < gap) return

        val lines = roots.flatMap { ScreenReader.readAll(it) }
        if (lines.isEmpty() && !shootBlind) return

        val text = lines.joinToString("\n")
        val windowSignature = roots.joinToString("+") { node ->
            (node.packageName?.toString() ?: "?") + ":" + node.childCount
        }
        // No change detection. An offer card drawn on a canvas changes neither the
        // accessibility text nor the window list, and gating the screenshot on a
        // change is how a real offer went unrecorded. While Uber is up, just shoot.
        val windowChanged = windowSignature != lastWindowSignature
        val textChanged = text != lastText
        val trigger = when {
            force -> "event"
            textChanged -> "text"
            windowChanged -> "window"
            else -> "cadence"
        }

        lastText = text
        lastWindowSignature = windowSignature
        lastCaptureAtMillis = now
        Log.i(TAG, "capture by $trigger, ${lines.size} lines")

        val onScreen = roots.firstOrNull()?.packageName?.toString() ?: "locked_or_unknown"
        val fix = position.lastKnown()

        val headerHead = buildString {
            append("package=").append(onScreen).append('\n')
            append("windows=").append(roots.size).append('\n')
            append("lines=").append(lines.size).append('\n')
            append("trigger=").append(trigger).append('\n')
            append("burst=").append(bursting).append('\n')
            append("test_mode=").append(testing).append('\n')
            append("blind=").append(roots.isEmpty()).append('\n')
            append("millis=").append(now).append('\n')
            if (fix != null) {
                append("lat=").append(fix.at.latitude).append('\n')
                append("lon=").append(fix.at.longitude).append('\n')
                append("fix_millis=").append(fix.measuredAtMillis).append('\n')
            } else {
                append("fix=none\n")
            }
        }

        capture { screen ->
            val treeHasCard = OfferCardReader.read(lines) != null
            val green = if (screen != null) AcceptBand.bandShare(sampleBand(screen)) else 0.0
            val button = AcceptBand.holdsButton(if (screen != null) sampleBand(screen) else IntArray(0))
            val routine = now - lastRoutineAtMillis >= ROUTINE_MILLIS

            BandLog.note(
                this,
                now,
                green,
                when {
                    treeHasCard -> "card"
                    button -> "ocr"
                    routine -> "routine"
                    else -> "skip"
                },
            )

            // Ninety-five per cent of what the screen shows is a map. Reading it
            // costs a fifth of a second each time and fills the phone with
            // pictures of nothing; the button's green says in microseconds
            // whether this frame is worth either.

            when {
                treeHasCard ->
                    finish(now, screen, headerHead, lines, text, emptyList(), -1, "card", green)
                screen != null && button ->
                    ScreenTextReader.read(screen) { ocrLines, millis ->
                        finish(now, screen, headerHead, lines, text, ocrLines, millis, "button", green)
                    }
                routine -> {
                    lastRoutineAtMillis = now
                    finish(now, screen, headerHead, lines, text, emptyList(), -1, "routine", green)
                }
                else -> runCatching { screen?.recycle() }
            }
        }
    }

    /** Decides once, on whichever source produced a card, and writes the record. */
    private fun finish(
        now: Long,
        screen: Bitmap?,
        headerHead: String,
        treeLines: List<String>,
        treeText: String,
        ocrLines: List<String>,
        ocrMillis: Long,
        why: String,
        greenFraction: Double,
    ) {
        val treeCard = OfferCardReader.read(treeLines)
        val source = if (treeCard != null) "a11y" else if (ocrLines.isNotEmpty()) "ocr" else "a11y"
        val lines = if (treeCard != null) treeLines else ocrLines.ifEmpty { treeLines }
        val text = lines.joinToString("\n")

        // The one screen that says an offer was accepted, read from the tree
        // rather than from `lines`: the tree returns this screen in full, and if
        // the bar at its foot happens to look like a button, `lines` is OCR's
        // version instead - the same words, wrapped and mangled.
        PickupScreen.read(treeLines)?.let { pickup ->
            JobStore.markTaken(this, pickup)
            Log.i(TAG, "pickup: " + pickup.store + " | " + pickup.address)
        }
        DropoffScreen.read(treeLines)?.let { dropoff ->
            JobStore.markDelivered(this, dropoff)
            Log.i(TAG, "dropoff: " + dropoff.address + " | unit=" + dropoff.unit)
        }
        // Whatever notes are on the board, in Chinese. Does nothing once they are
        // all done, and nothing at all until the model has been fetched.
        JobStore.translateNotes(this)

        val decision = decide(lines, text, now)
        DecisionLog.note(this, now, source, decision)

        val body = buildString {
            append(headerHead)
            append("source=").append(source).append('\n')
            append("ocr_ms=").append(ocrMillis).append('\n')
            append("ocr_lines=").append(ocrLines.size).append('\n')
            append("kept=").append(why).append('\n')
            if (source == "ocr" && OfferCardReader.read(ocrLines) != null) {
                append("windows_detail=").append(windowReport()).append('\n')
            }
            append("green=").append(String.format("%.3f", greenFraction)).append('\n')
            append(decision)
            append("screenshot=").append(screen != null).append('\n')
            append("---\n")
            append(text)
            if (source == "ocr" && treeText.isNotEmpty()) {
                append("\n=== tree (stale) ===\n").append(treeText)
            }
        }
        store.write(now, screen, body)
        // 1080x2400 in ARGB_8888 is ten megabytes; at one every two seconds the
        // collector cannot keep up unless each one is released here.
        runCatching { screen?.recycle() }
    }

    /**
     * How far the drop is from where the live set is worked from.
     *
     * Only as good as the card's own words allow, and [ChipText.fromCentre] says
     * which of the three that was. Nothing here decides anything - it is a number
     * for the driver to read while the timer runs.
     */
    private fun fromCentre(card: OfferCard, gazetteer: List<Suburb>): String? {
        val centre = Profiles.centre(this) ?: return null
        val suburb = SuburbIndex.findAll(card.dropoff, gazetteer).firstOrNull() ?: return null
        val began = System.currentTimeMillis()
        val spot = RoadIndex.find(
            dropoff = card.dropoff,
            suburb = suburb,
            crossings = RoadTable.crossings(this),
            roads = RoadTable.roads(this),
        )
        // This sits in front of the verdict, so what it costs is what the driver
        // waits. Logged every time rather than measured once: the cost depends on
        // the card, and a card that names two long roads is the slow one.
        Log.i(TAG, "roads: placed in " + (System.currentTimeMillis() - began) + " ms, " +
            spot::class.simpleName)
        return ChipText.fromCentre(spot, centre)
    }

    private fun decide(lines: List<String>, text: String, now: Long): String {
        // The card is read by layout, which is far stronger evidence than the
        // money-and-distance heuristic. The heuristic stays as the fallback for
        // a card whose text the accessibility tree does not expose.
        val card = OfferCardReader.read(lines)
        val offerShape = card != null || OfferShape.looksLikeOffer(text)

        // "Thinking" belongs to a screen that really looks like an offer - money
        // and a distance - not merely to a band of colour along the bottom. A
        // solid tab bar in any other app satisfied that, and the chip then sat
        // there for as long as the app was open, refreshed every two seconds.
        when {
            card != null -> Unit
            offerShape -> overlay.show(OverlayController.State.Thinking)
            // A verdict already on screen is left to its deadline. The chip
            // covers part of the card it was read from, so the next frame
            // often cannot read that card - hiding on that made it blink once
            // a second for as long as the card was up.
            overlay.showingVerdict() -> Unit
            else -> overlay.hide()
        }

        // The driver's own rules, and nothing else. The payout floors that used
        // to live here were invented by this app and are gone; the numbers are
        // still shown on the chip, they just do not decide anything.
        val rules = RulesStore.current(this)
        val ruling = card?.let { RuleJudge.judge(it, rules, gazetteer) }

        val searchIn = card?.dropoff ?: text
        val found = SuburbIndex.findAll(searchIn, gazetteer)

        val chime = when {
            !offerShape -> "none_not_offer_shape"
            LiveSettings.current?.areaSoundEnabled == false -> "suppressed_setting_off"
            ruling == null -> "none_no_card"
            else -> {
                val signature = RulingText.headline(ruling, card.isMatch) + RulingText.reason(ruling)
                if (signature == lastRungSignature && now - lastRungAtMillis < SAME_CALL_MILLIS) {
                    "suppressed_same_within_3s"
                } else {
                    lastRungSignature = signature
                    lastRungAtMillis = now
                    this@UberScreenService.chime.play(
                        when (ruling) {
                            is Ruling.Take -> AreaCall.AllInside(found)
                            is Ruling.Leave -> AreaCall.SomeOutside(found, emptyList())
                            else -> AreaCall.NoSuburb
                        }
                    )
                    "played_" + ruling::class.simpleName
                }
            }
        }

        if (card != null && ruling != null) {
            overlay.show(
                OverlayController.State.Decided(
                    ruling = ruling,
                    card = card,
                    fromCentre = fromCentre(card, gazetteer),
                )
            )
            // The verdict is the news while it is up; the heartbeat can wait.
            pulse.hide()
        }

        // Onto the board, so the two stops are still there after the card goes.
        // Repeats of the same card are dropped there, not here.
        if (card != null) {
            JobStore.add(
                context = this,
                atMillis = now,
                offer = com.weixu.ueatsmonitor.domain.OfferRecord(
                    isMatch = card.isMatch,
                    payout = card.payout.toString(),
                    pickup = card.pickup,
                    dropoff = card.dropoff,
                    ruling = ruling?.let { RulingText.headline(it, card.isMatch) },
                    why = ruling?.let(RulingText::reason),
                ),
            )
        }

        Log.i(TAG, "decide card=" + (card != null) + " offer=" + offerShape +
            " ruling=" + (ruling?.let { RulingText.headline(it, card.isMatch) } ?: "-") + " chime=" + chime)

        return buildString {
            append("card=").append(card != null).append('\n')
            if (card != null) {
                append("card_payout=").append(card.payout).append('\n')
                append("card_minutes=").append(card.duration?.value ?: -1).append('\n')
                append("card_miles=").append(card.distance?.let { String.format("%.2f", it.value) } ?: "?").append('\n')
                append("card_pickup=").append(card.pickup).append('\n')
                append("card_dropoff=").append(card.dropoff).append('\n')
                append("metrics=").append(
                    VerdictText.metricsLine(OfferEvaluator.metricsOf(OfferCardReader.toOffer(card)))
                ).append('\n')
            }
            if (ruling != null) {
                append("card_kind=").append(if (card.isMatch) "match" else "accept").append('\n')
                append("ruling=").append(RulingText.headline(ruling, card.isMatch)).append('\n')
                append("ruling_why=").append(RulingText.reason(ruling)).append('\n')
            }
            append("rules_suburbs=").append(rules.allowedSuburbs.size).append('\n')
            append("rules_denied_stores=").append(rules.deniedStores.size).append('\n')
            append("money=").append(CaptureText.hasMoney(text)).append('\n')
            append("offer_shape=").append(offerShape).append('\n')
            append("suburbs=").append(found.joinToString(",") { it.name }).append('\n')
            append("chime=").append(chime).append('\n')
        }
    }

    /** The rows of the Accept button, sampled across their width. */
    private fun sampleBand(screen: Bitmap): IntArray {
        val samples = ArrayList<Int>(AcceptBand.ROWS.size * BAND_SAMPLES)
        runCatching {
            for (fraction in AcceptBand.ROWS) {
                val y = (screen.height * fraction).toInt().coerceIn(0, screen.height - 1)
                val step = (screen.width / BAND_SAMPLES).coerceAtLeast(1)
                var x = step / 2
                while (x < screen.width) {
                    samples += screen.getPixel(x, y)
                    x += step
                }
            }
        }
        return samples.toIntArray()
    }

    /**
     * Every window the system reports, including the ones whose content we are
     * not given. An offer card was on screen while the tree described the map
     * underneath, and the window it lives in never appeared in our list - so the
     * windows that get dropped are exactly what has to be logged.
     */
    private fun windowReport(): String = runCatching {
        windows.orEmpty().joinToString(" | ") { window ->
            val root = window.root
            buildString {
                append("type=").append(window.type)
                append(" layer=").append(window.layer)
                append(" active=").append(window.isActive)
                append(" focused=").append(window.isFocused)
                append(" pkg=").append(root?.packageName ?: window.title ?: "?")
                append(" root=").append(root != null)
                append(" children=").append(root?.childCount ?: -1)
            }
        }.ifEmpty { "(none)" }
    }.getOrElse { "error: " + it.message }

    /** Says once every few seconds what the service can actually see. */
    private fun heartbeat(packages: List<String>, uberCount: Int) {
        val now = System.currentTimeMillis()
        if (now - lastHeartbeatAtMillis < HEARTBEAT_MILLIS) return
        lastHeartbeatAtMillis = now
        val active = rootInActiveWindow?.packageName?.toString() ?: "null"
        Log.i(TAG, "poll: windows=$packages active=$active uber=$uberCount")
        Log.i(TAG, "windows: " + windowReport())
    }

    /** Every Uber window currently up - an offer card can sit in its own. */
    private fun uberRoots(): List<AccessibilityNodeInfo> {
        val fromWindows = runCatching {
            windows.orEmpty().mapNotNull { it.root }
        }.getOrDefault(emptyList())
        // Always consider the active window too: a card can be a window the list
        // has not caught up with yet.
        val candidates = (fromWindows + listOfNotNull(rootInActiveWindow)).distinct()
        candidates.forEach { runCatching { it.refresh() } }
        val uber = candidates.filter { node ->
            OfferParser.isUberPackage(node.packageName?.toString().orEmpty())
        }
        if (uber.isNotEmpty()) lastSawUberAtMillis = System.currentTimeMillis()
        return uber
    }

    /** Hands a screenshot to [onReady], or null when the platform refuses one. */
    internal fun capture(onReady: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onReady(null)
            return
        }
        runCatching {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                executor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val bitmap = toBitmap(result.hardwareBuffer, result.colorSpace)
                        result.hardwareBuffer.close()
                        onReady(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "screenshot failed, code $errorCode")
                        onReady(null)
                    }
                },
            )
        }.onFailure {
            Log.w(TAG, "screenshot threw: " + it.message)
            onReady(null)
        }
    }

    private fun toBitmap(buffer: HardwareBuffer, colorSpace: ColorSpace): Bitmap? {
        val wrapped = Bitmap.wrapHardwareBuffer(buffer, colorSpace) ?: return null
        // Copy off the hardware buffer so the bitmap survives close() and can be compressed.
        return wrapped.copy(Bitmap.Config.ARGB_8888, false)
    }

    companion object {
        /** Set while the service is bound, so the keeper's clock can drive it. */
        @Volatile
        private var live: UberScreenService? = null

        /** Called once a second by [CaptureKeeperService], off the main thread. */
        fun pokeFromKeeper() {
            val service = live ?: return
            service.work.post { service.look() }
        }

        /**
         * Everything down: the overlays go, the keeper goes, and the service
         * switches itself off. Turning it back on means Settings -> Accessibility,
         * because nothing in an app can grant itself that permission again.
         */
        fun stopEverything(context: Context) {
            val service = live
            if (service == null) {
                CaptureKeeperService.stop(context)
                return
            }
            service.overlay.hide()
            service.pulse.hide()
            ServiceJournal.note(service, "读屏已退出")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { service.disableSelf() }
            }
            // Last, so nothing restarts it on the way out.
            CaptureKeeperService.stop(context)
        }

        /** Whether the reader is connected right now. */
        fun isRunning(): Boolean = live != null

        const val TAG = "UEatsMonitor"
        const val POLL_MILLIS = 1_000L
        /**
         * Measured on this phone over thirty seconds with Uber in front, counting
         * frames actually judged against screenshots the platform refused:
         *
         *   1.0 s -> 20 frames, 21 refusals, 30.1 C
         *   1.2 s -> 23 frames,  3 refusals, 30.7 C
         *   1.5 s -> 17 frames,  3 refusals
         *   2.0 s -> the setting below
         *
         * takeScreenshot is rate limited (error code 3), so asking faster than the
         * platform allows costs work and heat without yielding frames. 1.2 s was
         * the best of the four; two seconds is the conservative setting in use.
         */
        const val MIN_GAP_MILLIS = 2_000L
        const val FORCED_GAP_MILLIS = 400L
        /**
         * How long the same card stays quiet between rings. Short on purpose: the
         * driver's own alert can bury one beep, and a card sits on screen for half
         * a minute, so it should sound about ten times, not once.
         */
        const val SAME_CALL_MILLIS = 3_000L
        const val HEARTBEAT_MILLIS = 5_000L

        /**
         * A frame kept even with no button detected, so a shift is never blind.
         * Ten seconds, not thirty: an offer card is only on screen for a few tens
         * of seconds, and this is the net that has to catch one if the button
         * check ever fails. It costs disk, not CPU - no OCR runs on these.
         */
        const val ROUTINE_MILLIS = 10_000L
        const val BAND_SAMPLES = 60

        /** Events arrive in floods; the poll is the real clock. */
        const val EVENT_POST_GAP_MILLIS = 300L

        /** How long to keep shooting after the screen lights up. */
        const val BURST_MILLIS = 60_000L

        /** How long after seeing Uber we still treat the driver as on shift. */
        const val ON_SHIFT_MILLIS = 15L * 60 * 1000
    }
}

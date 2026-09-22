package com.weixu.ueatsmonitor.action

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.media.AudioManager
import android.media.ToneGenerator
import java.util.Locale
import android.util.Log
import android.widget.Toast
import com.weixu.ueatsmonitor.domain.VoiceCommand
import kotlinx.coroutines.launch
import com.weixu.ueatsmonitor.domain.VoiceTarget
import com.weixu.ueatsmonitor.domain.VoiceCommands
import com.weixu.ueatsmonitor.domain.SpokenLanguage

/**
 * Action. Keeps the microphone open for the length of a shift and acts on what
 * the driver says.
 *
 * Android has no continuous recognizer: one session hears one sentence and ends,
 * on a result or on silence. So a session is started again the moment the last
 * one ends, for as long as the switch in settings is on.
 *
 * Recognition is the slow part - the recognizer waits for a pause, then asks
 * the network - so a command is acted on from the first partial guess that
 * reads as one, and said back aloud straight away. The driver hears that he was
 * understood and does not have to say it again while the app switches.
 */
class VoiceService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    /**
     * The on-device recognizer first. On this phone the offline Mandarin pack is
     * downloaded into the on-device service, while the default recognizer is a
     * different app that never sees it and keeps going to the network. Cleared
     * for good the first time the on-device one says it has no such language.
     */
    private var offline = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    private var running = false

    /**
     * How many sessions in a row the recognizer refused as busy. The on-device
     * service can fill up with sessions nobody released - 396 of them on 18 Sept -
     * and then refuses every start for good, silently, until it is restarted.
     */
    private var busyInARow = 0

    /** Set once this session has acted, so the final result does not act again. */
    private var actedThisSession = false

    private var speech: TextToSpeech? = null
    private var speechReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        live = this
        askForOfflineChinese()
        speech = TextToSpeech(this) { status ->
            val tts = speech ?: return@TextToSpeech
            // Ready means the engine started; each confirmation sets its own language
            // and falls back to a tone when the phone has no voice for it.
            speechReady = status == TextToSpeech.SUCCESS
            if (!speechReady) Log.w(TAG, "voice: no speech engine, confirming with a tone")
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { main.post { again(NEXT_MILLIS) } }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { main.post { again(NEXT_MILLIS) } }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android refuses the microphone to a service it restarts on its own, in
        // the background. That refusal was thrown, and it took the whole process
        // down with it - the screen reader included. Give up quietly instead;
        // opening the app starts the voice again.
        if (runCatching { goForeground() }.isFailure) {
            Log.w(TAG, "voice: not allowed to start from the background")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            listen()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        live = null
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        speech?.shutdown()
        speech = null
        super.onDestroy()
    }

    private fun listen() {
        if (!running) return
        actedThisSession = false
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "voice: no recognizer on this phone")
            toast("手机上没有语音识别服务，语音命令用不了")
            stopSelf()
            return
        }
        val current = recognizer ?: create().also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val ask = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        // Mandarin first, English when he speaks it: the on-device recognizer
        // switches between the two mid-session from Android 14.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ask.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_QUICK_RESPONSE)
            ask.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, ArrayList(LANGUAGES))
        }
        // Leans the recognizer towards the few sentences that mean something here.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ask.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(VoiceCommands.PHRASES))
        }
        runCatching { current.startListening(ask) }
            .onFailure { Log.w(TAG, "voice: start failed", it); again(ERROR_BACKOFF_MILLIS) }
    }

    /**
     * Asks the phone to download its on-device Mandarin model, so recognition
     * stops needing the network and stops sending the car's audio to Google.
     * The phone does nothing if the model is already there.
     */
    private fun askForOfflineChinese() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            Log.w(TAG, "voice: no on-device recognizer on this phone")
            return
        }
        // Only for a language with no pack at all. Asking for one that is merely
        // out of date puts a system "download update" dialog over whatever is on
        // screen, every time the service starts.
        runCatching {
            val onDevice = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            onDevice.checkRecognitionSupport(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
                mainExecutor,
                object : android.speech.RecognitionSupportCallback {
                    override fun onSupportResult(support: android.speech.RecognitionSupport) {
                        val installed = support.installedOnDeviceLanguages.map { it.lowercase() }
                        LANGUAGES.filter { it.lowercase() !in installed }.forEach { language ->
                            onDevice.triggerModelDownload(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language),
                            )
                            Log.i(TAG, "voice: asked for the offline $language model")
                        }
                        main.postDelayed({ onDevice.destroy() }, 5_000L)
                    }

                    override fun onError(error: Int) {
                        Log.w(TAG, "voice: could not check offline languages, error $error")
                        onDevice.destroy()
                    }
                },
            )
        }.onFailure { Log.w(TAG, "voice: offline model request failed", it) }
    }

    private fun create(): SpeechRecognizer {
        if (offline && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            Log.i(TAG, "voice: listening on-device")
            return SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        }
        offline = false
        Log.i(TAG, "voice: listening through the default recognizer")
        return SpeechRecognizer.createSpeechRecognizer(this)
    }

    /** Starts the next session after a pause, so a failing recognizer does not spin. */
    private fun again(afterMillis: Long) {
        main.removeCallbacksAndMessages(null)
        main.postDelayed({ listen() }, afterMillis)
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            if (actedThisSession) return
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            Log.i(TAG, "voice heard: $heard")
            // The sentence after a bare "你好" is the question, whatever it says.
            val command = VoiceCommands.parse(heard)
            val bareWake = command is VoiceCommand.Ask && command.question.isEmpty()
            if (System.currentTimeMillis() < questionUntilMillis && heard.isNotEmpty() && !bareWake) {
                questionUntilMillis = 0L
                understood(VoiceCommand.Ask(heard.first(), SpokenLanguage.CHINESE))
                return
            }
            if (command != null) understood(command) else again(NEXT_MILLIS)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (actedThisSession) return
            val heard = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val command = VoiceCommands.parsePartial(heard) ?: return
            Log.i(TAG, "voice heard (partial): $heard")
            understood(command)
        }

        override fun onError(error: Int) {
            if (actedThisSession) return
            // No match and silence are the ordinary end of a quiet stretch; anything
            // else - busy, network, a missing language - is worth a longer wait.
            val quiet = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (!quiet) Log.w(TAG, "voice: recognizer error $error")
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                toast("没有麦克风权限，语音命令已停止")
                stopSelf()
                return
            }
            // No Mandarin on the device after all: fall back to the network for good.
            if (offline && (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
            ) {
                Log.w(TAG, "voice: on-device has no $LANGUAGE (error $error), using the network")
                offline = false
                recognizer?.destroy()
                recognizer = null
                again(NEXT_MILLIS)
                return
            }
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && ++busyInARow >= BUSY_LIMIT) {
                busyInARow = 0
                recognizer?.destroy()
                recognizer = null
                if (offline) {
                    // The default recognizer is a different app with its own capacity.
                    Log.w(TAG, "voice: on-device recognizer stuck busy, using the network")
                    offline = false
                    announce("语音换成备用了")
                    again(ERROR_BACKOFF_MILLIS)
                } else {
                    Log.w(TAG, "voice: every recognizer is busy, giving up")
                    announce("语音命令用不了了")
                    running = false
                    main.postDelayed({ stopSelf() }, ERROR_BACKOFF_MILLIS)
                }
                return
            }
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                recognizer?.destroy()
                recognizer = null
            }
            again(if (quiet) NEXT_MILLIS else ERROR_BACKOFF_MILLIS)
        }

        override fun onReadyForSpeech(params: Bundle?) {
            busyInARow = 0
        }
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /**
     * Stops listening, says the command back, and acts. Listening starts again
     * only once the confirmation has been spoken, so the microphone does not
     * hear "好，地图" and take it for the driver.
     */
    private fun understood(command: VoiceCommand) {
        actedThisSession = true
        main.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
        if (command is VoiceCommand.Ask && command.question.isEmpty()) {
            // "你好" and a pause: the question is coming. It answers "你好" back -
            // a clip rendered once and shipped in the app, not synthesised each
            // time - and the next sentence heard within a few seconds is taken as it.
            greet()
            questionUntilMillis = System.currentTimeMillis() + QUESTION_WINDOW_MILLIS
            again(NEXT_MILLIS)
            return
        }
        if (command is VoiceCommand.Ask) {
            // Nothing first: the answer is the confirmation. Listening stays off
            // while the answer is fetched and spoken, and comes back when the
            // speech ends - the same way as after "好".
            asking.launch {
                val words = when (val reply = Assistant.ask(this@VoiceService, command.question)) {
                    is Assistant.Reply.Answer -> reply.words
                    is Assistant.Reply.Failed -> "问不了：" + reply.why
                }
                main.post { announce(words) }
            }
            return
        }
        confirm(command)
        act(command)
    }

    /** Until when the next sentence heard is the question that followed a bare "你好". */
    private var questionUntilMillis: Long = 0L

    private val asking = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    /** The bundled "你好", played and released; a tone if the player will not start. */
    private fun greet() {
        val player = android.media.MediaPlayer.create(this, com.weixu.ueatsmonitor.R.raw.nihao)
        if (player == null) {
            tone(ToneGenerator.TONE_PROP_BEEP)
            return
        }
        player.setOnCompletionListener { it.release() }
        player.start()
    }

    private fun tone(kind: Int) {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tone.startTone(kind, 150)
            main.postDelayed({ tone.release() }, TONE_MILLIS)
        }
    }

    private fun confirm(command: VoiceCommand) {
        val english = command.language == SpokenLanguage.ENGLISH
        val words = when (command) {
            is VoiceCommand.SwitchTo -> if (english) command.target.confirmEnglish else command.target.confirmChinese
            is VoiceCommand.DriveToCentre -> if (english) "OK, centre" else "好，回中心"
            is VoiceCommand.StopNavigation -> if (english) "OK, stopping" else "好，关导航"
            is VoiceCommand.Ask -> ""
        }
        val tts = speech
        val voiced = speechReady && tts != null &&
            tts.setLanguage(if (english) ENGLISH else Locale.SIMPLIFIED_CHINESE) >= TextToSpeech.LANG_AVAILABLE
        if (voiced && tts != null) {
            val params = Bundle().apply { putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC) }
            val queued = tts.speak(words, TextToSpeech.QUEUE_FLUSH, params, "confirm-" + System.currentTimeMillis())
            if (queued == TextToSpeech.SUCCESS) return
        }
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 200)
            main.postDelayed({ tone.release() }, TONE_MILLIS)
        }
        main.postDelayed({ listen() }, TONE_MILLIS)
    }

    private fun act(command: VoiceCommand) {
        Log.i(TAG, "voice command: $command")
        when (command) {
            is VoiceCommand.SwitchTo -> {
                if (bringForward(command.target)) toast("切到" + command.target.spoken)
            }
            is VoiceCommand.DriveToCentre -> {
                val centre = Profiles.centre(this)
                if (centre == null) {
                    toast("这套选区没设中心，在电脑上设一个")
                    return
                }
                Navigation.driveTo(this, centre)
                toast("导航回中心")
            }
            is VoiceCommand.Ask -> Unit // answered in understood()
            is VoiceCommand.StopNavigation ->
                MapsNavigation.stop(this) { pressed -> toast(if (pressed) "已关导航" else "地图没在导航") }
        }
    }

    /** Puts [target] on screen. False when it is not installed. */
    private fun bringForward(target: VoiceTarget): Boolean {
        val launch = packageManager.getLaunchIntentForPackage(target.packageName)
        if (launch == null) {
            toast("没找到" + target.spoken)
            return false
        }
        runCatching {
            startActivity(
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }.onFailure { Log.w(TAG, "voice: switch failed", it) }
        return true
    }

    /** Said aloud, because the driver is not looking at the phone. A tone when there is no voice. */
    private fun announce(words: String) {
        Log.i(TAG, "voice: announcing $words")
        val tts = speech
        val voiced = speechReady && tts != null &&
            tts.setLanguage(Locale.SIMPLIFIED_CHINESE) >= TextToSpeech.LANG_AVAILABLE
        if (voiced && tts != null) {
            val params = Bundle().apply { putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC) }
            tts.speak(words, TextToSpeech.QUEUE_FLUSH, params, "announce-" + System.currentTimeMillis())
            return
        }
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tone.startTone(ToneGenerator.TONE_PROP_NACK, 400)
            main.postDelayed({ tone.release() }, TONE_MILLIS)
        }
    }

    private fun toast(text: String) = main.post {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun goForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "语音命令", NotificationManager.IMPORTANCE_MIN)
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("语音命令在听")
            .setContentText("说「地图」「送餐」「应用」或 switch to map / uber eats / application")
            .setOngoing(true)
            .build()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "UEatsMonitor"
        private const val CHANNEL = "voice"
        private const val NOTIFICATION_ID = 44
        private const val NEXT_MILLIS = 150L
        private const val ERROR_BACKOFF_MILLIS = 3_000L

        /** How long after "你好" the driver has to ask the question. */
        private const val QUESTION_WINDOW_MILLIS = 8_000L


        /** Five refusals, about fifteen seconds: past a moment's contention, into stuck. */
        private const val BUSY_LIMIT = 5
        private const val TONE_MILLIS = 500L

        /**
         * Mandarin, simplified, as the phone's offline packs name it. "zh-CN" is
         * understood online but matches no offline pack.
         */
        private const val LANGUAGE = "cmn-Hans-CN"

        /** Every language a command may be said in, the one listened for first at its head. */
        private val LANGUAGES = listOf(LANGUAGE, "en-AU")

        private val ENGLISH: Locale = Locale("en", "AU")

        fun start(context: Context) {
            context.startForegroundService(Intent(context, VoiceService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceService::class.java))
        }

        fun isRunning(): Boolean = live != null

        /** Probe: switch the running service to the default (network) recognizer, to compare its sounds. */
        fun useDefaultRecognizer() {
            val service = live ?: return
            service.main.post {
                service.offline = false
                service.recognizer?.destroy()
                service.recognizer = null
                service.again(NEXT_MILLIS)
            }
        }

        /**
         * Flips voice from the floating button, off the app's own screens. Turning
         * it on starts the microphone service; Android may refuse that from the
         * background, in which case the service stops itself quietly and
         * [changed] finds it still off.
         */
        fun toggle(context: Context, changed: () -> Unit) {
            val store = com.weixu.ueatsmonitor.App.instance.settingsStore
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
            if (isRunning()) {
                stop(context)
                scope.launch { store.setVoiceEnabled(false) }
            } else {
                scope.launch { store.setVoiceEnabled(true) }
                runCatching { start(context) }.onFailure { Log.w(TAG, "voice: start from the button refused", it) }
            }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(changed, 400L)
        }

        /** Speaks [words] through the running voice service; a toast when it is not running. */
        fun say(context: Context, words: String) {
            val service = live
            if (service == null) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, words, Toast.LENGTH_LONG).show()
                }
                return
            }
            service.main.post { service.announce(words) }
        }

        @Volatile
        private var live: VoiceService? = null
    }
}

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
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import org.json.JSONObject
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
 * Recognition is Vosk, running on the phone with a Mandarin model shipped in
 * the app. It listens without pause and makes no sound of its own: Google's
 * recognizers rang a tone at the end of every sentence, and could be left
 * refusing every start by sessions nobody released. Nothing said in the car
 * leaves the phone until a question is sent to the assistant.
 *
 * A command is acted on from the first partial guess that reads as one, and
 * said back aloud straight away. Listening pauses while the phone speaks, so
 * the microphone does not hear "好，地图" and take it for the driver.
 */
class VoiceService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var ears: SpeechService? = null
    private var running = false

    /** Set once a sentence has been acted on, so its final result does not act again. */
    private var actedThisSentence = false

    private var speech: TextToSpeech? = null
    private var speechReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        live = this
        speech = TextToSpeech(this) { status ->
            val tts = speech ?: return@TextToSpeech
            // Ready means the engine started; each confirmation sets its own language
            // and falls back to a tone when the phone has no voice for it.
            speechReady = status == TextToSpeech.SUCCESS
            if (!speechReady) Log.w(TAG, "voice: no speech engine, confirming with a tone")
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { main.post { resume() } }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { main.post { resume() } }
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
        ears?.stop()
        ears?.shutdown()
        ears = null
        model?.close()
        model = null
        speech?.shutdown()
        speech = null
        super.onDestroy()
    }

    /**
     * Unpacks the model from the app's assets on first run (a few seconds,
     * once), then opens the microphone and keeps it open.
     */
    private fun listen() {
        if (!running) return
        val ready = model
        if (ready != null) {
            open(ready)
            return
        }
        StorageService.unpack(
            this, MODEL_ASSET, MODEL_DIR,
            { unpacked ->
                model = unpacked
                if (running) open(unpacked)
            },
            { error ->
                Log.e(TAG, "voice: model failed to load", error)
                toast("语音模型加载失败，语音命令用不了")
                stopSelf()
            },
        )
    }

    private fun open(model: Model) {
        if (ears != null) return
        runCatching {
            val recognizer = Recognizer(model, SAMPLE_RATE)
            SpeechService(recognizer, SAMPLE_RATE).also {
                it.startListening(listener)
                ears = it
                Log.i(TAG, "voice: listening (vosk)")
            }
        }.onFailure {
            Log.e(TAG, "voice: could not open the microphone", it)
            toast("打不开麦克风，语音命令用不了")
            stopSelf()
        }
    }

    /** Listening carries on after the phone has spoken. */
    private fun resume() {
        actedThisSentence = false
        ears?.setPause(false)
    }

    /** Vosk writes Mandarin one character at a time: "你 好 地 图". */
    private fun heardIn(hypothesis: String?): List<String> {
        val text = runCatching { JSONObject(hypothesis ?: return emptyList()).optString("text") }
            .getOrDefault("").orEmpty().replace(" ", "").trim()
        return if (text.isEmpty()) emptyList() else listOf(text)
    }

    private val listener = object : RecognitionListener {
        override fun onResult(hypothesis: String?) = sentence(heardIn(hypothesis))
        override fun onFinalResult(hypothesis: String?) = sentence(heardIn(hypothesis))

        override fun onPartialResult(hypothesis: String?) {
            if (actedThisSentence) return
            val heard = runCatching { JSONObject(hypothesis ?: return).optString("partial") }
                .getOrDefault("").orEmpty().replace(" ", "").trim()
            if (heard.isEmpty()) return
            val command = VoiceCommands.parsePartial(listOf(heard)) ?: return
            Log.i(TAG, "voice heard (partial): $heard")
            understood(command)
        }

        override fun onError(exception: Exception?) {
            Log.w(TAG, "voice: recognizer error", exception)
        }

        override fun onTimeout() = Unit
    }

    /** A whole sentence, at the pause after it. */
    private fun sentence(heard: List<String>) {
        if (heard.isEmpty()) return
        Log.i(TAG, "voice heard: $heard")
        if (actedThisSentence) {
            actedThisSentence = false
            return
        }
        // The sentence after a bare "你好" is the question, whatever it says.
        val command = VoiceCommands.parse(heard)
        val bareWake = command is VoiceCommand.Ask && command.question.isEmpty()
        if (System.currentTimeMillis() < questionUntilMillis && !bareWake) {
            questionUntilMillis = 0L
            understood(VoiceCommand.Ask(heard.first(), SpokenLanguage.CHINESE))
            return
        }
        if (command != null) understood(command)
    }

    /**
     * Stops listening, says the command back, and acts. Listening starts again
     * only once the confirmation has been spoken, so the microphone does not
     * hear "好，地图" and take it for the driver.
     */
    private fun understood(command: VoiceCommand) {
        actedThisSentence = true
        main.removeCallbacksAndMessages(null)
        ears?.setPause(true)
        if (command is VoiceCommand.Ask && command.question.isEmpty()) {
            // "你好" and a pause: the question is coming. It answers "你好" back -
            // a clip rendered once and shipped in the app, not synthesised each
            // time - and the next sentence heard within a few seconds is taken as it.
            greet()
            questionUntilMillis = System.currentTimeMillis() + QUESTION_WINDOW_MILLIS
            main.postDelayed({ resume() }, GREET_MILLIS)
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
        main.postDelayed({ resume() }, TONE_MILLIS)
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
        main.postDelayed({ resume() }, TONE_MILLIS)
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
        /** How long after "你好" the driver has to ask the question. */
        private const val QUESTION_WINDOW_MILLIS = 8_000L

        /** The bundled greeting is half a second; listen again once it is over. */
        private const val GREET_MILLIS = 700L
        private const val TONE_MILLIS = 500L

        /** The Mandarin model in assets, and the folder it is unpacked to under files/. */
        private const val MODEL_ASSET = "model-cn"
        private const val MODEL_DIR = "model-cn"
        private const val SAMPLE_RATE = 16000.0f

        private val ENGLISH: Locale = Locale("en", "AU")

        fun start(context: Context) {
            context.startForegroundService(Intent(context, VoiceService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceService::class.java))
        }

        fun isRunning(): Boolean = live != null

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

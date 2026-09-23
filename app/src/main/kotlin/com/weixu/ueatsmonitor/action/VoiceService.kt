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
import org.vosk.android.StorageService
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.media.AudioManager
import android.media.ToneGenerator
import java.util.Locale
import android.util.Log
import android.widget.Toast
import com.weixu.ueatsmonitor.domain.Lang
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
    private var ears: Ears? = null
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
                toast(driverWords().voiceModelFailed)
                stopSelf()
            },
        )
    }

    private fun open(model: Model) {
        if (ears != null) return
        ears = Ears(model, VoiceCommands.GRAMMAR, onPartial = ::partial, onSentence = { sentence(listOf(it)) }).also { it.start() }
        Log.i(TAG, "voice: listening (vosk)")
    }

    /** Listening carries on after the phone has spoken. */
    private fun resume() {
        actedThisSentence = false
        ears?.pause(false)
        afterSpeaking?.let { afterSpeaking = null; it() }
    }

    private fun partial(heard: String) {
        if (actedThisSentence) return
        val command = VoiceCommands.parsePartial(listOf(heard)) ?: return
        Log.i(TAG, "voice heard (partial): $heard")
        understood(command)
    }

    /** A whole sentence, at the pause after it. */
    private fun sentence(heard: List<String>) {
        if (heard.isEmpty()) return
        Log.i(TAG, "voice heard: $heard")
        if (actedThisSentence) {
            actedThisSentence = false
            return
        }
        // Inside a conversation every sentence is a question - unless it is the
        // driver saying he is done, or "你好" again.
        val command = VoiceCommands.parse(heard)
        val bareWake = command is VoiceCommand.Ask && command.question.isEmpty()
        if (inConversation() && !bareWake) {
            // A command said mid-conversation is still a command; it also ends
            // the conversation, since the driver has moved on.
            if (command != null && command !is VoiceCommand.Ask) {
                endConversation()
                understood(command)
                return
            }
            if (VoiceCommands.isGoodbye(heard.first())) {
                endConversation()
                ears?.pause(true)
                ears?.forget()
                confirmWords("好")
                return
            }
            understood(VoiceCommand.Ask(heard.first(), SpokenLanguage.CHINESE))
            return
        }
        if (command != null) understood(command)
    }

    /**
     * A conversation: opened by "你好", kept open for a few seconds after each
     * answer so the next question needs no "你好", closed by silence or by
     * the driver saying he is done. While it is open the recogniser hears the
     * whole language; outside it, only the command phrases.
     */
    private fun inConversation(): Boolean = System.currentTimeMillis() < conversationUntilMillis

    private fun keepConversation(millis: Long) {
        conversationUntilMillis = System.currentTimeMillis() + millis
        ears?.listenTo(null)
        main.removeCallbacks(closeConversation)
        main.postDelayed(closeConversation, millis)
    }

    private fun endConversation() {
        conversationUntilMillis = 0L
        main.removeCallbacks(closeConversation)
        ears?.listenTo(VoiceCommands.GRAMMAR)
    }

    private val closeConversation = Runnable {
        if (inConversation()) return@Runnable
        Log.i(TAG, "voice: conversation closed")
        endConversation()
    }

    /**
     * Stops listening, says the command back, and acts. Listening starts again
     * only once the confirmation has been spoken, so the microphone does not
     * hear "好，地图" and take it for the driver.
     */
    private fun understood(command: VoiceCommand) {
        actedThisSentence = true
        main.removeCallbacksAndMessages(null)
        ears?.pause(true)
        ears?.forget()
        if (command is VoiceCommand.Ask && command.question.isEmpty()) {
            // "你好" and a pause: the question is coming. It answers "你好" back -
            // a clip rendered once and shipped in the app, not synthesised each
            // time - and the next sentence heard within a few seconds is taken as it.
            greet()
            keepConversation(QUESTION_WINDOW_MILLIS)
            main.postDelayed({ resume() }, GREET_MILLIS)
            return
        }
        if (command is VoiceCommand.Ask) {
            // Nothing first: the answer is the confirmation. Listening stays off
            // while the answer is fetched and spoken, and comes back when the
            // speech ends. The conversation then stays open a few seconds more
            // for a follow-up, and the clock is stopped while the phone speaks.
            main.removeCallbacks(closeConversation)
            conversationUntilMillis = Long.MAX_VALUE
            asking.launch {
                val words = when (val reply = Assistant.ask(this@VoiceService, command.question)) {
                    is Assistant.Reply.Answer -> reply.words
                    is Assistant.Reply.Failed ->
                        driverWords().couldNotAsk(reply.why)
                }
                main.post { announce(words); afterSpeaking = { keepConversation(FOLLOW_UP_MILLIS) } }
            }
            return
        }
        confirm(command)
        act(command)
    }

    /** Until when the driver is in a conversation with the assistant. */
    private var conversationUntilMillis: Long = 0L

    /** Run once when the phone finishes speaking, then cleared. */
    private var afterSpeaking: (() -> Unit)? = null

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
            is VoiceCommand.StopListening -> if (english) "OK, bye" else "好，关语音"
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
                if (bringForward(command.target)) toast(driverWords().switchedTo(command.target.spoken))
            }
            is VoiceCommand.DriveToCentre -> {
                val centre = Profiles.centre(this)
                if (centre == null) {
                    toast(driverWords().noCentreForVoice)
                    return
                }
                Navigation.driveTo(this, centre)
                toast(driverWords().navigatingToCentre)
            }
            is VoiceCommand.Ask -> Unit // answered in understood()
            is VoiceCommand.StopListening -> {
                // After the confirmation has been said; the setting goes with it,
                // so opening the app does not bring the microphone straight back.
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    com.weixu.ueatsmonitor.App.instance.settingsStore.setVoiceEnabled(false)
                }
                main.postDelayed({ stopSelf() }, STOP_AFTER_MILLIS)
            }
            is VoiceCommand.StopNavigation ->
                MapsNavigation.stop(this) { pressed ->
                    val words = driverWords()
                    toast(if (pressed) words.navigationClosed else words.mapsNotNavigating)
                }
        }
    }

    /** Puts [target] on screen. False when it is not installed. */
    private fun bringForward(target: VoiceTarget): Boolean {
        val launch = packageManager.getLaunchIntentForPackage(target.packageName)
        if (launch == null) {
            toast(driverWords().couldNotFind(target.spoken))
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

    private fun confirmWords(words: String) = announce(words)

    /** The language the driver reads and listens in, as the settings have it. */
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
            NotificationChannel(CHANNEL, driverWords().voiceChannel, NotificationManager.IMPORTANCE_MIN)
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(driverWords().voiceListening)
            .setContentText(driverWords().voiceListeningHint)
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
        /** Long enough for "好，关语音" to be said before the service goes. */
        private const val STOP_AFTER_MILLIS = 2_000L

        /** How long after "你好" the driver has to ask the question. */
        private const val QUESTION_WINDOW_MILLIS = 8_000L

        /** How long after an answer the next sentence still counts as a follow-up. */
        private const val FOLLOW_UP_MILLIS = 5_000L

        /** The bundled greeting is half a second; listen again once it is over. */
        private const val GREET_MILLIS = 700L
        private const val TONE_MILLIS = 500L

        /** The Mandarin model in assets, and the folder it is unpacked to under files/. */
        private const val MODEL_ASSET = "model-cn"
        private const val MODEL_DIR = "model-cn"

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

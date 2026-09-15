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
import com.weixu.ueatsmonitor.domain.VoiceCommands

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

    /** Set once this session has acted, so the final result does not act again. */
    private var actedThisSession = false

    private var speech: TextToSpeech? = null
    private var speechReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        askForOfflineChinese()
        speech = TextToSpeech(this) { status ->
            val tts = speech ?: return@TextToSpeech
            val language = if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.SIMPLIFIED_CHINESE) else -1
            speechReady = language >= TextToSpeech.LANG_AVAILABLE
            if (!speechReady) Log.w(TAG, "voice: no Chinese speech, confirming with a tone")
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { main.post { again(NEXT_MILLIS) } }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { main.post { again(NEXT_MILLIS) } }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (!running) {
            running = true
            listen()
        }
        return START_STICKY
    }

    override fun onDestroy() {
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
        runCatching {
            val onDevice = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            LANGUAGES.forEach { language ->
                onDevice.triggerModelDownload(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language),
                )
                Log.i(TAG, "voice: asked for the offline $language model")
            }
            main.postDelayed({ onDevice.destroy() }, 5_000L)
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
            val command = VoiceCommands.parse(heard)
            if (command != null) understood(command) else again(NEXT_MILLIS)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (actedThisSession) return
            val heard = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val command = VoiceCommands.parse(heard) ?: return
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
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                recognizer?.destroy()
                recognizer = null
            }
            again(if (quiet) NEXT_MILLIS else ERROR_BACKOFF_MILLIS)
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
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
        confirm(command)
        act(command)
    }

    private fun confirm(command: VoiceCommand) {
        val words = when (command) {
            is VoiceCommand.SwitchTo -> command.target.confirm
        }
        val tts = speech
        if (speechReady && tts != null) {
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
                val launch = packageManager.getLaunchIntentForPackage(command.target.packageName)
                if (launch == null) {
                    toast("没找到" + command.target.spoken)
                    return
                }
                runCatching {
                    startActivity(
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    )
                }.onFailure { Log.w(TAG, "voice: switch failed", it) }
                toast("切到" + command.target.spoken)
            }
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
            .setContentText("说「地图」「送餐」「应用」或 map / uber eats / application")
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
        private const val TONE_MILLIS = 500L

        /**
         * Mandarin, simplified, as the phone's offline packs name it. "zh-CN" is
         * understood online but matches no offline pack.
         */
        private const val LANGUAGE = "cmn-Hans-CN"

        /** Every language a command may be said in, the one listened for first at its head. */
        private val LANGUAGES = listOf(LANGUAGE, "en-AU")

        fun start(context: Context) {
            context.startForegroundService(Intent(context, VoiceService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceService::class.java))
        }
    }
}

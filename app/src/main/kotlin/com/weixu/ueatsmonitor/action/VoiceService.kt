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
 */
class VoiceService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

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
        super.onDestroy()
    }

    private fun listen() {
        if (!running) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "voice: no recognizer on this phone")
            toast("手机上没有语音识别服务，语音命令用不了")
            stopSelf()
            return
        }
        val current = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val ask = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        // Leans the recognizer towards the few sentences that mean something here.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ask.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(VoiceCommands.PHRASES))
        }
        runCatching { current.startListening(ask) }
            .onFailure { Log.w(TAG, "voice: start failed", it); again(ERROR_BACKOFF_MILLIS) }
    }

    /** Starts the next session after a pause, so a failing recognizer does not spin. */
    private fun again(afterMillis: Long) {
        main.removeCallbacksAndMessages(null)
        main.postDelayed({ listen() }, afterMillis)
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            Log.i(TAG, "voice heard: $heard")
            VoiceCommands.parse(heard)?.let(::act)
            again(NEXT_MILLIS)
        }

        override fun onError(error: Int) {
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
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
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
            .setContentText("说「地图」「送餐」「应用」")
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

        fun start(context: Context) {
            context.startForegroundService(Intent(context, VoiceService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceService::class.java))
        }
    }
}

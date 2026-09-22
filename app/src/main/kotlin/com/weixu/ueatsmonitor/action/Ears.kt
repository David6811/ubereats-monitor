package com.weixu.ueatsmonitor.action

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import kotlin.math.sqrt

/**
 * Action. The microphone, gated, feeding Vosk.
 *
 * Decoding every moment of a shift costs a third of a core. Almost all of it
 * is road noise, so the recogniser is only fed while the sound is well above
 * the floor it has settled to, and for a moment after; the rest is dropped
 * unheard. When a loud stretch ends the recogniser is flushed, which is how
 * a sentence is closed.
 *
 * [onPartial] and [onSentence] are called on the main thread with Mandarin
 * already joined up ("你好地图", not "你 好 地 图").
 */
class Ears(
    private val model: Model,
    /**
     * The few phrases to listen for, or null for anything at all. A short list
     * makes a small model far surer of "切地图" than "七地图"; the question
     * after "你好" needs the whole language, and asks for it with [listenTo].
     */
    private val phrases: List<String>?,
    private val onPartial: (String) -> Unit,
    private val onSentence: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var paused = false

    @Volatile
    private var stopped = false

    /** What the recogniser should be listening for from the next chunk on; null leaves it as it is. */
    @Volatile
    private var wanted: List<String>? = phrases

    @Volatile
    private var rewire = false

    /** Switch to the command list, or to the whole language with null. Takes effect on the next chunk. */
    fun listenTo(phrases: List<String>?) {
        wanted = phrases
        rewire = true
    }

    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
        thread = Thread({ run() }, "ears").apply { start() }
    }

    /** Dropped audio while the phone itself is speaking. */
    fun pause(on: Boolean) {
        paused = on
    }

    /**
     * Forget the sentence in progress. Acting on a partial guess leaves its tail
     * in the recogniser, and the next sentence came out as "图关导航".
     */
    fun forget() {
        rewire = true
    }

    fun stop() {
        stopped = true
        thread?.join(1_000)
    }

    @SuppressLint("MissingPermission")
    private fun run() {
        val bufferBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(bufferBytes, CHUNK_SAMPLES * 4),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "ears: microphone would not open")
            return
        }
        // The command recogniser is always there. Opened to the whole language,
        // a second one listens beside it: a small model hears "切地图" as
        // "七力度" when it may say anything, and as "切地图" when it may say
        // only the commands, so the command one is believed first.
        val commands = recognizerFor(phrases)
        var free: Recognizer? = null
        var listening: List<String>? = phrases
        val chunk = ShortArray(CHUNK_SAMPLES)
        // The chunk before the one that crossed the line: the first syllable of
        // a sentence starts quietly, and without it "你好" came out as "闭好".
        val before = ShortArray(CHUNK_SAMPLES)
        var beforeRead = 0
        var floor = INITIAL_FLOOR
        var hangover = 0
        var fed = false
        record.startRecording()
        try {
            while (!stopped) {
                val read = record.read(chunk, 0, chunk.size)
                if (read <= 0) continue
                if (rewire) {
                    rewire = false
                    if (wanted != listening) {
                        free?.close()
                        free = if (wanted == null) recognizerFor(null) else null
                        listening = wanted
                    }
                    commands.reset()
                    free?.reset()
                    fed = false
                    hangover = 0
                }
                if (paused) {
                    if (fed) { commands.reset(); free?.reset(); fed = false }
                    hangover = 0
                    continue
                }
                val level = rms(chunk, read)
                // The floor follows the quiet parts down quickly and the loud
                // parts up only slowly, so speech stays above it.
                floor = if (level < floor) level else floor + (level - floor) * FLOOR_RISE
                val loud = level > floor * SPEECH_OVER_FLOOR && level > MIN_SPEECH_LEVEL
                if (loud) hangover = HANGOVER_CHUNKS else if (hangover > 0) hangover--
                if (hangover == 0) {
                    if (fed) {
                        fed = false
                        sentenceFrom(text(commands.finalResult, "text"), free?.let { text(it.finalResult, "text") })
                    }
                    System.arraycopy(chunk, 0, before, 0, read)
                    beforeRead = read
                    continue
                }
                if (!fed && beforeRead > 0) {
                    commands.acceptWaveForm(before, beforeRead)
                    free?.acceptWaveForm(before, beforeRead)
                    beforeRead = 0
                }
                fed = true
                val commandDone = commands.acceptWaveForm(chunk, read)
                val freeDone = free?.acceptWaveForm(chunk, read) ?: false
                if (commandDone || freeDone) {
                    sentenceFrom(
                        text(if (commandDone) commands.result else commands.finalResult, "text"),
                        free?.let { text(if (freeDone) it.result else it.finalResult, "text") },
                    )
                    fed = false
                    hangover = 0
                } else {
                    // Only the command recogniser's guesses are acted on early.
                    val partial = text(commands.partialResult, "partial")
                    if (partial.isNotEmpty() && partial != UNKNOWN) main.post { onPartial(partial) }
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            commands.close()
            free?.close()
        }
    }

    /** The command recogniser's sentence when it heard a command; otherwise the free one's. */
    private fun sentenceFrom(command: String, free: String?) {
        val heard = when {
            command.isNotEmpty() && !command.contains(UNKNOWN) -> command
            free != null -> free.replace(UNKNOWN, "")
            else -> command
        }
        if (heard.isNotEmpty()) main.post { onSentence(heard) }
    }

    private fun text(json: String, key: String): String =
        runCatching { JSONObject(json).optString(key) }.getOrDefault("").replace(" ", "").trim()

    /**
     * Vosk's grammar: a JSON list of phrases in the model's own units. This
     * model's units are single characters, so "切地图" is written "切 地 图".
     * "[unk]" lets anything else come out as unknown instead of as the
     * nearest phrase. Null means the whole language.
     */
    private fun recognizerFor(phrases: List<String>?): Recognizer =
        if (phrases == null) Recognizer(model, SAMPLE_RATE.toFloat())
        else Recognizer(model, SAMPLE_RATE.toFloat(), grammar(phrases))

    private fun grammar(phrases: List<String>): String {
        val spaced = phrases.map { it.toCharArray().joinToString(" ") } + "[unk]"
        return spaced.joinToString(",", "[", "]") { "\"" + it + "\"" }
    }

    private fun rms(samples: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) {
            val v = samples[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / count)
    }

    private companion object {
        const val SAMPLE_RATE = 16_000

        /** What a grammar recogniser says for anything off its list. */
        const val UNKNOWN = "[unk]"

        /** A tenth of a second per chunk: fine enough to catch the start of a word. */
        const val CHUNK_SAMPLES = 1_600

        /** Keep feeding this long after the sound drops, so a pause inside a sentence does not cut it. */
        const val HANGOVER_CHUNKS = 8

        /** Speech is this many times louder than the floor the room has settled to. */
        const val SPEECH_OVER_FLOOR = 2.5

        /** And never so quiet as this, whatever the floor: a silent car has a floor near zero. */
        const val MIN_SPEECH_LEVEL = 300.0

        const val INITIAL_FLOOR = 500.0

        /** How fast the floor climbs towards a louder room, per chunk. */
        const val FLOOR_RISE = 0.02

        const val TAG = "UEatsMonitor"
    }
}

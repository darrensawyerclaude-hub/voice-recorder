package com.vrecorder.app

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import android.widget.ScrollView
import android.widget.TextView
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * Feasibility test for on-device transcription (debug builds only). Start with:
 *   adb shell am start -n com.vrecorder.app/.SttTestActivity --es model whisper-base --es file "Name.aac"
 * Models are pushed to Android/data/com.vrecorder.app/files/models/<model>/. Results (timings,
 * battery, temperature, transcript) go to files/stt/<model>.txt.
 */
class SttTestActivity : Activity() {

    private lateinit var log: TextView
    private val out = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = TextView(this).apply { setPadding(32, 64, 32, 32); textSize = 13f }
        setContentView(ScrollView(this).apply { addView(log) })
        val model = intent.getStringExtra("model") ?: "whisper-base"
        val fileName = intent.getStringExtra("file")
        val threads = intent.getIntExtra("threads", 4)
        thread(name = "stt-test") { runTest(model, fileName, threads) }
    }

    private fun say(line: String) {
        out.appendLine(line)
        runOnUiThread { log.append(line + "\n") }
    }

    private fun battery(): Triple<Int, Long, Float> {
        val bm = getSystemService(BatteryManager::class.java)
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charge = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) // µAh
        val temp = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f) ?: 0f
        return Triple(pct, charge, temp)
    }

    private fun runTest(model: String, fileName: String?, threads: Int) {
        try {
            val dir = File(getExternalFilesDir(null), "models/$model")
            val rec = Recordings.list(this).let { list ->
                if (fileName != null) list.first { it.name == fileName } else list.maxBy { it.sizeBytes }
            }
            say("Model: $model, threads: $threads")
            say("File: ${rec.name}")

            // Decode the AAC to mono float PCM; sherpa-onnx resamples to 16 kHz itself.
            var rate = 0
            val pcm = java.io.ByteArrayOutputStream()
            val tDecode = SystemClock.elapsedRealtime()
            Exporter.decodeToPcm(this, rec.uri, onFormat = { r, _ -> rate = r }) { pcm.write(it) }
            val shorts = ByteBuffer.wrap(pcm.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val samples = FloatArray(shorts.remaining()) { shorts.get(it) / 32768f }
            val audioSec = samples.size / rate.toFloat()
            say("Audio: %.1f s at %d Hz (decoded in %.1f s)".format(audioSec, rate, (SystemClock.elapsedRealtime() - tDecode) / 1000f))

            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = modelConfig(model, dir, threads),
                decodingMethod = "greedy_search",
            )
            val b0 = battery()
            val tLoad = SystemClock.elapsedRealtime()
            val recognizer = OfflineRecognizer(null, config)
            val loadSec = (SystemClock.elapsedRealtime() - tLoad) / 1000f
            say("Model loaded in %.1f s".format(loadSec))
            say("Battery at start: ${b0.first}% (${b0.second} µAh), ${b0.third} °C")

            // Whisper handles at most 30 s at a time, so feed fixed 28 s chunks.
            val chunk = rate * 28
            val transcript = StringBuilder()
            var maxTemp = b0.third
            val tRun = SystemClock.elapsedRealtime()
            var start = 0
            while (start < samples.size) {
                val end = minOf(start + chunk, samples.size)
                val stream = recognizer.createStream()
                stream.acceptWaveform(samples.copyOfRange(start, end), rate)
                recognizer.decode(stream)
                val text = recognizer.getResult(stream).text.trim()
                stream.release()
                transcript.appendLine("[${formatClock(start * 1000L / rate)}] $text")
                maxTemp = maxOf(maxTemp, battery().third)
                val done = end / rate.toFloat()
                val elapsed = (SystemClock.elapsedRealtime() - tRun) / 1000f
                say("  %.0f / %.0f s audio, %.0f s elapsed".format(done, audioSec, elapsed))
                start = end
            }
            val runSec = (SystemClock.elapsedRealtime() - tRun) / 1000f
            val b1 = battery()
            recognizer.release()

            say("")
            say("RESULT")
            say("Transcribed %.1f s of audio in %.1f s: %.2f× real time (%s)".format(
                audioSec, runSec, runSec / audioSec, if (runSec < audioSec) "faster than the recording" else "slower than the recording"))
            say("Battery: ${b0.first}% → ${b1.first}%, ${(b0.second - b1.second) / 1000} mAh used (unreliable while USB-charging)")
            say("Temperature: ${b0.third} °C → ${b1.third} °C (max $maxTemp °C)")
            say("")
            say("TRANSCRIPT")
            say(transcript.toString())
        } catch (e: Throwable) {
            say("FAILED: $e")
            e.stackTrace.take(8).forEach { say("  at $it") }
        } finally {
            val f = File(getExternalFilesDir(null), "stt/$model.txt")
            f.parentFile?.mkdirs()
            f.writeText(out.toString())
            say("Saved to ${f.path}")
        }
    }

    private fun modelConfig(model: String, dir: File, threads: Int): OfflineModelConfig {
        fun pick(vararg names: String) = names.map { File(dir, it) }.firstOrNull { it.exists() }?.path
            ?: error("None of ${names.toList()} in $dir (has ${dir.list()?.toList()})")
        val tokens = dir.listFiles()!!.first { it.name.endsWith("tokens.txt") }.path
        return if (model.startsWith("whisper")) {
            val prefix = model.removePrefix("whisper-")
            OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = pick("$prefix-encoder.int8.onnx", "$prefix.en-encoder.int8.onnx", "$prefix-encoder.onnx", "$prefix.en-encoder.onnx"),
                    decoder = pick("$prefix-decoder.int8.onnx", "$prefix.en-decoder.int8.onnx", "$prefix-decoder.onnx", "$prefix.en-decoder.onnx"),
                    language = "en",
                    task = "transcribe",
                    tailPaddings = -1,
                ),
                tokens = tokens,
                numThreads = threads,
                modelType = "whisper",
            )
        } else {
            val files = dir.list()!!.toList()
            fun find(part: String) = files.firstOrNull { it.contains(part) }?.let { File(dir, it).path } ?: ""
            OfflineModelConfig(
                moonshine = OfflineMoonshineModelConfig(
                    preprocessor = find("preprocess"),
                    encoder = find("encode"),
                    uncachedDecoder = find("uncached"),
                    cachedDecoder = files.firstOrNull { it.contains("cached") && !it.contains("uncached") }?.let { File(dir, it).path } ?: "",
                    mergedDecoder = find("merged"),
                ),
                tokens = tokens,
                numThreads = threads,
            )
        }
    }
}

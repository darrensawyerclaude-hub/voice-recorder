package com.vrecorder.app

import android.content.Context
import android.net.Uri
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File

/**
 * Transcribes a recording on the phone. The audio is decoded and resampled as a stream, a voice
 * detector splits it at pauses (so words aren't cut in half and silence isn't sent to the model,
 * where Whisper tends to invent "(buzzing)" and the like), and each stretch of speech is
 * transcribed as it's found. Memory stays flat however long the recording is.
 */
object Transcriber {

    private const val VAD_WINDOW = 512
    private const val GROUP_MAX_S = 20
    private const val GROUP_GAP_S = 3

    fun transcribe(
        context: Context,
        source: Uri,
        model: SpeechModel,
        totalMs: Long,
        isCancelled: () -> Boolean,
        onProgress: (doneMs: Long) -> Unit,
    ): List<TranscriptSegment> {
        val recognizer = OfflineRecognizer(null, recognizerConfig(context, model))
        val vad = Vad(
            context.assets,
            VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = "silero_vad.onnx",
                    threshold = 0.5f,
                    minSilenceDuration = 0.5f,
                    minSpeechDuration = 0.25f,
                    windowSize = VAD_WINDOW,
                    // Whisper handles at most 30 s at once; split long unbroken speech before that.
                    maxSpeechDuration = 25f,
                ),
                sampleRate = Resampler.OUT_RATE,
                numThreads = 1,
            ),
        )
        val segments = mutableListOf<TranscriptSegment>()
        try {
            var resampler: Resampler? = null
            var pending = FloatArray(0)
            var fed = 0L

            // Short pieces between pauses lose context ("Shares and elon masks"), so consecutive
            // speech is grouped up to GROUP_MAX_S and transcribed together. Cuts stay at pauses.
            val group = mutableListOf<com.k2fsa.sherpa.onnx.SpeechSegment>()
            fun groupSamples() = group.sumOf { it.samples.size }

            fun decodeGroup() {
                if (group.isEmpty()) return
                if (isCancelled()) throw InterruptedException("Cancelled")
                val audio = FloatArray(groupSamples())
                var at = 0
                group.forEach { it.samples.copyInto(audio, at); at += it.samples.size }
                val stream = recognizer.createStream()
                stream.acceptWaveform(audio, Resampler.OUT_RATE)
                recognizer.decode(stream)
                val text = BritishSpelling.apply(recognizer.getResult(stream).text.trim())
                stream.release()
                if (text.isNotEmpty() && !isNoiseLabel(text)) {
                    val last = group.last()
                    segments += TranscriptSegment(
                        group.first().start * 1000 / Resampler.OUT_RATE,
                        (last.start + last.samples.size) * 1000 / Resampler.OUT_RATE,
                        text,
                    )
                }
                group.clear()
            }

            fun drain() {
                while (!vad.empty()) {
                    val s = vad.front()
                    vad.pop()
                    val gap = group.lastOrNull()?.let { s.start - (it.start + it.samples.size) } ?: 0
                    // A long silence starts a new paragraph; so does reaching the size limit.
                    if (group.isNotEmpty() &&
                        (groupSamples() + s.samples.size > GROUP_MAX_S * Resampler.OUT_RATE || gap > GROUP_GAP_S * Resampler.OUT_RATE)
                    ) decodeGroup()
                    group += s
                }
            }

            Exporter.decodeToPcm(context, source, onFormat = { r, c -> resampler = Resampler(r, c) }) { chunk ->
                if (isCancelled()) throw InterruptedException("Cancelled")
                val samples = pending + resampler!!.process(chunk)
                val whole = samples.size / VAD_WINDOW * VAD_WINDOW
                var i = 0
                while (i < whole) {
                    vad.acceptWaveform(samples.copyOfRange(i, i + VAD_WINDOW))
                    i += VAD_WINDOW
                }
                pending = samples.copyOfRange(whole, samples.size)
                fed += whole
                drain()
                onProgress((fed * 1000 / Resampler.OUT_RATE).coerceAtMost(totalMs))
            }
            vad.flush()
            drain()
            decodeGroup()
            onProgress(totalMs)
        } finally {
            vad.release()
            recognizer.release()
        }
        return segments
    }

    // Whisper labels non-speech like "(music)" or "[inaudible]"; those aren't worth keeping.
    private fun isNoiseLabel(text: String) =
        Regex("""^[\[(*].{0,40}[\])*]$""").matches(text) || text.all { !it.isLetterOrDigit() }

    private fun recognizerConfig(context: Context, model: SpeechModel): OfflineRecognizerConfig {
        val dir = SpeechModels.dir(context, model)
        fun f(name: String) = File(dir, name).path
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val modelConfig = when (model) {
            SpeechModel.ACCURATE -> OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = f("small.en-encoder.int8.onnx"),
                    decoder = f("small.en-decoder.int8.onnx"),
                    language = "en",
                    task = "transcribe",
                    tailPaddings = -1,
                ),
                tokens = f("small.en-tokens.txt"),
                numThreads = threads,
                modelType = "whisper",
            )
            SpeechModel.QUICK -> OfflineModelConfig(
                moonshine = OfflineMoonshineModelConfig(
                    encoder = f("encoder_model.ort"),
                    mergedDecoder = f("decoder_model_merged.ort"),
                ),
                tokens = f("tokens.txt"),
                numThreads = threads,
            )
        }
        return OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = Resampler.OUT_RATE, featureDim = 80),
            modelConfig = modelConfig,
            decodingMethod = "greedy_search",
        )
    }
}

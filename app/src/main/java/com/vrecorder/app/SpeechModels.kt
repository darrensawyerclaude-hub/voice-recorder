package com.vrecorder.app

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The two on-device transcription models, downloaded on first use into the app's own storage.
 * Both are MIT licensed for English. Tested on a Galaxy S20 FE (docs/transcription-test.md).
 *
 * Before launch these should move to Play Asset Delivery (free, hosted by Google); for now they
 * come from the sherpa-onnx project's public downloads.
 */
enum class SpeechModel(
    val label: String,
    val summary: String,
    val downloadBytes: Long,
    // How many times faster than the recording it ran on the S20 FE with continuous speech.
    val speedOnTestPhone: Float,
) {
    QUICK("Quick", "About 92% of words right. Very fast.", 111_266_225L, 15f),
    ACCURATE("Accurate", "About 95% of words right. Slower.", 375_501_079L, 2f);

    val id get() = name.lowercase()
}

object SpeechModels {

    private const val HF_WHISPER = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small.en/resolve/main"
    private const val MOONSHINE_TAR =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-base-en-quantized-2026-02-27.tar.bz2"
    private val moonshineFiles = setOf("encoder_model.ort", "decoder_model_merged.ort", "tokens.txt")
    private val whisperFiles = listOf("small.en-encoder.int8.onnx", "small.en-decoder.int8.onnx", "small.en-tokens.txt")

    fun dir(context: Context, model: SpeechModel) = File(context.filesDir, "models/${model.id}")

    private fun marker(context: Context, model: SpeechModel) = File(dir(context, model), ".complete")

    fun isInstalled(context: Context, model: SpeechModel) = marker(context, model).exists()

    fun delete(context: Context, model: SpeechModel) {
        dir(context, model).deleteRecursively()
    }

    /** Downloads (and for Quick, unpacks) the model. [onProgress] gets bytes downloaded so far. */
    fun download(context: Context, model: SpeechModel, isCancelled: () -> Boolean, onProgress: (Long) -> Unit) {
        val dir = dir(context, model)
        dir.deleteRecursively()
        dir.mkdirs()
        var done = 0L
        val counted = { input: InputStream ->
            object : FilterInputStream(input) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (isCancelled()) throw InterruptedException("Cancelled")
                    val n = super.read(b, off, len)
                    if (n > 0) { done += n; onProgress(done) }
                    return n
                }
            }
        }
        when (model) {
            SpeechModel.ACCURATE -> whisperFiles.forEach { name ->
                open("$HF_WHISPER/$name").use { input ->
                    val part = File(dir, "$name.part")
                    part.outputStream().use { counted(input).copyTo(it, 64 * 1024) }
                    part.renameTo(File(dir, name))
                }
            }
            SpeechModel.QUICK -> open(MOONSHINE_TAR).use { input ->
                TarArchiveInputStream(BZip2CompressorInputStream(counted(input).buffered(64 * 1024))).use { tar ->
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        val name = entry.name.substringAfterLast('/')
                        if (entry.isDirectory || name !in moonshineFiles) continue
                        File(dir, name).outputStream().use { tar.copyTo(it, 64 * 1024) }
                    }
                }
                check(moonshineFiles.all { File(dir, it).exists() }) { "Download incomplete" }
            }
        }
        marker(context, model).writeText("ok")
    }

    private fun open(url: String): InputStream {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 20_000
        c.readTimeout = 60_000
        if (c.responseCode !in 200..299) error("Download failed (HTTP ${c.responseCode})")
        return c.inputStream
    }
}

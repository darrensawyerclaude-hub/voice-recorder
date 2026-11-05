package com.vrecorder.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

enum class ExportFormat(val label: String, val ext: String, val mime: String) {
    AAC("AAC (small, plays everywhere)", "aac", "audio/aac"),
    WAV("WAV (uncompressed, for editing)", "wav", "audio/wav"),
}

data class ExportResult(val exported: Int, val failed: List<String>)

/**
 * Copies or converts recordings into a folder the user picked with the system folder picker.
 * The originals are never changed.
 */
object Exporter {

    suspend fun export(
        context: Context,
        recordings: List<Recording>,
        format: ExportFormat,
        treeUri: Uri,
        onProgress: (done: Int, total: Int) -> Unit,
    ): ExportResult = withContext(Dispatchers.IO) {
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        val failed = mutableListOf<String>()
        recordings.forEachIndexed { i, rec ->
            ensureActive()
            withContext(Dispatchers.Main) { onProgress(i, recordings.size) }
            val base = rec.name.removeSuffix(".aac")
            val ok = runCatching {
                // The provider adds " (1)" etc. if the name is already taken.
                val target = DocumentsContract.createDocument(
                    context.contentResolver, parent, format.mime, "$base.${format.ext}"
                ) ?: error("Couldn't create file")
                context.contentResolver.openOutputStream(target)!!.use { out ->
                    when (format) {
                        ExportFormat.AAC -> context.contentResolver.openInputStream(rec.uri)!!.use { it.copyTo(out) }
                        ExportFormat.WAV -> writeWav(context, rec.uri, out)
                    }
                }
            }.isSuccess
            if (!ok) failed += base
        }
        withContext(Dispatchers.Main) { onProgress(recordings.size, recordings.size) }
        ExportResult(recordings.size - failed.size, failed)
    }

    /**
     * Decodes to 16-bit PCM in a temp file first, because the WAV header needs the final length
     * and some destinations (Drive, USB) can't seek back to fill it in.
     */
    private fun writeWav(context: Context, source: Uri, out: OutputStream) {
        val tmp = File.createTempFile("export", ".wav", context.cacheDir)
        try {
            RandomAccessFile(tmp, "rw").use { raf ->
                raf.write(ByteArray(WAV_HEADER_SIZE))
                val (rate, channels) = decodeToPcm(context, source) { chunk -> raf.write(chunk) }
                val dataLen = raf.length() - WAV_HEADER_SIZE
                raf.seek(0)
                raf.write(wavHeader(rate, channels, dataLen))
            }
            tmp.inputStream().use { it.copyTo(out) }
        } finally {
            tmp.delete()
        }
    }

    /** Returns (sample rate, channel count). */
    private fun decodeToPcm(context: Context, source: Uri, sink: (ByteArray) -> Unit): Pair<Int, Int> {
        val extractor = MediaExtractor()
        context.contentResolver.openFileDescriptor(source, "r")!!.use { pfd ->
            extractor.setDataSource(pfd.fileDescriptor)
        }
        try {
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(track)
            val inFormat = extractor.getTrackFormat(track)
            var rate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(inFormat.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(inFormat, null, null, 0)
            codec.start()
            try {
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                while (true) {
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val n = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        o >= 0 -> {
                            if (info.size > 0) {
                                val buf = codec.getOutputBuffer(o)!!
                                val chunk = ByteArray(info.size)
                                buf.position(info.offset)
                                buf.get(chunk)
                                sink(chunk)
                            }
                            codec.releaseOutputBuffer(o, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            return rate to channels
        } finally {
            extractor.release()
        }
    }

    private const val WAV_HEADER_SIZE = 44

    private fun wavHeader(rate: Int, channels: Int, dataLen: Long): ByteArray {
        val byteRate = rate * channels * 2
        val b = java.nio.ByteBuffer.allocate(WAV_HEADER_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt((36 + dataLen).toInt()).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort())
        b.putInt(rate).putInt(byteRate).putShort((channels * 2).toShort()).putShort(16)
        b.put("data".toByteArray()).putInt(dataLen.toInt())
        return b.array()
    }
}

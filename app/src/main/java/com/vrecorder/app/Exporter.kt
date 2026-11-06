package com.vrecorder.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.nio.ByteBuffer
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

enum class ExportFormat(val label: String, val ext: String, val mime: String) {
    M4A("M4A (recommended: opens on iPhone, Mac, Windows, Android)", "m4a", "audio/mp4"),
    AAC("AAC (the original file, best on Android)", "aac", "audio/aac"),
    WAV("WAV (uncompressed, for editing, about 10× bigger)", "wav", "audio/wav"),
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
                        ExportFormat.M4A -> {
                            val tmp = File.createTempFile("export", ".m4a", context.cacheDir)
                            try {
                                toM4a(context, rec.uri, tmp)
                                tmp.inputStream().use { it.copyTo(out) }
                            } finally {
                                tmp.delete()
                            }
                        }
                    }
                }
            }.isSuccess
            if (!ok) failed += base
        }
        withContext(Dispatchers.Main) { onProgress(recordings.size, recordings.size) }
        ExportResult(recordings.size - failed.size, failed)
    }

    /**
     * Repackages the ADTS stream into an MP4 container. The audio isn't re-encoded, so it's
     * instant and lossless; it just becomes a file iPhones, Macs and Windows open without fuss.
     */
    fun toM4a(context: Context, source: Uri, dest: File) {
        val extractor = MediaExtractor()
        context.contentResolver.openFileDescriptor(source, "r")!!.use { extractor.setDataSource(it.fileDescriptor) }
        val muxer = MediaMuxer(dest.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val track = audioTrack(extractor)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val out = muxer.addTrack(format)
            muxer.start()
            val maxSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 64 * 1024
            val buf = ByteBuffer.allocate(maxSize)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val n = extractor.readSampleData(buf, 0)
                if (n < 0) break
                info.set(0, n, extractor.sampleTime, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                muxer.writeSampleData(out, buf, info)
                extractor.advance()
            }
            muxer.stop()
        } finally {
            muxer.release()
            extractor.release()
        }
    }

    /** Roughly how big [toSmallM4a] output will be, from the original's size. */
    fun estimateSmallBytes(originalBytes: Long): Long = originalBytes * SMALL_BIT_RATE / ORIGINAL_BIT_RATE

    /**
     * Re-encodes at a low speech bitrate (mono, 32 kbps: about 0.25 MB a minute) so long
     * recordings fit email limits. Fine for listening back to speech; not for music.
     */
    fun toSmallM4a(context: Context, source: Uri, dest: File) {
        var encoder: MediaCodec? = null
        val muxer = MediaMuxer(dest.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxTrack = -1
        var ptsBytes = 0L
        var rate = 0
        var channels = 0
        val info = MediaCodec.BufferInfo()

        fun drain(endOfStream: Boolean) {
            val enc = encoder ?: return
            while (true) {
                val o = enc.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxTrack = muxer.addTrack(enc.outputFormat)
                        muxer.start()
                    }
                    o >= 0 -> {
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!isConfig && info.size > 0 && muxTrack >= 0) {
                            muxer.writeSampleData(muxTrack, enc.getOutputBuffer(o)!!, info)
                        }
                        enc.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                    o == MediaCodec.INFO_TRY_AGAIN_LATER && !endOfStream -> return
                }
            }
        }

        fun feed(bytes: ByteArray, flags: Int) {
            val enc = encoder!!
            var offset = 0
            while (true) {
                val i = enc.dequeueInputBuffer(10_000)
                if (i < 0) { drain(false); continue }
                val buf = enc.getInputBuffer(i)!!
                buf.clear()
                val n = minOf(buf.remaining(), bytes.size - offset)
                buf.put(bytes, offset, n)
                val pts = ptsBytes * 1_000_000 / (2L * channels * rate)
                ptsBytes += n
                offset += n
                val last = offset >= bytes.size
                enc.queueInputBuffer(i, 0, n, pts, if (last) flags else 0)
                drain(false)
                if (last) break
            }
        }

        try {
            decodeToPcm(context, source, onFormat = { r, c ->
                rate = r
                channels = c
                encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                    val f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, r, c).apply {
                        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                        setInteger(MediaFormat.KEY_BIT_RATE, SMALL_BIT_RATE.toInt())
                        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
                    }
                    configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    start()
                }
            }) { chunk -> feed(chunk, 0) }
            feed(ByteArray(0), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
            muxer.stop()
        } finally {
            encoder?.let { runCatching { it.stop() }; it.release() }
            runCatching { muxer.release() }
        }
    }

    /**
     * Makes M4A copies (full or small) in the app's cache for sharing, and returns content URIs
     * other apps can read. Old share copies are cleared first.
     */
    suspend fun prepareForSharing(
        context: Context,
        recordings: List<Recording>,
        small: Boolean,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<Uri> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
        recordings.mapIndexed { i, rec ->
            ensureActive()
            withContext(Dispatchers.Main) { onProgress(i, recordings.size) }
            val base = rec.name.removeSuffix(".aac")
            val file = File(dir, if (small) "$base (small).m4a" else "$base.m4a")
            if (small) toSmallM4a(context, rec.uri, file) else toM4a(context, rec.uri, file)
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.also { withContext(Dispatchers.Main) { onProgress(recordings.size, recordings.size) } }
    }

    private const val ORIGINAL_BIT_RATE = 128_000L
    private const val SMALL_BIT_RATE = 32_000L

    private fun audioTrack(extractor: MediaExtractor) = (0 until extractor.trackCount).first {
        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
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

    /**
     * Returns (sample rate, channel count). [onFormat] is called once with the PCM format
     * before the first chunk reaches [sink].
     */
    internal fun decodeToPcm(
        context: Context,
        source: Uri,
        onFormat: (rate: Int, channels: Int) -> Unit = { _, _ -> },
        sink: (ByteArray) -> Unit,
    ): Pair<Int, Int> {
        val extractor = MediaExtractor()
        context.contentResolver.openFileDescriptor(source, "r")!!.use { pfd ->
            extractor.setDataSource(pfd.fileDescriptor)
        }
        try {
            val track = audioTrack(extractor)
            extractor.selectTrack(track)
            val inFormat = extractor.getTrackFormat(track)
            var rate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(inFormat.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(inFormat, null, null, 0)
            codec.start()
            var announced = false
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
                                if (!announced) {
                                    onFormat(rate, channels)
                                    announced = true
                                }
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

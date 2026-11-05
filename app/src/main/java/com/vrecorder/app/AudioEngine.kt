package com.vrecorder.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import java.io.FileOutputStream
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Captures mic PCM, levels it (automatic gain + peak limiter) and encodes it to AAC in an
 * ADTS stream. ADTS frames are self-contained, so a crash loses only the last few milliseconds.
 *
 * Phone mics record speech well below full scale (around -25 dBFS measured on an S20 FE), so
 * gain is applied here rather than at playback, which keeps shared files at a normal level too.
 */
class AudioEngine(
    private val out: FileOutputStream,
    private val onLevel: (Float) -> Unit,
) {
    companion object {
        const val SAMPLE_RATE = 48_000
        private const val BIT_RATE = 128_000
        private const val BLOCK = 1024

        // Speech is brought up towards this RMS level (about -18 dBFS).
        private const val TARGET_RMS = 0.125f
        private const val MAX_GAIN = 10f // +20 dB
        // Blocks quieter than this are treated as background and don't push the gain up.
        private const val GATE_RMS = 0.003f
        private const val LIMIT = 0.95f
    }

    @Volatile private var running = false
    @Volatile var paused = false
    private var thread: Thread? = null

    private var gain = 3f
    private var limiterGain = 1f

    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked before the service is started.
    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, max(minBuf, BLOCK * 2 * 4),
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, BLOCK * 2)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        record.startRecording()

        running = true
        thread = Thread({ loop(record, codec) }, "audio-engine").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(2000)
        thread = null
    }

    private fun loop(record: AudioRecord, codec: MediaCodec) {
        val pcm = ShortArray(BLOCK)
        val info = MediaCodec.BufferInfo()
        var samplesWritten = 0L
        try {
            while (running) {
                val n = record.read(pcm, 0, BLOCK)
                if (n <= 0) continue
                // While paused the mic keeps running but audio is dropped, so the file has no gap.
                if (paused) {
                    onLevel(0f)
                    continue
                }
                process(pcm, n)
                queue(codec, pcm, n, samplesWritten * 1_000_000 / SAMPLE_RATE, 0)
                samplesWritten += n
                drain(codec, info)
            }
            queue(codec, pcm, 0, samplesWritten * 1_000_000 / SAMPLE_RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(codec, info, untilEos = true)
        } finally {
            runCatching { record.stop() }
            record.release()
            runCatching { codec.stop() }
            codec.release()
        }
    }

    private fun process(pcm: ShortArray, n: Int) {
        var sum = 0.0
        for (i in 0 until n) {
            val s = pcm[i] / 32768f
            sum += s * s
        }
        val rms = sqrt(sum / n).toFloat()

        if (rms > GATE_RMS) {
            val wanted = min(MAX_GAIN, max(1f, TARGET_RMS / rms))
            // Back off quickly when speech gets loud, recover slowly so the noise floor doesn't pump.
            gain += (wanted - gain) * if (wanted < gain) 0.3f else 0.02f
        }

        var peak = 0f
        for (i in 0 until n) {
            var y = pcm[i] / 32768f * gain
            val a = abs(y * limiterGain)
            if (a > LIMIT) limiterGain = LIMIT / abs(y)
            y *= limiterGain
            limiterGain = min(1f, limiterGain + 0.0002f)
            peak = max(peak, abs(y))
            pcm[i] = (y * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        onLevel(peak)
    }

    private fun queue(codec: MediaCodec, pcm: ShortArray, n: Int, ptsUs: Long, flags: Int) {
        val index = codec.dequeueInputBuffer(10_000)
        if (index < 0) return
        val buf = codec.getInputBuffer(index)!!
        buf.clear()
        buf.order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) buf.putShort(pcm[i])
        codec.queueInputBuffer(index, 0, n * 2, ptsUs, flags)
    }

    private fun drain(codec: MediaCodec, info: MediaCodec.BufferInfo, untilEos: Boolean = false) {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (untilEos) 10_000 else 0)
            if (index < 0) {
                if (untilEos && index == MediaCodec.INFO_TRY_AGAIN_LATER) continue
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue
                return
            }
            val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            if (!isConfig && info.size > 0) {
                val buf = codec.getOutputBuffer(index)!!
                val frame = ByteArray(info.size + 7)
                writeAdtsHeader(frame, frame.size)
                buf.position(info.offset)
                buf.get(frame, 7, info.size)
                out.write(frame)
            }
            codec.releaseOutputBuffer(index, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
        }
    }

    /** AAC-LC, 48 kHz (index 3), mono. */
    private fun writeAdtsHeader(b: ByteArray, length: Int) {
        val profile = 2
        val freqIdx = 3
        val channels = 1
        b[0] = 0xFF.toByte()
        b[1] = 0xF1.toByte()
        b[2] = (((profile - 1) shl 6) or (freqIdx shl 2) or (channels shr 2)).toByte()
        b[3] = (((channels and 3) shl 6) or (length shr 11)).toByte()
        b[4] = ((length shr 3) and 0xFF).toByte()
        b[5] = (((length and 7) shl 5) or 0x1F).toByte()
        b[6] = 0xFC.toByte()
    }
}

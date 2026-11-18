package com.local.dsapp.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.local.dsapp.net.SpeechApi
import okhttp3.Call

/**
 * 朗读，照官方网页版的做法：
 * 请求 /v1/tools/tts 拿裸 PCM（24kHz / 单声道 / 16-bit），边收边写进 AudioTrack 播放，
 * 首帧到达就开始出声。状态 idle → loading（已请求未出声）→ playing。
 */
object ReadAloud {

    enum class Status { Idle, Loading, Playing }

    var messageId by mutableStateOf<String?>(null)
        private set
    var status by mutableStateOf(Status.Idle)
        private set

    private var call: Call? = null
    private var track: AudioTrack? = null

    fun statusOf(id: String): Status = if (messageId == id) status else Status.Idle

    /** 同一条正在读 = 停止；否则从头读这一条 */
    fun toggle(apiKey: String, id: String, text: String, voice: String, onError: (String) -> Unit) {
        if (messageId == id) {
            stop()
            return
        }
        stop()
        if (apiKey.isBlank()) {
            onError("先在设置里填 API Key")
            return
        }
        if (text.isBlank()) {
            onError("这条回答没有可朗读的正文")
            return
        }

        messageId = id
        status = Status.Loading

        call = SpeechApi.synthesize(
            apiKey = apiKey,
            text = text,
            voice = voice,
            onChunk = { bytes -> writeChunk(bytes) },
            onFinish = { error ->
                if (error != null) {
                    val cb = onError
                    stop()
                    cb(error)
                } else {
                    drainAndStop()
                }
            }
        )
    }

    fun stop() {
        call?.cancel()
        call = null
        val t = track
        track = null
        if (t != null) {
            runCatching { t.pause() }
            runCatching { t.flush() }
            runCatching { t.release() }
        }
        messageId = null
        status = Status.Idle
    }

    /* ---------------------------------------------------------- 播放 */

    private var carry: Byte? = null
    private var writtenFrames = 0L

    private fun ensureTrack(): AudioTrack? {
        track?.let { return it }
        val minBuf = AudioTrack.getMinBufferSize(
            SpeechApi.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SpeechApi.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuf, SpeechApi.SAMPLE_RATE * 2))
            .build()
        t.play()
        track = t
        writtenFrames = 0
        carry = null
        return t
    }

    /** PCM 是 16-bit，奇数字节要留到下一段，否则会错位成噪音 */
    private fun writeChunk(chunk: ByteArray) {
        if (messageId == null) return
        val t = ensureTrack() ?: return
        if (status == Status.Loading) status = Status.Playing

        val prev = carry
        val data = if (prev != null) ByteArray(chunk.size + 1).also {
            it[0] = prev
            System.arraycopy(chunk, 0, it, 1, chunk.size)
        } else chunk

        val even = data.size - (data.size % 2)
        if (even < data.size) carry = data[data.size - 1] else carry = null
        if (even > 0) {
            t.write(data, 0, even)
            writtenFrames += even / 2
        }
    }

    /** 流结束不代表放完了：等播放头追上写入的帧数再收摊 */
    private fun drainAndStop() {
        val t = track
        if (t == null) {
            messageId = null
            status = Status.Idle
            return
        }
        val target = writtenFrames
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            if (t.playbackHeadPosition.toLong() >= target) break
            try {
                Thread.sleep(60)
            } catch (e: InterruptedException) {
                break
            }
        }
        stop()
    }
}

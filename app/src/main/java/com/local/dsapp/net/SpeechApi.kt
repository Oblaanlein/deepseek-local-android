package com.local.dsapp.net

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 语音合成，照官方 chat-demo 的 services/api/speech.ts：
 *   POST {origin}/v1/tools/tts
 *   headers: Authorization: Bearer / Content-Type: application/json
 *   body:    { text, voice_id: 'stella'|'tide', format: 'pcm', filter_markdown: true }
 *   response: 裸 PCM 流 —— 24kHz、单声道、16-bit 小端，没有文件头
 * 服务端会自己过滤代码块、表格、引用标记和长公式，所以正文可以原样提交。
 */
object SpeechApi {

    const val SAMPLE_RATE = 24000
    private const val TOOLS_BASE = "${Api.ORIGIN}/v1/tools"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    /**
     * 边收边回调 PCM 片段（调用线程 = 后台线程）。
     * onFinish(null) 表示正常结束；非空是错误文案。
     */
    fun synthesize(
        apiKey: String,
        text: String,
        voice: String,
        onChunk: (ByteArray) -> Unit,
        onFinish: (String?) -> Unit
    ): Call {
        val body = JSONObject()
            .put("text", text)
            .put("voice_id", voice)
            .put("format", "pcm")
            .put("filter_markdown", true)
            .toString()

        val request = Request.Builder()
            .url("$TOOLS_BASE/tts")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody(JSON))
            .build()

        val call = client.newCall(request)
        Thread {
            var error: String? = null
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val raw = response.body?.string().orEmpty()
                        error = when (response.code) {
                            400 -> "这段内容无法朗读"
                            401 -> "API Key 无效"
                            402 -> "余额不足"
                            429 -> "请求太频繁，稍后再试"
                            else -> parseError(raw) ?: "朗读失败（HTTP ${response.code}）"
                        }
                        return@use
                    }
                    val source = response.body?.source()
                    if (source == null) {
                        error = "朗读失败：没有收到音频数据"
                        return@use
                    }
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = source.read(buf)
                        if (n <= 0) break
                        if (n > 0) onChunk(buf.copyOf(n))
                    }
                }
            } catch (e: IOException) {
                if (!call.isCanceled()) error = e.message ?: "网络错误"
            } finally {
                onFinish(error)
            }
        }.apply { isDaemon = true }.start()

        return call
    }

    private fun parseError(text: String): String? {
        if (text.isBlank()) return null
        return runCatching {
            JSONObject(text).optJSONObject("error")?.optString("message")
        }.getOrNull() ?: text.take(200)
    }
}

package com.local.dsapp.net

import com.local.dsapp.data.Block
import com.local.dsapp.data.Msg
import com.local.dsapp.data.SYSTEM_PROMPT
import android.util.Base64
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * DeepSeek 的 Anthropic 兼容接口。
 *
 * 照官方 chat-demo 的 services/api/client.ts：
 *   POST https://api.deepseek.com/anthropic/v1/messages
 *   headers: x-api-key / Accept: text/event-stream
 *   body:    { model, system, messages, thinking, tools, safety_check, stream: true }
 *
 * 用这个而不是 /chat/completions，是因为只有它能拿到「思考过程」独立块
 * 和联网搜索的来源引用（官方 App 的形态就是这么来的）。
 */
object Api {

    const val ORIGIN = "https://api.deepseek.com"
    private const val BASE = "$ORIGIN/anthropic/v1"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // 流式：不设读超时
        .retryOnConnectionFailure(true)
        .build()

    /** 组装请求体，字段顺序与官方一致 */
    fun buildBody(
        model: String,
        thinking: Boolean,
        search: Boolean,
        messages: List<Msg>,
        readFile: (String) -> ByteArray? = { null }
    ): JSONObject = JSONObject().apply {
        put("model", model)
        put("system", SYSTEM_PROMPT)
        put("messages", toRequestMessages(messages, readFile))
        put("thinking", JSONObject().put("type", if (thinking) "enabled" else "disabled"))
        // 官方：读链接常开，搜索开关决定是否再声明搜索工具
        put("tools", JSONArray().apply {
            put(JSONObject().put("type", "deepseek_read_url").put("name", "deepseek_read_url"))
            if (search) {
                put(JSONObject().put("type", "deepseek_web_search").put("name", "deepseek_web_search"))
            }
        })
        put("safety_check", JSONObject().put("level", "high"))
        put("max_tokens", 8192)
        put("stream", true)
    }

    /** 把本地会话转成接口的 messages；助手侧的思考块要带 signature 回传 */
    private fun toRequestMessages(
        messages: List<Msg>,
        readFile: (String) -> ByteArray?
    ): JSONArray = JSONArray().apply {
        messages.forEach { m ->
            if (m.isUser) {
                val content = JSONArray()
                if (m.text.isNotBlank()) {
                    content.put(JSONObject().put("type", "text").put("text", m.text))
                }
                // 图片按 base64 内联；其它文件读成文本附在后面
                m.attachments.forEach { a ->
                    val bytes = readFile(a.path)
                    if (bytes != null) {
                        if (a.isImage) {
                            content.put(
                                JSONObject()
                                    .put("type", "image")
                                    .put(
                                        "source",
                                        JSONObject()
                                            .put("type", "base64")
                                            .put("media_type", a.mime.ifBlank { "image/jpeg" })
                                            .put(
                                                "data",
                                                Base64.encodeToString(bytes, Base64.NO_WRAP)
                                            )
                                    )
                            )
                        } else {
                            val txt = String(bytes, Charsets.UTF_8)
                            content.put(
                                JSONObject()
                                    .put("type", "text")
                                    .put("text", "【文件：" + a.name + "】\n" + txt)
                            )
                        }
                    }
                }
                put(JSONObject().put("role", "user").put("content", content))
            } else {
                val content = JSONArray()
                m.blocks.forEach { b ->
                    when (b) {
                        is Block.Thinking -> if (b.thinking.isNotBlank() && b.signature.isNotBlank()) {
                            content.put(
                                JSONObject()
                                    .put("type", "thinking")
                                    .put("thinking", b.thinking)
                                    .put("signature", b.signature)
                            )
                        }
                        is Block.Text -> if (b.text.isNotBlank()) {
                            content.put(JSONObject().put("type", "text").put("text", b.text))
                        }
                        is Block.Tool -> Unit
                    }
                }
                if (content.length() > 0) {
                    put(JSONObject().put("role", "assistant").put("content", content))
                }
            }
        }
    }

    /**
     * 发起流式请求。回调在调用线程上触发（请放到 IO 线程调用）。
     * onFinish(null) 表示正常结束；非空为错误信息。
     */
    fun stream(
        apiKey: String,
        body: JSONObject,
        onEvent: (JSONObject) -> Unit,
        onFinish: (String?) -> Unit
    ): Call {
        val request = Request.Builder()
            .url("$BASE/messages")
            .header("x-api-key", apiKey)
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody(JSON))
            .build()

        val call = client.newCall(request)
        Thread {
            var error: String? = null
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val text = response.body?.string().orEmpty()
                        error = parseError(text) ?: "HTTP ${response.code}"
                        return@use
                    }
                    val contentType = response.header("Content-Type").orEmpty()
                    val source = response.body?.source()
                    if (source == null || !contentType.contains("text/event-stream")) {
                        error = "服务端返回的不是事件流（$contentType）"
                        return@use
                    }
                    val data = StringBuilder()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.isEmpty() -> {
                                if (data.isNotEmpty()) {
                                    runCatching { JSONObject(data.toString()) }
                                        .getOrNull()
                                        ?.let { event ->
                                            if (event.optString("type") == "error") {
                                                error = parseError(event.toString()) ?: "生成失败"
                                            } else {
                                                onEvent(event)
                                            }
                                        }
                                    data.setLength(0)
                                }
                            }
                            line.startsWith("data:") -> {
                                val payload = line.removePrefix("data:").trimStart()
                                if (data.isNotEmpty()) data.append('\n')
                                data.append(payload)
                            }
                            line.startsWith("event:") || line.startsWith(":") -> Unit
                        }
                    }
                }
            } catch (e: IOException) {
                if (!call.isCanceled()) error = e.message ?: "网络错误"
            } catch (e: Exception) {
                error = e.message ?: "未知错误"
            } finally {
                onFinish(error)
            }
        }.apply { isDaemon = true }.start()

        return call
    }

    /** 用 GET /models 校验 Key（不产生费用），官方也是这么验的 */
    fun verifyKey(apiKey: String): String? {
        val request = Request.Builder()
            .url("$ORIGIN/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { r ->
                if (r.isSuccessful) null else parseError(r.body?.string().orEmpty()) ?: "HTTP ${r.code}"
            }
        } catch (e: Exception) {
            e.message ?: "网络错误"
        }
    }


    /**
     * 查账户余额：GET /user/balance
     * 返回 { is_available, balance_infos: [{ currency, total_balance, granted_balance, topped_up_balance }] }
     * 成功返回可读文案，失败返回 null（用 error 传错误信息）
     */
    fun balance(apiKey: String): Pair<String?, String?> {
        val request = Request.Builder()
            .url("$ORIGIN/user/balance")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) return null to (parseError(body) ?: "HTTP ${r.code}")
                val json = JSONObject(body)
                val infos = json.optJSONArray("balance_infos") ?: return null to "没有余额信息"
                if (infos.length() == 0) return null to "没有余额信息"
                val parts = (0 until infos.length()).mapNotNull { i ->
                    val o = infos.optJSONObject(i) ?: return@mapNotNull null
                    val cur = o.optString("currency")
                    val symbol = if (cur == "CNY") "¥" else "$"
                    val total = o.optString("total_balance")
                    "$symbol$total"
                }
                (parts.joinToString(" / ") + (if (json.optBoolean("is_available")) "" else "（不可用）")) to null
            }
        } catch (e: Exception) {
            null to (e.message ?: "网络错误")
        }
    }

    private fun parseError(text: String): String? {
        if (text.isBlank()) return null
        return runCatching {
            JSONObject(text).optJSONObject("error")?.optString("message")
        }.getOrNull() ?: text.take(300)
    }
}

package com.local.dsapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

const val MODEL_FLASH = "deepseek-flash"
const val MODEL_PRO = "deepseek-v4-pro"
const val SYSTEM_PROMPT = "该助手为DeepSeek，由深度求索公司创造。"

sealed class Block {
    data class Text(val text: String, val citations: List<Citation> = emptyList()) : Block()
    data class Thinking(val thinking: String, val signature: String = "") : Block()
    data class Tool(val kind: String, val query: String? = null, val url: String? = null) : Block()
}

data class Citation(val url: String, val title: String?, val citedText: String)

/** 附件：图片走 base64 直传，其它文件读成文本附在提问后面 */
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mime: String,
    /** 本地绝对路径（复制到应用私有目录，不依赖原文件权限） */
    val path: String,
    val isImage: Boolean,
    val size: Long = 0
)

data class Usage(
    val input: Int = 0,
    val cacheHit: Int = 0,
    val cacheMiss: Int = 0,
    val output: Int = 0
) {
    val totalIn: Int get() = input + cacheHit + cacheMiss
}

/**
 * 消息节点。官方网页版是树：每个节点有 parentId，父节点用 selectedChildId 指向当前选中的孩子，
 * 所以「重新生成」「修改问题」不是覆盖，而是**新增兄弟节点**，再用翻页器切换。
 */
data class Msg(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String = "",
    val blocks: List<Block> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val model: String? = null,
    val thinking: Boolean = false,
    val search: Boolean = false,
    val status: String = STATUS_DONE,
    val usage: Usage? = null,
    val error: String? = null,
    val thinkSeconds: Long = 0,
    val attachments: List<Attachment> = emptyList(),
    val parentId: String? = null,
    val selectedChildId: String? = null
) {
    val isUser: Boolean get() = role == "user"

    val plainText: String
        get() = blocks.filterIsInstance<Block.Text>().joinToString("") { it.text }

    val thinkingText: String
        get() = blocks.filterIsInstance<Block.Thinking>().joinToString("") { it.thinking }

    /** 列表摘要用的纯文本 */
    val preview: String
        get() = if (isUser) text else plainText

    companion object {
        const val STATUS_STREAMING = "streaming"
        const val STATUS_DONE = "done"
        const val STATUS_ERROR = "error"
        const val STATUS_STOPPED = "stopped"
    }
}

data class Session(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** 全部消息节点（树），渲染时按 selectedChildId 走出可见链 */
    val messages: List<Msg> = emptyList(),
    val rootMessageId: String? = null,
    val pinned: Boolean = false
) {
    private fun byId() = messages.associateBy { it.id }

    /** 从根沿 selectedChildId 走出的可见链 */
    fun visibleChain(): List<Msg> {
        val map = byId()
        val start = rootMessageId?.let { map[it] } ?: return emptyList()
        val out = ArrayList<Msg>()
        var cur: Msg? = start
        val guard = HashSet<String>()
        while (cur != null && guard.add(cur.id)) {
            out.add(cur)
            cur = cur.selectedChildId?.let { map[it] }
        }
        return out
    }

    /** 同一父节点下的兄弟（用于「1/2」翻页） */
    fun siblings(id: String): List<Msg> {
        val m = messages.firstOrNull { it.id == id } ?: return emptyList()
        return messages.filter { it.parentId == m.parentId && it.role == m.role }
            .sortedBy { it.createdAt }
    }

    /** 把 parent 的 selectedChildId 指到 target（parent 为 null 时改 root） */
    fun selectChild(parentId: String?, targetId: String): Session {
        return if (parentId == null) {
            copy(rootMessageId = targetId)
        } else {
            copy(messages = messages.map { if (it.id == parentId) it.copy(selectedChildId = targetId) else it })
        }
    }

    fun withMessage(msg: Msg): Session {
        val exists = messages.any { it.id == msg.id }
        return copy(
            messages = if (exists) messages.map { if (it.id == msg.id) msg else it } else messages + msg,
            updatedAt = System.currentTimeMillis()
        )
    }

    /** 某个节点之后的所有后代（修改问题时要丢掉旧的下游） */
    fun descendantsOf(id: String): Set<String> {
        val out = HashSet<String>()
        val children = messages.groupBy { it.parentId }
        fun walk(pid: String?) {
            children[pid]?.forEach {
                if (out.add(it.id)) walk(it.id)
            }
        }
        walk(id)
        return out
    }
}

/* ------------------------------------------------------------------ JSON */

fun Block.toJson(): JSONObject = JSONObject().apply {
    when (this@toJson) {
        is Block.Text -> {
            put("type", "text")
            put("text", text)
            if (citations.isNotEmpty()) {
                put("citations", JSONArray().apply {
                    citations.forEach {
                        put(JSONObject().apply {
                            put("url", it.url)
                            put("title", it.title ?: JSONObject.NULL)
                            put("citedText", it.citedText)
                        })
                    }
                })
            }
        }
        is Block.Thinking -> {
            put("type", "thinking")
            put("thinking", thinking)
            put("signature", signature)
        }
        is Block.Tool -> {
            put("type", "tool")
            put("kind", kind)
            put("query", query ?: JSONObject.NULL)
            put("url", url ?: JSONObject.NULL)
        }
    }
}

fun blockFromJson(o: JSONObject): Block? = when (o.optString("type")) {
    "text" -> Block.Text(
        text = o.optString("text"),
        citations = o.optJSONArray("citations")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val c = arr.optJSONObject(i) ?: return@mapNotNull null
                Citation(
                    url = c.optString("url"),
                    title = if (c.isNull("title")) null else c.optString("title"),
                    citedText = c.optString("citedText")
                )
            }
        } ?: emptyList()
    )
    "thinking" -> Block.Thinking(o.optString("thinking"), o.optString("signature"))
    "tool" -> Block.Tool(
        kind = o.optString("kind"),
        query = if (o.isNull("query")) null else o.optString("query"),
        url = if (o.isNull("url")) null else o.optString("url")
    )
    else -> null
}

fun Msg.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("role", role)
    put("text", text)
    put("createdAt", createdAt)
    put("model", model ?: JSONObject.NULL)
    put("thinking", thinking)
    put("search", search)
    put("status", status)
    put("error", error ?: JSONObject.NULL)
    put("thinkSeconds", thinkSeconds)
    if (attachments.isNotEmpty()) put("attachments", JSONArray().apply { attachments.forEach { a -> put(JSONObject().apply { put("id", a.id); put("name", a.name); put("mime", a.mime); put("path", a.path); put("isImage", a.isImage); put("size", a.size) }) } })
    put("parentId", parentId ?: JSONObject.NULL)
    put("selectedChildId", selectedChildId ?: JSONObject.NULL)
    put("blocks", JSONArray().apply { blocks.forEach { put(it.toJson()) } })
    usage?.let {
        put("usage", JSONObject().apply {
            put("input", it.input)
            put("cacheHit", it.cacheHit)
            put("cacheMiss", it.cacheMiss)
            put("output", it.output)
        })
    }
}

fun msgFromJson(o: JSONObject): Msg = Msg(
    id = o.optString("id"),
    role = o.optString("role"),
    text = o.optString("text"),
    blocks = o.optJSONArray("blocks")?.let { arr ->
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::blockFromJson) }
    } ?: emptyList(),
    createdAt = o.optLong("createdAt"),
    model = if (o.isNull("model")) null else o.optString("model"),
    thinking = o.optBoolean("thinking"),
    search = o.optBoolean("search"),
    status = o.optString("status", Msg.STATUS_DONE),
    error = if (o.isNull("error")) null else o.optString("error"),
    thinkSeconds = o.optLong("thinkSeconds"),
    attachments = o.optJSONArray("attachments")?.let { arr -> (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { a -> Attachment(id = a.optString("id"), name = a.optString("name"), mime = a.optString("mime"), path = a.optString("path"), isImage = a.optBoolean("isImage"), size = a.optLong("size")) } } } ?: emptyList(),
    parentId = if (o.isNull("parentId")) null else o.optString("parentId"),
    selectedChildId = if (o.isNull("selectedChildId")) null else o.optString("selectedChildId"),
    usage = o.optJSONObject("usage")?.let {
        Usage(
            input = it.optInt("input"),
            cacheHit = it.optInt("cacheHit"),
            cacheMiss = it.optInt("cacheMiss"),
            output = it.optInt("output")
        )
    }
)

fun Session.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
    put("rootMessageId", rootMessageId ?: JSONObject.NULL)
    put("pinned", pinned)
    put("messages", JSONArray().apply { messages.forEach { put(it.toJson()) } })
}

fun sessionFromJson(o: JSONObject): Session = Session(
    id = o.optString("id"),
    title = o.optString("title"),
    createdAt = o.optLong("createdAt"),
    updatedAt = o.optLong("updatedAt"),
    rootMessageId = if (o.isNull("rootMessageId")) null else o.optString("rootMessageId"),
    pinned = o.optBoolean("pinned"),
    messages = o.optJSONArray("messages")?.let { arr ->
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::msgFromJson) }
    } ?: emptyList()
).migrated()

/**
 * 兼容旧数据：树形结构上线前存的会话是线性列表（没有 parentId / rootMessageId），
 * 直接按 createdAt 串成一条链，否则 visibleChain() 会走出空列表、对话看起来像丢了。
 */
fun Session.migrated(): Session {
    if (rootMessageId != null || messages.isEmpty()) return this
    val sorted = messages.sortedBy { it.createdAt }
    val rebuilt = sorted.mapIndexed { i, m ->
        m.copy(
            parentId = if (i == 0) null else sorted[i - 1].id,
            selectedChildId = if (i < sorted.size - 1) sorted[i + 1].id else null
        )
    }
    return copy(messages = rebuilt, rootMessageId = rebuilt.first().id)
}

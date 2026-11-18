package com.local.dsapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 聊天记录导入导出，**格式对齐官方网页版**（chat-demo 的 history 模块，export_format_version = 3）：
 *
 *   deepseek_data-*.zip
 *   ├─ manifest.json                  {"export_format_version":3}
 *   ├─ chat_sessions/<session_id>.json  每个会话一个（含消息树、fragments）
 *   ├─ chat_session_index.json        [{id,title,pinned,message_count,inserted_at,updated_at}]
 *   └─ chat_sessions/<id>_files/…    图片（本地版没有附件，暂不产出）
 *
 * 导入**必须流式**：官方备份动辄几十 MB，单个会话可达 10MB+、几千条消息，
 * 把整个 zip 读进 ByteArray 再逐个解析会在手机上 OOM。
 * 这里直接用 ZipInputStream 逐条读，每条解析完就丢，峰值只有一个会话那么大。
 */
object Backup {

    const val FORMAT_VERSION = 3
    private const val MANIFEST = "manifest.json"
    private const val INDEX = "chat_session_index.json"

    private fun sessionPath(id: String) = "chat_sessions/$id.json"

    /** 导入结果：成功的会话 + 失败条数与第一条错误（不再静默跳过） */
    data class Result(
        val sessions: List<Session>,
        val failed: Int,
        val firstError: String?
    )

    /* ------------------------------------------------------------ 导出 */

    fun exportZip(sessions: List<Session>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write("""{"export_format_version":$FORMAT_VERSION}""".toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            sessions.forEach { s ->
                zip.putNextEntry(ZipEntry(sessionPath(s.id)))
                zip.write(sessionFileJson(s).toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            zip.putNextEntry(ZipEntry(INDEX))
            zip.write(indexJson(sessions).toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun indexJson(sessions: List<Session>): JSONArray = JSONArray().apply {
        sessions.forEach { s ->
            put(
                JSONObject()
                    .put("id", s.id)
                    .put("title", s.title.ifBlank { null } ?: JSONObject.NULL)
                    .put("pinned", s.pinned)
                    .put("message_count", s.messages.size)
                    .put("inserted_at", iso(s.createdAt))
                    .put("updated_at", iso(s.updatedAt))
            )
        }
    }

    /** 官方用数字 id，我们内部是 UUID：按「可见链优先」编号，保证 current_message_id 落在链尾 */
    private fun sessionFileJson(s: Session): JSONObject {
        val order = LinkedHashMap<String, Int>()
        s.visibleChain().forEach { order[it.id] = order.size + 1 }
        s.messages.forEach { if (!order.containsKey(it.id)) order[it.id] = order.size + 1 }

        val messages = JSONArray()
        s.messages.sortedBy { order[it.id] ?: Int.MAX_VALUE }.forEach { m ->
            val fragments = JSONArray()
            if (m.isUser) {
                fragments.put(JSONObject().put("type", "REQUEST").put("content", m.text))
            } else {
                if (m.thinkingText.isNotBlank()) {
                    fragments.put(
                        JSONObject()
                            .put("type", "THINK")
                            .put("content", m.thinkingText)
                            .put("elapsed_secs", if (m.thinkSeconds > 0) m.thinkSeconds else JSONObject.NULL)
                    )
                }
                m.blocks.forEach { b ->
                    when (b) {
                        is Block.Text -> if (b.text.isNotBlank()) {
                            fragments.put(JSONObject().put("type", "RESPONSE").put("content", b.text))
                        }
                        is Block.Tool -> fragments.put(
                            JSONObject().put("type", "TOOL_SEARCH").put(
                                "queries",
                                JSONArray().apply { b.query?.let { put(it) } }
                            ).put("results", JSONArray())
                        )
                        is Block.Thinking -> Unit
                    }
                }
            }

            messages.put(
                JSONObject()
                    .put("id", order[m.id])
                    .put("parent_id", m.parentId?.let { order[it] } ?: JSONObject.NULL)
                    .put("role", if (m.isUser) "USER" else "ASSISTANT")
                    .put("status", exportStatus(m.status))
                    .put("thinking_enabled", m.thinking)
                    .put("search_enabled", m.search)
                    .put("accumulated_token_usage", m.usage?.totalIn ?: 0)
                    .put("inserted_at", iso(m.createdAt))
                    .put("fragments", fragments)
            )
        }

        return JSONObject()
            .put("id", s.id)
            .put("title", s.title.ifBlank { null } ?: JSONObject.NULL)
            .put("pinned", s.pinned)
            .put("current_message_id", s.visibleChain().lastOrNull()?.let { order[it.id] } ?: 0)
            .put("inserted_at", iso(s.createdAt))
            .put("updated_at", iso(s.updatedAt))
            .put("messages", messages)
    }

    private fun exportStatus(status: String) = when (status) {
        Msg.STATUS_STREAMING -> "WIP"
        Msg.STATUS_ERROR -> "FAILED"
        Msg.STATUS_STOPPED -> "INCOMPLETE"
        else -> "FINISHED"
    }

    /* ------------------------------------------------------------ 导入 */

    /**
     * 导入 zip。**必须用 ZipFile（读中央目录）而不是 ZipInputStream**：
     * 官方导出器写的是流式 zip，条目用「数据描述符」+ STORED（不压缩），
     * Java 的 ZipInputStream 遇到这种组合会直接抛
     * `ZipException: only DEFLATED entries can have EXT descriptor`，
     * 一个条目都读不到（表现为「压缩包里没有找到会话」）。
     * ZipFile 走中央目录，对这种格式没问题。
     */
    fun importZipFile(file: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): Result {
        val sessions = ArrayList<Session>()
        var failed = 0
        var firstError: String? = null

        ZipFile(file).use { zip ->
            val names = java.util.Collections.list(zip.entries()).map { it.name }

            // 优先 chat_sessions/*.json；找不到就退一步，把所有非清单类 json 都试一遍
            var candidates = names.filter { isSessionEntry(it) }
            if (candidates.isEmpty()) {
                candidates = names.filter {
                    it.endsWith(".json") &&
                        !it.endsWith("manifest.json") &&
                        !it.endsWith("chat_session_index.json")
                }
            }
            if (candidates.isEmpty()) {
                throw IllegalStateException(
                    "压缩包里没有会话文件（共 " + names.size + " 个条目：" +
                        names.take(3).joinToString() + "…）"
                )
            }

            onProgress(0, candidates.size)

            candidates.forEachIndexed { index, name ->
                try {
                    val text = zip.getInputStream(zip.getEntry(name)).use { readText(it) }
                    val s = sessionFromArchive(JSONObject(text))
                    if (s != null) {
                        sessions.add(s)
                    } else {
                        failed++
                        if (firstError == null) {
                            firstError = "会话为空：" + name.substringAfterLast('/')
                        }
                    }
                } catch (t: Throwable) {
                    failed++
                    if (firstError == null) {
                        firstError = name.substringAfterLast('/') + " → " +
                            t.javaClass.simpleName + (t.message?.let { "：" + it } ?: "")
                    }
                }
                onProgress(index + 1, candidates.size)
            }
        }

        if (sessions.isEmpty()) {
            throw IllegalStateException(firstError ?: "压缩包里没有解析出任何会话")
        }
        return Result(sessions, failed, firstError)
    }

    private fun isSessionEntry(name: String): Boolean =
        name.startsWith("chat_sessions/") &&
            name.endsWith(".json") &&
            !name.removePrefix("chat_sessions/").contains('/')

    /** 逐字符读，避免「字节数组 + String」双份内存 */
    private fun readText(input: InputStream): String {
        val sb = StringBuilder(1 shl 20)
        val reader = InputStreamReader(input, Charsets.UTF_8)
        val buf = CharArray(16 * 1024)
        while (true) {
            val n = reader.read(buf)
            if (n <= 0) break
            sb.append(buf, 0, n)
        }
        return sb.toString()
    }

    /** 兼容早期纯 JSON 备份（没有 PK 头） */
    fun importLegacyJson(text: String): List<Session> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("sessions")
            ?: throw IllegalArgumentException("文件既不是 ZIP 备份，也不是本应用导出的 JSON")
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { sessionFromJson(it) }
        }
    }

    fun isZip(head: ByteArray): Boolean =
        head.size >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()

    private fun sessionFromArchive(o: JSONObject): Session? {
        val sid = o.optString("id").ifBlank { return null }
        val arr = o.optJSONArray("messages") ?: JSONArray()
        if (arr.length() == 0) return null

        val byNum = HashMap<Int, JSONObject>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { byNum[it.optInt("id")] = it }
        }

        // 官方用 current_message_id 表示「当前停留的消息」，从它往上走到根就是可见链
        var cur: Int? = o.optInt("current_message_id", -1).takeIf { it >= 0 && byNum.containsKey(it) }
            ?: byNum.keys.maxOrNull()
        val path = mutableListOf<Int>()
        val guard = HashSet<Int>()
        while (cur != null && guard.add(cur)) {
            path.add(cur)
            val m = byNum[cur] ?: break
            cur = if (m.isNull("parent_id")) null else m.optInt("parent_id").takeIf { byNum.containsKey(it) }
        }
        path.reverse()

        fun strId(n: Int) = "$sid-$n"
        val nextOf = HashMap<Int, Int>()
        for (i in 0 until path.size - 1) nextOf[path[i]] = path[i + 1]

        val msgs = ArrayList<Msg>(arr.length())
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val num = m.optInt("id")
            val role = m.optString("role")
            val frags = m.optJSONArray("fragments") ?: JSONArray()

            var text = ""
            var thinking = ""
            var elapsed = 0L
            val blocks = mutableListOf<Block>()
            val tools = mutableListOf<Block.Tool>()
            for (k in 0 until frags.length()) {
                val f = frags.optJSONObject(k) ?: continue
                when (f.optString("type")) {
                    "REQUEST" -> text += f.optString("content")
                    "RESPONSE" -> {
                        val content = f.optString("content")
                        if (content.isNotBlank()) {
                            blocks.add(Block.Text(content))
                            text += content
                        }
                    }
                    "THINK" -> {
                        thinking += f.optString("content")
                        if (!f.isNull("elapsed_secs")) elapsed = f.optLong("elapsed_secs")
                    }
                    "TOOL_SEARCH" -> {
                        val q = f.optJSONArray("queries")?.optString(0).orEmpty()
                        tools.add(Block.Tool(kind = "search", query = q.ifBlank { null }))
                    }
                }
            }

            val parentId = if (m.isNull("parent_id")) null else strId(m.optInt("parent_id"))
            val childId = nextOf[num]?.let { strId(it) }

            msgs.add(
                Msg(
                    id = strId(num),
                    role = if (role == "USER") "user" else "assistant",
                    text = if (role == "USER") text else "",
                    blocks = buildList {
                        if (role != "USER") {
                            if (thinking.isNotBlank()) add(Block.Thinking(thinking, ""))
                            addAll(tools)
                            addAll(blocks)
                        }
                    },
                    createdAt = parseIso(m.optString("inserted_at")),
                    thinking = m.optBoolean("thinking_enabled"),
                    search = m.optBoolean("search_enabled"),
                    status = importStatus(m.optString("status")),
                    thinkSeconds = elapsed,
                    parentId = parentId,
                    selectedChildId = childId
                )
            )
        }

        if (msgs.isEmpty()) return null
        val root = path.firstOrNull()?.let { strId(it) }
            ?: msgs.firstOrNull { it.parentId == null }?.id

        return Session(
            id = sid,
            title = if (o.isNull("title")) "" else o.optString("title"),
            createdAt = parseIso(o.optString("inserted_at")),
            updatedAt = parseIso(o.optString("updated_at")),
            messages = msgs,
            rootMessageId = root,
            pinned = o.optBoolean("pinned")
        ).migrated()
    }

    private fun importStatus(status: String) = when (status) {
        "WIP" -> Msg.STATUS_DONE
        "FAILED", "CONTENT_FILTER", "TIMEOUT" -> Msg.STATUS_ERROR
        "INCOMPLETE", "CONTEXT_LENGTH_EXCEEDED" -> Msg.STATUS_STOPPED
        else -> Msg.STATUS_DONE
    }

    private fun iso(ts: Long): String =
        if (ts <= 0) Instant.now().toString() else Instant.ofEpochMilli(ts).toString()

    private fun parseIso(s: String): Long =
        runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(s.replace(" ", "T")).toEpochMilli() }.getOrNull()
            ?: System.currentTimeMillis()
}

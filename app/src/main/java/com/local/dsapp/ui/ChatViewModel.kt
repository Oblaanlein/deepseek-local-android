package com.local.dsapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.local.dsapp.data.Attachment
import com.local.dsapp.data.Block
import com.local.dsapp.data.Citation
import com.local.dsapp.data.MODEL_FLASH
import com.local.dsapp.data.Msg
import com.local.dsapp.data.Session
import com.local.dsapp.data.Settings
import com.local.dsapp.data.Store
import com.local.dsapp.data.Usage
import com.local.dsapp.net.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class UiState(
    val sessions: List<Session> = emptyList(),
    val active: Session? = null,
    val input: String = "",
    val thinking: Boolean = false,
    val search: Boolean = false,
    val streaming: Boolean = false,
    val drawerOpen: Boolean = false,
    val settingsOpen: Boolean = false,
    val apiKey: String = "",
    val model: String = MODEL_FLASH,
    val autoCollapse: Boolean = true,
    /** 长用户消息自动折叠 */
    val autoCollapseLong: Boolean = true,
    /** 朗读音色 stella / tide */
    val voice: String = "stella",
    val showUsage: Boolean = true,
    val toast: String? = null,
    /** 正在「修改」的那条用户消息 id，非空时发送走分支逻辑 */
    val editingId: String? = null,
    /** 抽屉多选态 */
    val selecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    /** 附件面板是否展开 */
    val attachOpen: Boolean = false,
    /** 待发送的附件 */
    val pending: List<Attachment> = emptyList(),
    /** 正在导入聊天记录（弹进度框） */
    val importing: Boolean = false,
    /** 已解析 / 总数 */
    val importDone: Int = 0,
    val importTotal: Int = 0
) {
    val inChat: Boolean get() = active != null
    val chain: List<Msg> get() = active?.visibleChain().orEmpty()
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)
    private val settings = Settings(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var call: Call? = null

    init {
        _state.update {
            it.copy(
                sessions = store.sessions(),
                apiKey = settings.apiKey,
                model = settings.model,
                autoCollapse = settings.autoCollapseThinking,
                autoCollapseLong = settings.autoCollapseLong,
                voice = settings.voice,
                showUsage = settings.showUsage
            )
        }
    }

    /* ------------------------------------------------------------- 基础交互 */

    fun setInput(text: String) = _state.update { it.copy(input = text) }

    fun toggleThinking() = _state.update { it.copy(thinking = !it.thinking) }

    fun toggleSearch() = _state.update { it.copy(search = !it.search) }

    fun openDrawer(open: Boolean) = _state.update {
        it.copy(drawerOpen = open, selecting = if (open) it.selecting else false)
    }

    fun openSettings(open: Boolean) = _state.update { it.copy(settingsOpen = open) }

    fun toast(msg: String) = _state.update { it.copy(toast = msg) }

    fun clearToast() = _state.update { it.copy(toast = null) }

    fun newChat() = run { ReadAloud.stop(); _state.update {
        it.copy(active = null, drawerOpen = false, input = "", editingId = null, selecting = false)
    } }

    fun openSession(id: String) {
        ReadAloud.stop()
        val s = store.load(id) ?: return
        _state.update { it.copy(active = s, drawerOpen = false, editingId = null, selecting = false) }
    }

    /* ------------------------------------------------------------- 会话管理 */

    fun deleteSession(id: String) {
        store.delete(id)
        _state.update { st ->
            st.copy(
                sessions = store.sessions(),
                active = if (st.active?.id == id) null else st.active,
                selectedIds = st.selectedIds - id
            )
        }
    }

    fun renameSession(id: String, title: String) {
        val s = store.load(id) ?: return
        val next = s.copy(title = title, updatedAt = System.currentTimeMillis())
        persist(next)
        _state.update { st ->
            st.copy(
                sessions = withSession(st.sessions, next),
                active = if (st.active?.id == id) next else st.active
            )
        }
    }

    fun togglePin(id: String) {
        val s = store.load(id) ?: return
        val next = s.copy(pinned = !s.pinned)
        persist(next)
        _state.update { it.copy(sessions = withSession(it.sessions, next)) }
    }

    /* ------------------------------------------------------------- 多选 */

    fun startSelecting(id: String? = null) = _state.update {
        it.copy(selecting = true, selectedIds = if (id != null) setOf(id) else emptySet())
    }

    fun cancelSelecting() = _state.update { it.copy(selecting = false, selectedIds = emptySet()) }

    fun toggleSelected(id: String) = _state.update {
        val next = if (it.selectedIds.contains(id)) it.selectedIds - id else it.selectedIds + id
        it.copy(selectedIds = next)
    }

    fun deleteSelected() {
        val ids = _state.value.selectedIds
        ids.forEach { store.delete(it) }
        _state.update { st ->
            st.copy(
                sessions = st.sessions.filterNot { it.id in ids },
                active = if (st.active?.id in ids) null else st.active,
                selecting = false,
                selectedIds = emptySet()
            )
        }
    }

    fun pinSelected() {
        val ids = _state.value.selectedIds
        val pinned = _state.value.sessions.filter { it.id in ids }.map { it.copy(pinned = true) }
        pinned.forEach { persist(it) }
        _state.update { st ->
            val map = pinned.associateBy { it.id }
            st.copy(
                sessions = st.sessions.map { map[it.id] ?: it },
                selecting = false,
                selectedIds = emptySet()
            )
        }
    }

    /* ------------------------------------------------------------- 设置 */

    fun saveApiKey(key: String) {
        settings.apiKey = key
        _state.update { it.copy(apiKey = key) }
    }

    fun verifyKey(onResult: (String?) -> Unit) {
        val key = _state.value.apiKey
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) { Api.verifyKey(key) }
            onResult(err)
        }
    }

    fun setModel(model: String) {
        settings.model = model
        _state.update { it.copy(model = model) }
    }

    fun setAutoCollapseLong(v: Boolean) {
        settings.autoCollapseLong = v
        _state.update { it.copy(autoCollapseLong = v) }
    }

    fun setVoice(v: String) {
        settings.voice = v
        _state.update { it.copy(voice = v) }
    }

    fun setAutoCollapse(v: Boolean) {
        settings.autoCollapseThinking = v
        _state.update { it.copy(autoCollapse = v) }
    }

    fun setShowUsage(v: Boolean) {
        settings.showUsage = v
        _state.update { it.copy(showUsage = v) }
    }

    /* ------------------------------------------------------------- 导入导出 */

    /** 导出成官方格式的 zip（网页版可直接导入） */
    fun exportZip(): ByteArray = com.local.dsapp.data.Backup.exportZip(store.sessions())

    /**
     * 导入：支持官方 zip 备份，也兼容早期的纯 JSON。
     * 全程在 IO 线程 + 流式读取（官方备份可达几十 MB，单会话几千条消息），结果用 toast 回报。
     */
    fun importBackup(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            _state.update { it.copy(importing = true, importDone = 0, importTotal = 0) }
            val msg = withContext(Dispatchers.IO) {
                runCatching {
                    // ZipFile 需要文件路径，先把 SAF 的流落到缓存目录（官方备份是 STORED 未压缩，拷贝很快）
                    val tmp = File(ctx.cacheDir, "import-" + System.currentTimeMillis() + ".bin")
                    try {
                        ctx.contentResolver.openInputStream(uri)?.use { raw ->
                            tmp.outputStream().use { out -> raw.copyTo(out, 128 * 1024) }
                        } ?: throw IllegalStateException("打不开这个文件")

                        val head = ByteArray(2)
                        tmp.inputStream().use { it.read(head) }

                        val result = if (com.local.dsapp.data.Backup.isZip(head)) {
                            com.local.dsapp.data.Backup.importZipFile(tmp) { done, total ->
                                _state.update { it.copy(importDone = done, importTotal = total) }
                            }
                        } else {
                            com.local.dsapp.data.Backup.Result(
                                com.local.dsapp.data.Backup.importLegacyJson(
                                    tmp.readText(Charsets.UTF_8)
                                ), 0, null
                            )
                        }

                        result.sessions.forEach { store.save(it) }
                        _state.update { it.copy(sessions = store.sessions()) }

                        if (result.failed > 0) {
                            "已导入 ${result.sessions.size} 个对话，${result.failed} 个失败：${result.firstError}"
                        } else {
                            "已导入 ${result.sessions.size} 个对话"
                        }
                    } finally {
                        tmp.delete()
                    }
                }.getOrElse { "导入失败：${it.message ?: it.javaClass.simpleName}" }
            }
            _state.update { it.copy(importing = false, toast = msg) }
        }
    }

    /* ------------------------------------------------------------- 附件 */

    fun toggleAttach() = _state.update { it.copy(attachOpen = !it.attachOpen) }

    fun removeAttachment(id: String) =
        _state.update { it.copy(pending = it.pending.filterNot { a -> a.id == id }) }

    fun addAttachment(uri: Uri, name: String?, mime: String?, isImage: Boolean) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val att = withContext(Dispatchers.IO) {
                runCatching { saveAttachment(ctx, uri, name, mime, isImage) }.getOrNull()
            }
            if (att == null) {
                _state.update { it.copy(toast = "读取文件失败") }
            } else {
                _state.update { it.copy(pending = it.pending + att, attachOpen = true) }
            }
        }
    }

    /** 复制到应用私有目录；图片顺手压到最长边 1400px，避免 base64 太大 */
    private fun saveAttachment(
        ctx: android.content.Context,
        uri: Uri,
        name: String?,
        mime: String?,
        isImage: Boolean
    ): Attachment? {
        val resolver = ctx.contentResolver
        val realMime = mime ?: resolver.getType(uri) ?: "application/octet-stream"
        val img = isImage || realMime.startsWith("image/")
        val displayName = name ?: run {
            val cursor = resolver.query(uri, null, null, null, null)
            cursor?.use {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && it.moveToFirst()) it.getString(idx) else null
            }
        } ?: if (img) "image.jpg" else "file.txt"

        val dir = File(ctx.filesDir, "attachments").apply { mkdirs() }
        val dest = File(dir, UUID.randomUUID().toString() + if (img) ".jpg" else ".txt")

        if (img) {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val maxEdge = 1400
            val scale = minOf(1f, maxEdge.toFloat() / maxOf(bmp.width, bmp.height))
            val out = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * scale).toInt().coerceAtLeast(1),
                    (bmp.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else bmp
            dest.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        } else {
            resolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: return null
        }

        return Attachment(
            name = displayName,
            mime = if (img) "image/jpeg" else realMime,
            path = dest.absolutePath,
            isImage = img,
            size = dest.length()
        )
    }

    /* ------------------------------------------------------------- 分支 */

    /** 「修改」：把该问题填回输入框，发送时另开一个分支 */
    fun beginEdit(msgId: String) {
        val st = _state.value
        val msg = st.active?.messages?.firstOrNull { it.id == msgId } ?: return
        _state.update { it.copy(input = msg.text, editingId = msgId) }
    }

    fun cancelEdit() = _state.update { it.copy(editingId = null, input = "") }

    /** 翻页：在同父节点的兄弟之间切换 */
    /** 在内存里替换一条会话，避免每次操作都从 SharedPreferences 全量重读 + 解析（大会话要几百毫秒） */
    private fun withSession(list: List<Session>, s: Session): List<Session> =
        (list.filterNot { it.id == s.id } + s).sortedByDescending { it.updatedAt }

    /** 落盘放后台线程：大会话 toJson 很慢，不能占主线程 */
    private fun persist(s: Session) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { store.save(s) } }
    }

    fun switchBranch(msgId: String, delta: Int) {
        val st = _state.value
        val s = st.active ?: return
        val sibs = s.siblings(msgId)
        if (sibs.size < 2) return
        val idx = sibs.indexOfFirst { it.id == msgId }
        val target = sibs.getOrNull(idx + delta) ?: return
        val next = s.selectChild(target.parentId, target.id)
        // 关键：这里原来同步 store.save + store.sessions()，等于每次点击都把全部会话重新解析一遍
        persist(next)
        _state.update { it.copy(active = next, sessions = withSession(it.sessions, next)) }
    }

    /** 重新生成：为该问题的回答新增一个兄弟节点 */
    fun regenerate(assistantId: String) {
        val st = _state.value
        if (st.streaming) return
        val s = st.active ?: return
        val old = s.messages.firstOrNull { it.id == assistantId } ?: return
        if (old.isUser) return
        startStreaming(
            session = s,
            userMsgId = old.parentId,
            isNewAssistant = true
        )
    }

    /** 删除某一轮问答：把它和它的回答摘掉，后面的轮次接回上一轮（官方网页版的按轮删除） */
    fun deleteRound(userMsgId: String) {
        val s = _state.value.active ?: return
        val chain = s.visibleChain()
        val idx = chain.indexOfFirst { it.id == userMsgId }
        if (idx < 0) return
        val msg = chain[idx]
        val parentId = msg.parentId
        val doomed = s.descendantsOf(userMsgId) + userMsgId
        val nextMsg = chain.getOrNull(idx + 2)   // idx+1 是本轮回答，idx+2 是下一轮提问

        var msgs = s.messages.filterNot { it.id in doomed }
        msgs = msgs.map {
            when {
                nextMsg != null && it.id == nextMsg.id -> it.copy(parentId = parentId)
                it.selectedChildId in doomed -> it.copy(selectedChildId = null)
                else -> it
            }
        }

        var next = s.copy(messages = msgs)
        if (nextMsg != null) {
            next = if (parentId == null) next.copy(rootMessageId = nextMsg.id)
            else next.selectChild(parentId, nextMsg.id)
        }
        if (next.rootMessageId in doomed) next = next.copy(rootMessageId = null)
        next = next.copy(updatedAt = System.currentTimeMillis())

        persist(next)
        _state.update { it.copy(active = next, sessions = withSession(it.sessions, next)) }
    }

    fun stop() {
        call?.cancel()
        call = null
        _state.update { st ->
            val a = st.active ?: return@update st
            val msgs = a.messages.map {
                if (it.status == Msg.STATUS_STREAMING) it.copy(status = Msg.STATUS_STOPPED) else it
            }
            val next = a.copy(messages = msgs, updatedAt = System.currentTimeMillis())
            persist(next)
            st.copy(active = next, streaming = false, sessions = withSession(st.sessions, next))
        }
    }

    /* ------------------------------------------------------------- 发送 */

    fun send() {
        val st = _state.value
        val text = st.input.trim()
        if ((text.isEmpty() && st.pending.isEmpty()) || st.streaming) return
        if (st.apiKey.isBlank()) {
            _state.update { it.copy(toast = "先在设置里填 API Key", settingsOpen = true) }
            return
        }

        ReadAloud.stop()
        val now = System.currentTimeMillis()
        var session = st.active ?: Session(title = text.take(24), createdAt = now, updatedAt = now)
        val editingId = st.editingId

        val parentId: String?
        val userMsg: Msg

        if (editingId != null) {
            val old = session.messages.firstOrNull { it.id == editingId }
            parentId = old?.parentId
            userMsg = Msg(role = "user", text = text, createdAt = now, parentId = parentId, attachments = st.pending)
            session = session.withMessage(userMsg)
            session = session.selectChild(parentId, userMsg.id)
        } else {
            parentId = session.visibleChain().lastOrNull()?.id
            userMsg = Msg(role = "user", text = text, createdAt = now, parentId = parentId, attachments = st.pending)
            session = session.withMessage(userMsg)
            if (parentId == null) session = session.copy(rootMessageId = userMsg.id)
            else session = session.selectChild(parentId, userMsg.id)
        }

        if (session.title.isBlank()) session = session.copy(title = text.take(24))

        _state.update { it.copy(active = session, input = "", streaming = true, editingId = null, pending = emptyList(), attachOpen = false) }
        startStreaming(session, userMsg.id, isNewAssistant = true)
    }

    /** 统一的流式流程：userMsgId 为这条回答挂载的用户消息 */
    private fun startStreaming(session: Session, userMsgId: String?, isNewAssistant: Boolean) {
        val st = _state.value
        val now = System.currentTimeMillis()
        val assistant = Msg(
            role = "assistant",
            createdAt = now,
            model = st.model,
            thinking = st.thinking,
            search = st.search,
            status = Msg.STATUS_STREAMING,
            parentId = userMsgId
        )
        var s = session.withMessage(assistant)
        if (userMsgId != null) s = s.selectChild(userMsgId, assistant.id)
        else s = s.copy(rootMessageId = assistant.id)

        _state.update { it.copy(active = s, streaming = true) }

        // 请求上下文：可见链里到 userMsgId 为止
        val chain = s.visibleChain()
        val upTo = if (userMsgId == null) emptyList() else chain.takeWhile { it.id != assistant.id }
        val body = Api.buildBody(
            model = st.model,
            thinking = st.thinking,
            search = st.search,
            messages = upTo,
            readFile = { p -> File(p).takeIf { it.exists() }?.readBytes() }
        )

        val started = System.currentTimeMillis()
        var firstTextAt = 0L
        val blocks = mutableListOf<Block>()

        fun publish(status: String = Msg.STATUS_STREAMING, error: String? = null, usage: Usage? = null) {
            _state.update { cur ->
                val a = cur.active ?: return@update cur
                val old = a.messages.firstOrNull { it.id == assistant.id } ?: return@update cur
                val next = a.withMessage(
                    old.copy(
                        blocks = blocks.toList(),
                        status = status,
                        error = error ?: old.error,
                        usage = usage ?: old.usage
                    )
                )
                cur.copy(active = next)
            }
        }

        call = Api.stream(
            apiKey = st.apiKey,
            body = body,
            onEvent = { event ->
                when (event.optString("type")) {
                    "message_start" -> {
                        val u = event.optJSONObject("message")?.optJSONObject("usage")
                        if (u != null) publish(usage = u.toUsage())
                    }

                    "content_block_start" -> {
                        val cb = event.optJSONObject("content_block")
                        if (cb != null) {
                            val input = cb.optJSONObject("input")
                            when (cb.optString("type")) {
                                "text" -> blocks.add(Block.Text(cb.optString("text"), readCitations(cb)))
                                "thinking" -> blocks.add(
                                    Block.Thinking(cb.optString("thinking"), cb.optString("signature"))
                                )
                                "server_tool_use" -> blocks.add(
                                    Block.Tool(
                                        kind = cb.optString("kind"),
                                        query = input?.optJSONArray("queries")?.optString(0)
                                            ?: input?.optString("query"),
                                        url = input?.optString("url")
                                    )
                                )
                            }
                            publish()
                        }
                    }

                    "content_block_delta" -> {
                        val idx = event.optInt("index", -1)
                        val d = event.optJSONObject("delta")
                        if (d != null && idx >= 0 && idx < blocks.size) {
                            val b = blocks[idx]
                            when (d.optString("type")) {
                                "text_delta" -> if (b is Block.Text) {
                                    if (firstTextAt == 0L) firstTextAt = System.currentTimeMillis()
                                    blocks[idx] = b.copy(text = b.text + d.optString("text"))
                                }

                                "thinking_delta" -> if (b is Block.Thinking) {
                                    blocks[idx] = b.copy(thinking = b.thinking + d.optString("thinking"))
                                }

                                "signature_delta" -> if (b is Block.Thinking) {
                                    blocks[idx] = b.copy(signature = d.optString("signature"))
                                }

                                "citations_delta" -> if (b is Block.Text) {
                                    val c = d.optJSONObject("citation")
                                    if (c != null) {
                                        blocks[idx] = b.copy(
                                            citations = b.citations + Citation(
                                                url = c.optString("url"),
                                                title = if (c.isNull("title")) null else c.optString("title"),
                                                citedText = c.optString("cited_text")
                                            )
                                        )
                                    }
                                }
                            }
                            publish()
                        }
                    }

                    "message_delta" -> event.optJSONObject("usage")?.let { publish(usage = it.toUsage()) }
                }
            },
            onFinish = { error ->
                val elapsed =
                    ((firstTextAt.takeIf { it > 0 } ?: System.currentTimeMillis()) - started) / 1000
                val cancelled = call?.isCanceled() ?: false
                val failed = error != null && !cancelled
                viewModelScope.launch(Dispatchers.Main) {
                    publish(
                        status = if (failed) Msg.STATUS_ERROR else Msg.STATUS_DONE,
                        error = if (failed) error else null
                    )
                    _state.update { cur ->
                        val a = cur.active
                        if (a == null) {
                            cur.copy(streaming = false)
                        } else {
                            val m = a.messages.firstOrNull { it.id == assistant.id }
                            val next = if (m != null) {
                                a.withMessage(
                                    m.copy(thinkSeconds = if (m.thinkingText.isNotBlank()) elapsed else 0)
                                )
                            } else a
                            persist(next)
                            cur.copy(active = next, streaming = false, sessions = withSession(cur.sessions, next))
                        }
                    }
                    call = null
                }
            }
        )
    }

    private fun JSONObject.toUsage() = Usage(
        input = optInt("input_tokens"),
        cacheHit = optInt("cache_read_input_tokens"),
        cacheMiss = optInt("cache_creation_input_tokens"),
        output = optInt("output_tokens")
    )

    private fun readCitations(o: JSONObject): List<Citation> {
        val arr = o.optJSONArray("citations") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val c = arr.optJSONObject(i) ?: return@mapNotNull null
            Citation(
                url = c.optString("url"),
                title = if (c.isNull("title")) null else c.optString("title"),
                citedText = c.optString("cited_text")
            )
        }
    }
}

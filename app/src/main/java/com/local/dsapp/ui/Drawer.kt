package com.local.dsapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.dsapp.data.Msg
import com.local.dsapp.data.Session
import java.util.Calendar

@Composable
fun HistoryDrawer(
    visible: Boolean,
    sessions: List<Session>,
    activeId: String?,
    selecting: Boolean,
    selectedIds: Set<String>,
    onClose: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onPin: (String) -> Unit,
    onStartSelecting: (String) -> Unit,
    onToggleSelected: (String) -> Unit,
    onCancelSelecting: () -> Unit,
    onDeleteSelected: () -> Unit,
    onPinSelected: () -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit
) {
    val c = LocalDsColors.current
    var query by remember { mutableStateOf("") }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x33000000))
                    .clickable(onClick = onClose)
            )
            Column(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.82f)
                    .background(c.bg)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .clickable(enabled = false) {}
            ) {
                if (selecting) {
                    SelectingHeader(selectedIds.size, onCancelSelecting)
                } else {
                    SearchField(query, { query = it })
                }

                Box(Modifier.weight(1f)) {
                    if (selecting) {
                        SelectList(sessions, selectedIds, onToggleSelected)
                    } else if (query.isNotBlank()) {
                        val hits = search(sessions, query)
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (hits.isEmpty()) {
                                item {
                                    Text(
                                        "没有匹配的对话内容",
                                        style = DsType.body.copy(color = c.textTertiary, fontSize = 14.sp),
                                        modifier = Modifier.padding(20.dp)
                                    )
                                }
                            }
                            items(hits, key = { it.session.id + it.msgId }) { hit ->
                                HitRow(hit) { onOpen(hit.session.id) }
                            }
                        }
                    } else {
                        SessionList(sessions, activeId, onOpen, onDelete, onRename, onPin, onStartSelecting)
                    }
                }

                if (selecting) {
                    SelectBar(onPinSelected, onDeleteSelected)
                } else {
                    // 底部：齿轮 + 设置（本地版没有账号体系）
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSettings)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DsIcon(IconKind.Gear, size = 20.dp, tint = c.text, stroke = 1.6f)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "设置",
                            style = DsType.body.copy(color = c.text, fontSize = 15.sp),
                            modifier = Modifier.weight(1f)
                        )
                        DsIcon(IconKind.ChevronRight, size = 15.dp, tint = c.textTertiary)
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ 搜索 */

private data class Hit(
    val session: Session,
    val msgId: String,
    val title: String,
    val snippet: String,
    val time: Long,
    val branch: String?
)

private fun search(sessions: List<Session>, q: String): List<Hit> {
    val out = mutableListOf<Hit>()
    sessions.forEach { s ->
        var added = false
        // 标题命中优先
        if (s.title.contains(q, ignoreCase = true)) {
            val first = s.visibleChain().firstOrNull { it.preview.isNotBlank() }
            out.add(
                Hit(
                    session = s,
                    msgId = "title",
                    title = s.title.ifBlank { "新对话" },
                    snippet = first?.preview?.replace("\n", " ")?.take(60) ?: "",
                    time = s.updatedAt,
                    branch = null
                )
            )
            added = true
        }
        if (!added) {
            // 全文命中：遍历所有节点（含未选中的分支）
            s.messages.firstOrNull { it.preview.contains(q, ignoreCase = true) }?.let { m ->
                val sibs = s.siblings(m.id)
                val idx = sibs.indexOfFirst { it.id == m.id }
                out.add(
                    Hit(
                        session = s,
                        msgId = m.id,
                        title = s.title.ifBlank { "新对话" },
                        snippet = snippetOf(m.preview, q),
                        time = s.updatedAt,
                        branch = if (sibs.size > 1) "${idx + 1}/${sibs.size}" else null
                    )
                )
            }
        }
    }
    return out.sortedByDescending { it.time }
}

private fun snippetOf(text: String, q: String, radius: Int = 26): String {
    val flat = text.replace("\n", " ")
    val i = flat.indexOf(q, ignoreCase = true)
    if (i < 0) return flat.take(60)
    val start = (i - radius).coerceAtLeast(0)
    val end = (i + q.length + radius).coerceAtMost(flat.length)
    return buildString {
        if (start > 0) append("…")
        append(flat.substring(start, end))
        if (end < flat.length) append("…")
    }
}

@Composable
private fun HitRow(hit: Hit, onClick: () -> Unit) {
    val c = LocalDsColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        ConvIcon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    hit.title,
                    style = DsType.body.copy(color = c.text, fontSize = 15.5.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTime(hit.time),
                    style = DsType.label.copy(color = c.textTertiary, fontSize = 12.5.sp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hit.branch != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .border(1.dp, c.pillBorder, RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "‹ ${hit.branch} ›",
                            style = DsType.label.copy(color = c.textSecondary, fontSize = 11.sp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    hit.snippet,
                    style = DsType.label.copy(color = c.textSecondary, fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 搜索结果左边那个「对话」小图标 */
@Composable
private fun ConvIcon() {
    val c = LocalDsColors.current
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(c.surfaceAlt),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(19.dp)) {
            val w = size.width
            val st = Stroke(width = w * 0.085f)
            drawRoundRect(
                c.textSecondary,
                topLeft = Offset(w * 0.06f, w * 0.06f),
                size = Size(w * 0.88f, w * 0.78f),
                cornerRadius = CornerRadius(w * 0.2f),
                style = st
            )
            val lw = w * 0.085f
            drawLine(
                c.textSecondary,
                Offset(w * 0.26f, w * 0.32f),
                Offset(w * 0.74f, w * 0.32f),
                lw
            )
            drawLine(
                c.textSecondary,
                Offset(w * 0.26f, w * 0.52f),
                Offset(w * 0.6f, w * 0.52f),
                lw
            )
        }
    }
}

/* ------------------------------------------------------------------ 列表 */

@Composable
private fun SessionList(
    sessions: List<Session>,
    activeId: String?,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onPin: (String) -> Unit,
    onStartSelecting: (String) -> Unit
) {
    val c = LocalDsColors.current
    val pinned = sessions.filter { it.pinned }.sortedByDescending { it.updatedAt }
    val others = sessions.filter { !it.pinned }

    LazyColumn(Modifier.fillMaxSize()) {
        if (pinned.isNotEmpty()) {
            item(key = "h_pin") {
                Text(
                    "置顶",
                    style = DsType.label.copy(color = c.textSecondary, fontSize = 12.5.sp),
                    modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 6.dp)
                )
            }
            items(pinned, key = { "p_" + it.id }) { s ->
                SessionRow(
                    s, activeId == s.id, false,
                    onOpen = { onOpen(s.id) },
                    onDelete = { onDelete(s.id) },
                    onRename = { onRename(s.id, it) },
                    onPin = { onPin(s.id) },
                    onSelect = { onStartSelecting(s.id) }
                )
            }
            item(key = "divider") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .height(1.dp)
                        .background(c.divider)
                )
            }
        }

        groupByTime(others).forEach { (label, list) ->
            item(key = "h_$label") {
                Text(
                    label,
                    style = DsType.label.copy(color = c.textSecondary, fontSize = 12.5.sp),
                    modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 6.dp)
                )
            }
            items(list, key = { it.id }) { s ->
                SessionRow(
                    s, activeId == s.id, false,
                    onOpen = { onOpen(s.id) },
                    onDelete = { onDelete(s.id) },
                    onRename = { onRename(s.id, it) },
                    onPin = { onPin(s.id) },
                    onSelect = { onStartSelecting(s.id) }
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SessionRow(
    session: Session,
    active: Boolean,
    selecting: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onPin: () -> Unit,
    onSelect: () -> Unit
) {
    val c = LocalDsColors.current
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 1.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (active) c.pillOnBg else Color.Transparent)
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { menu = true }
                )
                .padding(horizontal = 10.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                session.title.ifBlank { "新对话" },
                style = DsType.body.copy(
                    color = if (active) c.accent else c.text,
                    fontSize = 15.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (session.pinned) {
                DsIcon(IconKind.Pin, size = 14.dp, tint = c.textTertiary, stroke = 1.5f)
                Spacer(Modifier.width(6.dp))
            }
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable { menu = true }
                    .padding(6.dp)
            ) {
                DsIcon(IconKind.More, size = 16.dp, tint = c.textSecondary, stroke = 1.5f)
            }
        }

        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            containerColor = c.surface,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 10.dp,
                tonalElevation = 0.dp
        ) {
            DItem("重命名", IconKind.Pencil, c.text) { menu = false; renaming = true }
            DItem(
                if (session.pinned) "取消置顶" else "置顶",
                IconKind.Pin,
                c.text
            ) { menu = false; onPin() }
            DItem("多选", IconKind.Sliders, c.text) { menu = false; onSelect() }
            DItem("删除", IconKind.Trash, c.danger) { menu = false; onDelete() }
        }
    }

    if (renaming) {
        RenameDialog(
            initial = session.title,
            onDismiss = { renaming = false },
            onConfirm = {
                renaming = false
                onRename(it)
            }
        )
    }
}

@Composable
private fun DItem(label: String, icon: IconKind, tint: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = DsType.body.copy(color = tint, fontSize = 15.sp)) },
        leadingIcon = { DsIcon(icon, size = 19.dp, tint = tint, stroke = 1.6f) },
        onClick = onClick
    )
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val c = LocalDsColors.current
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名", style = DsType.title.copy(color = c.text)) },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surfaceAlt)
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = DsType.body.copy(color = c.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim().ifBlank { initial }) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        containerColor = c.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

/* ------------------------------------------------------------------ 多选 */

@Composable
private fun SelectingHeader(count: Int, onCancel: () -> Unit) {
    val c = LocalDsColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "已选择 $count 个对话",
            style = DsType.title.copy(color = c.text, fontSize = 17.sp),
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(c.surfaceAlt)
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center
        ) {
            DsIcon(IconKind.Close, size = 15.dp, tint = c.text, stroke = 1.8f)
        }
    }
}

@Composable
private fun SelectList(
    sessions: List<Session>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit
) {
    val c = LocalDsColors.current
    val pinned = sessions.filter { it.pinned }
    val others = sessions.filter { !it.pinned }

    LazyColumn(Modifier.fillMaxSize()) {
        if (pinned.isNotEmpty()) {
            item { SectionTitle("置顶") }
            items(pinned, key = { "p_" + it.id }) { s ->
                SelectRow(s, selectedIds.contains(s.id)) { onToggle(s.id) }
            }
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .height(1.dp)
                        .background(c.divider)
                )
            }
        }
        groupByTime(others).forEach { (label, list) ->
            item { SectionTitle(label) }
            items(list, key = { it.id }) { s ->
                SelectRow(s, selectedIds.contains(s.id)) { onToggle(s.id) }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val c = LocalDsColors.current
    Text(
        text,
        style = DsType.label.copy(color = c.textSecondary, fontSize = 12.5.sp),
        modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun SelectRow(session: Session, checked: Boolean, onClick: () -> Unit) {
    val c = LocalDsColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(21.dp)
                .clip(CircleShape)
                .background(if (checked) c.accent else Color.Transparent)
                .border(
                    1.4.dp,
                    if (checked) c.accent else c.pillBorder,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) DsIcon(IconKind.Check, size = 13.dp, tint = Color.White, stroke = 2.2f)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            session.title.ifBlank { "新对话" },
            style = DsType.body.copy(color = c.text, fontSize = 15.5.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SelectBar(onPin: () -> Unit, onDelete: () -> Unit) {
    val c = LocalDsColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(c.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onPin),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DsIcon(IconKind.Pin, size = 19.dp, tint = c.text, stroke = 1.6f)
            Spacer(Modifier.width(8.dp))
            Text("置顶", style = DsType.body.copy(color = c.text, fontSize = 15.sp))
        }
        Box(
            Modifier
                .width(1.dp)
                .height(22.dp)
                .background(c.divider)
        )
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onDelete),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DsIcon(IconKind.Trash, size = 19.dp, tint = c.danger, stroke = 1.6f)
            Spacer(Modifier.width(8.dp))
            Text("删除", style = DsType.body.copy(color = c.danger, fontSize = 15.sp))
        }
    }
}

/* ------------------------------------------------------------------ 其它 */

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val c = LocalDsColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.surfaceAlt)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DsIcon(IconKind.Search, size = 17.dp, tint = c.textSecondary, stroke = 1.7f)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "搜索对话内容...",
                    style = DsType.body.copy(color = c.textTertiary, fontSize = 15.sp)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = DsType.body.copy(color = c.text, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(c.pillBorder)
                    .clickable { onQuery("") },
                contentAlignment = Alignment.Center
            ) {
                DsIcon(IconKind.Close, size = 11.dp, tint = c.surface, stroke = 2.4f)
            }
        }
    }
}

private fun formatTime(ts: Long): String {
    val now = Calendar.getInstance()
    val t = Calendar.getInstance().apply { timeInMillis = ts }
    val sameDay = now.get(Calendar.YEAR) == t.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) {
        String.format("%02d:%02d", t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE))
    } else {
        "${t.get(Calendar.MONTH) + 1}月${t.get(Calendar.DAY_OF_MONTH)}日"
    }
}

private fun groupByTime(sessions: List<Session>): List<Pair<String, List<Session>>> {
    val now = Calendar.getInstance()
    val startOfToday = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val startOfYesterday = startOfToday - 86400_000L
    val startOf7 = startOfToday - 6 * 86400_000L
    val startOf30 = startOfToday - 29 * 86400_000L

    val buckets = linkedMapOf<String, MutableList<Session>>()
    sessions.sortedByDescending { it.updatedAt }.forEach { s ->
        val label = when {
            s.updatedAt >= startOfToday -> "今天"
            s.updatedAt >= startOfYesterday -> "昨天"
            s.updatedAt >= startOf7 -> "7 天内"
            s.updatedAt >= startOf30 -> "30 天内"
            else -> "更早"
        }
        buckets.getOrPut(label) { mutableListOf() }.add(s)
    }
    return buckets.map { it.key to it.value.toList() }
}

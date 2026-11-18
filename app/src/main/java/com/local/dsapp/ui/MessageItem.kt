package com.local.dsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.dsapp.data.Block
import com.local.dsapp.data.Msg
import kotlinx.coroutines.delay

@Composable
fun MessageItem(
    msg: Msg,
    siblings: List<Msg>,
    autoCollapseThinking: Boolean,
    showUsage: Boolean,
    streaming: Boolean,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onSwitchBranch: (Int) -> Unit,
    onToast: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    apiKey: String,
    voice: String
) {
    if (msg.isUser) {
        UserMessage(msg, siblings, onSwitchBranch, onEdit, onToast)
    } else {
        AssistantMessage(
            msg, siblings, autoCollapseThinking, showUsage, streaming,
            onRegenerate, onSwitchBranch, onToast, onOpenUrl, apiKey, voice
        )
    }
}

/* ------------------------------------------------------------------ 通用 */

@Composable
private fun PagerArrow(left: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalDsColors.current
    Box(
        Modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(2.dp)
    ) {
        DsIcon(
            if (left) IconKind.ChevronLeft else IconKind.ChevronRight,
            size = 15.dp,
            tint = if (enabled) c.textSecondary else c.divider,
            stroke = 1.8f
        )
    }
}

/** 复制按钮：点一下变对勾 + toast，1.6 秒后复原 */
@Composable
private fun CopyAction(text: String, onToast: (String) -> Unit) {
    val c = LocalDsColors.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                clipboard.setText(AnnotatedString(text))
                copied = true
                onToast("已复制")
            }
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) {
        DsIcon(
            if (copied) IconKind.Check else IconKind.Copy,
            size = 18.dp,
            tint = if (copied) c.accent else c.textTertiary,
            stroke = 1.6f
        )
    }
}

/* ------------------------------------------------------------------ 用户消息 */

@Composable
private fun UserMessage(
    msg: Msg,
    siblings: List<Msg>,
    onSwitchBranch: (Int) -> Unit,
    onEdit: () -> Unit,
    onToast: (String) -> Unit
) {
    val c = LocalDsColors.current
    var menu by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.End
    ) {
        Box {
            Box(
                Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.userBubble)
                    .then(
                        if (selecting) Modifier
                        else Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { menu = true }
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    // 附件缩略图（图片直接显示，其它文件显示文件名）
                    if (msg.attachments.isNotEmpty()) {
                        Row(
                            Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = if (msg.text.isBlank()) 0.dp else 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            msg.attachments.forEach { a ->
                                if (a.isImage) {
                                    val bmp = remember(a.path) {
                                        android.graphics.BitmapFactory.decodeFile(a.path)
                                    }
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = a.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(120.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                    }
                                } else {
                                    Row(
                                        Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0x14000000))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        DsIcon(
                                            IconKind.File,
                                            size = 15.dp,
                                            tint = c.textSecondary,
                                            stroke = 1.5f
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            a.name,
                                            style = DsType.label.copy(
                                                color = c.textSecondary,
                                                fontSize = 11.5.sp
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (msg.text.isNotBlank()) {
                        if (selecting) {
                            SelectionContainer {
                                Text(msg.text, style = DsType.body.copy(color = c.text))
                            }
                        } else {
                            Text(msg.text, style = DsType.body.copy(color = c.text))
                        }
                    }
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
                MenuItem("复制", IconKind.Copy) {
                    menu = false
                    clipboard.setText(AnnotatedString(msg.text))
                    onToast("已复制")
                }
                MenuItem("选择文本", IconKind.SelectText) {
                    menu = false
                    selecting = true
                }
                MenuItem("修改", IconKind.Pencil) {
                    menu = false
                    onEdit()
                }
            }
        }

        if (selecting) {
            Text(
                "拖动手柄选择，完成后点这里",
                style = DsType.label.copy(color = c.accent, fontSize = 12.5.sp),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable { selecting = false }
                    .padding(4.dp)
            )
        }

        if (siblings.size > 1) {
            val idx = siblings.indexOfFirst { it.id == msg.id }
            Row(
                Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PagerArrow(left = true, enabled = idx > 0) { onSwitchBranch(-1) }
                Text(
                    "${idx + 1}/${siblings.size}",
                    style = DsType.label.copy(color = c.textSecondary, fontSize = 13.sp),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                PagerArrow(left = false, enabled = idx < siblings.size - 1) { onSwitchBranch(1) }
            }
        }
    }
}

/* ------------------------------------------------------------------ 助手消息 */

@Composable
private fun AssistantMessage(
    msg: Msg,
    siblings: List<Msg>,
    autoCollapseThinking: Boolean,
    showUsage: Boolean,
    streaming: Boolean,
    onRegenerate: () -> Unit,
    onSwitchBranch: (Int) -> Unit,
    onToast: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    apiKey: String,
    voice: String
) {
    val c = LocalDsColors.current
    val thinking = msg.thinkingText
    val hasText = msg.plainText.isNotBlank()
    val isThisStreaming = streaming && msg.status == Msg.STATUS_STREAMING

    var expanded by remember(msg.id) {
        mutableStateOf(!(autoCollapseThinking && msg.status != Msg.STATUS_STREAMING))
    }
    var menu by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    val readStatus = ReadAloud.statusOf(msg.id)
    val clipboard = LocalClipboardManager.current

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp)
            // 长按挂在整条消息上：内层内容 Column 的命中区随文本宽度浮动，容易漏
            .then(
                if (selecting || isThisStreaming) Modifier
                else Modifier.combinedClickable(onClick = {}, onLongClick = { menu = true })
            )
    ) {
        if (thinking.isNotBlank()) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isThisStreaming && !hasText) "正在思考" else "已思考（用时 ${msg.thinkSeconds.coerceAtLeast(1)} 秒）",
                    style = DsType.label.copy(color = c.textSecondary, fontSize = 14.sp)
                )
                Spacer(Modifier.width(4.dp))
                DsIcon(
                    if (expanded) IconKind.ChevronDown else IconKind.ChevronRight,
                    size = 15.dp,
                    tint = c.textSecondary,
                    stroke = 1.6f
                )
            }

            if (expanded) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(top = 4.dp, bottom = 8.dp)
                ) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(c.divider, RoundedCornerShape(1.dp))
                    )
                    Row(Modifier.padding(start = 10.dp)) {
                        Text("•", style = DsType.thinking.copy(color = c.textTertiary))
                        Text(
                            thinking,
                            style = DsType.thinking.copy(color = c.textSecondary),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(6.dp))
            }
        }

        Box {
            Column {
                msg.blocks.forEach { b ->
                    when (b) {
                        is Block.Tool -> ToolRow(b)
                        is Block.Text -> if (b.text.isNotBlank()) {
                            if (selecting) {
                                SelectionContainer { Markdown(b.text, c) }
                            } else {
                                Markdown(b.text, c)
                            }
                        }
                        is Block.Thinking -> Unit
                    }
                }

                if (isThisStreaming && !hasText && thinking.isBlank()) {
                    Text("正在生成…", style = DsType.body.copy(color = c.textSecondary))
                }

                msg.error?.let {
                    Text(
                        it,
                        style = DsType.label.copy(color = c.danger, fontSize = 13.5.sp),
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
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
                MenuItem("复制", IconKind.Copy) {
                    menu = false
                    clipboard.setText(AnnotatedString(msg.plainText))
                    onToast("已复制")
                }
                MenuItem("选择文本", IconKind.SelectText) {
                    menu = false
                    selecting = true
                }
                MenuItem("重新生成", IconKind.Refresh) {
                    menu = false
                    onRegenerate()
                }
                MenuItem("朗读", IconKind.Speaker) {
                    menu = false
                    ReadAloud.toggle(apiKey, msg.id, msg.plainText, voice) { onToast(it) }
                }
            }
        }

        if (selecting) {
            Text(
                "拖动手柄选择，完成后点这里",
                style = DsType.label.copy(color = c.accent, fontSize = 12.5.sp),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable { selecting = false }
                    .padding(4.dp)
            )
        }

        if (!isThisStreaming) {
            val citations = msg.blocks.filterIsInstance<Block.Text>().flatMap { it.citations }
            if (citations.isNotEmpty()) Citations(citations, onOpenUrl)
            ActionRow(
                msg = msg,
                siblings = siblings,
                showUsage = showUsage,
                onSwitchBranch = onSwitchBranch,
                onRegenerate = onRegenerate,
                onToggleSpeak = {
                    ReadAloud.toggle(apiKey, msg.id, msg.plainText, voice) { onToast(it) }
                },
                onToast = onToast
            )
        }
    }
}

@Composable
private fun ActionRow(
    msg: Msg,
    siblings: List<Msg>,
    showUsage: Boolean,
    onSwitchBranch: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onToggleSpeak: () -> Unit,
    onToast: (String) -> Unit
) {
    val c = LocalDsColors.current

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (siblings.size > 1) {
            val idx = siblings.indexOfFirst { it.id == msg.id }
            PagerArrow(left = true, enabled = idx > 0) { onSwitchBranch(-1) }
            Text(
                "${idx + 1}/${siblings.size}",
                style = DsType.label.copy(color = c.textSecondary, fontSize = 13.sp),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            PagerArrow(left = false, enabled = idx < siblings.size - 1) { onSwitchBranch(1) }
            Spacer(Modifier.width(6.dp))
        }

        CopyAction(msg.plainText, onToast)

        // 朗读：空闲是播放三角，加载/播放中是四根跳动竖条（同官方）
        val readStatus = ReadAloud.statusOf(msg.id)
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleSpeak)
                .padding(horizontal = 9.dp, vertical = 7.dp)
        ) {
            if (readStatus == ReadAloud.Status.Idle) {
                DsIcon(IconKind.Speaker, size = 18.dp, tint = c.textTertiary, stroke = 1.6f)
            } else {
                ReadingBars(playing = readStatus == ReadAloud.Status.Playing, tint = c.accent)
            }
        }

        Spacer(Modifier.weight(1f))

        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRegenerate)
                .padding(horizontal = 9.dp, vertical = 7.dp)
        ) {
            DsIcon(IconKind.Refresh, size = 18.dp, tint = c.textTertiary, stroke = 1.6f)
        }
    }

    if (showUsage) {
        val detail = com.local.dsapp.data.Pricing.detail(msg)
        var openDetail by remember { mutableStateOf(false) }
        val text = if (detail == null) {
            msg.model.orEmpty()
        } else {
            "输入 ${detail.total - detail.out} · 输出 ${detail.out} tokens · " +
                com.local.dsapp.data.Pricing.format(detail.cny)
        }
        if (text.isNotBlank()) {
            Text(
                text,
                style = DsType.label.copy(color = c.textTertiary, fontSize = 11.5.sp),
                modifier = Modifier
                    .padding(top = 2.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = detail != null) { openDetail = true }
                    .padding(vertical = 2.dp)
            )
        }
        if (openDetail && detail != null) {
            UsageDialog(detail) { openDetail = false }
        }
    }
}

/** 点用量行弹出的明细，内容和网页版悬停提示一致 */
@Composable
private fun UsageDialog(
    d: com.local.dsapp.data.Pricing.Detail,
    onDismiss: () -> Unit
) {
    val c = LocalDsColors.current
    val fx = com.local.dsapp.data.Pricing.USD_TO_CNY

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                "本次调用（${if (d.peak) "高峰价" else "低谷价"}）",
                style = DsType.title.copy(color = c.text, fontSize = 16.sp)
            )
        },
        text = {
            Column {
                UsageLine("输入（缓存命中）", "${d.hit} tokens", "$" + d.hitPrice + "/M")
                UsageLine("输入（缓存未命中）", "${d.miss} tokens", "$" + d.missPrice + "/M")
                UsageLine("输出", "${d.out} tokens", "$" + d.outPrice + "/M")
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Spacer(Modifier.height(10.dp))
                Text(
                    "合计 ${d.total} tokens ≈ ${com.local.dsapp.data.Pricing.format(d.cny)}" +
                        "（\$${"%.5f".format(d.usd)} × $fx）",
                    style = DsType.body.copy(color = c.text, fontSize = 14.sp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "按 deepseek 官方价目表（美元）折算人民币（${d.model}）",
                    style = DsType.label.copy(color = c.textTertiary, fontSize = 11.5.sp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}

@Composable
private fun UsageLine(label: String, value: String, unit: String) {
    val c = LocalDsColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = DsType.label.copy(color = c.textSecondary, fontSize = 13.sp),
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = DsType.label.copy(color = c.text, fontSize = 13.sp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            unit,
            style = DsType.label.copy(color = c.textTertiary, fontSize = 11.5.sp)
        )
    }
}

@Composable
private fun MenuItem(label: String, icon: IconKind, onClick: () -> Unit) {
    val c = LocalDsColors.current
    DropdownMenuItem(
        text = { Text(label, style = DsType.body.copy(color = c.text, fontSize = 15.sp)) },
        leadingIcon = { DsIcon(icon, size = 19.dp, tint = c.text, stroke = 1.6f) },
        onClick = onClick
    )
}

@Composable
private fun ToolRow(b: Block.Tool) {
    val c = LocalDsColors.current
    val label = when (b.kind) {
        "search", "deep_search" -> "已搜索：${b.query ?: "网页"}"
        "image_search", "deep_image_search" -> "已搜索图片：${b.query ?: ""}"
        "read_link" -> "已读取链接"
        else -> "已调用联网能力"
    }
    Row(
        Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.surfaceAlt)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DsIcon(IconKind.Globe, size = 14.dp, tint = c.textSecondary, stroke = 1.6f)
        Spacer(Modifier.width(6.dp))
        Text(label, style = DsType.label.copy(color = c.textSecondary), maxLines = 1)
    }
}

@Composable
private fun Citations(citations: List<com.local.dsapp.data.Citation>, onOpenUrl: (String) -> Unit) {
    val c = LocalDsColors.current
    val seen = mutableSetOf<String>()
    val unique = citations.filter { seen.add(it.url) }
    Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Text("参考来源", style = DsType.label.copy(color = c.textSecondary, fontSize = 12.5.sp))
        unique.forEachIndexed { i, cite ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onOpenUrl(cite.url) }
                    .padding(vertical = 5.dp)
            ) {
                Text("${i + 1}", style = DsType.label.copy(color = c.accent, fontSize = 12.5.sp))
                Spacer(Modifier.width(8.dp))
                Text(
                    cite.title ?: cite.url,
                    style = DsType.label.copy(color = c.accent, fontSize = 12.5.sp),
                    maxLines = 1
                )
            }
        }
    }
}

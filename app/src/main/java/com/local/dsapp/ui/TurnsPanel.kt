package com.local.dsapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.dsapp.data.Msg
import com.local.dsapp.data.Session

/**
 * 右侧「对话轮次」栏：列出每一轮提问。
 * 点一条跳转；右侧「…」里可以修改这一轮、重新生成回答、删除这一轮。
 * 有多个分支时，在问题下面显示「问题 2/3」「回答 1/2」这样的标记。
 */
@Composable
fun TurnsPanel(
    visible: Boolean,
    chain: List<Msg>,
    session: Session?,
    onClose: () -> Unit,
    onJump: (Int) -> Unit,
    onEdit: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onDeleteRound: (String) -> Unit
) {
    val c = LocalDsColors.current
    var pendingDelete by remember { mutableStateOf<Msg?>(null) }

    val rounds = chain.mapIndexedNotNull { i, m -> if (m.isUser) i to m else null }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
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
                    .fillMaxWidth(0.76f)
                    .align(Alignment.CenterEnd)
                    .background(c.bg)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .clickable(enabled = false) {}
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "对话轮次",
                            style = DsType.title.copy(color = c.text, fontSize = 17.sp)
                        )
                        Text(
                            "共 ${rounds.size} 轮",
                            style = DsType.label.copy(color = c.textSecondary, fontSize = 12.5.sp)
                        )
                    }
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(c.surfaceAlt)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        DsIcon(IconKind.Close, size = 15.dp, tint = c.text, stroke = 1.8f)
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .height(1.dp)
                        .background(c.divider)
                )

                if (rounds.isEmpty()) {
                    Text(
                        "这个对话还没有提问",
                        style = DsType.body.copy(color = c.textTertiary, fontSize = 14.sp),
                        modifier = Modifier.padding(20.dp)
                    )
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(rounds, key = { _, item -> item.second.id }) { ordinal, item ->
                        val chainIndex = item.first
                        val question = item.second
                        val answer = chain.getOrNull(chainIndex + 1)?.takeIf { !it.isUser }
                        val answerSibs = answer?.let { session?.siblings(it.id) }.orEmpty()
                        val questionSibs = session?.siblings(question.id).orEmpty()

                        TurnRow(
                            ordinal = ordinal + 1,
                            question = question.text,
                            questionBranch = if (questionSibs.size > 1) {
                                val qi = questionSibs.indexOfFirst { it.id == question.id }
                                "${qi + 1}/${questionSibs.size}"
                            } else null,
                            answerBranch = if (answerSibs.size > 1 && answer != null) {
                                val ai = answerSibs.indexOfFirst { it.id == answer.id }
                                "${ai + 1}/${answerSibs.size}"
                            } else null,
                            onJump = { onJump(chainIndex) },
                            onEdit = { onEdit(question.id) },
                            onRegenerate = { answer?.let { onRegenerate(it.id) } },
                            onDelete = { pendingDelete = question }
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }

    pendingDelete?.let { msg ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = c.surface,
            shape = RoundedCornerShape(16.dp),
            title = { Text("删除这一轮？", style = DsType.title.copy(color = c.text, fontSize = 16.sp)) },
            text = {
                Text(
                    "「${msg.text.take(30)}」以及它的回答会一起删除，后面的轮次会自动接上。",
                    style = DsType.body.copy(color = c.textSecondary, fontSize = 14.sp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteRound(msg.id)
                    pendingDelete = null
                    onClose()
                }) { Text("删除", color = c.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TurnRow(
    ordinal: Int,
    question: String,
    questionBranch: String?,
    answerBranch: String?,
    onJump: () -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit
) {
    val c = LocalDsColors.current
    var menu by remember { mutableStateOf(false) }

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onJump)
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(c.surfaceAlt),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$ordinal",
                    style = DsType.label.copy(color = c.textSecondary, fontSize = 11.sp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    question.ifBlank { "（空）" },
                    style = DsType.body.copy(color = c.text, fontSize = 14.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
                val tags = listOfNotNull(
                    questionBranch?.let { "问题 $it" },
                    answerBranch?.let { "回答 $it" }
                )
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row {
                        tags.forEach { tag ->
                            Box(
                                Modifier
                                    .padding(end = 6.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .border(1.dp, c.pillBorder, RoundedCornerShape(5.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    tag,
                                    style = DsType.label.copy(color = c.textSecondary, fontSize = 10.5.sp)
                                )
                            }
                        }
                    }
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { menu = true }
                    .padding(6.dp)
            ) {
                DsIcon(IconKind.More, size = 16.dp, tint = c.textTertiary, stroke = 1.5f)
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
            PanelItem("跳到这一轮", IconKind.ChevronRight) { menu = false; onJump() }
            PanelItem("修改这一轮", IconKind.Pencil) { menu = false; onEdit() }
            PanelItem("重新生成回答", IconKind.Refresh) { menu = false; onRegenerate() }
            PanelItem("删除这一轮", IconKind.Trash, c.danger) { menu = false; onDelete() }
        }
    }
}

@Composable
private fun PanelItem(
    label: String,
    icon: IconKind,
    tint: Color = LocalDsColors.current.text,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label, style = DsType.body.copy(color = tint, fontSize = 15.sp)) },
        leadingIcon = { DsIcon(icon, size = 19.dp, tint = tint, stroke = 1.6f) },
        onClick = onClick
    )
}

package com.local.dsapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import com.local.dsapp.R
import com.local.dsapp.data.Msg

@Composable
fun ChatScreen(
    state: UiState,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onToggleThinking: () -> Unit,
    onToggleSearch: () -> Unit,
    onRegenerate: (String) -> Unit,
    onEdit: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onSwitchBranch: (String, Int) -> Unit,
    onToast: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDeleteRound: (String) -> Unit,
    onToggleAttach: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onAddUri: (android.net.Uri, String?, String?, Boolean) -> Unit
) {
    val c = LocalDsColors.current
    // 长对话（几千条）下这两项每帧重算会明显掉帧，按会话缓存
    val chain = remember(state.active) { state.active?.visibleChain() ?: emptyList() }
    val siblingMap = remember(state.active) {
        val map = HashMap<String, List<Msg>>()
        state.active?.messages?.groupBy { it.parentId }?.forEach { (_, kids) ->
            kids.forEach { map[it.id] = kids }
        }
        map
    }
    val listState = rememberLazyListState()

    var turnsOpen by remember { mutableStateOf(false) }
    var jumpTarget by remember { mutableStateOf<Int?>(null) }

    val scope = rememberCoroutineScope()
    LaunchedEffect(jumpTarget) {
        jumpTarget?.let { idx ->
            if (idx in chain.indices) {
                // 跨得远就直接瞬移，别一格一格滚（长对话从底部跳到顶部要滚很久）
                if (kotlin.math.abs(idx - listState.firstVisibleItemIndex) > 8) {
                    listState.scrollToItem(idx)
                } else {
                    listState.animateScrollToItem(idx)
                }
            }
            jumpTarget = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(c.bg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            TopBar(
                title = state.active?.title.orEmpty(),
                onTitleDoubleTap = { scope.launch { listState.scrollToItem(0) } },
                onMenu = onMenu,
                onTurns = { turnsOpen = true },
                onNewChat = onNewChat
            )

            Box(
                Modifier
                    .weight(1f)
                    // 右滑唤出左边栏（历史），左滑唤出右边栏（轮次）
                    .pointerInput(Unit) {
                        var acc = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { acc = 0f },
                            onHorizontalDrag = { _, dragAmount -> acc += dragAmount },
                            onDragEnd = {
                                when {
                                    acc > 240f -> onMenu()
                                    acc < -240f -> turnsOpen = true
                                }
                            }
                        )
                    }
            ) {
                if (chain.isEmpty()) {
                    HomeBody()
                } else {
                    MessageList(
                        state, chain, siblingMap, listState,
                        onRegenerate, onEdit, onSwitchBranch, onToast, onOpenUrl
                    )
                }
            }

            if (state.editingId != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surfaceAlt)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "正在修改这条消息，发送后会作为新分支",
                        style = DsType.label.copy(color = c.textSecondary, fontSize = 12.5.sp),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "取消",
                        style = DsType.label.copy(color = c.accent, fontSize = 12.5.sp),
                        modifier = Modifier
                            .clickable(onClick = onCancelEdit)
                            .padding(4.dp)
                    )
                }
            }

            InputCard(
                input = state.input,
                streaming = state.streaming,
                thinking = state.thinking,
                search = state.search,
                attachOpen = state.attachOpen,
                pending = state.pending,
                onInput = onInput,
                onSend = onSend,
                onStop = onStop,
                onToggleThinking = onToggleThinking,
                onToggleSearch = onToggleSearch,
                onToggleAttach = onToggleAttach,
                onRemoveAttachment = onRemoveAttachment,
                onAddUri = onAddUri,
                onToast = onToast
            )
        }

        TurnsPanel(
            visible = turnsOpen,
            chain = chain,
            session = state.active,
            onClose = { turnsOpen = false },
            onJump = { idx ->
                jumpTarget = idx
                turnsOpen = false
            },
            onEdit = { id ->
                turnsOpen = false
                onEdit(id)
            },
            onRegenerate = { id ->
                turnsOpen = false
                onRegenerate(id)
            },
            onDeleteRound = onDeleteRound
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    onTitleDoubleTap: () -> Unit,
    onMenu: () -> Unit,
    onTurns: () -> Unit,
    onNewChat: () -> Unit
) {
    val c = LocalDsColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onMenu
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                DsIcon(IconKind.Menu, size = 22.dp, tint = c.text, stroke = 1.9f)
            }
            // 轮次栏入口：菜单右边这个「目录」图标
            Box(
                Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTurns
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                DsIcon(IconKind.Turns, size = 21.dp, tint = c.text, stroke = 1.8f)
            }
        }

        // 标题占中间的剩余宽度，过长自动省略，永远不会压到两侧图标。
        // 双击这块区域回到对话最顶端（手势挂在外层 Box 上，命中区比文字本身大）
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onTitleDoubleTap() })
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                title,
                style = DsType.title.copy(color = c.text, fontSize = 16.5.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        Row(
            Modifier.padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable { }
                    .padding(8.dp)
            ) {
                DsIcon(IconKind.SpeakerOff, size = 21.dp, tint = c.text, stroke = 1.6f)
            }
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onNewChat)
                    .padding(8.dp)
            ) {
                DsIcon(IconKind.PlusCircle, size = 22.dp, tint = c.text, stroke = 1.6f)
            }
        }
    }
}

@Composable
private fun HomeBody() {
    val c = LocalDsColors.current
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Image(
            painterResource(R.drawable.whale),
            contentDescription = null,
            modifier = Modifier.size(62.dp)
        )
        Spacer(Modifier.height(22.dp))
        Text(
            "嗨！今天想聊些什么？",
            style = DsType.body.copy(
                color = c.text,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
        Spacer(Modifier.weight(1.06f))
    }
}

@Composable
private fun MessageList(
    state: UiState,
    chain: List<Msg>,
    siblingMap: Map<String, List<Msg>>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onRegenerate: (String) -> Unit,
    onEdit: (String) -> Unit,
    onSwitchBranch: (String, Int) -> Unit,
    onToast: (String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val lastLen = chain.lastOrNull()?.let { it.plainText.length + it.thinkingText.length } ?: 0

    // 跟随模式：只有用户本来就停在底部时才自动滚；手动往上翻就停止跟随，
    // 翻回底部再自动恢复。否则流式输出期间会被一直拽回末尾，没法往下看。
    var follow by remember { mutableStateOf(true) }
    var programmatic by remember { mutableStateOf(false) }

    var lastSize by remember { mutableStateOf(0) }
    LaunchedEffect(chain.size, lastLen) {
        // 只在「流式输出中」或「新增了消息」时自动滚；
        // 切换分支（条数不变、非流式）不滚，否则会突然跳一下
        val grew = chain.size != lastSize
        lastSize = chain.size
        if (chain.isEmpty() || !follow) return@LaunchedEffect
        if (!grew && !state.streaming) return@LaunchedEffect
        programmatic = true
        listState.scrollToItem(chain.lastIndex)
        // scrollToItem 会把最后一条对齐到顶部；再滚一大截由框架钳到底，才是真正贴底
        listState.scrollBy(20_000f)
        programmatic = false
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling || programmatic) return@collect
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            follow = last != null &&
                last.index == info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + 8
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
        contentPadding = PaddingValues(bottom = 10.dp)
    ) {
        items(chain, key = { it.id }) { msg ->
            MessageItem(
                msg = msg,
                siblings = siblingMap[msg.id] ?: listOf(msg),
                autoCollapseThinking = state.autoCollapse,
                showUsage = state.showUsage,
                streaming = state.streaming,
                onRegenerate = { onRegenerate(msg.id) },
                onEdit = { onEdit(msg.id) },
                onSwitchBranch = { delta ->
                    val idx = chain.indexOfFirst { it.id == msg.id }
                    onSwitchBranch(msg.id, delta)
                    if (idx >= 0) {
                        scope.launch {
                            // 只等一帧，减少可见的中间态；然后按「超出视口多少」一次性补滚，
                            // 保证分页按钮被拉回屏幕内，且长→短时不做无谓的负向滚动（那会闪一下）
                            withFrameNanos { }
                            val info = listState.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.index == idx }
                                ?: return@launch
                            val overflow = item.offset + item.size - info.viewportEndOffset
                            if (overflow > 0) listState.scrollBy(overflow.toFloat())
                        }
                    }
                },
                onToast = onToast,
                onOpenUrl = onOpenUrl,
                apiKey = state.apiKey,
                voice = state.voice,
                autoCollapseLong = state.autoCollapseLong
            )
        }
    }
}

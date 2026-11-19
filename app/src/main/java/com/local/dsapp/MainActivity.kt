package com.local.dsapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.dsapp.ui.ChatScreen
import com.local.dsapp.ui.ChatViewModel
import com.local.dsapp.ui.DsTheme
import com.local.dsapp.ui.DsType
import com.local.dsapp.ui.HistoryDrawer
import com.local.dsapp.ui.LocalDsColors
import com.local.dsapp.ui.SettingsScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DsTheme(dark = isSystemInDarkTheme()) { Root(vm) }
        }
    }
}

@Composable
private fun Root(vm: ChatViewModel) {
    val state by vm.state.collectAsState()
    val c = LocalDsColors.current
    val context = LocalContext.current

    val openUrl: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    BackHandler(enabled = state.drawerOpen || state.settingsOpen || state.editingId != null) {
        when {
            state.selecting -> vm.cancelSelecting()
            state.drawerOpen -> vm.openDrawer(false)
            state.settingsOpen -> vm.openSettings(false)
            state.editingId != null -> vm.cancelEdit()
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        if (state.settingsOpen) {
            SettingsScreen(
                state = state,
                onBack = { vm.openSettings(false) },
                onSaveKey = vm::saveApiKey,
                onVerify = { cb -> vm.verifyKey(cb) },
                onModel = vm::setModel,
                onAutoCollapse = vm::setAutoCollapse,
                onShowUsage = vm::setShowUsage,
                onVoice = vm::setVoice,
                onAutoCollapseLong = vm::setAutoCollapseLong,
                onExport = { vm.exportZip() },
                onImport = { uri -> vm.importBackup(uri) },
                onToast = { msg -> vm.toast(msg) }
            )
        } else {
            ChatScreen(
                state = state,
                onMenu = { vm.openDrawer(true) },
                onNewChat = vm::newChat,
                onInput = vm::setInput,
                onSend = vm::send,
                onStop = vm::stop,
                onToggleThinking = vm::toggleThinking,
                onToggleSearch = vm::toggleSearch,
                onRegenerate = { id -> vm.regenerate(id) },
                onEdit = { id -> vm.beginEdit(id) },
                onCancelEdit = { vm.cancelEdit() },
                onSwitchBranch = { id, delta -> vm.switchBranch(id, delta) },
                onToast = { msg -> vm.toast(msg) },
                onOpenUrl = openUrl,
                onDeleteRound = { id -> vm.deleteRound(id) },
                onToggleAttach = { vm.toggleAttach() },
                onRemoveAttachment = { id -> vm.removeAttachment(id) },
                onAddUri = { uri, name, mime, isImage -> vm.addAttachment(uri, name, mime, isImage) }
            )
        }

        HistoryDrawer(
            visible = state.drawerOpen,
            sessions = state.sessions,
            activeId = state.active?.id,
            selecting = state.selecting,
            selectedIds = state.selectedIds,
            onClose = { vm.openDrawer(false) },
            onOpen = vm::openSession,
            onDelete = vm::deleteSession,
            onRename = { id, title -> vm.renameSession(id, title) },
            onPin = { id -> vm.togglePin(id) },
            onStartSelecting = { id -> vm.startSelecting(id) },
            onToggleSelected = { id -> vm.toggleSelected(id) },
            onCancelSelecting = { vm.cancelSelecting() },
            onDeleteSelected = { vm.deleteSelected() },
            onPinSelected = { vm.pinSelected() },
            onNewChat = vm::newChat,
            onSettings = {
                vm.openDrawer(false)
                vm.openSettings(true)
            }
        )

        // toast：保留最后一次的文案，否则淡出动画期间 toast 已置空，会闪出一个空气泡
        val toast = state.toast
        var lastToast by remember { mutableStateOf("") }
        LaunchedEffect(toast) {
            if (toast != null) lastToast = toast
        }
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text(
                lastToast,
                style = DsType.label.copy(color = c.bg, fontSize = 13.sp),
                modifier = Modifier
                    .padding(bottom = 120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.text.copy(alpha = 0.9f))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
        // 导入进度：模态弹窗，不可点掉（解析期间别让用户乱点）
        if (state.importing) {
            ImportProgressDialog(done = state.importDone, total = state.importTotal)
        }

        LaunchedEffect(toast) {
            if (toast != null) {
                delay(1800)
                vm.clearToast()
            }
        }
    }
}

@Composable
private fun ImportProgressDialog(done: Int, total: Int) {
    val c = LocalDsColors.current
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(
            Modifier
                .width(240.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.surface)
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(34.dp),
                color = c.accent,
                strokeWidth = 3.dp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "正在导入聊天记录",
                style = DsType.body.copy(color = c.text, fontSize = 15.sp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (total > 0) "$done / $total 个对话" else "正在读取文件…",
                style = DsType.label.copy(color = c.textSecondary, fontSize = 12.5.sp)
            )
            if (total > 0) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { done.toFloat() / total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = c.accent,
                    trackColor = c.divider
                )
            }
        }
    }
}

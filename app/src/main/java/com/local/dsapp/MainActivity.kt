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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                onExport = { vm.exportZip() },
                onImport = { bytes -> vm.importBackup(bytes) },
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

        val toast = state.toast
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text(
                toast.orEmpty(),
                style = DsType.label.copy(color = c.bg, fontSize = 13.sp),
                modifier = Modifier
                    .padding(bottom = 120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.text.copy(alpha = 0.9f))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
        LaunchedEffect(toast) {
            if (toast != null) {
                delay(1800)
                vm.clearToast()
            }
        }
    }
}

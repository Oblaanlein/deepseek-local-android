package com.local.dsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.dsapp.data.MODEL_FLASH
import com.local.dsapp.data.MODEL_PRO

@Composable
fun SettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onSaveKey: (String) -> Unit,
    onVerify: ((String?) -> Unit) -> Unit,
    onModel: (String) -> Unit,
    onAutoCollapse: (Boolean) -> Unit,
    onShowUsage: (Boolean) -> Unit,
    onVoice: (String) -> Unit,
    onExport: () -> ByteArray,
    onImport: (android.net.Uri) -> Unit,
    onToast: (String) -> Unit
) {
    val c = LocalDsColors.current
    var keyDialog by remember { mutableStateOf(false) }
    var modelDialog by remember { mutableStateOf(false) }
    var voiceDialog by remember { mutableStateOf(false) }

    val ctx = androidx.compose.ui.platform.LocalContext.current

    // 导出：系统「另存为」，写出官方格式的 zip（不需要存储权限）
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(onExport())
                }
            }.onSuccess { onToast("已导出") }
                .onFailure { onToast("导出失败：${it.message}") }
        }
    }

    // 导入：系统文件选择器，读字节（zip 或旧版 JSON 都能认）
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // 真正解析在 ViewModel 的 IO 线程里做（官方备份可能几十 MB），结果由它 toast 回来
            onImport(uri)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.surfaceAlt)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                DsIcon(IconKind.ChevronLeft, size = 22.dp, tint = c.text, stroke = 1.9f)
            }
            Text(
                "设置",
                style = DsType.title.copy(color = c.text, fontSize = 16.5.sp),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            GroupTitle("账户")
            Card {
                SettingRow(IconKind.Pin, "API Key", onClick = { keyDialog = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (state.apiKey.isBlank()) "未填写" else "已填写",
                            style = DsType.label.copy(color = c.textSecondary, fontSize = 14.sp)
                        )
                        DsIcon(IconKind.ChevronRight, size = 15.dp, tint = c.textTertiary)
                    }
                }

                Divider()
                SettingRow(IconKind.Sliders, "模型", onClick = { modelDialog = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (state.model == MODEL_PRO) "deepseek-v4-pro" else "deepseek-flash",
                            style = DsType.label.copy(color = c.textSecondary, fontSize = 14.sp)
                        )
                        DsIcon(IconKind.ChevronRight, size = 15.dp, tint = c.textTertiary)
                    }
                }
            }

            GroupTitle("应用")
            Card {
                SettingRow(IconKind.Atom, "思考结束后自动收起", onClick = {}) {
                    Switch(
                        modifier = Modifier.scale(0.82f),
                        checked = state.autoCollapse,
                        onCheckedChange = onAutoCollapse,
                        colors = SwitchDefaults.colors(checkedTrackColor = c.accent)
                    )
                }
                Divider()
                SettingRow(IconKind.Copy, "显示 token 用量", onClick = {}) {
                    Switch(
                        modifier = Modifier.scale(0.82f),
                        checked = state.showUsage,
                        onCheckedChange = onShowUsage,
                        colors = SwitchDefaults.colors(checkedTrackColor = c.accent)
                    )
                }
                Divider()
                SettingRow(IconKind.Speaker, "朗读音色", onClick = { voiceDialog = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (state.voice == "tide") "暗潮（男声）" else "海星（女声）",
                            style = DsType.label.copy(color = c.textSecondary, fontSize = 14.sp)
                        )
                        DsIcon(IconKind.ChevronRight, size = 15.dp, tint = c.textTertiary)
                    }
                }
            }

            GroupTitle("数据")
            Card {
                SettingRow(IconKind.Share, "导出聊天记录", onClick = {
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                        .format(java.util.Date())
                    exportLauncher.launch("deepseek_data-$stamp.zip")
                }) {
                    Text(
                        "${state.sessions.size} 个对话 · ZIP",
                        style = DsType.label.copy(color = c.textSecondary, fontSize = 14.sp)
                    )
                }
                Divider()
                SettingRow(IconKind.Copy, "导入聊天记录", onClick = {
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/json", "*/*"))
                }) {
                    DsIcon(IconKind.ChevronRight, size = 15.dp, tint = c.textTertiary)
                }
            }

            GroupTitle("关于")
            Card {
                SettingRow(IconKind.Globe, "版本", onClick = {}) {
                    Text(
                        "0.1.0",
                        style = DsType.label.copy(color = c.textSecondary, fontSize = 14.sp)
                    )
                }
                Divider()
                SettingRow(IconKind.Search, "数据说明", onClick = {}) {
                    Text(
                        "全部本地",
                        style = DsType.label.copy(color = c.textSecondary, fontSize = 14.sp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "对话与 Key 只存在本机，不上传任何服务器（除了直连 DeepSeek 接口）",
                style = DsType.label.copy(color = c.textTertiary, fontSize = 12.sp),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (keyDialog) {
        var draft by remember { mutableStateOf(state.apiKey) }
        var result by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { keyDialog = false },
            title = { Text("API Key", style = DsType.title.copy(color = c.text)) },
            text = {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.surfaceAlt)
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it; result = null },
                            singleLine = true,
                            textStyle = DsType.body.copy(color = c.text, fontSize = 14.sp),
                            cursorBrush = SolidColor(c.accent),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (result != null) {
                        Text(
                            result!!,
                            style = DsType.label.copy(color = c.textSecondary, fontSize = 12.5.sp),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSaveKey(draft)
                    keyDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onSaveKey(draft)
                    onVerify { result = it ?: "连接正常，Key 可用" }
                }) { Text("测试连接") }
            },
            containerColor = c.surface
        )
    }

    if (voiceDialog) {
        AlertDialog(
            onDismissRequest = { voiceDialog = false },
            title = { Text("朗读音色", style = DsType.title.copy(color = c.text)) },
            text = {
                Column {
                    ModelOption("stella", "海星 · 女声", state.voice) {
                        onVoice("stella"); voiceDialog = false
                    }
                    Spacer(Modifier.height(6.dp))
                    ModelOption("tide", "暗潮 · 男声", state.voice) {
                        onVoice("tide"); voiceDialog = false
                    }
                }
            },
            confirmButton = {},
            containerColor = c.surface
        )
    }

    if (modelDialog) {
        AlertDialog(
            onDismissRequest = { modelDialog = false },
            title = { Text("选择模型", style = DsType.title.copy(color = c.text)) },
            text = {
                Column {
                    ModelOption("deepseek-flash", "快，日常够用（V4.1-Flash）", state.model) {
                        onModel(MODEL_FLASH); modelDialog = false
                    }
                    Spacer(Modifier.height(6.dp))
                    ModelOption("deepseek-v4-pro", "更强，复杂任务", state.model) {
                        onModel(MODEL_PRO); modelDialog = false
                    }
                }
            },
            confirmButton = {},
            containerColor = c.surface
        )
    }
}

@Composable
private fun ModelOption(id: String, desc: String, current: String, onClick: () -> Unit) {
    val c = LocalDsColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (id == current) c.pillOnBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(id, style = DsType.body.copy(color = c.text, fontSize = 14.5.sp))
            Text(desc, style = DsType.label.copy(color = c.textSecondary, fontSize = 12.sp))
        }
        if (id == current) DsIcon(IconKind.Check, size = 16.dp, tint = c.accent)
    }
}

@Composable
private fun GroupTitle(text: String) {
    val c = LocalDsColors.current
    Text(
        text,
        style = DsType.label.copy(color = c.textSecondary, fontSize = 12.5.sp),
        modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val c = LocalDsColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
    ) { content() }
}

@Composable
private fun Divider() {
    val c = LocalDsColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 46.dp)
            .height(1.dp)
            .background(c.divider)
    )
}

@Composable
private fun SettingRow(
    icon: IconKind,
    title: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    val c = LocalDsColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        DsIcon(icon, size = 19.dp, tint = c.text, stroke = 1.6f)
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = DsType.body.copy(color = c.text, fontSize = 15.sp),
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

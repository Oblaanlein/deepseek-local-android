package com.local.dsapp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.local.dsapp.data.Attachment
import java.io.File

/**
 * 输入卡片。尺寸按官方截图校准：
 * 卡片总高约 60dp（文字行 22 + 间距 4 + 胶囊 26 + 上下内边距 8），
 * 胶囊更窄、图标更大，底部留白更小。
 */
@Composable
fun InputCard(
    input: String,
    streaming: Boolean,
    thinking: Boolean,
    search: Boolean,
    attachOpen: Boolean,
    pending: List<Attachment>,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onToggleThinking: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleAttach: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onAddUri: (Uri, String?, String?, Boolean) -> Unit,
    onToast: (String) -> Unit
) {
    val c = LocalDsColors.current
    val ctx = LocalContext.current

    /* ---------------- 拍照 ---------------- */
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val camLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) camUri?.let { onAddUri(it, "photo.jpg", "image/jpeg", true) }
        camUri = null
    }

    /* ---------------- 相册 / 文件 ---------------- */
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { onAddUri(it, null, ctx.contentResolver.getType(it), true) }
    }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onAddUri(it, null, ctx.contentResolver.getType(it), false) }
    }

    /* ---------------- 语音输入 ---------------- */
    var listening by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onToast("这台设备没有可用的语音识别")
            return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        r.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) {
                listening = false
                onToast("没听清，再试一次")
            }

            override fun onResults(results: android.os.Bundle?) {
                listening = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) onInput(input + text)
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        recognizer = r
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        r.startListening(intent)
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else onToast("需要录音权限才能语音输入")
    }

    DisposableEffect(Unit) {
        onDispose { recognizer?.destroy() }
    }

    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x12000000))
                .background(c.surface, RoundedCornerShape(22.dp))
                .border(1.dp, c.divider, RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Box(Modifier.fillMaxWidth()) {
                if (input.isEmpty()) {
                    Text(
                        "发消息或按住说话",
                        style = DsType.body.copy(color = c.textTertiary, fontSize = 15.sp)
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = onInput,
                    textStyle = DsType.body.copy(color = c.text),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                )
            }

            Spacer(Modifier.height(7.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(thinking, IconKind.Atom, "深度思考", onClick = onToggleThinking)
                Spacer(Modifier.width(7.dp))
                Pill(search, IconKind.Globe, "智能搜索", iconSize = 18.dp, onClick = onToggleSearch)
                Spacer(Modifier.weight(1f))

                // ⊕ / ⊗：展开或收起附件面板
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onToggleAttach)
                        .padding(5.dp)
                ) {
                    DsIcon(
                        if (attachOpen) IconKind.Close else IconKind.PlusCircle,
                        size = 23.dp,
                        tint = c.text,
                        stroke = 1.6f
                    )
                }

                Spacer(Modifier.width(4.dp))

                when {
                    streaming -> Box(
                        Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onStop)
                            .padding(5.dp)
                    ) {
                        DsIcon(IconKind.Stop, size = 22.dp, tint = c.text, stroke = 1.8f)
                    }

                    input.isNotBlank() || pending.isNotEmpty() -> Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(c.accent)
                            .clickable(onClick = onSend),
                        contentAlignment = Alignment.Center
                    ) {
                        DsIcon(IconKind.ArrowUp, size = 18.dp, tint = Color.White, stroke = 2.1f)
                    }

                    else -> Box(
                        Modifier
                            .clip(CircleShape)
                            .clickable {
                                micPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                            .padding(5.dp)
                    ) {
                        DsIcon(
                            IconKind.Mic,
                            size = 22.dp,
                            tint = if (listening) c.accent else c.text,
                            stroke = 1.6f
                        )
                    }
                }
            }
        }

        if (listening) {
            Text(
                "正在听…",
                style = DsType.label.copy(color = c.accent, fontSize = 12.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        // 附件面板：已选文件缩略图 + 拍照/相册/文件
        if (attachOpen) {
            if (pending.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pending.forEach { a ->
                        AttachmentChip(a) { onRemoveAttachment(a.id) }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AttachAction("拍照", IconKind.Camera, Modifier.weight(1f)) {
                    val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
                    val f = File(dir, "cap_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
                    camUri = uri
                    camLauncher.launch(uri)
                }
                AttachAction("相册", IconKind.Image, Modifier.weight(1f)) {
                    galleryLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                }
                AttachAction("文件", IconKind.File, Modifier.weight(1f)) {
                    fileLauncher.launch(arrayOf("*/*"))
                }
            }
        }

        Text(
            "回答由 AI 生成，仅供参考",
            style = DsType.label.copy(color = c.textTertiary, fontSize = 11.sp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AttachmentChip(a: Attachment, onRemove: () -> Unit) {
    val c = LocalDsColors.current
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.surfaceAlt)
    ) {
        if (a.isImage) {
            val bmp = remember(a.path) { android.graphics.BitmapFactory.decodeFile(a.path) }
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = a.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp)
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                DsIcon(IconKind.File, size = 20.dp, tint = c.textSecondary, stroke = 1.5f)
                Spacer(Modifier.height(3.dp))
                Text(
                    a.name,
                    style = DsType.label.copy(color = c.textSecondary, fontSize = 9.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(17.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            DsIcon(IconKind.Close, size = 9.dp, tint = Color.White, stroke = 2.4f)
        }
    }
}

@Composable
private fun AttachAction(
    label: String,
    icon: IconKind,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = LocalDsColors.current
    Column(
        modifier
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceAlt)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        DsIcon(icon, size = 24.dp, tint = c.text, stroke = 1.6f)
        Spacer(Modifier.height(6.dp))
        Text(label, style = DsType.label.copy(color = c.text, fontSize = 13.sp))
    }
}

@Composable
private fun Pill(
    on: Boolean,
    icon: IconKind,
    label: String,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    onClick: () -> Unit
) {
    val c = LocalDsColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) c.pillOnBg else Color.Transparent)
            .border(1.dp, if (on) c.pillOnBorder else c.pillBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DsIcon(icon, size = iconSize, tint = if (on) c.accent else c.text, stroke = 1.6f)
        Spacer(Modifier.width(3.dp))
        Text(
            label,
            style = DsType.pill.copy(color = if (on) c.accent else c.text, fontSize = 13.sp),
            maxLines = 1,
            modifier = Modifier.widthIn(max = 88.dp)
        )
    }
}

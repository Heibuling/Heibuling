package com.example.ntfypop.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ntfypop.data.ChatRepo
import com.example.ntfypop.data.local.MessageRec
import com.example.ntfypop.data.local.Peer
import com.example.ntfypop.data.local.Store
import com.example.ntfypop.data.prefs.SettingsStore
import com.example.ntfypop.media.ImageCompressor
import com.example.ntfypop.media.VoiceRecorder
import com.example.ntfypop.ui.components.GlassSurface
import com.example.ntfypop.ui.components.GlassTextField
import com.example.ntfypop.ui.components.glassBrush
import com.example.ntfypop.ui.doodle.DoodleScreen
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(peer: Peer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context.applicationContext) }
    val store = remember { Store.get(context.applicationContext) }
    val repo = remember { ChatRepo(store, settings) }

    androidx.activity.compose.BackHandler { onBack() }

    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var sendingImage by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var showDoodle by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }

    fun startRecording() {
        if (VoiceRecorder.start(context)) {
            recording = true
            elapsed = 0L
            status = "录音中…（最长 20 秒，再点一次结束）"
        } else {
            status = "无法开始录音"
        }
    }

    fun finishRecording() {
        if (!VoiceRecorder.isRecording()) return
        val res = VoiceRecorder.stop()
        recording = false
        elapsed = 0L
        if (res == null) {
            status = "录音太短，已取消"
            return
        }
        scope.launch {
            try {
                repo.sendVoice(peer, res.file, res.durationMs, res.peaks)
                status = ""
            } catch (e: Exception) {
                status = "语音发送失败：" + e.message
            }
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecording() else status = "没有录音权限，请在系统设置里允许麦克风"
    }

    LaunchedEffect(Unit) {
        VoiceRecorder.onTick = { elapsed = it }
        VoiceRecorder.onAutoStop = { finishRecording() }
    }
    DisposableEffect(Unit) {
        onDispose {
            VoiceRecorder.onTick = null
            VoiceRecorder.onAutoStop = null
            if (VoiceRecorder.isRecording()) VoiceRecorder.cancel()
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            sendingImage = true
            status = "正在压缩并上传图片…"
            try {
                val res = ImageCompressor.compress(context, uri)
                if (res == null) {
                    status = "图片读取失败"
                } else {
                    repo.sendImage(peer, res.bytes, "img_" + System.currentTimeMillis() + ".jpg", "image/jpeg")
                    status = ""
                }
            } catch (e: Exception) {
                status = "图片发送失败：" + e.message
            } finally {
                sendingImage = false
            }
        }
    }
    val all by store.messages.collectAsState()
    val list = (all[peer.uid] ?: emptyList()).sortedBy { it.ts }
    val listState = rememberLazyListState()

    LaunchedEffect(list.size) {
        if (list.isNotEmpty()) listState.animateScrollToItem(list.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF0A0C12),
                    0.45f to Color(0xFF0D1018),
                    1f to Color(0xFF121A2B),
                ),
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text(
                text = peer.remoteName.ifBlank { peer.uid },
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "端到端直连",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.35f),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.mid }) { m -> MessageRow(m) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                enabled = !sendingImage && !recording,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) { Text("图片", fontSize = 14.sp) }
            TextButton(
                onClick = { showDoodle = true },
                enabled = !sendingImage && !recording,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) { Text("涂鸦", fontSize = 14.sp) }
            TextButton(
                onClick = {
                    if (recording) {
                        finishRecording()
                    } else {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !sendingImage,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    if (recording) "停止 " + (elapsed / 1000) + "s" else "语音",
                    fontSize = 14.sp,
                    color = if (recording) androidx.compose.ui.graphics.Color(0xFFEF4444) else androidx.compose.ui.graphics.Color.Unspecified,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (recording) {
                Text(
                    "录音中…",
                    fontSize = 12.sp,
                    color = androidx.compose.ui.graphics.Color(0xFFEF4444),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            GlassTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = "说点什么…",
                maxLines = 4,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val text = input.trim()
                    if (text.isEmpty()) return@Button
                    input = ""
                    scope.launch {
                        try {
                            repo.sendText(peer, text)
                            status = ""
                        } catch (e: Exception) {
                            status = "发送失败：" + e.message
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 4.dp),
            ) { Text("发送") }
        }
        if (status.isNotEmpty()) {
            Text(
                text = status,
                fontSize = 12.sp,
                color = if (status.contains("失败")) Color(0xFFEF4444) else Color(0xFF9CA3AF),
                modifier = Modifier.padding(start = 14.dp, bottom = 10.dp),
            )
        }
    }

    if (showDoodle) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showDoodle = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            DoodleScreen(
                onCancel = { showDoodle = false },
                onSend = { strokes ->
                    showDoodle = false
                    scope.launch {
                        try {
                            repo.sendDoodle(peer, strokes)
                            status = ""
                        } catch (e: Exception) {
                            status = "涂鸦发送失败：" + e.message
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun MessageRow(m: MessageRec) {
    val alignment = if (m.outgoing) Alignment.CenterEnd else Alignment.CenterStart
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(horizontalAlignment = if (m.outgoing) Alignment.End else Alignment.Start) {
            GlassSurface(
                shape = RoundedCornerShape(18.dp),
                brush = glassBrush(!m.outgoing),
            ) {
                Text(
                    text = m.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    color = Color.White,
                )
            }
            if (m.outgoing) {
                Text(
                    text = stateLabel(m.state),
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private fun stateLabel(state: String): String = when (state) {
    MessageRec.STATE_SENDING -> "发送中…"
    MessageRec.STATE_SENT -> "已发出"
    MessageRec.STATE_DELIVERED -> "已送达 ✓"
    MessageRec.STATE_DISMISSED -> "已查看 ✓✓"
    MessageRec.STATE_FAILED -> "发送失败"
    else -> state
}

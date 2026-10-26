package com.example.ntfypop.service.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ntfypop.data.ChatRepo
import com.example.ntfypop.data.local.Peer
import com.example.ntfypop.data.local.Store
import com.example.ntfypop.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 常驻悬浮球：可拖动、松手吸附边缘、点击展开输入面板。 */
@Composable
fun FloatingBallView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xCC15161A))
            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("💬", fontSize = 20.sp)
    }
}

/** 紧凑输入面板：选好友 + 输入 + 8 个快捷 emoji + 发送。 */
@Composable
fun BallInputPanel(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { Store.get(context.applicationContext) }
    val settings = remember { SettingsStore(context.applicationContext) }
    val repo = remember { ChatRepo(store, settings) }

    val peers by store.peers.collectAsState()
    var target by remember { mutableStateOf<Peer?>(null) }
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    var lastPeerUid by remember { mutableStateOf<String?>(null) }

    // 读取"上次发给谁"，展开面板时默认选中它（避免每次都要重新选人）
    LaunchedEffect(Unit) {
        lastPeerUid = settings.lastPeerUid.first()
    }
    LaunchedEffect(peers, lastPeerUid) {
        if (peers.isEmpty()) {
            target = null
            return@LaunchedEffect
        }
        val current = target
        if (current == null || peers.none { it.uid == current.uid }) {
            target = peers.firstOrNull { it.uid == lastPeerUid } ?: peers.first()
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AcrylicSurface(onClick = { }) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "发给：" + (target?.remoteName?.ifBlank { target?.uid } ?: "（还没有朋友）"),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (peers.size > 1) {
                    TextButton(onClick = {
                        val idx = peers.indexOfFirst { it.uid == target?.uid }
                        val next = peers[(idx + 1) % peers.size]
                        target = next
                        scope.launch { settings.setLastPeerUid(next.uid) }
                    }) { Text("切换", fontSize = 13.sp) }
                }
                TextButton(onClick = onClose) { Text("收起", fontSize = 13.sp) }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("说点什么…") },
                maxLines = 3,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                listOf("😀", "😂", "🥰", "😎", "👍", "🔥", "🎉", "❤️").forEach { e ->
                    TextButton(
                        onClick = { text = text + e },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) { Text(e, fontSize = 18.sp) }
                }
            }

            Button(
                onClick = {
                    val t = target ?: return@Button
                    val body = text.trim()
                    if (body.isEmpty()) return@Button
                    scope.launch {
                        try {
                            repo.sendText(t, body)
                            settings.setLastPeerUid(t.uid)
                            text = ""
                            status = "已发送"
                            onClose()
                        } catch (e: Exception) {
                            status = "发送失败：" + e.message
                        }
                    }
                },
                enabled = text.isNotBlank() && target != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("发送") }

            if (status.isNotEmpty()) {
                Text(
                    text = status,
                    fontSize = 11.sp,
                    color = if (status.contains("失败")) Color(0xFFEF4444) else Color(0xFF9CA3AF),
                )
            }
        }
    }
}

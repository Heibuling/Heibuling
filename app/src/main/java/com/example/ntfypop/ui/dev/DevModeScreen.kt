package com.example.ntfypop.ui.dev

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ntfypop.data.local.MessageRec
import com.example.ntfypop.data.local.Store
import com.example.ntfypop.data.prefs.SettingsStore
import com.example.ntfypop.service.DiagState
import com.example.ntfypop.service.LinkService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DEV_PASSWORD = "Heibuling072840"

/**
 * 开发者诊断模式（密码保护）。
 * 只展示「连接与收发状态」，用于排查"消息为什么没到"：
 * 每个 topic 的连接/重连次数/最后事件/最后服务端 id/最后错误，失败消息，以及最近 60 条事件日志。
 * 不涉及任何人的消息内容、相册或其他隐私数据。
 */
@Composable
fun DevModeScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context.applicationContext) }

    var unlocked by remember { mutableStateOf(false) }
    var pwd by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    val topics by DiagState.topics.collectAsState()
    val events by DiagState.events.collectAsState()
    val messages by store.messages.collectAsState()
    val peers by store.peers.collectAsState()
    val pending by store.pending.collectAsState()
    val identity by settings.identity.collectAsState(initial = null)

    LaunchedEffect(Unit) { store.load() }

    if (!unlocked) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(devBackdrop())
                .safeDrawingPadding()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("开发者模式", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("仅用于排查连接问题，输入密码进入。", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = pwd,
                onValueChange = { pwd = it },
                singleLine = true,
                label = { Text("密码") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(error, color = Color(0xFFEF4444), fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { if (pwd == DEV_PASSWORD) unlocked = true else error = "密码不正确" },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("进入") }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("返回") }
        }
        return
    }

    val failed = messages.values.flatten().filter { it.state == MessageRec.STATE_FAILED }
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(devBackdrop())
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("诊断面板", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("返回") }
        }

        // 一键操作
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                LinkService.restart(context)
                status = "已触发强制重连（会用 since 补收漏掉的消息）"
            }) { Text("强制重连", fontSize = 13.sp) }
            OutlinedButton(onClick = { DiagState.clear(); status = "日志已清空" }) { Text("清空日志", fontSize = 13.sp) }
        }
        OutlinedButton(
            onClick = {
                val sb = StringBuilder()
                sb.append("Heibuling 诊断信息 ").append(Date()).append("\n")
                sb.append("服务器: ").append(identity?.serverUrl ?: "?").append("\n")
                sb.append("好友数: ").append(peers.size).append("  待加入邀请: ").append(pending.size)
                    .append("  失败消息: ").append(failed.size).append("\n\n")
                sb.append("== 订阅状态 ==\n")
                topics.values.forEach { t ->
                    sb.append(t.topic).append("\n  已连接=").append(t.connected)
                        .append(" 重连次数=").append(t.attempts)
                        .append(" 最后事件=").append(t.lastEvent)
                        .append(" 最后id=").append(t.lastServerId)
                        .append(" 错误=").append(t.lastError).append("\n")
                }
                sb.append("\n== 好友 topic ==\n")
                peers.forEach { sb.append(it.remoteName).append(" -> ").append(it.topic).append("\n") }
                sb.append("\n== 最近事件 ==\n")
                events.forEach { sb.append(it).append("\n") }
                copy(context, sb.toString())
                status = "诊断信息已复制到剪贴板"
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("复制完整诊断信息") }

        if (status.isNotEmpty()) Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

        // 订阅状态
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("订阅状态（" + topics.size + " 个 topic）", fontWeight = FontWeight.Bold)
                if (topics.isEmpty()) Text("暂无订阅记录", fontSize = 12.sp)
                topics.values.forEach { t ->
                    val color = if (t.connected) Color(0xFF34D399) else Color(0xFFEF4444)
                    Text(
                        text = (if (t.connected) "● 已连接  " else "○ 未连接  ") + t.topic,
                        fontSize = 12.sp,
                        color = color,
                    )
                    Text(
                        text = "重连次数=" + t.attempts +
                            "  最后事件=" + (if (t.lastEvent.isEmpty()) "无" else t.lastEvent) +
                            "  时间=" + (if (t.lastEventAt > 0) fmt.format(Date(t.lastEventAt)) else "-"),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                    Text(
                        text = "最后服务端 id=" + (if (t.lastServerId.isEmpty()) "无" else t.lastServerId) +
                            (if (t.lastError.isNotEmpty()) "  错误=" + t.lastError else ""),
                        fontSize = 11.sp,
                        color = if (t.lastError.isNotEmpty()) Color(0xFFFFB4AB) else Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }

        // 好友
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("好友（" + peers.size + "）", fontWeight = FontWeight.Bold)
                peers.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = p.remoteName + "  →  " + p.topic,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { scope.launch { store.removePeerAndMessages(p.uid) } }) {
                            Text("删除", fontSize = 12.sp)
                        }
                    }
                }
                if (pending.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("待对方加入（" + pending.size + "）", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    pending.forEach { Text(it.topic, fontSize = 12.sp) }
                }
            }
        }

        // 失败消息
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("发送失败的消息（" + failed.size + "）", fontWeight = FontWeight.Bold)
                if (failed.isEmpty()) {
                    Text("没有失败消息", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                } else {
                    failed.take(10).forEach { m ->
                        Text(
                            text = m.mid + "  →  " + m.text.take(20),
                            fontSize = 12.sp,
                            color = Color(0xFFFFB4AB),
                        )
                    }
                }
            }
        }

        // 事件日志
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("最近事件（" + events.size + "）", fontWeight = FontWeight.Bold)
                if (events.isEmpty()) Text("暂无日志", fontSize = 12.sp)
                events.forEach { Text(it, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f)) }
            }
        }

        Text(
            text = "提示：如果好友列表里没有对方，说明对方还没点「我收到邀请码」完成配对；" +
                "如果他已配对但仍收不到消息，点上面的「强制重连」补收一次，再看订阅状态与错误。",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.55f),
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** 开发者面板的背景：半透明深色 —— 主页在下面被模糊后，这里呈现"毛玻璃"观感，文字不再重叠。 */
private fun devBackdrop() = androidx.compose.ui.graphics.Brush.verticalGradient(
    0f to Color(0xF00A0C12),
    0.5f to Color(0xF00C0F17),
    1f to Color(0xF0121A2B),
)

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm?.setPrimaryClip(ClipData.newPlainText("Heibuling 诊断", text))
}

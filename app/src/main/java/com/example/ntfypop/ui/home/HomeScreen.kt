package com.example.ntfypop.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ntfypop.core.Constants
import com.example.ntfypop.data.ChatRepo
import com.example.ntfypop.data.local.MessageRec
import com.example.ntfypop.data.local.Peer
import com.example.ntfypop.data.local.Store
import com.example.ntfypop.data.prefs.Identity
import com.example.ntfypop.data.prefs.SettingsStore
import com.example.ntfypop.data.remote.Pairing
import com.example.ntfypop.domain.EnvelopeCodec
import com.example.ntfypop.service.LinkService
import com.example.ntfypop.service.overlay.BallController
import com.example.ntfypop.service.overlay.OverlayController
import com.example.ntfypop.ui.components.GlassSectionTitle
import com.example.ntfypop.ui.dev.DevModeScreen
import com.example.ntfypop.ui.components.GlassSurface
import com.example.ntfypop.ui.components.glassBrush
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onOpenChat: (Peer) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings = remember { SettingsStore(context.applicationContext) }
    val store = remember { Store.get(context.applicationContext) }
    val repo = remember { ChatRepo(store, settings) }

    var identity by remember { mutableStateOf<Identity?>(null) }
    var overlayOk by remember { mutableStateOf(OverlayController.canDraw(context)) }
    var status by remember { mutableStateOf("") }
    var showPaste by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showDebug by remember { mutableStateOf(false) }
    var showInvitePicker by remember { mutableStateOf(false) }
    var showDev by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Peer?>(null) }
    val ballShowing by BallController.showing.collectAsState()

    val peers by store.peers.collectAsState()
    val pending by store.pending.collectAsState()
    val messages by store.messages.collectAsState()

    LaunchedEffect(Unit) {
        store.load()
        identity = settings.ensureInitialized()
        LinkService.start(context)
        if (OverlayController.canDraw(context)) BallController.show(context, settings)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) overlayOk = OverlayController.canDraw(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF0A0C12),
                    0.45f to Color(0xFF0D1018),
                    1f to Color(0xFF121A2B),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .then(if (showDev) Modifier.blur(16.dp) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 标题（长按可查看配对信息，默认隐藏 uid/服务器/topic）
            Text(
                text = "Heibuling",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = { showDebug = true })
                },
            )
            Text(
                text = "你们几个人的悄悄话，说完就散",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
            )

            // 我的昵称卡片
            GlassSurface(modifier = Modifier.fillMaxWidth(), onClick = {
                renameText = identity?.name.orEmpty()
                showRename = true
            }) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(42.dp).clip(CircleShape).background(glassBrush(false)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (identity?.name ?: "?").take(1),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = identity?.name ?: "…",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                        )
                        Text(
                            text = "点这里改昵称",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            if (!overlayOk) {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("还差一个权限才能收到弹框", color = Color(0xFFFFB4AB), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + context.packageName),
                                    ),
                                )
                            },
                        ) { Text("去授予悬浮窗权限") }
                    }
                }
            }

            // 悬浮球开关
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (ballShowing) "桌面悬浮球（已开启）" else "桌面悬浮球", color = Color.White, fontWeight = FontWeight.Medium)
                        Text(
                            text = "不离开当前 App 就能发消息",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 12.sp,
                        )
                    }
                    TextButton(onClick = {
                        if (ballShowing) {
                            BallController.hide()
                            status = "已关闭悬浮球"
                        } else {
                            BallController.show(context, settings)
                            status = "已开启悬浮球"
                        }
                    }) { Text(if (ballShowing) "关闭" else "开启") }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            GlassSectionTitle("我的好友（" + peers.size + "）")

            if (peers.isEmpty() && pending.isEmpty()) {
                Text(
                    text = "还没有好友。点下面的「邀请好友」生成邀请码，发给对方即可。",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 13.sp,
                )
            }

            peers.forEach { peer ->
                val last = messages[peer.uid]?.maxByOrNull { it.ts }
                GlassSurface(
                    modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onOpenChat(peer) },
                            onLongPress = { deleteTarget = peer },
                        )
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(glassBrush(false)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = (peer.remoteName.ifBlank { "?" }).take(1),
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = peer.remoteName.ifBlank { "好友" },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = last?.text?.take(26) ?: "点开开始聊天",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                            )
                        }
                        StateDot(last?.state)
                    }
                }
            }

            pending.forEach { p ->
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("等待对方加入", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "把邀请码发给他，他打开 App 粘贴即可",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                        )
                        TextButton(onClick = {
                            val self = identity ?: return@TextButton
                            inviteCode = Pairing.encode(
                                Pairing.Invite(sv = self.serverUrl, tp = p.topic, uid = self.uid, nm = self.name),
                            )
                        }) { Text("查看邀请码") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    // 已经有没用过的邀请时，不静默新建（否则会出现一排「等待对方加入」）
                    if (pending.isEmpty()) {
                        scope.launch {
                            val self = settings.ensureInitialized()
                            val topic = Constants.TOPIC_PREFIX +
                                EnvelopeCodec.randomCrockford(Constants.TOPIC_RANDOM_LEN)
                            store.addPendingInvite(topic)
                            val code = Pairing.encode(
                                Pairing.Invite(sv = self.serverUrl, tp = topic, uid = self.uid, nm = self.name),
                            )
                            inviteCode = code
                            copyToClipboard(context, code)
                            status = "邀请码已复制，发给好友即可"
                        }
                    } else {
                        showInvitePicker = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("邀请好友") }

            OutlinedButton(
                onClick = { showPaste = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("我收到邀请码（粘贴配对）") }

            // 开发者模式入口（放在最下面，平时不显眼）
            TextButton(onClick = { showDev = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "开发者模式",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.38f),
                )
            }

            if (status.isNotEmpty()) {
                Text(status, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showPaste) {
        AlertDialog(
            onDismissRequest = { showPaste = false },
            title = { Text("粘贴邀请码") },
            text = {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    label = { Text("NTP1-...") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val inv = Pairing.decode(pasteText)
                    showPaste = false
                    pasteText = ""
                    if (inv == null) {
                        status = "邀请码无法解析，请检查是否复制完整"
                        return@TextButton
                    }
                    scope.launch {
                        settings.setServerUrl(inv.sv)
                        store.upsertPeer(
                            Peer(
                                uid = inv.uid,
                                topic = inv.tp,
                                remoteName = inv.nm,
                                createdAt = System.currentTimeMillis(),
                                lastSeenAt = System.currentTimeMillis(),
                            ),
                        )
                        val ok = repo.sayHello(inv.sv, inv.tp)
                        status = if (ok) "配对成功，已向「" + inv.nm + "」报到" else "已保存，报到消息稍后重试"
                    }
                }) { Text("配对") }
            },
            dismissButton = { TextButton(onClick = { showPaste = false }) { Text("取消") } },
        )
    }

    inviteCode?.let { code ->
        AlertDialog(
            onDismissRequest = { inviteCode = null },
            title = { Text("把邀请码发给好友") },
            text = {
                Column {
                    Text(code, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("让对方装同一个 APK → 打开 App → 点「我收到邀请码」→ 粘贴这段即可。", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    copyToClipboard(context, code)
                    status = "已复制"
                }) { Text("复制") }
            },
            dismissButton = { TextButton(onClick = { inviteCode = null }) { Text("关闭") } },
        )
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("修改昵称") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(12) },
                    singleLine = true,
                    label = { Text("昵称") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = renameText.trim()
                    showRename = false
                    if (v.isNotEmpty()) {
                        scope.launch {
                            settings.setName(v)
                            identity = settings.ensureInitialized()
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("取消") } },
        )
    }

    if (showInvitePicker) {
        AlertDialog(
            onDismissRequest = { showInvitePicker = false },
            title = { Text("还有 " + pending.size + " 个邀请没人用") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "先把已有的邀请码发给对方；对方配对成功后，这里会自动消失。也可以用「删除」清掉不用的。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    pending.forEachIndexed { index, p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "邀请 " + (index + 1),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                val self = identity ?: return@TextButton
                                showInvitePicker = false
                                inviteCode = Pairing.encode(
                                    Pairing.Invite(sv = self.serverUrl, tp = p.topic, uid = self.uid, nm = self.name),
                                )
                            }) { Text("查看") }
                            TextButton(onClick = {
                                scope.launch { store.removePendingInvite(p.topic) }
                            }) { Text("删除") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showInvitePicker = false
                    if (pending.size >= 5) {
                        status = "待加入的邀请太多，请先删除几个"
                    } else {
                        scope.launch {
                            val self = settings.ensureInitialized()
                            val topic = Constants.TOPIC_PREFIX +
                                EnvelopeCodec.randomCrockford(Constants.TOPIC_RANDOM_LEN)
                            store.addPendingInvite(topic)
                            val code = Pairing.encode(
                                Pairing.Invite(sv = self.serverUrl, tp = topic, uid = self.uid, nm = self.name),
                            )
                            inviteCode = code
                            copyToClipboard(context, code)
                            status = "已新建邀请并复制"
                        }
                    }
                }) { Text("再新建一个") }
            },
            dismissButton = { TextButton(onClick = { showInvitePicker = false }) { Text("取消") } },
        )
    }

    // 长按标题才显示配对信息（默认完全隐藏 uid / 服务器 / topic）
    if (showDebug) {
        AlertDialog(
            onDismissRequest = { showDebug = false },
            title = { Text("配对信息（勿外传）") },
            text = {
                Column {
                    Text("昵称：" + (identity?.name ?: ""), style = MaterialTheme.typography.bodySmall)
                    Text("uid：" + (identity?.uid ?: ""), style = MaterialTheme.typography.bodySmall)
                    Text("服务器：" + (identity?.serverUrl ?: ""), style = MaterialTheme.typography.bodySmall)
                    Text("调试 topic：" + (identity?.topic ?: ""), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("这些信息等同于密码，不要发给任何人。", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val info = "uid=" + (identity?.uid ?: "") + " topic=" + (identity?.topic ?: "")
                    copyToClipboard(context, info)
                }) { Text("复制") }
            },
            dismissButton = { TextButton(onClick = { showDebug = false }) { Text("关闭") } },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除好友「" + target.remoteName.ifBlank { "好友" } + "」？") },
            text = {
                Text(
                    text = "会同时删除与他的聊天记录。对方不会收到任何通知；" +
                        "如果之后还想联系，需要重新生成邀请码让他再配对一次。",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val uid = target.uid
                    deleteTarget = null
                    scope.launch {
                        store.removePeerAndMessages(uid)
                        status = "已删除好友"
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }

    if (showDev) {
        DevModeScreen(onClose = { showDev = false })
    }

}

@Composable
fun StateDot(state: String?) {
    val color = when (state) {
        MessageRec.STATE_SENDING -> Color(0xFF9E9E9E)
        MessageRec.STATE_SENT -> Color(0xFFBDBDBD)
        MessageRec.STATE_DELIVERED -> Color(0xFF34D399)
        MessageRec.STATE_DISMISSED -> Color(0xFF60A5FA)
        MessageRec.STATE_FAILED -> Color(0xFFEF4444)
        else -> Color(0xFF4B5563)
    }
    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm?.setPrimaryClip(ClipData.newPlainText("Heibuling", text))
}

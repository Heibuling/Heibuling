package com.example.ntfypop.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ntfypop.data.prefs.SettingsStore
import com.example.ntfypop.service.overlay.OverlayController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 可选的品牌列表（第 4 项保活教程按品牌切换）。 */
private val ROM_BRANDS = listOf("vivo / iQOO", "OPPO / 一加", "小米 / Redmi", "华为 / 荣耀", "三星", "其他")

@Composable
fun PermissionWizard(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings = remember { SettingsStore(context.applicationContext) }

    var notifOk by remember { mutableStateOf(hasNotificationPermission(context)) }
    var overlayOk by remember { mutableStateOf(OverlayController.canDraw(context)) }
    var batteryOk by remember { mutableStateOf(isIgnoringBattery(context)) }
    var romExpanded by remember { mutableStateOf(false) }
    var romBrand by remember { mutableStateOf(detectBrand()) }
    var romRead by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val saved = settings.romBrand.first()
        if (saved.isNotBlank()) romBrand = saved
        romRead = settings.romGuideRead.first()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifOk = hasNotificationPermission(context)
                overlayOk = OverlayController.canDraw(context)
                batteryOk = isIgnoringBattery(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifOk = granted }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("让 Heibuling 能收到消息", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("这几项只影响「能不能收到弹框」，不涉及任何隐私数据。", style = MaterialTheme.typography.bodySmall)

        StepCard(
            index = 1,
            title = "通知权限",
            desc = "让「保持连接」的常驻通知可见（不影响收消息）",
            ok = notifOk,
            actionText = "允许",
            onAction = {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )

        StepCard(
            index = 2,
            title = "悬浮窗权限（最关键）",
            desc = "没有它，对方发来的消息不会弹出透明气泡",
            ok = overlayOk,
            actionText = "去设置",
            onAction = {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName)),
                )
            },
        )

        StepCard(
            index = 3,
            title = "电池优化白名单",
            desc = "避免深度休眠时被系统冻结而断连",
            ok = batteryOk,
            actionText = "去设置",
            onAction = {
                try {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + context.packageName),
                        ),
                    )
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            },
        )

        // 第 4 项：可展开 + 选品牌 + 读完变绿勾
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = (if (romRead) "✅ " else "❌ ") + "4. 后台保活设置",
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (romRead) {
                        "已按「" + romBrand + "」的指引设置完成"
                    } else {
                        "当前品牌：$romBrand　（点下面按钮展开教程，不同品牌路径完全不同）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { romExpanded = !romExpanded }) {
                        Text(if (romExpanded) "收起教程" else "展开教程")
                    }
                    if (!romRead) {
                        TextButton(onClick = {
                            scope.launch {
                                settings.setRomBrand(romBrand)
                                settings.setRomGuideRead(true)
                            }
                            romRead = true
                        }) { Text("我已按教程设置完") }
                    } else {
                        TextButton(onClick = {
                            scope.launch { settings.setRomGuideRead(false) }
                            romRead = false
                        }) { Text("重新设置") }
                    }
                }

                if (romExpanded) {
                    Text("选择你的手机品牌：", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ROM_BRANDS.forEach { b ->
                            val selected = b == romBrand
                            if (selected) {
                                Button(onClick = {
                                    romBrand = b
                                    scope.launch { settings.setRomBrand(b) }
                                }) { Text(b, style = MaterialTheme.typography.bodySmall) }
                            } else {
                                OutlinedButton(onClick = {
                                    romBrand = b
                                    scope.launch { settings.setRomBrand(b) }
                                }) { Text(b, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    romSteps(romBrand).forEachIndexed { i, s ->
                        Text((i + 1).toString() + ". " + s, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "设置完成后点上面的「我已按教程设置完」，这一项就会变成绿勾。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = {
                scope.launch { settings.setWizardDone(true) }
                onDone()
            },
            enabled = overlayOk,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (overlayOk) "完成，继续" else "请先开启悬浮窗权限") }

        TextButton(
            onClick = {
                scope.launch { settings.setWizardDone(true) }
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("稍后设置（可能收不到弹框）") }
    }
}

@Composable
private fun StepCard(
    index: Int,
    title: String,
    desc: String,
    ok: Boolean,
    actionText: String,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = (if (ok) "✅ " else "❌ ") + index.toString() + ". " + title,
                fontWeight = FontWeight.Bold,
            )
            Text(desc, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onAction) { Text(actionText) }
        }
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return true
    return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

private fun isIgnoringBattery(context: Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java) ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/** 自动识别当前手机品牌。 */
private fun detectBrand(): String {
    val m = Build.MANUFACTURER.lowercase() + " " + Build.BRAND.lowercase()
    return when {
        m.contains("vivo") || m.contains("iqoo") -> "vivo / iQOO"
        m.contains("oppo") || m.contains("oneplus") || m.contains("realme") -> "OPPO / 一加"
        m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> "小米 / Redmi"
        m.contains("huawei") || m.contains("honor") -> "华为 / 荣耀"
        m.contains("samsung") -> "三星"
        else -> "其他"
    }
}

/** 所选品牌对应的保活路径。 */
private fun romSteps(brand: String): List<String> = when (brand) {
    "vivo / iQOO" -> listOf(
        "设置 → 应用与权限 → 权限管理 → 自启动 → 允许 Heibuling",
        "设置 → 电池 → 后台耗电管理 → Heibuling → 允许「后台高耗电」",
        "设置 → 应用与权限 → 权限管理 → 悬浮窗 → 允许（有的机型写作「后台弹出界面」）",
        "打开最近任务 → 长按 Heibuling 卡片 → 点「锁定」",
        "设置 → 电池 → 关闭省电模式，或把 Heibuling 加入白名单",
    )
    "OPPO / 一加" -> listOf(
        "设置 → 应用 → 应用管理 → Heibuling → 允许「自启动」",
        "同一页 → 允许「关联启动」",
        "设置 → 电池 → 应用耗电管理 → Heibuling → 允许「后台活动」",
        "权限管理 → 「后台弹出界面」→ 允许",
        "打开最近任务 → 下拉 Heibuling 卡片加锁，避免被一键清理",
        "若开了省电模式，把 Heibuling 设为「无限制」",
    )
    "小米 / Redmi" -> listOf(
        "设置 → 应用设置 → 应用管理 → Heibuling → 自启动 打开",
        "同一页 → 「其他权限」→ 显示在其他应用上层 → 允许",
        "设置 → 省电与电池 → 应用智能省电 → Heibuling → 无限制",
        "最近任务 → 长按 Heibuling 卡片 → 加锁",
    )
    "华为 / 荣耀" -> listOf(
        "设置 → 应用 → 应用启动管理 → Heibuling → 改为「手动管理」（自启动/关联启动/后台活动全开）",
        "设置 → 电池 → 更多电池设置 → 打开「休眠时始终保持网络连接」",
        "设置 → 应用 → 权限 → 悬浮窗（或「显示在其他应用上层」）→ 允许",
        "最近任务 → 下拉 Heibuling 卡片加锁",
    )
    "三星" -> listOf(
        "设置 → 应用程序 → Heibuling → 电池 → 不受限制",
        "设置 → 应用程序 → Heibuling → 允许「在其他应用上层显示」",
        "设置 → 电池 → 关闭对 Heibuling 的自适应电池优化",
    )
    else -> listOf(
        "在设置里搜索「自启动」，允许 Heibuling 自启动",
        "在设置里搜索「后台活动 / 后台耗电」，允许 Heibuling 后台运行",
        "在设置里搜索「悬浮窗 / 后台弹出界面」，允许 Heibuling",
        "打开最近任务，把 Heibuling 加锁，避免被一键清理",
    )
}

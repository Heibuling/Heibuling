package com.example.ntfypop.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ntfypop.data.prefs.SettingsStore
import kotlinx.coroutines.launch

/**
 * 首次进入必须先设置昵称：朋友收到你的消息时，气泡上显示的就是它。
 */
@Composable
fun NicknameScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context.applicationContext) }
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "给自己起个昵称",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "朋友收到你的消息时，屏幕上的气泡会显示这个名字。以后可以在设置里改。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(12) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("昵称") },
            placeholder = { Text("比如：小黑 / 阿布 / 你的名字") },
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    // 必须先建立身份（uid/topic），否则后续 ensureInitialized 会把昵称当成默认值重置
                    settings.ensureInitialized()
                    settings.setName(name.trim())
                    settings.setNicknameSet(true)
                    onDone()
                }
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("进入 Heibuling") }
    }
}

package com.example.ntfypop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.ntfypop.data.local.Peer
import com.example.ntfypop.data.prefs.SettingsStore
import com.example.ntfypop.ui.chat.ChatScreen
import com.example.ntfypop.ui.home.HomeScreen
import com.example.ntfypop.ui.onboarding.NicknameScreen
import com.example.ntfypop.ui.onboarding.PermissionWizard
import com.example.ntfypop.ui.theme.NtfyPopTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NtfyPopTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNav()
                }
            }
        }
    }
}

@Composable
private fun AppNav() {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context.applicationContext) }
    var ready by remember { mutableStateOf<Boolean?>(null) }
    var nickSet by remember { mutableStateOf<Boolean?>(null) }
    var opened by remember { mutableStateOf<Peer?>(null) }

    LaunchedEffect(Unit) {
        ready = settings.wizardDone.first()
        nickSet = settings.nicknameSet.first()
    }

    val peer = opened
    val wizardDone = ready
    val hasNick = nickSet
    when {
        wizardDone == null || hasNick == null -> Box(modifier = Modifier.fillMaxSize())
        !wizardDone -> PermissionWizard(onDone = { ready = true })
        !hasNick -> NicknameScreen(onDone = { nickSet = true })
        peer == null -> HomeScreen(onOpenChat = { opened = it })
        else -> ChatScreen(peer = peer, onBack = { opened = null })
    }
}

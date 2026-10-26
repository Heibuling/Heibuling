package com.example.ntfypop.service.overlay

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView

/**
 * 能拦 BACK 的 Compose 容器。
 *
 * 之前的 setOnKeyListener 不生效：面板窗口虽然可聚焦，但视图本身没拿到焦点，
 * 必须重写 dispatchKeyEvent 才能在返回键/侧滑返回时收到事件。
 */
class BackKeyComposeView(
    context: Context,
    private val onBack: () -> Unit,
) : AbstractComposeView(context) {

    private var panelContent: (@Composable () -> Unit)? = null

    fun setPanelContent(content: @Composable () -> Unit) {
        panelContent = content
    }

    @Composable
    override fun Content() {
        panelContent?.invoke()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Log.i("Heibuling", "面板收到按键 code=" + event.keyCode + " action=" + event.action)
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

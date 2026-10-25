package com.example.ntfypop.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ntfypop.core.Constants
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 用 WindowManager + ComposeView 显示顶部半透明气泡。
 * 不使用 Activity（Android 15 后台启动 Activity 受限），且不抢焦点。
 */
object OverlayController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = ArrayDeque<OverlayItem>()
    private var draining = false

    private val _current = MutableStateFlow<OverlayItem?>(null)
    val current: StateFlow<OverlayItem?> = _current

    private var pending: CompletableDeferred<Boolean>? = null
    private var wm: WindowManager? = null
    private var owner: OverlayLifecycleOwner? = null
    private var view: ComposeView? = null
    private var attached = false

    @Volatile
    private var queuePaused = false

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun enqueue(context: Context, item: OverlayItem) {
        queue.addLast(item)
        val app = context.applicationContext
        scope.launch { drain(app) }
    }

    /**
     * 下载完成后原地替换当前气泡里的图片，不重新排队、不闪烁。
     * path 为空表示下载失败，此时把占位文案换成失败提示。
     */
    fun attachImage(mid: String, path: String) {
        val cur = _current.value ?: return
        if (cur.id != mid) return
        _current.value = if (path.isBlank()) {
            cur.copy(text = "（图片已过期或下载失败）")
        } else {
            cur.copy(imagePath = path, text = "")
        }
    }

    /** 语音下载完成后原地替换。 */
    fun attachVoice(mid: String, path: String, durationMs: Long, peaks: List<Float>) {
        val cur = _current.value ?: return
        if (cur.id != mid) return
        _current.value = cur.copy(
            voicePath = path,
            voiceDurationMs = durationMs,
            voicePeaks = peaks,
            text = "",
        )
    }

    /** 涂鸦笔画就绪（内联或附件下载完成后），原地替换并开始逐笔回放。 */
    fun attachDoodle(mid: String, strokes: List<com.example.ntfypop.media.DoodleStroke>) {
        val cur = _current.value ?: return
        if (cur.id != mid) return
        _current.value = cur.copy(doodleStrokes = strokes, text = "")
    }

    /** 附件过期或下载失败。 */
    fun failMedia(mid: String, message: String) {
        val cur = _current.value ?: return
        if (cur.id != mid) return
        _current.value = cur.copy(text = message)
    }

    /** 展开悬浮球输入面板时暂停出队，避免气泡盖住输入面板。 */
    fun pauseQueue(paused: Boolean) {
        queuePaused = paused
    }

    fun dismissCurrent() {
        pending?.complete(true)
    }

    private suspend fun drain(context: Context) {
        if (draining) return
        draining = true
        try {
            while (queue.isNotEmpty()) {
                while (queuePaused) delay(150)
                showOne(context, queue.removeFirst())
            }
        } finally {
            draining = false
            detach()
        }
    }

    private suspend fun showOne(context: Context, item: OverlayItem) {
        if (!canDraw(context)) {
            Log.w(Constants.LOG_TAG, "没有悬浮窗权限，不弹框（不回执 delivered）")
            item.onClosed(false)
            return
        }
        if (!attach(context)) {
            item.onClosed(false)
            return
        }
        val d = CompletableDeferred<Boolean>()
        pending = d
        _current.value = item
        view?.let { awaitFirstFrame(it) }
        Log.i(Constants.LOG_TAG, "气泡已显示: " + item.sender)
        item.onShown()
        val manual = withTimeoutOrNull(Constants.BUBBLE_DURATION_MS) { d.await() } ?: false
        _current.value = null
        delay(Constants.EXIT_ANIM_MS.toLong())
        pending = null
        item.onClosed(manual)
    }

    private fun attach(context: Context): Boolean {
        if (attached && view != null) return true
        return try {
            val o = OverlayLifecycleOwner()
            o.onStart()
            val cv = ComposeView(context).apply {
                setViewTreeLifecycleOwner(o)
                setViewTreeViewModelStoreOwner(o)
                setViewTreeSavedStateRegistryOwner(o)
                setContent { OverlayHost() }
            }
            val manager = context.getSystemService(WindowManager::class.java) ?: return false
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                y = statusBarHeight(context) + dp(context, 4)
            }
            manager.addView(cv, lp)
            owner = o
            view = cv
            wm = manager
            attached = true
            true
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG, "悬浮窗创建失败: " + e.message)
            false
        }
    }

    private fun detach() {
        val v = view
        if (v != null) {
            try {
                wm?.removeViewImmediate(v)
            } catch (e: Exception) {
                Log.w(Constants.LOG_TAG, "移除悬浮窗失败: " + e.message)
            }
        }
        attached = false
        view = null
        wm = null
        owner?.onStop()
        owner = null
    }

    private suspend fun awaitFirstFrame(v: View) {
        if (v.isAttachedToWindow && v.height > 0) return
        suspendCancellableCoroutine<Unit> { cont ->
            val vto = v.viewTreeObserver
            val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (v.height > 0) {
                        if (v.viewTreeObserver.isAlive) v.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            }
            vto.addOnGlobalLayoutListener(listener)
            cont.invokeOnCancellation {
                if (vto.isAlive) vto.removeOnGlobalLayoutListener(listener)
            }
        }
    }

    private fun statusBarHeight(context: Context): Int {
        val res = context.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else dp(context, 24)
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

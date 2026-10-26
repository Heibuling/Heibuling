package com.example.ntfypop.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ntfypop.core.Constants
import com.example.ntfypop.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 悬浮球 + 紧凑输入面板。
 *
 * 两个技术要点：
 * 1) 球窗口必须是 WRAP_CONTENT 并用 updateViewLayout 移动窗口本身。
 *    如果做成全屏窗口再在内部偏移，会挡住底下所有 App 的触摸（NOT_TOUCH_MODAL 只对"窗口外"生效）。
 * 2) 面板窗口**不能带 FLAG_NOT_FOCUSABLE**，否则收不到键盘输入；收起后必须把球恢复成不可聚焦，
 *    否则会抢走用户正在用的 App 的焦点。
 */
object BallController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var wm: WindowManager? = null
    private var ballView: ComposeView? = null
    private var ballLp: WindowManager.LayoutParams? = null
    private var ballOwner: OverlayLifecycleOwner? = null

    private var panelView: BackKeyComposeView? = null
    private var panelOwner: OverlayLifecycleOwner? = null
    private var open = false
    private var backCallback: OnBackInvokedCallback? = null

    private val _panelOpen = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** 面板是否展开（供 Activity 拦截返回键用） */
    val panelOpen: kotlinx.coroutines.flow.StateFlow<Boolean> = _panelOpen

    private val _showing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val showing: kotlinx.coroutines.flow.StateFlow<Boolean> = _showing

    private var screenW = 0
    private var screenH = 0
    private var ballSize = 0

    /** 与屏幕左右边缘保持的安全距离：ColorOS 等 ROM 的侧边手势区约 200px 宽，
     *  球贴边会被系统截走触摸，实测必须留出足够余量。 */
    private const val EDGE_MARGIN_DP = 24

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isShowing(): Boolean = ballView != null

    fun show(context: Context, settings: SettingsStore) {
        val ctx = context.applicationContext
        if (!canDraw(ctx) || ballView != null) return
        try {
            val dm = ctx.resources.displayMetrics
            screenW = dm.widthPixels
            screenH = dm.heightPixels
            ballSize = dp(ctx, 48)

            val owner = OverlayLifecycleOwner().also { it.onStart() }
            val view = ComposeView(ctx).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent { FloatingBallView() }
            }
            val manager = ctx.getSystemService(WindowManager::class.java) ?: return
            val lp = WindowManager.LayoutParams(
                ballSize,
                ballSize,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    // 关键：不加这个标志，x/y 会被当成"内容区坐标"，系统会再叠加一个状态栏高度，
                    // 导致窗口实际位置比预期下移（实测偏移 141px），点击全部打偏。
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = screenW - ballSize - dp(ctx, EDGE_MARGIN_DP)
                y = (screenH * 0.65f).toInt()
            }
            // 用原生 OnTouchListener 处理点击/拖动：实测在 WRAP_CONTENT 悬浮窗里
            // Compose 的 pointerInput 收不到事件（窗口层能收到，但触摸没有进入 Compose 输入管线），
            // 所以这里绕过 Compose 手势，直接在 View 层处理，稳定可靠。
            val slop = ViewConfiguration.get(ctx).scaledTouchSlop
            var lastX = 0f
            var lastY = 0f
            var moved = 0f
            var dragged = false
            view.setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.i(Constants.LOG_TAG, "球触摸 DOWN raw=" + ev.rawX + "," + ev.rawY + " local=" + ev.x + "," + ev.y)
                        lastX = ev.rawX
                        lastY = ev.rawY
                        moved = 0f
                        dragged = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - lastX
                        val dy = ev.rawY - lastY
                        lastX = ev.rawX
                        lastY = ev.rawY
                        moved += kotlin.math.abs(dx) + kotlin.math.abs(dy)
                        if (!dragged && moved > slop) dragged = true
                        if (dragged) drag(dx, dy)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        Log.i(Constants.LOG_TAG, "球触摸 UP dragged=" + dragged)
                        if (dragged) {
                            endDrag(ctx, settings)
                        } else {
                            Log.i(Constants.LOG_TAG, "悬浮球被点击（原生触摸）")
                            togglePanel(ctx, settings)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        Log.i(Constants.LOG_TAG, "球触摸 CANCEL")
                        if (dragged) endDrag(ctx, settings)
                        true
                    }
                    else -> false
                }
            }
            manager.addView(view, lp)

            wm = manager
            ballView = view
            ballLp = lp
            ballOwner = owner
            _showing.value = true
            Log.i(Constants.LOG_TAG, "悬浮球已显示 x=" + lp.x + " y=" + lp.y + " size=" + ballSize + " screen=" + screenW + "x" + screenH)

            // 恢复上次的位置（Y 比例）
            scope.launch {
                try {
                    val ratio = settings.ballY.first()
                    if (ratio in 0.02f..0.96f) {
                        lp.y = (screenH * ratio).toInt()
                        manager.updateViewLayout(view, lp)
                    }
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG, "悬浮球创建失败: " + e.message)
        }
    }

    fun hide() {
        closePanel()
        val v = ballView ?: return
        try {
            wm?.removeViewImmediate(v)
        } catch (e: Exception) {
        }
        ballView = null
        ballLp = null
        ballOwner?.onStop()
        ballOwner = null
        _showing.value = false
        Log.i(Constants.LOG_TAG, "悬浮球已移除")
    }

    fun drag(dx: Float, dy: Float) {
        val lp = ballLp ?: return
        val manager = wm ?: return
        val view = ballView ?: return
        lp.x = (lp.x + dx.toInt()).coerceIn(0, (screenW - ballSize).coerceAtLeast(0))
        lp.y = (lp.y + dy.toInt()).coerceIn(0, (screenH - ballSize).coerceAtLeast(0))
        try {
            manager.updateViewLayout(view, lp)
        } catch (e: Exception) {
        }
    }

    /** 松手：吸附到最近的左右边缘，并持久化 Y 比例。 */
    fun endDrag(context: Context, settings: SettingsStore) {
        val lp = ballLp ?: return
        val manager = wm ?: return
        val view = ballView ?: return
        val ctx = context.applicationContext
        val margin = dp(ctx, EDGE_MARGIN_DP)
        lp.x = if (lp.x + ballSize / 2 < screenW / 2) margin else screenW - ballSize - margin
        try {
            manager.updateViewLayout(view, lp)
        } catch (e: Exception) {
        }
        scope.launch {
            try {
                settings.setBallY(lp.y.toFloat() / screenH.coerceAtLeast(1))
            } catch (e: Exception) {
            }
        }
    }

    fun togglePanel(context: Context, settings: SettingsStore) {
        if (open) closePanel() else openPanel(context, settings)
    }

    private fun openPanel(context: Context, settings: SettingsStore) {
        val ctx = context.applicationContext
        if (open) return
        try {
            val owner = OverlayLifecycleOwner().also { it.onStart() }
            val view = BackKeyComposeView(ctx, onBack = { closePanel() }).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setPanelContent { BallInputPanel(onClose = { closePanel() }) }
            }
            val manager = wm ?: return
            val width = dp(ctx, 300)
            val lp = WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                val bx = ballLp?.x ?: 0
                val by = ballLp?.y ?: 0
                x = if (bx > screenW / 2) (screenW - width - dp(ctx, 10)) else dp(ctx, 10)
                x = x.coerceAtLeast(0)
                val above = by - dp(ctx, 230)
                y = if (above > dp(ctx, 40)) above else (by + ballSize + dp(ctx, 10))
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
            manager.addView(view, lp)
            panelView = view
            panelOwner = owner
            open = true
            _panelOpen.value = true
            OverlayController.pauseQueue(true)

            // Android 16 的预测性返回由系统分发器处理，覆盖窗口收不到 BACK 键。
            // 但"按返回"的第一下一定会收起键盘 —— 所以监听键盘收起，顺手把面板一起收起。
            val imeState = booleanArrayOf(false)
            view.setOnApplyWindowInsetsListener { v, insets ->
                val imeBottom = if (Build.VERSION.SDK_INT >= 30) {
                    insets.getInsets(android.view.WindowInsets.Type.ime()).bottom
                } else {
                    0
                }
                if (imeBottom > 0) {
                    imeState[0] = true
                } else if (imeState[0]) {
                    imeState[0] = false
                    Log.i(Constants.LOG_TAG, "键盘已收起，自动收起输入面板")
                    closePanel()
                }
                v.onApplyWindowInsets(insets)
                insets
            }
            // 正式做法：覆盖窗口也要在 Android 13+ 的返回分发器上注册回调，
            // 否则侧滑返回/返回键会被系统吃掉（预测性返回），面板收不到任何按键事件
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    val dispatcher = view.findOnBackInvokedDispatcher()
                    if (dispatcher != null) {
                        val cb = OnBackInvokedCallback { closePanel() }
                        dispatcher.registerOnBackInvokedCallback(
                            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                            cb,
                        )
                        backCallback = cb
                        Log.i(Constants.LOG_TAG, "已在返回分发器上注册面板收起回调")
                    }
                } catch (e: Exception) {
                    Log.w(Constants.LOG_TAG, "注册返回回调失败: " + e.message)
                }
            }
            view.requestFocus()
            view.postDelayed({
                try {
                    if (Build.VERSION.SDK_INT >= 31) {
                        val imm = ctx.getSystemService(InputMethodManager::class.java)
                        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                    }
                } catch (e: Exception) {
                }
            }, 250)
            Log.i(Constants.LOG_TAG, "输入面板已展开")
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG, "输入面板创建失败: " + e.message)
        }
    }

    fun closePanel() {
        if (!open) return
        backCallback?.let { cb ->
            try {
                if (Build.VERSION.SDK_INT >= 33) panelView?.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(cb)
            } catch (e: Exception) {
            }
        }
        backCallback = null
        open = false
        _panelOpen.value = false
        Log.i(Constants.LOG_TAG, "面板已收起（状态已记录）")
        OverlayController.pauseQueue(false)
        val v = panelView
        if (v != null) {
            try {
                wm?.removeViewImmediate(v)
            } catch (e: Exception) {
            }
        }
        panelView = null
        panelOwner?.onStop()
        panelOwner = null
        Log.i(Constants.LOG_TAG, "输入面板已收起")
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

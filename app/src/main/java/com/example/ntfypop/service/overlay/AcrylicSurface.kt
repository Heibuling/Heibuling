package com.example.ntfypop.service.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 亚克力 / 高光玻璃质感卡片。
 *
 * 技术真相：第三方应用无法对"窗口背后"的内容做真实背景模糊（那是系统特权），
 * RenderEffect 只能模糊本窗口自身的内容。因此这里用「5 层配方」模拟亚克力：
 *   半透明深底 + 顶部高光渐变 + 1dp 白色内描边 + 大圆角 + 外部投影，
 * 并且只对**装饰层**做柔化（Modifier.blur 在 API 31+ 生效、以下自动忽略），
 * 绝不模糊文字层，避免文字糊成一团。
 */
@Composable
fun AcrylicSurface(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 20.dp, shape = shape)
            .clip(shape)
            .background(Color(0xE6151619))
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.22f), shape = shape)
            .clickable(onClick = onClick),
    ) {
        // 装饰层：顶部高光渐变（可柔化），位于内容之下
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(8.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.10f),
                        0.35f to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                ),
        )
        content()
    }
}

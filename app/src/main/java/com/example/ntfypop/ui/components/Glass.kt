package com.example.ntfypop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 应用内统一的高光玻璃底：半透明深底 + 顶部高光 + 细白描边 + 大圆角 + 投影。 */
fun glassBrush(dark: Boolean = true): Brush = if (dark) {
    Brush.verticalGradient(
        0f to Color(0xF01C2029),
        0.45f to Color(0xE6161A22),
        1f to Color(0xE612151B),
    )
} else {
    Brush.verticalGradient(
        0f to Color(0xF02A3E72),
        0.5f to Color(0xE61D4ED8),
        1f to Color(0xE61A3FB0),
    )
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    brush: Brush = glassBrush(),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(elevation = 12.dp, shape = shape)
            .clip(shape)
            .background(brush)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.30f),
                    0.4f to Color.White.copy(alpha = 0.10f),
                    1f to Color.White.copy(alpha = 0.06f),
                ),
                shape = shape,
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        content = content,
    )
}

/** 玻璃输入框：透明底 + 高光描边，代替默认的 OutlinedTextField。 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    maxLines: Int = 4,
    focusRequester: FocusRequester? = null,
) {
    GlassSurface(modifier = modifier, shape = RoundedCornerShape(24.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 13.dp),
        ) {
            if (value.isEmpty()) {
                androidx.compose.material3.Text(
                    text = placeholder,
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 15.sp,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                maxLines = maxLines,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color(0xFF7DD3FC)),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            )
        }
    }
}

@Composable
fun GlassSectionTitle(text: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier,
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}

package com.example.ntfypop.ui.doodle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ntfypop.media.DoodleStroke
import kotlin.math.roundToInt

private val PALETTE = listOf(
    "#FFFFFFFF", "#FFFF6B6B", "#FF4ADE80", "#FF60A5FA", "#FFFACC15", "#FFC084FC",
)
private val WIDTHS = listOf(3f, 6f, 12f)

/**
 * 全屏涂鸦画布：采集归一化坐标与时间戳，接收端按时间戳逐笔回放。
 * 注意：这里的坐标按画布宽高归一化（0..1），笔宽按画布宽归一化，保证不同分辨率一致。
 */
@Composable
fun DoodleScreen(
    onCancel: () -> Unit,
    onSend: (strokes: List<DoodleStroke>, canvasW: Int, canvasH: Int) -> Unit,
) {
    val strokes = remember { mutableStateListOf<DoodleStroke>() }
    var color by remember { mutableStateOf(PALETTE[0]) }
    var widthPx by remember { mutableStateOf(WIDTHS[1]) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var strokeStart by remember { mutableStateOf(0) }
    val startAt = remember { System.currentTimeMillis() }

    fun nowMs(): Int = (System.currentTimeMillis() - startAt).toInt()

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0C0F)).safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("← 取消") }
            Text("涂鸦", fontSize = 18.sp, color = Color.White)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1) }) { Text("撤销") }
            TextButton(onClick = { strokes.clear() }) { Text("清空") }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(10.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                .background(Color(0xFF15161A))
                .border(1.dp, Color.White.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                .onSizeChanged { canvasSize = it }
                .pointerInput(color, widthPx) {
                    detectDragGestures(
                        onDragStart = { off ->
                            live = listOf(off)
                            strokeStart = nowMs()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            live = live + change.position
                        },
                        onDragEnd = {
                            val w = canvasSize.width
                            val h = canvasSize.height
                            if (w > 0 && h > 0 && live.size >= 2) {
                                val flat = ArrayList<Float>(live.size * 2)
                                live.forEach { o ->
                                    flat.add((o.x / w).coerceIn(0f, 1f))
                                    flat.add((o.y / h).coerceIn(0f, 1f))
                                }
                                strokes.add(
                                    DoodleStroke(
                                        c = color,
                                        w = widthPx / w,
                                        t0 = strokeStart,
                                        t1 = nowMs(),
                                        p = flat,
                                    ),
                                )
                            }
                            live = emptyList()
                        },
                    )
                },
        ) {
            strokes.forEach { s -> drawStroke(s, size.width, size.height) }
            if (live.size >= 2 && canvasSize.width > 0) {
                val path = Path()
                live.forEachIndexed { i, o ->
                    if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                drawPath(
                    path = path,
                    color = parseColor(color),
                    style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PALETTE.forEach { c ->
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(parseColor(c))
                        .border(
                            width = if (c == color) 3.dp else 1.dp,
                            color = if (c == color) Color.White else Color.White.copy(alpha = 0.3f),
                            shape = CircleShape,
                        )
                        .clickable { color = c },
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            WIDTHS.forEach { w ->
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (w == widthPx) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        .clickable { widthPx = w },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size((w / 1.6f).dp.coerceAtLeast(2.dp))
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                }
            }
        }

        Button(
            onClick = { onSend(strokes.toList(), canvasSize.width, canvasSize.height) },
            enabled = strokes.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
        ) { Text("发送涂鸦（" + strokes.size + " 笔）") }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    s: DoodleStroke,
    w: Float,
    h: Float,
) {
    val pts = s.p
    if (pts.size < 4) return
    val path = Path()
    var i = 0
    while (i + 1 < pts.size) {
        val x = pts[i] * w
        val y = pts[i + 1] * h
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        i += 2
    }
    drawPath(
        path = path,
        color = parseColor(s.c),
        style = Stroke(
            width = (s.w * w).coerceAtLeast(1f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    Color.White
}

private fun Float.dp(): androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(this)

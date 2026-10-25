package com.example.ntfypop.service.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ntfypop.core.Constants

@Composable
fun OverlayHost() {
    val item by OverlayController.current.collectAsState()
    val cur = item
    if (cur == null) {
        Box(modifier = Modifier.fillMaxWidth())
    } else {
        OverlayBubble(
            sender = cur.sender,
            text = cur.text,
            imagePath = cur.imagePath,
            voicePath = cur.voicePath,
            voiceDurationMs = cur.voiceDurationMs,
            voicePeaks = cur.voicePeaks,
            doodleStrokes = cur.doodleStrokes,
            onTap = { OverlayController.dismissCurrent() },
        )
    }
}

@Composable
fun OverlayBubble(
    sender: String,
    text: String,
    imagePath: String? = null,
    voicePath: String? = null,
    voiceDurationMs: Long = 0L,
    voicePeaks: List<Float> = emptyList(),
    doodleStrokes: List<com.example.ntfypop.media.DoodleStroke> = emptyList(),
    onTap: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        visible = true
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(Constants.BUBBLE_DURATION_MS.toInt(), easing = LinearEasing),
        )
    }

    val offsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else (-20).dp,
        animationSpec = tween(Constants.ENTER_ANIM_MS),
    )
    val alphaValue by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(Constants.ENTER_ANIM_MS),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .offset(y = offsetY)
            .alpha(alphaValue),
    ) {
        AcrylicSurface(onClick = onTap) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sender,
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "×",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 18.sp,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (imagePath != null) {
                    val bitmap = remember(imagePath) { android.graphics.BitmapFactory.decodeFile(imagePath) }
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                if (voicePath != null) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    var playing by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color.White.copy(alpha = 0.16f))
                                .clickable {
                                    if (playing) {
                                        com.example.ntfypop.media.VoicePlayer.stop()
                                        playing = false
                                    } else {
                                        playing = true
                                        com.example.ntfypop.media.VoicePlayer.play(voicePath) { playing = false }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (playing) "❚❚" else "▶", color = Color.White, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Row(
                            modifier = Modifier.weight(1f).height(26.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            val bars = if (voicePeaks.isEmpty()) List(32) { 0.2f } else voicePeaks
                            bars.forEach { p ->
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height((4f + p * 22f).dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                                        .background(Color.White.copy(alpha = 0.55f)),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = formatDuration(voiceDurationMs),
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (text.isNotBlank()) {
                        Text(text = text, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
                if (doodleStrokes.isNotEmpty()) {
                    val raw = remember(doodleStrokes) { com.example.ntfypop.media.DoodleCodec.rawDurationMs(doodleStrokes) }
                    val playMs = remember(doodleStrokes) { com.example.ntfypop.media.DoodleCodec.replayDurationMs(doodleStrokes) }
                    val t = remember { Animatable(0f) }
                    LaunchedEffect(doodleStrokes) {
                        t.snapTo(0f)
                        t.animateTo(raw.toFloat(), animationSpec = tween(playMs, easing = LinearEasing))
                    }
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.fillMaxWidth().height(170.dp),
                    ) {
                        doodleStrokes.forEach { s ->
                            val dur = (s.t1 - s.t0).coerceAtLeast(1)
                            val frac = ((t.value - s.t0) / dur).coerceIn(0f, 1f)
                            if (frac > 0f) {
                                val pts = s.p
                                val total = pts.size / 2
                                if (total >= 2) {
                                    val count = (total * frac).toInt().coerceIn(2, total)
                                    val path = Path()
                                    for (i in 0 until count) {
                                        val x = pts[i * 2] * size.width
                                        val y = pts[i * 2 + 1] * size.height
                                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                    }
                                    drawPath(
                                        path = path,
                                        color = parseColor(s.c),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = (s.w * size.width).coerceAtLeast(1f),
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                            join = androidx.compose.ui.graphics.StrokeJoin.Round,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                if (voicePath == null && doodleStrokes.isEmpty()) Text(
                    text = text,
                    color = Color.White.copy(alpha = 0.95f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color.White.copy(alpha = 0.55f),
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val m = total / 60L
    val s = total % 60L
    return m.toString() + ":" + (if (s < 10) "0" + s else s.toString())
}

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    Color.White
}

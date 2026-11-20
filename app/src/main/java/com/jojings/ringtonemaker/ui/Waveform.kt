package com.jojings.ringtonemaker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.jojings.ringtonemaker.audio.Pcm
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private enum class DragTarget { Start, End, Move, Pan }

/**
 * Waveform with a draggable selection.
 *  - drag the green knob (top) to move the start, the blue knob (bottom) to move the end
 *  - drag inside the yellow selection to slide it
 *  - drag outside it to scroll (when zoomed in)
 *  - tap outside the selection to jump the nearest edge there
 */
@Composable
fun Waveform(
    peaks: FloatArray,
    durationMs: Long,
    selStart: Long,
    selEnd: Long,
    playheadMs: Long,
    viewStart: Long,
    viewSpan: Long,
    onStart: (Long) -> Unit,
    onEnd: (Long) -> Unit,
    onMove: (Long) -> Unit,
    onPan: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s by rememberUpdatedState(selStart)
    val e by rememberUpdatedState(selEnd)
    val vs by rememberUpdatedState(viewStart)
    val span by rememberUpdatedState(viewSpan)
    val start by rememberUpdatedState(onStart)
    val end by rememberUpdatedState(onEnd)
    val move by rememberUpdatedState(onMove)
    val pan by rememberUpdatedState(onPan)

    val reveal = remember(peaks) { Animatable(0f) }
    LaunchedEffect(peaks) { reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    Canvas(
        modifier
            .pointerInput(Unit) {
                var target = DragTarget.Pan
                var anchorStart = 0L
                var anchorEnd = 0L
                var anchorView = 0L
                var msPerPx = 1f
                var acc = 0f
                detectDragGestures(
                    onDragStart = { o ->
                        msPerPx = span.toFloat() / size.width
                        val xs = (s - vs) / msPerPx
                        val xe = (e - vs) / msPerPx
                        val slop = 30.dp.toPx()
                        val ds = abs(o.x - xs)
                        val de = abs(o.x - xe)
                        target = when {
                            min(ds, de) < slop -> if (o.x < (xs + xe) / 2f) DragTarget.Start else DragTarget.End
                            o.x in xs..xe -> DragTarget.Move
                            else -> DragTarget.Pan
                        }
                        anchorStart = s
                        anchorEnd = e
                        anchorView = vs
                        acc = 0f
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        acc += drag.x
                        val d = (acc * msPerPx).toLong()
                        when (target) {
                            DragTarget.Start -> start(anchorStart + d)
                            DragTarget.End -> end(anchorEnd + d)
                            DragTarget.Move -> move(anchorStart + d)
                            DragTarget.Pan -> pan(anchorView - d)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val ms = vs + (o.x / size.width * span).toLong()
                    if (ms < s) start(ms) else if (ms > e) end(ms)
                }
            },
    ) {
        drawWave(peaks, durationMs, selStart, selEnd, playheadMs, viewStart, viewSpan, reveal.value)
    }
}

private fun DrawScope.drawWave(
    peaks: FloatArray,
    durationMs: Long,
    selStart: Long,
    selEnd: Long,
    playheadMs: Long,
    viewStart: Long,
    viewSpan: Long,
    reveal: Float,
) {
    val w = size.width
    val h = size.height
    val mid = h / 2
    val msPerPx = viewSpan.toFloat() / w
    fun x(ms: Long) = (ms - viewStart) / msPerPx

    // selection background
    val xs = x(selStart)
    val xe = x(selEnd)
    drawRect(PopColors.Yellow.copy(alpha = 0.55f), Offset(xs, 0f), Size(xe - xs, h))

    // centre line
    drawLine(PopColors.Ink.copy(alpha = 0.15f), Offset(0f, mid), Offset(w, mid), 2f)

    // bars
    val barW = 3.dp.toPx()
    val step = barW + 2.dp.toPx()
    val nBars = (w / step).toInt()
    val selBrush = Brush.verticalGradient(
        listOf(PopColors.Pink, PopColors.Purple, PopColors.Pink), startY = mid - h * 0.45f, endY = mid + h * 0.45f,
    )
    val maxBar = h * 0.42f
    val minBar = 1.5.dp.toPx()
    for (i in 0 until nBars) {
        val bx = i * step
        val ms0 = viewStart + (bx * msPerPx).toLong()
        if (ms0 > durationMs) break
        val ms1 = ms0 + (step * msPerPx).toLong()
        val p0 = (ms0 / Pcm.MS_PER_PEAK).toInt()
        val p1 = max(p0 + 1, (ms1 / Pcm.MS_PER_PEAK).toInt())
        var pk = 0f
        for (k in p0 until min(p1, peaks.size)) if (peaks[k] > pk) pk = peaks[k]
        val grow = ((reveal * 1.4f - bx / w) * 3f).coerceIn(0f, 1f)
        val bh = max(minBar, pk * maxBar * grow)
        val center = (ms0 + ms1) / 2
        val inSel = center in selStart..selEnd
        if (inSel) {
            drawRoundRect(selBrush, Offset(bx, mid - bh), Size(barW, bh * 2), CornerRadius(barW / 2))
        } else {
            drawRoundRect(PopColors.Ink.copy(alpha = 0.28f), Offset(bx, mid - bh), Size(barW, bh * 2), CornerRadius(barW / 2))
        }
    }

    // playhead
    if (playheadMs >= 0) {
        val px = x(playheadMs)
        drawLine(PopColors.Ink, Offset(px, 0f), Offset(px, h), 3.dp.toPx())
        val tri = Path().apply {
            moveTo(px - 7.dp.toPx(), 0f); lineTo(px + 7.dp.toPx(), 0f); lineTo(px, 9.dp.toPx()); close()
        }
        drawPath(tri, PopColors.Ink)
    }

    // handles
    drawHandle(xs, h, top = true, color = PopColors.Lime)
    drawHandle(xe, h, top = false, color = PopColors.Cyan)
}

private fun DrawScope.drawHandle(x: Float, h: Float, top: Boolean, color: androidx.compose.ui.graphics.Color) {
    drawLine(PopColors.Ink, Offset(x, 0f), Offset(x, h), 3.dp.toPx())
    val r = 13.dp.toPx()
    val cy = if (top) r + 2.dp.toPx() else h - r - 2.dp.toPx()
    val c = Offset(x, cy)
    drawCircle(color, r, c)
    drawCircle(PopColors.Ink, r, c, style = Stroke(3.dp.toPx()))
    // grip
    val g = 4.dp.toPx()
    for (dx in listOf(-g, 0f, g)) {
        drawLine(PopColors.Ink, Offset(x + dx, cy - r * 0.4f), Offset(x + dx, cy + r * 0.4f), 2.dp.toPx())
    }
}

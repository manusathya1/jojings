package com.jojings.ringtonemaker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------- Palette

object PopColors {
    val Pink = Color(0xFFFF3D9A)
    val Yellow = Color(0xFFFFE135)
    val Cyan = Color(0xFF22E4FF)
    val Lime = Color(0xFFB4FF39)
    val Orange = Color(0xFFFF8A1F)
    val Purple = Color(0xFF8B5CFF)
    val Blue = Color(0xFF3D7BFF)
    val Red = Color(0xFFFF4747)
    val Ink = Color(0xFF1A1033)
    val Paper = Color(0xFFFFFDF7)

    val accents = listOf(Pink, Cyan, Yellow, Lime, Orange, Purple)
    fun accent(i: Int) = accents[((i % accents.size) + accents.size) % accents.size]
}

@Composable
fun PopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PopColors.Pink,
            onPrimary = PopColors.Ink,
            secondary = PopColors.Cyan,
            tertiary = PopColors.Yellow,
            background = PopColors.Purple,
            surface = PopColors.Paper,
            onSurface = PopColors.Ink,
        ),
        content = content,
    )
}

val TitleShadow = androidx.compose.ui.graphics.Shadow(PopColors.Ink, Offset(5f, 5f), 0f)

// ---------------------------------------------------------------- Background

/** Animated candy gradient with drifting blobs and a pop-art halftone dot pattern. */
@Composable
fun PopBackground(content: @Composable BoxScope.() -> Unit) {
    val inf = rememberInfiniteTransition(label = "bg")
    val t by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse), label = "bgT",
    )
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        listOf(Color(0xFFFF4FA8), Color(0xFF9B5CFF), Color(0xFF3DA9FF)),
                        start = Offset(size.width * t * 0.6f, 0f),
                        end = Offset(size.width * (1f - t * 0.4f), size.height),
                    ),
                )
                val blobs = listOf(
                    Triple(PopColors.Yellow, 0.15f, 0.12f),
                    Triple(PopColors.Cyan, 0.85f, 0.35f),
                    Triple(PopColors.Lime, 0.25f, 0.75f),
                    Triple(PopColors.Orange, 0.9f, 0.9f),
                )
                blobs.forEachIndexed { i, (c, bx, by) ->
                    val a = (t * 2 * PI + i * 1.7).toFloat()
                    drawCircle(
                        c.copy(alpha = 0.35f),
                        radius = size.minDimension * (0.22f + 0.04f * sin(a)),
                        center = Offset(size.width * bx + 40.dp.toPx() * cos(a), size.height * by + 50.dp.toPx() * sin(a)),
                    )
                }
                // halftone dots
                val step = 22.dp.toPx()
                val dot = Color.White.copy(alpha = 0.12f)
                var y = 0f
                var row = 0
                while (y < size.height) {
                    var x = if (row % 2 == 0) 0f else step / 2
                    while (x < size.width) {
                        drawCircle(dot, radius = 2.2.dp.toPx(), center = Offset(x, y))
                        x += step
                    }
                    y += step * 0.87f
                    row++
                }
            },
        content = content,
    )
}

// ---------------------------------------------------------------- Tap bursts (every touch animates)

private class Burst(val center: Offset, val seed: Int) {
    val anim = Animatable(0f)
}

/** Wrap the whole app in this: every finger-down anywhere spawns a colourful burst. */
@Composable
fun TapBurstLayer(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val bursts = remember { mutableStateListOf<Burst>() }
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        ev.changes.forEach { c ->
                            if (c.changedToDownIgnoreConsumed()) {
                                val b = Burst(c.position, Random.nextInt(360))
                                bursts.add(b)
                                if (bursts.size > 12) bursts.removeAt(0)
                                scope.launch {
                                    b.anim.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
                                    bursts.remove(b)
                                }
                            }
                        }
                    }
                }
            }
            .drawWithContent {
                drawContent()
                bursts.forEach { drawBurst(it) }
            },
        content = content,
    )
}

private fun DrawScope.drawBurst(b: Burst) {
    val t = b.anim.value
    val fade = 1f - t
    drawCircle(
        PopColors.Paper.copy(alpha = fade),
        radius = 10.dp.toPx() + 46.dp.toPx() * t,
        center = b.center,
        style = Stroke(width = 5.dp.toPx() * fade),
    )
    drawCircle(
        PopColors.Yellow.copy(alpha = fade * 0.9f),
        radius = 6.dp.toPx() + 22.dp.toPx() * t,
        center = b.center,
        style = Stroke(width = 3.dp.toPx() * fade),
    )
    val n = 9
    for (i in 0 until n) {
        val ang = Math.toRadians((b.seed + i * 360.0 / n)).toFloat()
        val dist = 14.dp.toPx() + 58.dp.toPx() * t
        val p = b.center + Offset(cos(ang) * dist, sin(ang) * dist)
        val c = PopColors.accent(i + b.seed)
        if (i % 3 == 0) {
            rotate(degrees = t * 360f + i * 40f, pivot = p) {
                val s = 9.dp.toPx() * fade
                drawRect(c.copy(alpha = fade), topLeft = p - Offset(s / 2, s / 2), size = Size(s, s))
            }
        } else {
            drawCircle(c.copy(alpha = fade), radius = 5.5.dp.toPx() * fade + 1f, center = p)
        }
    }
}

// ---------------------------------------------------------------- Squishy clickable

/** Squish on press, springy wiggle on release, haptic tick. */
fun Modifier.popClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.86f else 1f, spring(dampingRatio = 0.3f, stiffness = 650f), label = "squish",
    )
    val wiggle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationZ = wiggle.value
        }
        .clickable(interaction, indication = null, enabled = enabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch {
                wiggle.snapTo(0f)
                wiggle.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 500f), initialVelocity = 260f)
            }
            onClick()
        }
}

// ---------------------------------------------------------------- Surfaces

/** Chunky pop-art surface: thick ink outline + hard offset shadow. */
@Composable
fun PopSurface(
    modifier: Modifier = Modifier,
    color: Color = PopColors.Paper,
    shape: Shape = RoundedCornerShape(22.dp),
    shadow: Dp = 5.dp,
    pressed: Float = 0f,
    fill: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.padding(end = shadow, bottom = shadow)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(shadow, shadow)
                .background(PopColors.Ink, shape),
        )
        Box(
            Modifier
                .then(if (fill) Modifier.fillMaxSize() else Modifier)
                .offset { IntOffset((shadow.toPx() * pressed).roundToInt(), (shadow.toPx() * pressed).roundToInt()) }
                .background(color, shape)
                .border(3.dp, PopColors.Ink, shape),
            content = content,
        )
    }
}

@Composable
fun PopCard(
    modifier: Modifier = Modifier,
    color: Color = PopColors.Paper,
    content: @Composable ColumnScope.() -> Unit,
) {
    PopSurface(modifier, color) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun PopButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PopColors.Pink,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    big: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(0.35f, 900f), label = "press")
    val bg by animateColorAsState(if (enabled) color else Color(0xFFD9D3E8), label = "bg")
    val wiggle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    PopSurface(
        modifier.graphicsLayer {
            val s = 1f - 0.05f * press
            scaleX = s; scaleY = s
            rotationZ = wiggle.value
        },
        color = bg,
        shape = RoundedCornerShape(if (big) 24.dp else 18.dp),
        shadow = if (big) 6.dp else 4.dp,
        pressed = press,
    ) {
        Row(
            Modifier
                .clickable(interaction, indication = null) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch {
                        wiggle.snapTo(0f)
                        wiggle.animateTo(0f, spring(0.25f, 450f), initialVelocity = if (enabled) 200f else 400f)
                    }
                    if (enabled) onClick()
                }
                .padding(horizontal = if (big) 20.dp else 14.dp, vertical = if (big) 18.dp else 11.dp)
                .widthIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = PopColors.Ink, modifier = Modifier.size(if (big) 28.dp else 20.dp))
                if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
            }
            if (text.isNotEmpty()) {
                Text(
                    text,
                    color = PopColors.Ink,
                    fontWeight = FontWeight.Black,
                    fontSize = if (big) 19.sp else 15.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Round icon button with ink outline. */
@Composable
fun PopIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PopColors.Yellow,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    Box(
        modifier
            .popClickable(enabled = enabled, onClick = onClick)
            .size(size)
            .background(if (enabled) color else Color(0xFFD9D3E8), CircleShape)
            .border(3.dp, PopColors.Ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = PopColors.Ink, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun PopChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PopColors.Lime,
    enabled: Boolean = true,
) {
    val bg by animateColorAsState(
        when {
            !enabled -> Color(0xFFE6E1EF)
            selected -> color
            else -> PopColors.Paper
        },
        label = "chip",
    )
    val lift by animateFloatAsState(if (selected) 1f else 0f, spring(0.4f, 500f), label = "lift")
    Box(
        modifier
            .popClickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { translationY = -3.dp.toPx() * lift }
            .background(bg, RoundedCornerShape(50))
            .border(2.5.dp, if (enabled) PopColors.Ink else PopColors.Ink.copy(alpha = 0.3f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            color = if (enabled) PopColors.Ink else PopColors.Ink.copy(alpha = 0.35f),
        )
    }
}

// ---------------------------------------------------------------- Dialog (in-window so bursts show on it)

@Composable
fun PopDialog(
    onDismiss: () -> Unit,
    title: String,
    color: Color = PopColors.Paper,
    content: @Composable ColumnScope.() -> Unit,
) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(0.5f, 380f)) }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(PopColors.Ink.copy(alpha = 0.55f * appear.value.coerceIn(0f, 1f)))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        PopSurface(
            Modifier
                .padding(22.dp)
                .graphicsLayer {
                    val a = appear.value
                    scaleX = 0.5f + 0.5f * a
                    scaleY = 0.5f + 0.5f * a
                    rotationZ = (1f - a) * -10f
                    alpha = a.coerceIn(0f, 1f)
                }
                .pointerInput(Unit) { detectTapGestures { } },
            color = color,
            shadow = 7.dp,
        ) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 24.sp, color = PopColors.Ink)
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}

// ---------------------------------------------------------------- Toast

@Composable
fun PopToast(text: String, key: Any, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val y = remember(key) { Animatable(1f) }
    LaunchedEffect(key) {
        y.animateTo(0f, spring(0.45f, 350f))
        delay(2400)
        y.animateTo(1.4f, tween(250))
        onDone()
    }
    PopSurface(
        modifier
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .graphicsLayer {
                translationY = y.value * 180.dp.toPx()
                rotationZ = y.value * 6f
            },
        color = PopColors.Yellow,
        shadow = 5.dp,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            color = PopColors.Ink,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------- Confetti

private class Confetto(
    val x: Float, val vx: Float, val vy: Float, val spin: Float,
    val color: Color, val w: Float, val h: Float, val delay: Float,
)

@Composable
fun ConfettiOverlay(trigger: Int) {
    if (trigger == 0) return
    val anim = remember(trigger) { Animatable(0f) }
    val pieces = remember(trigger) {
        List(110) {
            Confetto(
                x = Random.nextFloat(),
                vx = (Random.nextFloat() - 0.5f) * 0.6f,
                vy = -0.6f - Random.nextFloat() * 0.9f,
                spin = (Random.nextFloat() - 0.5f) * 1440f,
                color = PopColors.accent(Random.nextInt(6)).let { if (Random.nextInt(5) == 0) Color.White else it },
                w = 8f + Random.nextFloat() * 10f,
                h = 5f + Random.nextFloat() * 8f,
                delay = Random.nextFloat() * 0.15f,
            )
        }
    }
    LaunchedEffect(trigger) { anim.animateTo(1f, tween(2600, easing = LinearEasing)) }
    if (anim.value >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val t0 = anim.value
        pieces.forEach { p ->
            val t = ((t0 - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            // launched from the bottom, flying up then falling with gravity
            val px = size.width * (p.x + p.vx * t)
            val py = size.height * (1.05f + p.vy * t * 1.6f + 1.9f * t * t)
            val alpha = if (t > 0.8f) (1f - t) / 0.2f else 1f
            val d = density
            rotate(p.spin * t, Offset(px, py)) {
                drawRoundRect(
                    p.color.copy(alpha = alpha),
                    topLeft = Offset(px - p.w * d / 2, py - p.h * d / 2),
                    size = Size(p.w * d, p.h * d),
                    cornerRadius = CornerRadius(2f * d),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Equalizer + spinner

@Composable
fun DancingBars(modifier: Modifier = Modifier, color: Color = PopColors.Ink, bars: Int = 4) {
    val inf = rememberInfiniteTransition(label = "eq")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "eqT")
    Canvas(modifier.size(22.dp, 18.dp)) {
        val bw = size.width / (bars * 1.6f)
        for (i in 0 until bars) {
            val h = size.height * (0.3f + 0.7f * ((sin((t * 2 * PI + i * 1.3).toFloat()) + 1f) / 2f))
            drawRoundRect(
                color,
                topLeft = Offset(i * bw * 1.6f, size.height - h),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2),
            )
        }
    }
}

@Composable
fun SpinningRecord(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "rec")
    val deg by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "recT")
    Canvas(modifier.size(96.dp).rotate(deg)) {
        val r = size.minDimension / 2
        drawCircle(PopColors.Ink, r)
        for (k in 1..4) drawCircle(Color.White.copy(alpha = 0.15f), r * (0.45f + k * 0.12f), style = Stroke(1.5.dp.toPx()))
        drawArc(PopColors.Pink, -40f, 50f, false, style = Stroke(4.dp.toPx()), topLeft = Offset(r * 0.18f, r * 0.18f), size = Size(r * 1.64f, r * 1.64f))
        drawCircle(PopColors.Yellow, r * 0.38f)
        drawCircle(PopColors.Pink, r * 0.2f)
        drawCircle(PopColors.Ink, r * 0.06f)
    }
}

/** Chunky striped progress bar. */
@Composable
fun PopProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = PopColors.Lime) {
    val inf = rememberInfiniteTransition(label = "stripes")
    val shift by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "s")
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(200), label = "p")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(PopColors.Paper, RoundedCornerShape(50))
            .border(3.dp, PopColors.Ink, RoundedCornerShape(50)),
    ) {
        val inset = 4.dp.toPx()
        val w = (size.width - inset * 2) * p
        if (w <= 0f) return@Canvas
        val h = size.height - inset * 2
        drawRoundRect(color, Offset(inset, inset), Size(w, h), CornerRadius(h / 2))
        val stripe = 14.dp.toPx()
        var x = inset - stripe * 2 + shift * stripe * 2
        while (x < inset + w) {
            val left = x.coerceAtLeast(inset)
            val right = (x + stripe).coerceAtMost(inset + w)
            if (right > left) {
                drawRect(Color.White.copy(alpha = 0.35f), Offset(left, inset), Size(right - left, h))
            }
            x += stripe * 2
        }
    }
}

fun formatMs(ms: Long, tenths: Boolean = true): String {
    val total = ms.coerceAtLeast(0)
    val m = total / 60_000
    val s = (total / 1000) % 60
    return if (tenths) "%d:%02d.%d".format(m, s, (total / 100) % 10) else "%d:%02d".format(m, s)
}

val LabelStyle = TextStyle(fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.2.sp, color = PopColors.Ink)

package com.jojings.ringtonemaker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jojings.ringtonemaker.MainViewModel
import com.jojings.ringtonemaker.audio.Pcm

private val LENGTH_PRESETS = listOf(1, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120)

@Composable
fun EditorScreen(vm: MainViewModel) {
    val src = vm.source ?: return
    val dur = src.durationMs
    val playFrame by vm.preview.position.collectAsState()
    val playing = playFrame >= 0
    val playheadMs = if (playing) Pcm.framesToMs(playFrame) else -1L

    var zoom by remember(src) { mutableIntStateOf(1) }
    val span = (dur / zoom).coerceAtLeast(minOf(dur, 2_000L))
    var viewStart by remember(src) { mutableLongStateOf(0L) }
    fun clampView(v: Long) = v.coerceIn(0L, (dur - span).coerceAtLeast(0L))
    fun setZoom(z: Int) {
        val newSpan = (dur / z).coerceAtLeast(minOf(dur, 2_000L))
        if (z > 1 && newSpan == span && z > zoom) return
        zoom = z
        val center = (vm.selStart + vm.selEnd) / 2
        viewStart = (center - newSpan / 2).coerceIn(0L, (dur - newSpan).coerceAtLeast(0L))
    }
    val vStart = clampView(viewStart)

    var showSave by remember { mutableStateOf(false) }

    BackHandler { vm.goHome() }

    Box(Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize()) {
        // ---- top bar
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PopIconButton(Icons.AutoMirrored.Filled.ArrowBack, { vm.goHome() }, contentDescription = "Back")
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Cut your tune",
                    style = TextStyle(fontWeight = FontWeight.Black, fontSize = 28.sp, color = Color.White, shadow = TitleShadow),
                )
                Text(
                    src.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            PlayBig(playing, size = 56.dp) { vm.togglePreview() }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {

            // ---- waveform
            PopCard {
                Waveform(
                    peaks = src.peaks,
                    durationMs = dur,
                    selStart = vm.selStart,
                    selEnd = vm.selEnd,
                    playheadMs = playheadMs,
                    viewStart = vStart,
                    viewSpan = span,
                    onStart = vm::setStart,
                    onEnd = vm::setEnd,
                    onMove = vm::moveSelection,
                    onPan = { viewStart = clampView(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatMs(vStart), style = LabelStyle)
                    Spacer(Modifier.weight(1f))
                    Text("total ${formatMs(dur, false)}", style = LabelStyle.copy(color = PopColors.Purple))
                    Spacer(Modifier.weight(1f))
                    Text(formatMs(vStart + span), style = LabelStyle)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PopIconButton(Icons.Filled.ZoomOut, { setZoom((zoom / 2).coerceAtLeast(1)) }, color = PopColors.Cyan, enabled = zoom > 1, contentDescription = "Zoom out")
                    AnimatedContent(zoom, transitionSpec = { scaleIn(spring(0.4f)) togetherWith scaleOut() }, label = "zoom") { z ->
                        Text("${z}x", fontWeight = FontWeight.Black, fontSize = 20.sp, color = PopColors.Ink, modifier = Modifier.width(48.dp))
                    }
                    PopIconButton(Icons.Filled.ZoomIn, { setZoom((zoom * 2).coerceAtMost(256)) }, color = PopColors.Cyan, enabled = span > 2_000L, contentDescription = "Zoom in")
                    PopIconButton(Icons.Filled.FitScreen, { setZoom(1) }, color = PopColors.Lime, contentDescription = "Fit")
                    Spacer(Modifier.weight(1f))
                    Text("drag the\nknobs!", style = LabelStyle.copy(fontSize = 11.sp, color = PopColors.Pink))
                }
            }
            Spacer(Modifier.height(14.dp))

            // ---- start / length / end
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("START", formatMs(vm.selStart), PopColors.Lime, Modifier.weight(1f))
                StatPill("LENGTH", formatMs(vm.selLength), PopColors.Yellow, Modifier.weight(1.1f), big = true)
                StatPill("END", formatMs(vm.selEnd), PopColors.Cyan, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))

            // ---- fine tune
            PopCard {
                Text("FINE TUNE", style = LabelStyle)
                Spacer(Modifier.height(10.dp))
                NudgeRow("Start", PopColors.Lime) { vm.setStart(vm.selStart + it) }
                Spacer(Modifier.height(10.dp))
                NudgeRow("End", PopColors.Cyan) { vm.setEnd(vm.selEnd + it) }
            }
            Spacer(Modifier.height(14.dp))

            // ---- length presets & fades
            PopCard {
                Text("QUICK LENGTH  (1 sec – 2 min)", style = LabelStyle)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LENGTH_PRESETS.forEachIndexed { i, sec ->
                        val ms = sec * 1000L
                        PopChip(
                            text = if (sec >= 60) "${sec / 60}:${"%02d".format(sec % 60)}" else "${sec}s",
                            selected = vm.selLength == ms,
                            onClick = { vm.setLength(ms) },
                            color = PopColors.accent(i),
                            enabled = ms <= dur,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("EFFECTS", style = LabelStyle)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PopChip("Fade in", vm.fadeIn, { vm.fadeIn = !vm.fadeIn; vm.preview.stop() }, color = PopColors.Pink)
                    PopChip("Fade out", vm.fadeOut, { vm.fadeOut = !vm.fadeOut; vm.preview.stop() }, color = PopColors.Orange)
                }
            }
            Spacer(Modifier.height(18.dp))

            // ---- actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayBig(playing) { vm.togglePreview() }
                Spacer(Modifier.width(14.dp))
                PopButton(
                    "Save Tune",
                    { vm.preview.stop(); showSave = true },
                    Modifier.weight(1f),
                    color = PopColors.Lime,
                    icon = Icons.Filled.ContentCut,
                    big = true,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
      }

        if (showSave) {
            NameDialog(
                title = "Name your tune",
                initial = src.title.take(40),
                confirm = "Save",
                onDismiss = { showSave = false },
                onConfirm = { showSave = false; vm.saveSelection(it) },
            )
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color, modifier: Modifier, big: Boolean = false) {
    PopSurface(modifier, color = color, shadow = 4.dp, shape = RoundedCornerShape(18.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = LabelStyle.copy(fontSize = 10.sp))
            AnimatedContent(value, transitionSpec = { scaleIn(spring(0.45f, 600f), 0.7f) togetherWith scaleOut(tween(80)) }, label = "stat") { v ->
                Text(v, fontWeight = FontWeight.Black, fontSize = if (big) 20.sp else 16.sp, color = PopColors.Ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun NudgeRow(label: String, color: Color, onNudge: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .background(color, CircleShape)
                .border(2.dp, PopColors.Ink, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.Black, fontSize = 15.sp, color = PopColors.Ink, modifier = Modifier.width(44.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(-1000L to "-1s", -100L to "-.1", 100L to "+.1", 1000L to "+1s").forEach { (d, t) ->
                Box(
                    Modifier
                        .weight(1f)
                        .popClickable { onNudge(d) }
                        .background(if (d < 0) PopColors.Paper else color, RoundedCornerShape(14.dp))
                        .border(2.5.dp, PopColors.Ink, RoundedCornerShape(14.dp))
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(t, fontWeight = FontWeight.Black, fontSize = 14.sp, color = PopColors.Ink)
                }
            }
        }
    }
}

@Composable
fun PlayBig(playing: Boolean, size: androidx.compose.ui.unit.Dp = 72.dp, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "pulse")
    val pulse by inf.animateFloat(1f, 1.08f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "p")
    Box(
        Modifier
            .popClickable(onClick = onClick)
            .graphicsLayer {
                val s = if (playing) pulse else 1f
                scaleX = s; scaleY = s
            }
            .size(size)
            .background(if (playing) PopColors.Orange else PopColors.Pink, CircleShape)
            .border(3.5.dp, PopColors.Ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(playing, transitionSpec = { scaleIn(spring(0.4f)) togetherWith scaleOut() }, label = "pp") { p ->
            Icon(
                if (p) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (p) "Stop preview" else "Play preview",
                tint = PopColors.Ink,
                modifier = Modifier.size(size * 0.56f),
            )
        }
    }
}

package com.jojings.ringtonemaker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jojings.ringtonemaker.MainViewModel
import com.jojings.ringtonemaker.audio.Pcm

private val GAPS = listOf(0L to "No gap", 250L to "0.25s", 500L to "0.5s", 1000L to "1s", 2000L to "2s")

@Composable
fun JoinScreen(vm: MainViewModel) {
    val playFrame by vm.preview.position.collectAsState()
    val playing = playFrame >= 0
    var showSave by remember { mutableStateOf(false) }

    BackHandler { vm.goHome() }

    Box(Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize()) {
        // Fixed top bar with the play button.
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PopIconButton(Icons.AutoMirrored.Filled.ArrowBack, { vm.goHome() }, contentDescription = "Back")
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Join tunes",
                    style = TextStyle(fontWeight = FontWeight.Black, fontSize = 28.sp, color = Color.White, shadow = TitleShadow),
                )
                Text("Tap tunes below to line them up", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            PlayBig(playing, size = 56.dp) { vm.toggleJoinPreview() }
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "sequence") {
                PopCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("YOUR MIX", style = LabelStyle)
                        Spacer(Modifier.weight(1f))
                        if (vm.joinList.isNotEmpty()) {
                            Text(
                                "clear",
                                style = LabelStyle.copy(color = PopColors.Pink),
                                modifier = Modifier.popClickable { vm.joinList.clear(); vm.preview.stop() }.padding(4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (vm.joinList.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .border(2.5.dp, PopColors.Ink.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(22.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Empty — add tunes from the list below!", fontWeight = FontWeight.Bold, color = PopColors.Ink.copy(alpha = 0.6f))
                        }
                    } else {
                        vm.joinList.forEachIndexed { i, t ->
                            SequenceRow(
                                index = i,
                                name = t.name,
                                durationMs = t.durationMs,
                                canUp = i > 0,
                                canDown = i < vm.joinList.lastIndex,
                                onUp = { vm.moveInJoin(i, -1) },
                                onDown = { vm.moveInJoin(i, 1) },
                                onRemove = { vm.removeFromJoin(i) },
                            )
                            if (i < vm.joinList.lastIndex) Spacer(Modifier.height(8.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    TotalMeter(vm.joinTotalMs)
                    Spacer(Modifier.height(14.dp))
                    Text("GAP BETWEEN TUNES", style = LabelStyle)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GAPS.forEachIndexed { i, (ms, label) ->
                            PopChip(label, vm.joinGapMs == ms, { vm.joinGapMs = ms; vm.preview.stop() }, color = PopColors.accent(i + 1))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayBig(playing) { vm.toggleJoinPreview() }
                        Spacer(Modifier.width(14.dp))
                        PopButton(
                            "Join & Save",
                            { vm.preview.stop(); if (vm.joinList.size < 2) vm.showToast("Add at least 2 tunes to join!") else showSave = true },
                            Modifier.weight(1f),
                            color = PopColors.Lime,
                            icon = Icons.AutoMirrored.Filled.CallMerge,
                            big = true,
                        )
                    }
                }
            }

            item(key = "libTitle") {
                Text(
                    "Saved tunes",
                    style = TextStyle(fontWeight = FontWeight.Black, fontSize = 22.sp, color = Color.White, shadow = TitleShadow),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            items(vm.tunes, key = { "lib-" + it.file.path }) { t ->
                val idx = vm.tunes.indexOf(t)
                val count = vm.joinList.count { it.file == t.file }
                Box(Modifier.popClickable { vm.addToJoin(t) }) {
                    PopSurface(color = PopColors.Paper, shadow = 4.dp) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .background(PopColors.accent(idx), CircleShape)
                                    .border(2.5.dp, PopColors.Ink, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Add, "Add", tint = PopColors.Ink) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.name, fontWeight = FontWeight.Black, color = PopColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatMs(t.durationMs), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PopColors.Ink.copy(alpha = 0.6f))
                            }
                            AnimatedContent(count, transitionSpec = { scaleIn(spring(0.3f)) togetherWith scaleOut() }, label = "c") { c ->
                                if (c > 0) {
                                    Box(
                                        Modifier
                                            .background(PopColors.Pink, CircleShape)
                                            .border(2.dp, PopColors.Ink, CircleShape)
                                            .padding(horizontal = 9.dp, vertical = 2.dp),
                                    ) { Text("×$c", fontWeight = FontWeight.Black, color = PopColors.Ink) }
                                }
                            }
                        }
                    }
                }
            }
        }

      }

        if (showSave) {
            NameDialog(
                title = "Name your mix",
                initial = vm.joinList.take(2).joinToString(" + ") { it.name }.take(40),
                confirm = "Save",
                onDismiss = { showSave = false },
                onConfirm = { showSave = false; vm.saveJoin(it) },
            )
        }
    }
}

@Composable
private fun SequenceRow(
    index: Int,
    name: String,
    durationMs: Long,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val color = PopColors.accent(index)
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .border(2.5.dp, PopColors.Ink, RoundedCornerShape(16.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(color, CircleShape)
                .border(2.dp, PopColors.Ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("${index + 1}", fontWeight = FontWeight.Black, color = PopColors.Ink) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Black, color = PopColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatMs(durationMs), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PopColors.Ink.copy(alpha = 0.6f))
        }
        PopIconButton(Icons.Filled.KeyboardArrowUp, onUp, size = 34.dp, color = PopColors.Paper, enabled = canUp, contentDescription = "Move up")
        Spacer(Modifier.width(6.dp))
        PopIconButton(Icons.Filled.KeyboardArrowDown, onDown, size = 34.dp, color = PopColors.Paper, enabled = canDown, contentDescription = "Move down")
        Spacer(Modifier.width(6.dp))
        PopIconButton(Icons.Filled.Close, onRemove, size = 34.dp, color = PopColors.Red, contentDescription = "Remove")
    }
}

@Composable
private fun TotalMeter(totalMs: Long) {
    val over = totalMs > Pcm.MAX_TUNE_MS
    val frac by animateFloatAsState((totalMs.toFloat() / Pcm.MAX_TUNE_MS).coerceIn(0f, 1f), spring(0.6f), label = "f")
    val color by animateColorAsState(
        when {
            over -> PopColors.Red
            frac > 0.8f -> PopColors.Orange
            else -> PopColors.Lime
        },
        label = "c",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("TOTAL", style = LabelStyle)
        Spacer(Modifier.weight(1f))
        Text(
            "${formatMs(totalMs)} / 2:00",
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            color = if (over) PopColors.Red else PopColors.Ink,
        )
    }
    Spacer(Modifier.height(6.dp))
    PopProgressBar(frac, color = color)
    if (over) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Over 2 minutes — the mix will be trimmed to 2:00 with a fade-out.",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = PopColors.Red,
        )
    }
}

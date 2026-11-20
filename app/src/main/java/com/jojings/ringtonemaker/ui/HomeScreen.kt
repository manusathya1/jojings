package com.jojings.ringtonemaker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jojings.ringtonemaker.MainViewModel
import com.jojings.ringtonemaker.data.RingKind
import com.jojings.ringtonemaker.data.Tune
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.sin

private val PICK_TYPES = arrayOf(
    "audio/*", "video/*", "application/ogg", "application/x-matroska", "application/octet-stream",
)

@Composable
fun HomeScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.openFile(uri)
    }

    var expanded by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Tune?>(null) }
    var deleting by remember { mutableStateOf<Tune?>(null) }
    var settingAs by remember { mutableStateOf<Tune?>(null) }
    var needPermission by remember { mutableStateOf<Pair<Tune, RingKind>?>(null) }
    var pendingDefault by remember { mutableStateOf<Pair<Tune, RingKind>?>(null) }

    // Back from the "modify system settings" screen: finish what the user asked for.
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pendingDefault?.let { (tune, kind) ->
            pendingDefault = null
            if (vm.repo.canSetDefaults()) {
                vm.setAs(tune, kind, makeDefault = true)
            } else {
                vm.setAs(tune, kind, makeDefault = false)
            }
        }
    }

    // Android 9 and older need storage permission to put a sound in the shared Ringtones folder.
    var afterStorage by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = afterStorage
        afterStorage = null
        if (granted) action?.invoke() else vm.showToast("Storage permission is needed to add ringtones on this phone.")
    }
    fun withStorage(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            afterStorage = action
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") { Header() }
            item(key = "actions") {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigAction(
                        "Make a Tune", "from songs & videos", Icons.Filled.MusicNote, PopColors.Yellow,
                        Modifier.weight(1f),
                    ) { picker.launch(PICK_TYPES) }
                    BigAction(
                        "Join Tunes", "mix saved tunes", Icons.AutoMirrored.Filled.CallMerge, PopColors.Cyan,
                        Modifier.weight(1f),
                    ) { vm.openJoin() }
                }
            }
            item(key = "title") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text(
                        "My Tunes",
                        style = TextStyle(fontWeight = FontWeight.Black, fontSize = 26.sp, color = Color.White, shadow = TitleShadow),
                    )
                    Spacer(Modifier.width(10.dp))
                    AnimatedContent(vm.tunes.size, transitionSpec = { scaleIn(spring(0.35f)) togetherWith scaleOut() }, label = "count") { n ->
                        Box(
                            Modifier
                                .background(PopColors.Lime, CircleShape)
                                .border(2.5.dp, PopColors.Ink, CircleShape)
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                        ) { Text("$n", fontWeight = FontWeight.Black, fontSize = 18.sp, color = PopColors.Ink) }
                    }
                }
            }
            if (vm.tunes.isEmpty()) {
                item(key = "empty") { EmptyState { picker.launch(PICK_TYPES) } }
            }
            items(vm.tunes, key = { it.file.path }) { tune ->
                val index = vm.tunes.indexOf(tune)
                TuneCard(
                    tune = tune,
                    accent = PopColors.accent(index),
                    playing = vm.tunePlayer.playing == tune.file,
                    progress = if (vm.tunePlayer.playing == tune.file) vm.tunePlayer.progress else 0f,
                    expanded = expanded == tune.file.path,
                    onPlay = { vm.toggleTune(tune) },
                    onExpand = { expanded = if (expanded == tune.file.path) null else tune.file.path },
                    onSetAs = { settingAs = tune },
                    onRename = { renaming = tune },
                    onShare = { runCatching { context.startActivity(vm.repo.shareIntent(tune)) } },
                    onDelete = { deleting = tune },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        renaming?.let { t ->
            NameDialog(
                title = "Rename tune",
                initial = t.name,
                confirm = "Rename",
                onDismiss = { renaming = null },
                onConfirm = { renaming = null; vm.renameTune(t, it) },
            )
        }
        deleting?.let { t ->
            PopDialog(onDismiss = { deleting = null }, title = "Delete tune?") {
                Text("“${t.name}” will be gone for good.", fontWeight = FontWeight.Bold, color = PopColors.Ink)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PopButton("Keep", { deleting = null }, Modifier.weight(1f), color = PopColors.Paper)
                    PopButton("Delete", { deleting = null; vm.deleteTune(t) }, Modifier.weight(1f), color = PopColors.Red, icon = Icons.Filled.Delete)
                }
            }
        }
        settingAs?.let { t ->
            PopDialog(onDismiss = { settingAs = null }, title = "Use “${t.name}” as…") {
                listOf(
                    Triple(RingKind.Ringtone, Icons.Filled.RingVolume, PopColors.Pink),
                    Triple(RingKind.Notification, Icons.Filled.Notifications, PopColors.Cyan),
                    Triple(RingKind.Alarm, Icons.Filled.Alarm, PopColors.Orange),
                ).forEach { (kind, icon, color) ->
                    PopButton(
                        kind.label,
                        {
                            settingAs = null
                            withStorage {
                                if (vm.repo.canSetDefaults()) vm.setAs(t, kind, makeDefault = true)
                                else needPermission = t to kind
                            }
                        },
                        Modifier.fillMaxWidth(),
                        color = color,
                        icon = icon,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        needPermission?.let { (t, kind) ->
            PopDialog(onDismiss = { needPermission = null }, title = "One quick thing!") {
                Text(
                    "To make it your default ${kind.label.lowercase()}, Android needs you to let Jojings " +
                        "“modify system settings”. Turn the switch on, then come back.",
                    fontWeight = FontWeight.Bold,
                    color = PopColors.Ink,
                )
                Spacer(Modifier.height(18.dp))
                PopButton(
                    "Open settings",
                    {
                        needPermission = null
                        pendingDefault = t to kind
                        runCatching {
                            settingsLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")),
                            )
                        }.onFailure { pendingDefault = null; vm.setAs(t, kind, makeDefault = false) }
                    },
                    Modifier.fillMaxWidth(),
                    color = PopColors.Lime,
                )
                Spacer(Modifier.height(10.dp))
                PopButton(
                    "Just add to the ${kind.label.lowercase()} list",
                    { needPermission = null; vm.setAs(t, kind, makeDefault = false) },
                    Modifier.fillMaxWidth(),
                    color = PopColors.Paper,
                )
            }
        }
    }
}

@Composable
private fun Header() {
    val inf = rememberInfiniteTransition(label = "title")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "tt")
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row {
            "Jojings".forEachIndexed { i, ch ->
                Text(
                    ch.toString(),
                    modifier = Modifier.graphicsLayer {
                        translationY = sin((t * 2 * PI + i * 0.7).toFloat()) * 7.dp.toPx()
                        rotationZ = sin((t * 2 * PI + i * 0.9).toFloat()) * 5f
                    },
                    style = TextStyle(
                        fontWeight = FontWeight.Black,
                        fontSize = 52.sp,
                        color = PopColors.accent(i + 2).let { if (it == PopColors.Purple) PopColors.Paper else it },
                        shadow = TitleShadow,
                    ),
                )
            }
        }
        Box(
            Modifier
                .graphicsLayer { rotationZ = -2f }
                .background(PopColors.Ink, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) {
            Text("RINGTONE MAKER", color = PopColors.Yellow, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 3.sp)
        }
    }
}

@Composable
private fun BigAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val inf = rememberInfiniteTransition(label = "bob")
    val bob by inf.animateFloat(-4f, 4f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "b")
    Box(modifier.fillMaxHeight().popClickable(onClick = onClick)) {
        PopSurface(Modifier.fillMaxHeight(), color = color, shadow = 6.dp, fill = true) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Box(
                    Modifier
                        .graphicsLayer { rotationZ = bob * 2 }
                        .size(52.dp)
                        .background(PopColors.Paper, CircleShape)
                        .border(3.dp, PopColors.Ink, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = PopColors.Ink, modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.height(12.dp))
                Text(title, fontWeight = FontWeight.Black, fontSize = 20.sp, color = PopColors.Ink)
                Text(subtitle, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PopColors.Ink.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "empty")
    val bounce by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "eb")
    Box(Modifier.popClickable(onClick = onPick)) {
        PopCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.LibraryMusic,
                    null,
                    tint = PopColors.Pink,
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            translationY = -bounce * 12.dp.toPx()
                            rotationZ = (bounce - 0.5f) * 14f
                        },
                )
                Spacer(Modifier.height(8.dp))
                Text("No tunes yet!", fontWeight = FontWeight.Black, fontSize = 22.sp, color = PopColors.Ink)
                Text(
                    "Tap here or “Make a Tune” and pick any song or video.",
                    fontWeight = FontWeight.Bold,
                    color = PopColors.Ink.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun TuneCard(
    tune: Tune,
    accent: Color,
    playing: Boolean,
    progress: Float,
    expanded: Boolean,
    onPlay: () -> Unit,
    onExpand: () -> Unit,
    onSetAs: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tilt by animateFloatAsState(if (expanded) -1.2f else 0f, spring(0.4f), label = "tilt")
    PopSurface(modifier.graphicsLayer { rotationZ = tilt }, color = PopColors.Paper) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .popClickable(onClick = onPlay)
                        .size(56.dp)
                        .background(accent, CircleShape)
                        .border(3.dp, PopColors.Ink, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(playing, transitionSpec = { scaleIn(spring(0.4f)) togetherWith scaleOut() }, label = "play") { p ->
                        Icon(
                            if (p) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (p) "Stop" else "Play",
                            tint = PopColors.Ink,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).popClickable(onClick = onExpand)) {
                    Text(
                        tune.name,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = PopColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .background(accent, RoundedCornerShape(8.dp))
                                .border(2.dp, PopColors.Ink, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 1.dp),
                        ) {
                            Text(formatMs(tune.durationMs), fontWeight = FontWeight.Black, fontSize = 12.sp, color = PopColors.Ink)
                        }
                        Spacer(Modifier.width(8.dp))
                        if (playing) {
                            DancingBars(color = PopColors.Pink)
                        } else {
                            Text(
                                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(tune.createdAt)),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PopColors.Ink.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
                val turn by animateFloatAsState(if (expanded) 90f else 0f, spring(0.4f), label = "turn")
                PopIconButton(
                    Icons.Filled.MoreHoriz,
                    onExpand,
                    Modifier.graphicsLayer { rotationZ = turn },
                    color = if (expanded) PopColors.Yellow else PopColors.Paper,
                    size = 40.dp,
                    contentDescription = "More",
                )
            }
            if (playing) {
                Spacer(Modifier.height(10.dp))
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    drawRoundRect(PopColors.Ink.copy(alpha = 0.12f), cornerRadius = CornerRadius(size.height / 2))
                    drawRoundRect(accent, Offset.Zero, Size(size.width * progress, size.height), CornerRadius(size.height / 2))
                }
            }
            AnimatedVisibility(
                expanded,
                enter = expandVertically(spring(0.6f, 400f)) + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionTile("Set as", Icons.Filled.RingVolume, PopColors.Pink, Modifier.weight(1f), onSetAs)
                    ActionTile("Rename", Icons.Filled.Edit, PopColors.Yellow, Modifier.weight(1f), onRename)
                    ActionTile("Share", Icons.Filled.Share, PopColors.Cyan, Modifier.weight(1f), onShare)
                    ActionTile("Delete", Icons.Filled.Delete, PopColors.Red, Modifier.weight(1f), onDelete)
                }
            }
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .popClickable(onClick = onClick)
            .background(color, RoundedCornerShape(16.dp))
            .border(2.5.dp, PopColors.Ink, RoundedCornerShape(16.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = PopColors.Ink, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontWeight = FontWeight.Black, fontSize = 12.sp, color = PopColors.Ink, maxLines = 1)
    }
}

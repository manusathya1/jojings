package com.jojings.ringtonemaker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import com.jojings.ringtonemaker.ui.BusyOverlay
import com.jojings.ringtonemaker.ui.ConfettiOverlay
import com.jojings.ringtonemaker.ui.EditorScreen
import com.jojings.ringtonemaker.ui.HomeScreen
import com.jojings.ringtonemaker.ui.JoinScreen
import com.jojings.ringtonemaker.ui.PopBackground
import com.jojings.ringtonemaker.ui.PopTheme
import com.jojings.ringtonemaker.ui.PopToast
import com.jojings.ringtonemaker.ui.TapBurstLayer

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIncoming(intent)

        setContent {
            PopTheme {
                PopBackground {
                    TapBurstLayer {
                        Box(Modifier.fillMaxSize().systemBarsPadding()) {
                            AnimatedContent(
                                targetState = vm.screen,
                                transitionSpec = {
                                    val forward = targetState.ordinal > initialState.ordinal
                                    (slideInHorizontally(spring(0.8f, 380f)) { if (forward) it else -it } +
                                        scaleIn(spring(0.6f), 0.85f) + fadeIn()) togetherWith
                                        (slideOutHorizontally(tween(220)) { if (forward) -it / 3 else it / 3 } +
                                            scaleOut(tween(220), 0.9f) + fadeOut(tween(180)))
                                },
                                label = "screen",
                            ) { screen ->
                                when (screen) {
                                    Screen.Home -> HomeScreen(vm)
                                    Screen.Editor -> EditorScreen(vm)
                                    Screen.Join -> JoinScreen(vm)
                                }
                            }
                        }

                        vm.busy?.let { BusyOverlay(it, vm::cancelWork) }

                        vm.toast?.let { msg ->
                            Box(Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
                                PopToast(msg.text, msg.id, onDone = { vm.dismissToast(msg) })
                            }
                        }

                        ConfettiOverlay(vm.celebrate)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    override fun onStop() {
        super.onStop()
        // Don't keep blasting music when the user leaves the app.
        vm.tunePlayer.stop()
        vm.preview.stop()
    }

    /** Audio/video shared to the app ("Share → Jojings" or "Open with"). */
    private fun handleIncoming(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (uri != null) vm.openFile(uri)
    }
}

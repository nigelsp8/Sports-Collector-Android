package com.nigelspeight.sportscollector.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nigelspeight.sportscollector.AppServices
import com.nigelspeight.sportscollector.audio.AudioManager
import com.nigelspeight.sportscollector.game.GameScene
import com.nigelspeight.sportscollector.game.GameView
import com.nigelspeight.sportscollector.level.Level

/// Hosts one level's `GameView` full-screen (system bars hidden, like the iOS
/// screen's hidden status bar). `injectedLevel`, when set, is played instead of
/// loading `mapID` - lets the level editor play-test an unsaved edit.
@Composable
fun GameScreen(mapID: Int, injectedLevel: Level?, onExit: () -> Unit, onQuit: () -> Unit) {
    val level = remember(mapID, injectedLevel) {
        injectedLevel ?: runCatching { AppServices.loadLevel(mapID) }.getOrNull()
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            AudioManager.stopMusic()
        }
    }

    BackHandler(onBack = onExit)

    Box(
        modifier = Modifier
            .fillMaxSize()
            // No inset padding: the game view draws edge to edge (backgrounds and
            // popup dimming fill the whole screen) and keeps its own board/HUD
            // clear of the status bar and cutout - see `GameView.updateInsets`.
            .background(Color(GameScene.BACKGROUND_COLOR)),
        contentAlignment = Alignment.Center,
    ) {
        if (level == null) {
            Text("Failed to load level $mapID", color = Color.White)
        } else {
            AndroidView(
                factory = { context -> GameView(context, level, onContinue = onExit, onQuit = onQuit) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

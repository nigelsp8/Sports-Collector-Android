package com.nigelspeight.sportscollector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.nigelspeight.sportscollector.audio.AudioManager
import com.nigelspeight.sportscollector.level.Level
import com.nigelspeight.sportscollector.level.LevelOrder
import com.nigelspeight.sportscollector.ui.GameScreen
import com.nigelspeight.sportscollector.ui.LevelEditorScreen
import com.nigelspeight.sportscollector.ui.LevelEditorState
import com.nigelspeight.sportscollector.ui.LevelSelectScreen
import com.nigelspeight.sportscollector.ui.theme.SportsCollectorTheme

/// The app's navigation stack (the iOS `UINavigationController`).
sealed interface Screen {
    data object LevelSelect : Screen
    data class Game(val mapID: Int, val injectedLevel: Level? = null) : Screen
    class Editor(val state: LevelEditorState) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SportsCollectorTheme {
                val backStack = remember { mutableStateListOf<Screen>(Screen.LevelSelect) }
                fun pop() {
                    if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                }

                BackHandler(enabled = backStack.size > 1) { pop() }

                when (val screen = backStack.last()) {
                    Screen.LevelSelect -> LevelSelectScreen(
                        onPlay = { mapID -> backStack.add(Screen.Game(mapID)) },
                        onEdit = { mapID, level ->
                            backStack.add(Screen.Editor(LevelEditorState(level, mapID, LevelOrder.resourceName(mapID))))
                        },
                    )

                    is Screen.Game -> GameScreen(screen.mapID, screen.injectedLevel, onExit = ::pop)

                    is Screen.Editor -> LevelEditorScreen(
                        state = screen.state,
                        onBack = ::pop,
                        onPlayTest = { level -> backStack.add(Screen.Game(screen.state.mapID, level)) },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AudioManager.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        AudioManager.onAppBackgrounded()
    }
}

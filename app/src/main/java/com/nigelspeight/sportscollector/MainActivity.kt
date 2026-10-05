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
import com.nigelspeight.sportscollector.ui.SectionScreen
import com.nigelspeight.sportscollector.ui.SectionScreenState
import com.nigelspeight.sportscollector.ui.theme.SportsCollectorTheme

/// The app's navigation stack (the iOS `UINavigationController`).
sealed interface Screen {
    data object Sections : Screen
    data object LevelSelect : Screen
    data class Game(val mapID: Int, val injectedLevel: Level? = null) : Screen
    class Editor(val state: LevelEditorState) : Screen
}

class MainActivity : ComponentActivity() {
    /// Flip to `false` to boot straight into the level-select/editor tool
    /// instead of the user-facing section carousel.
    private val launchIntoSectionScreen = false //true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SportsCollectorTheme {
                val root = if (launchIntoSectionScreen) Screen.Sections else Screen.LevelSelect
                val backStack = remember { mutableStateListOf(root) }
                val sectionState = remember { SectionScreenState() }
                fun pop() {
                    if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                }

                fun popToRoot() {
                    while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                }

                BackHandler(enabled = backStack.size > 1) { pop() }

                when (val screen = backStack.last()) {
                    Screen.Sections -> SectionScreen(
                        state = sectionState,
                        onPlay = { mapID -> backStack.add(Screen.Game(mapID)) },
                    )

                    Screen.LevelSelect -> LevelSelectScreen(
                        onPlay = { mapID -> backStack.add(Screen.Game(mapID)) },
                        onEdit = { mapID, level ->
                            backStack.add(Screen.Editor(LevelEditorState(level, mapID, LevelOrder.resourceName(mapID))))
                        },
                    )

                    is Screen.Game -> GameScreen(screen.mapID, screen.injectedLevel, onExit = ::pop, onQuit = ::popToRoot)

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

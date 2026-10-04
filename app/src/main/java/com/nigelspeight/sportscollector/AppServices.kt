package com.nigelspeight.sportscollector

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.nigelspeight.sportscollector.audio.AudioManager
import com.nigelspeight.sportscollector.game.Textures
import com.nigelspeight.sportscollector.level.Level
import com.nigelspeight.sportscollector.level.LevelBundle
import com.nigelspeight.sportscollector.level.LevelEditStore
import com.nigelspeight.sportscollector.level.LevelLoader
import com.nigelspeight.sportscollector.level.LevelOrder
import java.io.FileNotFoundException

class SportsCollectorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppServices.init(this)
    }
}

/// Process-wide singletons the iOS version reached via `Bundle.main`,
/// `UserDefaults.standard`, and the Documents directory.
object AppServices {
    lateinit var levelBundle: LevelBundle
        private set
    lateinit var editStore: LevelEditStore
        private set
    lateinit var progress: LevelProgressStore
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        val assets = app.assets
        levelBundle = LevelBundle { name ->
            try {
                assets.open("Levels/$name.txt").bufferedReader().use { it.readText() }
            } catch (_: FileNotFoundException) {
                null
            }
        }
        editStore = LevelEditStore(app.filesDir)
        progress = LevelProgressStore(app.getSharedPreferences("progress", Context.MODE_PRIVATE))
        Textures.init(assets)
        AudioManager.init(app)
    }

    /// `mapID` is the 1-based map slot; resolves it to its on-disk level file
    /// through `LevelOrder`, honoring any level-editor override.
    fun loadLevel(mapID: Int): Level =
        LevelLoader.loadLevel(LevelOrder.resourceName(mapID), mapID, levelBundle, editStore)
}

/// Persists each level's best win result (highest score, with its matching star
/// count) across launches. Keyed by `Level.mapNumber` - the 1-based map slot,
/// not the shuffled on-disk level file number (`LevelOrder`).
class LevelProgressStore(private val prefs: SharedPreferences) {
    data class Result(val score: Int, val stars: Int)

    fun bestResult(mapNumber: Int): Result? {
        val key = key(mapNumber)
        if (!prefs.contains("$key.score") || !prefs.contains("$key.stars")) return null
        return Result(prefs.getInt("$key.score", 0), prefs.getInt("$key.stars", 0))
    }

    /// Records a win, keeping the previous best if it already had an equal or
    /// higher score. Returns whichever result ends up stored.
    fun recordWin(mapNumber: Int, score: Int, stars: Int): Result {
        val candidate = Result(score, stars)
        val existing = bestResult(mapNumber)
        if (existing != null && existing.score >= candidate.score) return existing
        val key = key(mapNumber)
        prefs.edit().putInt("$key.score", score).putInt("$key.stars", stars).apply()
        return candidate
    }

    /// Clears every recorded best score/stars, regardless of level count.
    fun resetAllProgress() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(KEY_PREFIX) }.forEach(editor::remove)
        editor.apply()
    }

    private fun key(mapNumber: Int) = KEY_PREFIX + mapNumber

    private companion object {
        const val KEY_PREFIX = "levelProgress."
    }
}

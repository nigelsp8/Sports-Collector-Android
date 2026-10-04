package com.nigelspeight.sportscollector.game

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.nigelspeight.sportscollector.engine.ObjectiveKind
import com.nigelspeight.sportscollector.engine.TileType

/// Loads and caches the game's bitmaps from `assets/images`. Combines the iOS
/// `TileTextureProvider` (TileType -> art), `GridFrameAtlas` (autotile frames
/// sliced from `grid1tiles.png`), and plain `SKTexture(imageNamed:)` lookups.
object Textures {
    private lateinit var assets: AssetManager
    private val cache = HashMap<String, Texture>()

    fun init(assets: AssetManager) {
        this.assets = assets
    }

    private fun decode(path: String): Bitmap =
        assets.open(path).use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: error("Could not decode $path")
        }

    /// A top-level image such as "HUD_score" or "nextbutton".
    fun image(name: String): Texture = cache.getOrPut(name) { Texture(decode("images/$name.png")) }

    /// Large images (the 2048x2048 level backgrounds) are loaded fresh and not
    /// cached, so playing through every background doesn't pin ~100MB of bitmaps.
    fun uncachedImage(name: String): Texture = Texture(decode("images/$name.png"))

    private fun pill(name: String): Texture = cache.getOrPut("pills/$name") { Texture(decode("images/pills/$name.png")) }

    fun tile(type: TileType): Texture = pill(fileName(type))

    val jellyOverlay: Texture get() = pill("bkgtile1")
    val wallHorizontal: Texture get() = pill("wall-horizontal")
    val wallVertical: Texture get() = pill("wall-vertical")

    fun objective(kind: ObjectiveKind): Texture = when (kind) {
        is ObjectiveKind.Collect -> tile(kind.type)
        ObjectiveKind.ClearJelly -> jellyOverlay
    }

    private fun fileName(type: TileType): String = when (type) {
        TileType.PINK_TABLET -> "frisbee"               // BLUE
        TileType.ORANGE_TABLET -> "rugbyball"           // BROWN
        TileType.YELLOW_TABLET -> "boxingglove"         // RED
        TileType.GREEN_TABLET -> "tennisball"           // GREEN
        TileType.PURPLE_TABLET -> "refsshirt"           // BLACK/WHITE
        TileType.BLUE_TABLET -> "soccerballcoloured"    // WHITE/BLACK

        TileType.ORANGE_BLUE_PILL -> "rugbyballx2"
        TileType.PINK_GREEN_PILL -> "frisbeex2"
        TileType.PURPLE_YELLOW_PILL -> "refsshirtx2"

        TileType.PINK_BACTERIA -> "trophy1"
        TileType.ORANGE_BACTERIA -> "trophies"
        TileType.YELLOW_BACTERIA -> "boxinggloves"
        TileType.GREEN_BACTERIA -> "chequeredflags"
        TileType.PURPLE_BACTERIA -> "goalkeepernet"
        TileType.BLUE_BACTERIA -> "timingwatch"

        TileType.SOLID_STAGE_1 -> "solid1"
        TileType.SOLID_STAGE_2 -> "solid2"
        TileType.SOLID_STAGE_3 -> "solid3"
    }

    /// `gamebar.png` is a 1024x1024 atlas whose only painted content is the top
    /// ~19.36% strip (the rest is unused canvas) - matches the original's own UV crop.
    val gamebar: Texture by lazy {
        val full = image("gamebar").bitmap
        Texture(full, Rect(0, 0, full.width, (full.height * 0.19359375).toInt()))
    }

    // region Grid frame atlas

    /// Bits: 1/2/4/8 = left/right/up/down neighbor missing, 16/32/64/128 =
    /// diagonal-only touch at top-left/top-right/bottom-left/bottom-right,
    /// 256/257 = fully-interior checkerboard (alternates by cell parity). Any
    /// bitmask not covered here never occurs for a valid level shape.
    private val frameForBitmask: Map<Int, Int> = mapOf(
        1 to 8, 2 to 10, 4 to 1, 8 to 17,
        5 to 11, 6 to 12, 7 to 28, 11 to 27, 12 to 22, 13 to 30, 14 to 29, 15 to 19,
        16 to 0, 32 to 2, 48 to 20,
        64 to 16, 128 to 18,
        240 to 31,
        256 to 32, 257 to 33,
        3 to 23,
        9 to 3, 10 to 4,
        192 to 26,
        80 to 34, 160 to 24,
        70 to 35, 41 to 36,
        26 to 37, 133 to 38,
        18 to 40, 33 to 41,
        208 to 15, 224 to 21,
        68 to 42, 132 to 43,
    )

    /// Slices `grid1tiles.png` (an 8x8 atlas of frames, rows counted top-down).
    fun gridFrame(bitmask: Int): Texture? {
        val frame = frameForBitmask[bitmask] ?: return null
        return cache.getOrPut("gridframe/$frame") {
            val base = image("grid1tiles").bitmap
            val cell = base.width / 8
            val col = frame % 8
            val row = frame / 8
            Texture(base, Rect(col * cell, row * cell, (col + 1) * cell, (row + 1) * cell))
        }
    }

    // endregion
}

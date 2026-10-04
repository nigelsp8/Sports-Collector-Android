package com.nigelspeight.sportscollector.game

import com.nigelspeight.sportscollector.engine.GridPoint
import com.nigelspeight.sportscollector.engine.TileType
import com.nigelspeight.sportscollector.level.Level
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/// A tile sprite that remembers its own `TileType`, so `BoardNode` can tell
/// which nodes are bacteria (the only tile type that continuously wobbles).
class TileSpriteNode(texture: Texture) : SpriteNode(texture) {
    var tileType: TileType? = null

    /// Per-instance wobble tuning so multiple bacteria on screen don't all
    /// animate in perfect lockstep. Stable for the sprite's lifetime - nodes
    /// persist across moves and swaps.
    val wobblePhase = Random.nextDouble(0.0, 2 * Math.PI)
    val wobbleSpeedScale = Random.nextDouble(0.8, 1.25)
    val wobbleAmplitudeScale = Random.nextDouble(0.75, 1.3)
}

/// Renders the board's static layout (cell backgrounds, walls, jelly overlays)
/// and manages tile sprite nodes, keyed by grid position. Pure presentation - it
/// has no game-rule knowledge; `GameScene` tells it what happened via discrete
/// calls (`placeTile`, `moveTile`, `removeTile`, ...) driven by `GameEvent`s.
class BoardNode(val level: Level, private val dp: Float) : Node() {
    companion object {
        /// Shared key for every positional animation on a tile node, so a fast
        /// cascade's repeated moves replace each other instead of fighting.
        private const val MOVE_ACTION_KEY = "tileMove"
        /// Shared key for every scale/fade animation on a tile node.
        private const val SCALE_ACTION_KEY = "tileScale"

        /// Base duration of the swap-over animation. `GameScene` mirrors this into
        /// `GameEngine.swapAnimationDuration`.
        const val SWAP_DURATION = 0.15

        private const val SPAWN_FADE_DURATION = 0.25
        private const val FALL_IN_DURATION = 0.35
        private const val RESHUFFLE_OUT_DURATION = 0.12
        private const val RESHUFFLE_IN_DURATION = 0.22

        /// Amplitude of each corner's displacement, as a fraction of the tile's own size.
        private const val WOBBLE_AMPLITUDE = 0.07
        /// Each corner advances at its own rate (radians/sec) so the four corners
        /// never move in lockstep - ported from the original's per-corner phase
        /// accumulators (2.3/2.2/2.4/2.6 degrees per frame at 60fps).
        private const val WOBBLE_TL = 2.408
        private const val WOBBLE_TR = 2.304
        private const val WOBBLE_BL = 2.513
        private const val WOBBLE_BR = 2.723
    }

    var cellSize = 0f
        private set

    /// Debug aid: scales every tile animation's duration. 1.0 is normal speed.
    var animationSpeedMultiplier = 1.0

    private val boardLayer = Node()
    private val jellyLayer = Node()
    private val wallLayer = Node()
    private val tileLayer = Node()
    private val selectionLayer = Node()

    /// Persistent highlight ring shown around the currently-selected tile -
    /// distinct from the transient `pulse` bounce.
    private val selectionHighlight = RoundedRectNode(0f, 0f, 0f).apply {
        strokeColor = 0xFFFFFFFF.toInt()
        lineWidth = 4 * dp
        isHidden = true
        zPosition = 10f
    }

    private val tileNodes = HashMap<GridPoint, TileSpriteNode>()
    private val jellyNodes = HashMap<GridPoint, SpriteNode>()
    private val wallNodes = HashMap<String, SpriteNode>()
    /// Each active cell's own checkerboard background node. Hidden while a cell
    /// holds jelly, so the jelly overlay reads as fully opaque ground.
    private val gridTileNodes = HashMap<GridPoint, SpriteNode>()

    init {
        addChild(boardLayer)
        addChild(jellyLayer)
        addChild(wallLayer)
        addChild(tileLayer)
        selectionLayer.addChild(selectionHighlight)
        addChild(selectionLayer)
    }

    /// (Re)computes cell size to fit `width` x `height` and rebuilds the static
    /// layer. Existing tile nodes are repositioned, not recreated.
    fun layout(width: Float, height: Float) {
        cellSize = min(width / level.width, height / level.height) * 0.92f
        val highlightSize = cellSize * 0.92f
        selectionHighlight.width = highlightSize
        selectionHighlight.height = highlightSize
        selectionHighlight.cornerRadius = highlightSize * 0.18f

        boardLayer.removeAllChildren()
        gridTileNodes.clear()
        jellyLayer.removeAllChildren()
        wallLayer.removeAllChildren()
        wallNodes.clear()
        val previousJellyPoints = jellyNodes.keys.toSet()
        jellyNodes.clear()

        buildStaticLayer(previousJellyPoints)

        for ((gp, node) in tileNodes) {
            val p = point(gp)
            node.setPosition(p.first, p.second)
            node.setSize(cellSize * 0.88f, cellSize * 0.88f)
        }
    }

    /// Grid -> board-space point (row increases downward, like the level data).
    fun point(grid: GridPoint): Pair<Float, Float> {
        val x = (grid.col - (level.width - 1) / 2f) * cellSize
        val y = (grid.row - (level.height - 1) / 2f) * cellSize
        return x to y
    }

    /// Board-space point -> grid point, or null if it doesn't land within a cell's footprint.
    fun gridPoint(px: Float, py: Float): GridPoint? {
        if (cellSize <= 0) return null
        val colF = px / cellSize + (level.width - 1) / 2f
        val rowF = py / cellSize + (level.height - 1) / 2f
        val candidate = GridPoint(rowF.roundToInt(), colF.roundToInt())
        if (candidate.row !in 0 until level.height || candidate.col !in 0 until level.width) return null
        val (cx, cy) = point(candidate)
        if (abs(px - cx) > cellSize / 2 || abs(py - cy) > cellSize / 2) return null
        return candidate
    }

    private fun buildStaticLayer(jellyPoints: Set<GridPoint>) {
        buildGridFrame()

        level.cells.forEachIndexed { index, spec ->
            if (!spec.isActive) return@forEachIndexed
            val gp = GridPoint(index / level.width, index % level.width)
            if (spec.hasJelly && (gp in jellyPoints || jellyPoints.isEmpty())) addJelly(gp)
            if (spec.hasWallRight) addWall(gp, horizontal = false)
            if (spec.hasWallBelow) addWall(gp, horizontal = true)
        }
    }

    private fun isActive(row: Int, col: Int): Boolean {
        if (row !in 0 until level.height || col !in 0 until level.width) return false
        return level.cell(GridPoint(row, col)).isActive
    }

    /// Ports `drawGridFrame`/`setupGrid`'s autotile technique: every active
    /// cell gets an alternating checkerboard tile, and every cell bordering
    /// the play area gets whichever edge/corner tile matches which sides
    /// face active cells. Bitmasks are accumulated on a grid padded by one
    /// cell on every side.
    private fun buildGridFrame() {
        val paddedRows = level.height + 2
        val paddedCols = level.width + 2
        val bits = Array(paddedRows) { IntArray(paddedCols) }

        for (row in 0 until level.height) {
            for (col in 0 until level.width) {
                if (!isActive(row, col)) continue
                val leftMissing = !isActive(row, col - 1)
                val rightMissing = !isActive(row, col + 1)
                val upMissing = !isActive(row - 1, col)
                val downMissing = !isActive(row + 1, col)
                val tl = !isActive(row - 1, col - 1)
                val tr = !isActive(row - 1, col + 1)
                val bl = !isActive(row + 1, col - 1)
                val br = !isActive(row + 1, col + 1)

                val fx = col + 1
                val fy = row + 1

                if (leftMissing) bits[fy][fx - 1] = bits[fy][fx - 1] or 1
                if (rightMissing) bits[fy][fx + 1] = bits[fy][fx + 1] or 2
                if (upMissing) bits[fy - 1][fx] = bits[fy - 1][fx] or 4
                if (downMissing) bits[fy + 1][fx] = bits[fy + 1][fx] or 8

                if (tl && leftMissing && upMissing) bits[fy - 1][fx - 1] = bits[fy - 1][fx - 1] or 16
                if (tr && rightMissing && upMissing) bits[fy - 1][fx + 1] = bits[fy - 1][fx + 1] or 32
                if (bl && leftMissing && downMissing) bits[fy + 1][fx - 1] = bits[fy + 1][fx - 1] or 64
                if (br && rightMissing && downMissing) bits[fy + 1][fx + 1] = bits[fy + 1][fx + 1] or 128
            }
        }

        for (row in 0 until level.height) {
            for (col in 0 until level.width) {
                if (!isActive(row, col)) continue
                bits[row + 1][col + 1] = if ((row + col) and 1 != 0) 256 else 257
            }
        }

        for (fy in 0 until paddedRows) {
            for (fx in 0 until paddedCols) {
                val bitmask = bits[fy][fx]
                if (bitmask == 0) continue
                val texture = Textures.gridFrame(bitmask) ?: continue
                val node = SpriteNode(texture)
                // A hair of overlap hides hairline seams between adjacent frames
                // that float rounding can otherwise leave visible.
                node.setSize(cellSize + 0.5f, cellSize + 0.5f)
                val gp = GridPoint(fy - 1, fx - 1)
                val (px, py) = point(gp)
                node.setPosition(px, py)
                boardLayer.addChild(node)
                if (bitmask == 256 || bitmask == 257) gridTileNodes[gp] = node
            }
        }
    }

    private fun addJelly(gp: GridPoint) {
        val jelly = SpriteNode(Textures.jellyOverlay)
        jelly.setSize(cellSize, cellSize)
        val (px, py) = point(gp)
        jelly.setPosition(px, py)
        jelly.zPosition = 1f
        jellyLayer.addChild(jelly)
        jellyNodes[gp] = jelly
        gridTileNodes[gp]?.isHidden = true
    }

    private fun wallKey(point: GridPoint, horizontal: Boolean) =
        "${point.row},${point.col},${if (horizontal) "b" else "r"}"

    private fun addWall(gp: GridPoint, horizontal: Boolean) {
        val node = SpriteNode(if (horizontal) Textures.wallHorizontal else Textures.wallVertical)
        val (cx, cy) = point(gp)
        if (horizontal) {
            node.setSize(cellSize * 0.9f, cellSize * 0.3f)
            node.setPosition(cx, cy + cellSize / 2)
        } else {
            node.setSize(cellSize * 0.3f, cellSize * 0.9f)
            node.setPosition(cx + cellSize / 2, cy)
        }
        node.zPosition = 3f
        wallLayer.addChild(node)
        wallNodes[wallKey(gp, horizontal)] = node
    }

    fun removeWall(a: GridPoint, b: GridPoint) {
        val fade = 0.2 * animationSpeedMultiplier
        val key = if (a.row == b.row) {
            wallKey(if (a.col < b.col) a else b, horizontal = false)
        } else {
            wallKey(if (a.row < b.row) a else b, horizontal = true)
        }
        wallNodes.remove(key)?.run(Action.sequence(Action.fadeOut(fade), Action.removeFromParent()))
    }

    fun clearJelly(gp: GridPoint) {
        val node = jellyNodes.remove(gp) ?: return
        node.run(Action.sequence(Action.fadeOut(0.3 * animationSpeedMultiplier), Action.removeFromParent()))
        gridTileNodes[gp]?.isHidden = false
    }

    // region Tiles

    fun tileNode(gp: GridPoint): SpriteNode? = tileNodes[gp]

    /// Swaps the two nodes currently tracked at `a` and `b` (both the map entries
    /// and their on-screen positions), so a committed swap in the engine stays in
    /// sync with which sprite the rendering layer considers to be at each grid point.
    fun swapTileNodes(a: GridPoint, b: GridPoint) {
        val nodeA = tileNodes[a] ?: return
        val nodeB = tileNodes[b] ?: return
        tileNodes[a] = nodeB
        tileNodes[b] = nodeA
        val duration = SWAP_DURATION * animationSpeedMultiplier
        val (bx, by) = point(b)
        val (ax, ay) = point(a)
        nodeA.run(Action.moveTo(bx, by, duration), MOVE_ACTION_KEY)
        nodeB.run(Action.moveTo(ax, ay, duration), MOVE_ACTION_KEY)
    }

    fun placeTile(type: TileType, gp: GridPoint, droppingInFromAbove: Boolean = false): SpriteNode {
        // Defensive: never silently orphan a node that's already tracked here.
        removeTile(gp)

        val node = TileSpriteNode(Textures.tile(type))
        node.tileType = type
        node.setSize(cellSize * 0.88f, cellSize * 0.88f)
        node.zPosition = 5f
        node.alpha = 0f
        val (tx, ty) = point(gp)
        node.setPosition(tx, if (droppingInFromAbove) ty - cellSize * 3 else ty)
        tileLayer.addChild(node)
        tileNodes[gp] = node
        node.run(Action.fadeIn(SPAWN_FADE_DURATION * animationSpeedMultiplier), SCALE_ACTION_KEY)
        if (droppingInFromAbove) {
            node.run(Action.moveTo(tx, ty, FALL_IN_DURATION * animationSpeedMultiplier, Timing.EASE_IN), MOVE_ACTION_KEY)
        }
        return node
    }

    fun moveTile(from: GridPoint, to: GridPoint, duration: Double) {
        val node = tileNodes.remove(from) ?: return
        // Defensive: never silently orphan a node already tracked at the destination.
        if (to != from) removeTile(to)
        tileNodes[to] = node
        val (tx, ty) = point(to)
        // Eased in (starts slow, speeds up) so a multi-row fall reads as gravity accelerating.
        node.run(Action.moveTo(tx, ty, duration * animationSpeedMultiplier, Timing.EASE_IN), MOVE_ACTION_KEY)
    }

    fun removeTile(gp: GridPoint) {
        val node = tileNodes.remove(gp) ?: return
        val scaleFade = 0.2 * animationSpeedMultiplier
        node.run(
            Action.sequence(
                Action.group(Action.scaleTo(0.1f, scaleFade), Action.fadeOut(scaleFade)),
                Action.removeFromParent(),
            ),
            SCALE_ACTION_KEY,
        )
    }

    /// Detaches (rather than fades) the tile node at `gp`, for callers that want
    /// to animate it independently - e.g. a "fly to the HUD" objective-collection
    /// flight. Its position stays expressed in this node's coordinate space.
    fun detachTile(gp: GridPoint): SpriteNode? {
        val node = tileNodes.remove(gp) ?: return null
        node.removeFromParent()
        return node
    }

    /// Same as `detachTile`, but for the jelly overlay layer.
    fun detachJelly(gp: GridPoint): SpriteNode? {
        val node = jellyNodes.remove(gp) ?: return null
        node.removeFromParent()
        gridTileNodes[gp]?.isHidden = false
        return node
    }

    fun updateTexture(gp: GridPoint, type: TileType) {
        val node = tileNodes[gp] ?: return
        node.texture = Textures.tile(type)
        node.tileType = type
        if (!type.isBacteria) node.warp = null
    }

    /// After a no-more-moves shuffle the engine reassigns tile types across the
    /// whole board without any sprite changing grid points, so each tile scales
    /// down and fades out, swaps to its new type, then scales back up and fades
    /// in; `delay` lets the caller stagger these into a ripple.
    fun reshuffleTile(gp: GridPoint, type: TileType, delay: Double) {
        val node = tileNodes[gp]
        if (node == null) {
            placeTile(type, gp)
            return
        }
        val outDuration = RESHUFFLE_OUT_DURATION * animationSpeedMultiplier
        val inDuration = RESHUFFLE_IN_DURATION * animationSpeedMultiplier
        node.run(
            Action.sequence(
                Action.wait(delay),
                Action.group(Action.scaleTo(0.2f, outDuration, Timing.EASE_IN), Action.fadeOut(outDuration)),
                Action.run {
                    node.texture = Textures.tile(type)
                    node.tileType = type
                    if (!type.isBacteria) node.warp = null
                },
                Action.group(Action.scaleTo(1f, inDuration, Timing.EASE_OUT), Action.fadeIn(inDuration)),
            ),
            SCALE_ACTION_KEY,
        )
    }

    fun pulse(gp: GridPoint) {
        val node = tileNodes[gp] ?: return
        val half = 0.15 * animationSpeedMultiplier
        node.run(Action.sequence(Action.scaleTo(1.15f, half), Action.scaleTo(1f, half)), SCALE_ACTION_KEY)
    }

    /// Shows the "currently selected" ring at `gp`. Selection is a state change,
    /// not an event, so it jumps straight there.
    fun showSelection(gp: GridPoint) {
        val (px, py) = point(gp)
        selectionHighlight.setPosition(px, py)
        selectionHighlight.isHidden = false
    }

    fun hideSelection() {
        selectionHighlight.isHidden = true
    }

    // endregion

    // region Bacteria wobble

    /// Continuously distorts every bacteria tile's four corners - the original
    /// draws bacteria (and only bacteria) with this always-on "jelly wobble" so
    /// they read as organic/alive versus the plain tiles. Called once per frame.
    fun updateBacteriaWobble(elapsedTime: Double) {
        for (node in tileNodes.values) {
            if (node.tileType?.isBacteria != true) continue
            val t = elapsedTime * node.wobbleSpeedScale + node.wobblePhase
            val a = WOBBLE_AMPLITUDE * node.wobbleAmplitudeScale
            val warp = node.warp ?: FloatArray(8).also { node.warp = it }
            // SpriteKit's offsets are y-up; flip them for the y-down canvas.
            fun set(i: Int, baseX: Float, baseY: Float, rate: Double) {
                warp[i * 2] = baseX + (a * sin(rate * t)).toFloat()
                warp[i * 2 + 1] = baseY - (a * cos(rate * t)).toFloat()
            }
            set(0, 0f, 0f, WOBBLE_TL)
            set(1, 1f, 0f, WOBBLE_TR)
            set(2, 0f, 1f, WOBBLE_BL)
            set(3, 1f, 1f, WOBBLE_BR)
        }
    }

    // endregion
}

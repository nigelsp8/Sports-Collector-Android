package com.nigelspeight.sportscollector.game

import com.nigelspeight.sportscollector.AppServices
import com.nigelspeight.sportscollector.audio.AudioManager
import com.nigelspeight.sportscollector.engine.EnginePhase
import com.nigelspeight.sportscollector.engine.GameEngine
import com.nigelspeight.sportscollector.engine.GameEvent
import com.nigelspeight.sportscollector.engine.GridPoint
import com.nigelspeight.sportscollector.engine.ObjectiveKind
import com.nigelspeight.sportscollector.engine.StarThreshold
import com.nigelspeight.sportscollector.level.Level
import kotlin.random.Random

/// Root of the game's node tree (the iOS `SKScene`). Origin is the screen
/// centre; y grows downward. `GameView` drives `update` once per frame and
/// forwards touches in scene coordinates.
class GameScene(
    private val level: Level,
    private var width: Float,
    private var height: Float,
    private val dp: Float,
) : Node() {
    private val engine = GameEngine(level)
    private val background = BackgroundNode(level)
    private val boardNode = BoardNode(level, dp)
    private val hud = HUDNode(dp)
    private val banner = BannerNode(dp)
    private val tutorialOverlay = TutorialOverlayNode(dp)
    private val winOverlay = WinOverlayNode(dp)
    /// The win screen's rotating backdrop - kept separate from `winOverlay`
    /// (and given the same z slot as `background`) so it renders behind the board.
    private val winBackground = WinBackgroundNode()
    private val loseOverlay = LoseOverlayNode(dp)
    private val objectivesOverlay = ObjectivesOverlayNode(dp)

    /// Invoked when an overlay's Continue/Back button is tapped, so the host can
    /// return to level select.
    var onContinue: (() -> Unit)? = null

    /// Invoked when the win overlay's Restart button is tapped, so the host can
    /// rebuild the scene from a fresh board.
    var onRestart: (() -> Unit)? = null

    var isPaused = false
        private set

    /// Accumulated from clamped per-frame `deltaTime`, so background/wobble
    /// sinusoids continue smoothly after the app is backgrounded.
    private var animationTime = 0.0
    private var selectedPoint: GridPoint? = null
    /// `animationTime` when `selectedPoint` was last set, so a selection that's
    /// never followed up on auto-clears.
    private var selectedAt: Double? = null
    private var announcedTutorial = false
    /// The pointer currently pressing the pause button, if any.
    private var pauseButtonPointer: Int? = null

    /// Debug aid (see HUD's speed button): cycles through slower fall-step pacing
    /// + slower tile animations so a cascade can be watched step by step.
    private class DebugSpeed(val label: String, val fallStepInterval: Double, val animationMultiplier: Double)
    private val debugSpeeds = listOf(
        DebugSpeed("Speed: 1x", 0.0, 1.0),
        DebugSpeed("Speed: 0.75x", 0.0, 1.5),
        DebugSpeed("Speed: 0.5x", 0.0, 2.0),
        DebugSpeed("Speed: 0.25x", 0.35, 4.0),
        DebugSpeed("Speed: 0.1x", 0.9, 8.0),
    )
    private var debugSpeedIndex = 0

    companion object {
        /// How long a tile stays visibly selected with no follow-up tap.
        private const val SELECTION_TIMEOUT = 3.0
        const val BACKGROUND_COLOR = 0xFF141414.toInt()
    }

    init {
        engine.swapAnimationDuration = BoardNode.SWAP_DURATION

        addChild(background)
        winBackground.zPosition = -100f
        winBackground.isHidden = true
        addChild(winBackground)
        addChild(boardNode)
        addChild(hud)
        addChild(banner)
        addChild(tutorialOverlay)
        addChild(winOverlay)
        addChild(loseOverlay)
        addChild(objectivesOverlay)
    }

    /// Called once the hosting view has a size (`SKScene.didMove(to:)`).
    fun didMove() {
        layoutForCurrentSize()
        seedInitialTiles()
        AudioManager.playMusic("DrPopperTune1", loop = true)
    }

    fun resize(width: Float, height: Float) {
        this.width = width
        this.height = height
        layoutForCurrentSize()
    }

    private fun layoutForCurrentSize() {
        background.layout(width, height)
        boardNode.layout(width * 0.92f, height * 0.72f)
        boardNode.setPosition(0f, 0f)
        hud.layout(width, height)
        winOverlay.layout(width, height)
        winBackground.layout(width, height)
        loseOverlay.layout(width, height)
        objectivesOverlay.layout(width, height)
    }

    private fun seedInitialTiles() {
        for (row in 0 until level.height) {
            for (col in 0 until level.width) {
                val gp = GridPoint(row, col)
                engine.board[gp]?.tile?.let { boardNode.placeTile(it, gp) }
            }
        }
        refreshHUD()

        objectivesOverlay.configure(engine.objectives)
        objectivesOverlay.show()
    }

    private fun refreshHUD() {
        hud.update(level.mapNumber, engine.score, engine.movesRemaining, engine.objectives)
    }

    // region Frame loop

    fun update(deltaTime: Double) {
        if (isPaused) return
        animationTime += deltaTime

        val selected = selectedAt
        if (selected != null && animationTime - selected > SELECTION_TIMEOUT) {
            selectedPoint = null
            selectedAt = null
            boardNode.hideSelection()
        }

        background.update(animationTime)
        boardNode.updateBacteriaWobble(animationTime)

        // Captured before `advance()` mutates `engine.objectives`, so the batch
        // handler can tell whether a given objective still had a nonzero count
        // *before* the removal it's currently looking at.
        val objectivesBefore = engine.objectives.associate { it.kind to it.remaining }
        val events = engine.advance(deltaTime)
        if (events.isNotEmpty()) {
            handle(events, objectivesBefore)
            refreshHUD()
        }

        if (!announcedTutorial && engine.phase == EnginePhase.TUTORIAL_RESTRICTED) {
            announcedTutorial = true
            engine.tutorialRect?.let { rect ->
                val center = GridPoint((rect.minRow + rect.maxRow) / 2, (rect.minCol + rect.maxCol) / 2)
                val (px, py) = boardNode.point(center)
                val p = boardNode.convertToRoot(px, py)
                tutorialOverlay.show(p.x, p.y)
            }
        }

        tick(deltaTime)
    }

    /// Processes one frame's full event batch, rather than one event at a time,
    /// so `TileRemoved`/`JellyCleared` can be paired with a same-kind
    /// `ObjectiveProgressed` that follows shortly after. When a removal is linked
    /// to an objective that still had a nonzero count, it flies to that
    /// objective's HUD icon instead of fading in place, and the HUD's count label
    /// only decrements once it lands - see `animateObjectiveCollection`.
    private fun handle(events: List<GameEvent>, objectivesBefore: Map<ObjectiveKind, Int>) {
        playBatchSounds(events)

        val remainingByKind = objectivesBefore.toMutableMap()

        fun pairedObjectiveProgressed(index: Int, kind: ObjectiveKind): Int? {
            for (lookahead in index + 1 until events.size) {
                when (val event = events[lookahead]) {
                    is GameEvent.TileRemoved, is GameEvent.JellyCleared -> return null
                    is GameEvent.ObjectiveProgressed -> if (event.kind == kind) return event.remaining
                    else -> Unit
                }
            }
            return null
        }

        events.forEachIndexed { index, event ->
            when (event) {
                is GameEvent.TileRemoved -> {
                    val kind = ObjectiveKind.Collect(event.tile)
                    val remaining = pairedObjectiveProgressed(index, kind)
                    if (remaining != null) {
                        val before = remainingByKind[kind] ?: 0
                        remainingByKind[kind] = remaining
                        if (before > 0) {
                            val node = boardNode.detachTile(event.point)
                            if (node != null) {
                                animateObjectiveCollection(node, kind, remaining)
                                return@forEachIndexed
                            }
                        }
                    }
                    spawnCollectionBurst(Textures.tile(event.tile), event.point)
                    boardNode.removeTile(event.point)
                }

                is GameEvent.JellyCleared -> {
                    val kind = ObjectiveKind.ClearJelly
                    val remaining = pairedObjectiveProgressed(index, kind)
                    if (remaining != null) {
                        val before = remainingByKind[kind] ?: 0
                        remainingByKind[kind] = remaining
                        if (before > 0) {
                            val node = boardNode.detachJelly(event.point)
                            if (node != null) {
                                animateObjectiveCollection(node, kind, remaining)
                                return@forEachIndexed
                            }
                        }
                    }
                    spawnCollectionBurst(Textures.jellyOverlay, event.point)
                    boardNode.clearJelly(event.point)
                }

                else -> handle(event)
            }
        }
    }

    /// Plays each sound category at most once per event batch, mirroring the
    /// original game's per-update-pass boolean-gated sound playback, so a big
    /// cascade doesn't fire the same short sound a dozen times at once.
    private fun playBatchSounds(events: List<GameEvent>) {
        if (events.any { it is GameEvent.TileMoved }) playSound("PillsFalling1Sec")
        if (events.any { it is GameEvent.TileRemoved && it.tile.isBacteria }) playSound("BacteriaFall_1sec")
        if (events.any { it is GameEvent.TileRemoved && !it.tile.isBacteria }) playSound("StarryEffect1A_UpBeat")
        if (events.any { it is GameEvent.JellyCleared }) playSound("Squelch_Tile1")
        if (events.any { it is GameEvent.SolidDamaged && it.to != null }) playSound("Explosion2Loud")
        if (events.any { it is GameEvent.SolidDamaged && it.to == null }) playSound("Explosion1")
        if (events.any { it is GameEvent.WallDestroyed }) playSound(if (Random.nextBoolean()) "Siren1_2secs" else "Siren2_2secs")
        if (events.any { it is GameEvent.ComboPillDowngraded }) playSound("PillX2")
    }

    private fun playSound(name: String) = AudioManager.playSound(name)

    /// Flies a detached tile/jelly sprite from its current board position to its
    /// objective's HUD icon, decrementing that icon's count only once it lands.
    private fun animateObjectiveCollection(node: SpriteNode, kind: ObjectiveKind, remaining: Int) {
        node.removeAllActions()
        val start = boardNode.convertToRoot(node.x, node.y)
        addChild(node)
        node.setPosition(start.x, start.y)
        node.zPosition = 50f

        val target = hud.scenePosition(kind)
        if (target == null) {
            // Defensive: HUD isn't tracking this objective - fall back to a plain
            // fade instead of flying nowhere.
            node.run(
                Action.sequence(
                    Action.group(Action.scaleTo(0.1f, 0.2), Action.fadeOut(0.2)),
                    Action.removeFromParent(),
                ),
            )
            hud.collect(kind, remaining)
            return
        }

        val duration = 0.45 * boardNode.animationSpeedMultiplier
        node.run(
            Action.sequence(
                Action.group(
                    Action.moveTo(target.x, target.y, duration, Timing.EASE_IN),
                    Action.scaleTo(0.3f, duration),
                ),
                Action.run { hud.collect(kind, remaining) },
                Action.fadeOut(0.08),
                Action.removeFromParent(),
            ),
        )
    }

    /// Small particle burst, using the removed object's own texture, for removals
    /// that have no HUD objective to fly toward. Purely cosmetic and self-removing.
    private fun spawnCollectionBurst(texture: Texture, gp: GridPoint) {
        val speedMultiplier = Random.nextDouble(2.5, 3.5)
        val emitter = ParticleBurstNode(
            texture = texture,
            count = 10,
            lifetime = 0.35 * speedMultiplier,
            lifetimeRange = 0.1 * speedMultiplier,
            speed = 70 * dp,
            speedRange = 30 * dp,
            baseSize = texture.width * dp,
            scale = 0.132f,
            scaleRange = 0.12f,
            scaleSpeed = (-0.5 / speedMultiplier).toFloat(),
            alphaSpeed = (-2.6 / speedMultiplier).toFloat(),
            rotationSpeed = 2f,
        )
        val (px, py) = boardNode.point(gp)
        val p = boardNode.convertToRoot(px, py)
        emitter.setPosition(p.x, p.y)
        emitter.zPosition = 40f
        addChild(emitter)
        emitter.run(Action.sequence(Action.wait(0.5 * speedMultiplier), Action.removeFromParent()))
    }

    private fun handle(event: GameEvent) {
        when (event) {
            is GameEvent.IntroFadeInStarted, is GameEvent.ObjectiveProgressed, is GameEvent.ObjectiveFlyCompleted,
            is GameEvent.BacteriaFellOff, is GameEvent.ScoreChanged, is GameEvent.MovesChanged,
            is GameEvent.TileRemoved, is GameEvent.JellyCleared -> Unit

            is GameEvent.SwapAnimated -> {
                boardNode.swapTileNodes(event.a, event.b)
                playSound("Swap_Pill1A")
            }

            is GameEvent.TilePlaced -> boardNode.placeTile(event.type, event.point, droppingInFromAbove = true)

            is GameEvent.SwapRejected -> {
                boardNode.pulse(event.a)
                boardNode.pulse(event.b)
                playSound("SwapPillError1")
            }

            is GameEvent.TileMoved -> boardNode.moveTile(event.from, event.to, duration = 0.16)

            is GameEvent.ComboPillDowngraded -> boardNode.updateTexture(event.point, event.to)

            is GameEvent.SolidDamaged -> {
                val to = event.to
                if (to != null) boardNode.updateTexture(event.point, to) else boardNode.removeTile(event.point)
            }

            is GameEvent.WallDestroyed -> boardNode.removeWall(event.a, event.b)

            is GameEvent.HintSuggested -> {
                boardNode.pulse(event.a)
                boardNode.pulse(event.b)
                playSound("ShowHint")
            }

            GameEvent.NoMoreMovesShuffleStarted -> banner.show("No More Moves!")

            GameEvent.NoMoreMovesShuffleFinished -> resyncAllTilePositions()

            GameEvent.TutorialUnlocked -> tutorialOverlay.hide()

            is GameEvent.Won -> {
                AudioManager.playMusic("PILLPOPTUNE", loop = false)
                // The engine's own score/stars are computed the instant the board
                // is solved, before the moves-remaining bonus the win overlay
                // reveals - recompute both against the post-bonus total so the
                // persisted result and the star rating shown both reflect it.
                val movesLeft = engine.movesRemaining
                val finalScore = event.score + movesLeft * WinOverlayNode.MOVE_BONUS_PER_MOVE
                val finalStars = StarThreshold.stars(finalScore, level.starThresholds)
                AppServices.progress.recordWin(level.mapNumber, finalScore, finalStars)
                background.isHidden = true
                winBackground.isHidden = false
                winOverlay.show(event.score, movesLeft, finalStars) { currentScore, moves ->
                    hud.update(level.mapNumber, currentScore, moves, engine.objectives)
                }
            }

            GameEvent.Lost -> {
                AudioManager.playMusic("DoctorPopperLoseJingle_4sec", loop = false)
                loseOverlay.show()
            }
        }
    }

    /// After a shuffle the engine's board has an entirely fresh arrangement;
    /// rather than tracking which sprite went where, resync every tile's texture
    /// against the current board state, staggered by distance from the top-left
    /// corner so it reads as a ripple sweeping across the board.
    private fun resyncAllTilePositions() {
        for (row in 0 until level.height) {
            for (col in 0 until level.width) {
                val gp = GridPoint(row, col)
                val tile = engine.board[gp]?.tile ?: continue
                boardNode.reshuffleTile(gp, tile, delay = (row + col) * 0.025)
            }
        }
    }

    // endregion

    // region Touch input

    private fun togglePause() {
        // Direct alpha set, not an animated show/hide: a paused scene never
        // evaluates queued actions, so a fade-in would never reach visible.
        isPaused = !isPaused
        if (isPaused) banner.setVisibleInstantly(true, "Paused") else banner.setVisibleInstantly(false)
    }

    private fun cycleDebugSpeed() {
        debugSpeedIndex = (debugSpeedIndex + 1) % debugSpeeds.size
        val speed = debugSpeeds[debugSpeedIndex]
        engine.debugFallStepInterval = speed.fallStepInterval
        engine.swapAnimationDuration = BoardNode.SWAP_DURATION * speed.animationMultiplier
        boardNode.animationSpeedMultiplier = speed.animationMultiplier
        hud.setSpeedText(speed.label)
    }

    /// `x`/`y` are in scene coordinates. Overlays, HUD and board all sit at the
    /// scene origin, so scene coordinates are also their local coordinates.
    fun touchBegan(pointerId: Int, x: Float, y: Float) {
        if (!objectivesOverlay.isHidden) {
            if (objectivesOverlay.containsBackButton(x, y)) {
                onContinue?.invoke()
            } else if (objectivesOverlay.containsContinueButton(x, y)) {
                objectivesOverlay.hide()
            }
            return
        }
        if (!winOverlay.isHidden) {
            if (winOverlay.containsRestartButton(x, y)) {
                onRestart?.invoke()
            } else if (winOverlay.containsContinueButton(x, y)) {
                onContinue?.invoke()
            }
            return
        }
        if (!loseOverlay.isHidden) {
            if (loseOverlay.containsContinueButton(x, y)) onContinue?.invoke()
            return
        }
        if (hud.containsPauseToggle(x, y)) {
            pauseButtonPointer = pointerId
            hud.setPauseButtonPressed(true)
            togglePause()
            return
        }
        if (HUDNode.SHOW_SPEED_BUTTON && hud.containsSpeedToggle(x, y)) {
            cycleDebugSpeed()
            return
        }
        engine.notifyUserInteraction()
        val gp = boardNode.gridPoint(x - boardNode.x, y - boardNode.y) ?: return

        val selected = selectedPoint
        if (selected != null) {
            selectedPoint = null
            selectedAt = null
            boardNode.hideSelection()
            if (selected != gp) engine.attemptSwap(selected, gp)
        } else {
            // Only show the tile as selected if a move is actually possible from
            // here - a cell with no tile, or a tap while the board is
            // mid-animation/won/lost, can never lead anywhere.
            if (boardNode.tileNode(gp) == null ||
                (engine.phase != EnginePhase.IDLE && engine.phase != EnginePhase.TUTORIAL_RESTRICTED)
            ) return
            selectedPoint = gp
            selectedAt = animationTime
            boardNode.showSelection(gp)
        }
    }

    fun touchMoved(x: Float, y: Float) {
        val selected = selectedPoint ?: return
        val gp = boardNode.gridPoint(x - boardNode.x, y - boardNode.y) ?: return
        if (gp == selected) return
        selectedPoint = null
        selectedAt = null
        boardNode.hideSelection()
        engine.attemptSwap(selected, gp)
    }

    fun touchEnded(pointerId: Int) {
        if (pauseButtonPointer != pointerId) return
        pauseButtonPointer = null
        hud.setPauseButtonPressed(false)
    }

    // endregion
}

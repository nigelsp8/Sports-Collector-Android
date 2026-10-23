package com.nigelspeight.sportscollector.game

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import com.nigelspeight.sportscollector.audio.AudioManager
import com.nigelspeight.sportscollector.engine.Objective
import com.nigelspeight.sportscollector.engine.TutorialStep
import com.nigelspeight.sportscollector.level.Level
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()
private const val DIM = 0xBF000000.toInt() // black at 75% alpha

/// The part of the screen clear of the status bar and display cutout, in scene
/// coordinates (origin at the screen centre). Game content is laid out within
/// it; backgrounds and dimming overlays still cover the full screen.
data class SafeArea(
    val screenWidth: Float,
    val screenHeight: Float,
    val insetLeft: Float = 0f,
    val insetTop: Float = 0f,
    val insetRight: Float = 0f,
    val insetBottom: Float = 0f,
) {
    val width: Float get() = screenWidth - insetLeft - insetRight
    val height: Float get() = screenHeight - insetTop - insetBottom
    val centerX: Float get() = (insetLeft - insetRight) / 2
    val centerY: Float get() = (insetTop - insetBottom) / 2

    /// Centres `node` on the safe area and stretches `fullScreen` (a child of
    /// `node`) back out over the entire screen.
    fun coverScreen(node: Node, fullScreen: SpriteNode) {
        node.setPosition(centerX, centerY)
        fullScreen.setSize(screenWidth, screenHeight)
        fullScreen.setPosition(-centerX, -centerY)
    }
}

/// Full-scene background, reproducing the original game's `drawBackground`
/// technique: the source art is authored larger than the visible window (a
/// 2048x2048 texture where only a 64%-wide x 75%-tall crop is ever shown), and
/// that crop is slowly panned along an elliptical sinusoidal path.
class BackgroundNode(level: Level) : SpriteNode() {
    companion object {
        private const val CROP_WIDTH_FRACTION = 0.64f
        private const val CROP_HEIGHT_FRACTION = 0.75f
        private const val PAN_AMPLITUDE_X = 0.18f
        private const val PAN_AMPLITUDE_Y = 0.125f
        private const val ANGULAR_SPEED = 2.7 * Math.PI / 180 // ~2.7 deg/sec drift rate
    }

    init {
        var lt = level.mapNumber / 17
        if (lt > 5) lt -= 6
        texture = Textures.uncachedImage("bkg${lt + 1}b")
        zPosition = -100f
    }

    fun layout(width: Float, height: Float) {
        setSize(width / CROP_WIDTH_FRACTION, height / CROP_HEIGHT_FRACTION)
    }

    fun update(elapsedTime: Double) {
        val angle = elapsedTime * ANGULAR_SPEED
        setPosition(
            width * PAN_AMPLITUDE_X * sin(angle).toFloat(),
            -height * PAN_AMPLITUDE_Y * cos(angle).toFloat(),
        )
    }
}

/// iOS blurs the win/lose backdrops live with a `CIGaussianBlur`; since the
/// blur doesn't depend on the backdrop's rotation/warp, blurring the texture
/// once up front (by downsampling and letting bilinear upscaling smooth it)
/// gives the same look.
private fun blurred(texture: Texture): Texture {
    var bitmap = texture.bitmap
    repeat(2) {
        bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
    }
    return Texture(bitmap)
}

/// The spinning sunburst backdrop shown behind the win card, replacing the
/// normal panning `BackgroundNode`.
class WinBackgroundNode : Node() {
    private val sprite = SpriteNode(blurred(Textures.uncachedImage("bkgwin")))

    init {
        addChild(sprite)
        sprite.run(Action.repeatForever(Action.rotateBy((-Math.PI * 2).toFloat(), 14.0)))
    }

    fun layout(width: Float, height: Float) {
        // A square image rotating about its own centre only guarantees full
        // screen coverage at every angle once its side is at least the
        // screen's diagonal.
        val diagonal = sqrt(width * width + height * height)
        sprite.setSize(diagonal, diagonal)
    }
}

/// The wobbling backdrop shown behind the lose card, replacing the normal
/// panning `BackgroundNode` - uses the "bkglose" art, continuously distorted at
/// its four corners the same way `BoardNode` wobbles bacteria tiles (see
/// `BoardNode.updateBacteriaWobble`).
class LoseBackgroundNode : Node() {
    companion object {
        /// Amplitude of each corner's displacement, as a fraction of the
        /// sprite's own size - mirrors `BoardNode`'s bacteria wobble.
        private const val WOBBLE_AMPLITUDE = 0.007
        /// Each corner advances at its own rate (radians/sec) so the four
        /// corners never move in lockstep.
        private const val WOBBLE_TL = 2.408
        private const val WOBBLE_TR = 2.304
        private const val WOBBLE_BL = 2.513
        private const val WOBBLE_BR = 2.723
    }

    private val sprite = SpriteNode(blurred(Textures.uncachedImage("bkglose")))
    private val warp = FloatArray(8)

    init {
        addChild(sprite)
        sprite.warp = warp
        update(0.0)
    }

    fun layout(width: Float, height: Float) {
        // Oversize a little so the wobble's corner displacement never pulls an
        // edge in far enough to reveal whatever sits behind this node.
        val margin = (1 + WOBBLE_AMPLITUDE * 2).toFloat()
        sprite.setSize(width * margin, height * margin)
    }

    fun update(elapsedTime: Double) {
        // SpriteKit's offsets are y-up; flip them for the y-down canvas.
        fun set(i: Int, baseX: Float, baseY: Float, rate: Double) {
            warp[i * 2] = baseX + (WOBBLE_AMPLITUDE * sin(rate * elapsedTime)).toFloat()
            warp[i * 2 + 1] = baseY - (WOBBLE_AMPLITUDE * cos(rate * elapsedTime)).toFloat()
        }
        set(0, 0f, 0f, WOBBLE_TL)
        set(1, 1f, 0f, WOBBLE_TR)
        set(2, 0f, 1f, WOBBLE_BL)
        set(3, 1f, 1f, WOBBLE_BR)
    }
}

/// A centered text banner for transient messages ("No More Moves!").
class BannerNode(dp: Float) : Node() {
    private val label = LabelNode(32 * dp).apply { color = WHITE }

    init {
        alpha = 0f
        zPosition = 100f
        addChild(label)
    }

    fun show(message: String, duration: Double = 1.2) {
        label.text = message
        removeAllActions()
        run(Action.sequence(Action.fadeIn(0.2), Action.wait(duration), Action.fadeOut(0.3)))
    }

    fun showPersistently(message: String) {
        label.text = message
        removeAllActions()
        run(Action.fadeIn(0.3))
    }

    fun hide() {
        removeAllActions()
        run(Action.fadeOut(0.2))
    }

    /// Sets visibility directly rather than via a fade - needed while the scene
    /// is paused, since a paused scene never evaluates queued actions.
    fun setVisibleInstantly(visible: Boolean, message: String? = null) {
        removeAllActions()
        if (message != null) label.text = message
        alpha = if (visible) 1f else 0f
    }
}

/// Shown while `GameEngine.phase == TUTORIAL_RESTRICTED`: dims everything
/// except the two cells the player is restricted to, draws a box around them,
/// shows the level's tutorial title/summary, and animates `hand.png` back and
/// forth between the two cells. Purely cosmetic - the actual input restriction
/// lives in `GameEngine.tutorialRect`/`attemptSwap`.
class TutorialOverlayNode(private val dp: Float) : Node() {
    companion object {
        /// Gap between the highlighted cells' combined footprint and the dim
        /// cutout/box around them, as a fraction of `cellSize`.
        private const val HIGHLIGHT_PADDING = 0.18f
        private const val HAND_MOVE_DURATION = 0.55
        private const val HAND_WAIT_DURATION = 0.25
    }

    private class Geometry(
        val ax: Float, val ay: Float, val bx: Float, val by: Float, val cellSize: Float,
    )

    private val dimTop = SpriteNode(color = DIM)
    private val dimBottom = SpriteNode(color = DIM)
    private val dimLeft = SpriteNode(color = DIM)
    private val dimRight = SpriteNode(color = DIM)
    private val highlightBox = RoundedRectNode(0f, 0f, 0f).apply {
        strokeColor = WHITE
        lineWidth = 4 * dp
        zPosition = 2f
    }
    private val titleLabel = LabelNode().apply {
        color = WHITE
        vAlign = LabelNode.VAlign.TOP
        zPosition = 3f
    }
    private val summaryLabel = LabelNode().apply {
        color = WHITE
        vAlign = LabelNode.VAlign.TOP
        zPosition = 3f
    }
    private val hand = SpriteNode(Textures.image("hand")).apply {
        setSize(90 * dp, 76 * dp)
        alpha = 0f
        zPosition = 4f
    }

    private var lastGeometry: Geometry? = null
    private var handWaypoints: Pair<PointF, PointF>? = null

    init {
        isHidden = true
        // Above the board and HUD, below the result/pause overlays.
        zPosition = 100f
        addChild(dimTop)
        addChild(dimBottom)
        addChild(dimLeft)
        addChild(dimRight)
        addChild(highlightBox)
        addChild(titleLabel)
        addChild(summaryLabel)
        addChild(hand)
    }

    /// Fades everything in, starts the hand's back-and-forth loop, and sets the
    /// title/summary text. Cell centres and `cellSize` are in scene coordinates
    /// (already converted from `BoardNode`).
    fun show(step: TutorialStep, ax: Float, ay: Float, bx: Float, by: Float, cellSize: Float, area: SafeArea) {
        titleLabel.text = step.title
        summaryLabel.text = step.summary

        layout(Geometry(ax, ay, bx, by, cellSize), area)

        isHidden = false
        alpha = 0f
        run(Action.fadeIn(0.3))
        startHandLoop()
    }

    /// Pure repositioning against the same last-shown step, for resizes.
    fun relayout(area: SafeArea) {
        lastGeometry?.let { layout(it, area) }
    }

    fun hide() {
        hand.removeAllActions()
        run(Action.sequence(Action.fadeOut(0.25), Action.run { isHidden = true }))
    }

    private fun layout(geometry: Geometry, area: SafeArea) {
        lastGeometry = geometry
        val cellSize = geometry.cellSize

        val padding = cellSize * HIGHLIGHT_PADDING
        val highlight = RectF(
            min(geometry.ax, geometry.bx) - cellSize / 2 - padding,
            min(geometry.ay, geometry.by) - cellSize / 2 - padding,
            max(geometry.ax, geometry.bx) + cellSize / 2 + padding,
            max(geometry.ay, geometry.by) + cellSize / 2 + padding,
        )

        highlightBox.width = highlight.width()
        highlightBox.height = highlight.height()
        highlightBox.cornerRadius = cellSize * 0.18f
        highlightBox.setPosition(highlight.centerX(), highlight.centerY())

        layoutDim(highlight, area.screenWidth, area.screenHeight)

        // Clears `HUDNode`'s top-zone level/score/moves badges, which sit a
        // width-scaled band (~0.15 x width) under a 12dp inset from the safe
        // area's top edge - see `HUDNode.layout`.
        val width = area.width
        val hudTopZoneClearance = width * 0.16f + 12 * dp + 24 * dp
        val titleTopY = area.centerY - area.height / 2 + hudTopZoneClearance
        titleLabel.fontSize = min(width * 0.1f, 56 * dp)
        titleLabel.setPosition(area.centerX, titleTopY)
        summaryLabel.fontSize = min(width * 0.055f, 28 * dp)
        summaryLabel.maxLayoutWidth = area.screenWidth * 0.86f
        summaryLabel.setPosition(area.centerX, titleTopY + titleLabel.measureHeight() + 10 * dp)

        // One tile right, one tile down from each highlighted cell.
        handWaypoints = PointF(geometry.ax + cellSize, geometry.ay + cellSize) to
            PointF(geometry.bx + cellSize, geometry.by + cellSize)
        if (hand.alpha > 0f) startHandLoop()
    }

    private fun layoutDim(highlight: RectF, screenWidth: Float, screenHeight: Float) {
        val halfW = screenWidth / 2
        val halfH = screenHeight / 2

        val topHeight = max(0f, highlight.top + halfH)
        dimTop.setSize(screenWidth, topHeight)
        dimTop.setPosition(0f, highlight.top - topHeight / 2)

        val bottomHeight = max(0f, halfH - highlight.bottom)
        dimBottom.setSize(screenWidth, bottomHeight)
        dimBottom.setPosition(0f, highlight.bottom + bottomHeight / 2)

        val leftWidth = max(0f, highlight.left + halfW)
        dimLeft.setSize(leftWidth, highlight.height())
        dimLeft.setPosition(highlight.left - leftWidth / 2, highlight.centerY())

        val rightWidth = max(0f, halfW - highlight.right)
        dimRight.setSize(rightWidth, highlight.height())
        dimRight.setPosition(highlight.right + rightWidth / 2, highlight.centerY())
    }

    private fun startHandLoop() {
        val (posA, posB) = handWaypoints ?: return
        hand.removeAllActions()
        hand.setPosition(posA.x, posA.y)
        hand.alpha = 0f
        val wait = Action.wait(HAND_WAIT_DURATION)
        hand.run(
            Action.sequence(
                Action.fadeAlphaTo(0.9f, 0.3),
                Action.repeatForever(
                    Action.sequence(
                        wait,
                        Action.moveTo(posB.x, posB.y, HAND_MOVE_DURATION, Timing.EASE_IN_EASE_OUT),
                        wait,
                        Action.moveTo(posA.x, posA.y, HAND_MOVE_DURATION, Timing.EASE_IN_EASE_OUT),
                    ),
                ),
            ),
        )
    }
}

private fun titleLabel(text: String, fontSize: Float) = LabelNode(fontSize).apply {
    this.text = text
    color = WHITE
    strokeColor = BLACK
    strokeWidthPercent = 4f
    vAlign = LabelNode.VAlign.BASELINE
}

/// Full-screen "You Win" overlay: darkens the screen behind a win card with a
/// star rating and Continue/Restart buttons. Owns everything except the
/// rotating `WinBackgroundNode` backdrop, which `GameScene` positions behind
/// the board.
class WinOverlayNode(private val dp: Float) : Node() {
    companion object {
        /// Points awarded for each move the player had left when the board was
        /// solved, revealed one at a time by `show` before the stars/Continue
        /// button appear.
        const val MOVE_BONUS_PER_MOVE = 3000
        private const val MOVE_BONUS_TICK_INTERVAL = 0.25
    }

    private val dimOverlay = SpriteNode(color = DIM)
    private val title = titleLabel("You Win!", 80 * dp)
    private val winImage = SpriteNode(Textures.image("win"))
    private val scoreLabel = cardLabel()
    private val moveBonusLabel = cardLabel()
    private val starsImage = SpriteNode(Textures.image("starsx3"))
    private val continueButton = SpriteNode(Textures.image("nextbutton"))
    private val restartButton = SpriteNode(Textures.image("rewindbutton"))

    private var cardFontSize = 24 * dp

    private fun cardLabel() = LabelNode().apply {
        color = WHITE
        strokeColor = BLACK
        strokeWidthPercent = 3f
    }

    init {
        isHidden = true
        zPosition = 150f
        addChild(dimOverlay)
        addChild(title)
        addChild(winImage)
        addChild(scoreLabel)
        addChild(moveBonusLabel)
        addChild(starsImage)
        addChild(continueButton)
        addChild(restartButton)
    }

    /// Content is laid out within the safe area; the dim overlay still covers
    /// the whole screen, including under the status bar/cutout.
    fun layout(area: SafeArea) {
        val width = area.width
        val height = area.height
        area.coverScreen(this, dimOverlay)

        val winSize = width * 0.75f
        val winY = -height * 0.04f
        winImage.setSize(winSize, winSize)
        winImage.setPosition(0f, winY)

        val titleFontSize = min(width * 0.16f, 80 * dp)
        title.fontSize = titleFontSize
        title.setPosition(0f, winY - winSize / 2 - titleFontSize * 0.3f)

        val starsWidth = winSize * 0.56f
        starsImage.setSize(starsWidth, starsWidth * (440f / 724f))
        starsImage.setPosition(0f, winY + winSize * 0.5f)

        cardFontSize = min(width * 0.07f, 32 * dp)
        scoreLabel.fontSize = cardFontSize
        moveBonusLabel.fontSize = cardFontSize * 0.6f
        scoreLabel.setPosition(0f, winY + winSize * 0.1f)
        moveBonusLabel.setPosition(0f, winY + winSize * 0.1f + cardFontSize * 1.4f)

        val buttonSize = width * 0.2f
        val spacing = buttonSize + 16 * dp
        val buttonY = height / 2 - buttonSize * 0.75f - 24 * dp
        continueButton.setSize(buttonSize * 1.5f, buttonSize * 1.5f)
        continueButton.setPosition(spacing, buttonY)
        restartButton.setSize(buttonSize, buttonSize)
        restartButton.setPosition(-spacing, buttonY)
    }

    /// Shows the win card immediately (title/win image/running score), then -
    /// once the fade-in finishes - ticks `movesRemaining` move-bonus awards into
    /// the score one at a time, only revealing the star rating and buttons once
    /// that tally finishes. `finalStars` is already computed against the score
    /// including the move bonus. `onBonusTick(currentScore, movesLeft)` fires
    /// once immediately and again after every tick, so the HUD behind this
    /// overlay can stay in sync.
    fun show(baseScore: Int, movesRemaining: Int, finalStars: Int, onBonusTick: (Int, Int) -> Unit = { _, _ -> }) {
        starsImage.isHidden = true
        continueButton.isHidden = true
        restartButton.isHidden = true
        scoreLabel.alpha = 1f
        moveBonusLabel.alpha = 1f
        scoreLabel.text = baseScore.toString()
        moveBonusLabel.isHidden = movesRemaining <= 0
        moveBonusLabel.text = "Moves Bonus: $movesRemaining x $MOVE_BONUS_PER_MOVE"
        onBonusTick(baseScore, movesRemaining)

        isHidden = false
        alpha = 0f
        run(
            Action.sequence(
                Action.fadeIn(0.3),
                Action.run { runMoveBonusSequence(baseScore, movesRemaining, finalStars, onBonusTick) },
            ),
        )
    }

    private fun runMoveBonusSequence(baseScore: Int, movesRemaining: Int, finalStars: Int, onBonusTick: (Int, Int) -> Unit) {
        if (movesRemaining <= 0) {
            revealStarsAndContinue(finalStars)
            return
        }

        var runningScore = baseScore
        val tickActions = mutableListOf<Action>()
        for (movesLeftAfterTick in movesRemaining - 1 downTo 0) {
            tickActions += Action.wait(MOVE_BONUS_TICK_INTERVAL)
            tickActions += Action.run {
                runningScore += MOVE_BONUS_PER_MOVE
                scoreLabel.text = runningScore.toString()
                scoreLabel.run(Action.sequence(Action.scaleTo(1.15f, 0.06), Action.scaleTo(1f, 0.09)))
                moveBonusLabel.text = "Moves Bonus: $movesLeftAfterTick x $MOVE_BONUS_PER_MOVE"
                onBonusTick(runningScore, movesLeftAfterTick)
            }
        }
        tickActions += Action.wait(MOVE_BONUS_TICK_INTERVAL)
        tickActions += Action.run { revealStarsAndContinue(finalStars) }
        run(Action.sequence(tickActions), "moveBonusSequence")
    }

    private fun revealStarsAndContinue(stars: Int) {
        moveBonusLabel.run(Action.fadeOut(0.2))

        if (stars <= 0) {
            starsImage.isHidden = true
        } else {
            starsImage.isHidden = false
            starsImage.alpha = 0f
            starsImage.setScale(1.6f)
            starsImage.texture = Textures.image("starsx${min(stars, 3)}")
            starsImage.run(Action.group(Action.fadeIn(0.3), Action.scaleTo(1f, 0.3, Timing.EASE_OUT)))
            AudioManager.playSound("WinStars")
        }

        for (button in listOf(continueButton, restartButton)) {
            button.isHidden = false
            button.alpha = 0f
            button.run(Action.fadeIn(0.3))
        }
    }

    /// Hit tests in this node's own coordinate space.
    fun containsContinueButton(px: Float, py: Float): Boolean = continueButton.contains(px, py)
    fun containsRestartButton(px: Float, py: Float): Boolean = restartButton.contains(px, py)
}

/// Full-screen "lose" overlay: darkens the screen behind a lose card with a
/// Continue button that returns to level select and a Restart button that
/// replays the level. The wobbling `LoseBackgroundNode` backdrop (which
/// `GameScene` positions separately, behind the board) swaps in as soon as the
/// loss happens, mirroring the win backdrop.
class LoseOverlayNode(private val dp: Float) : Node() {
    private val dimOverlay = SpriteNode(color = DIM)
    private val title = titleLabel("Unlucky!", 80 * dp)
    private val loseImage = SpriteNode(Textures.image("lose"))
    private val continueButton = SpriteNode(Textures.image("nextbutton"))
    private val restartButton = SpriteNode(Textures.image("rewindbutton"))

    init {
        isHidden = true
        zPosition = 150f
        addChild(dimOverlay)
        addChild(title)
        addChild(loseImage)
        addChild(continueButton)
        addChild(restartButton)
    }

    /// Content is laid out within the safe area; the dim overlay still covers
    /// the whole screen, including under the status bar/cutout.
    fun layout(area: SafeArea) {
        val width = area.width
        val height = area.height
        area.coverScreen(this, dimOverlay)

        val loseSize = width * 0.75f
        val loseY = -height * 0.04f
        loseImage.setSize(loseSize, loseSize)
        loseImage.setPosition(0f, loseY)

        val titleFontSize = min(width * 0.16f, 80 * dp)
        title.fontSize = titleFontSize
        title.setPosition(0f, loseY - loseSize / 2 - titleFontSize * 0.3f)

        val buttonSize = width * 0.2f
        val spacing = buttonSize + 16 * dp
        val buttonY = height / 2 - buttonSize * 0.75f - 24 * dp
        continueButton.setSize(buttonSize, buttonSize)
        continueButton.setPosition(spacing, buttonY)
        restartButton.setSize(buttonSize, buttonSize)
        restartButton.setPosition(-spacing, buttonY)
    }

    fun show() {
        isHidden = false
        alpha = 0f
        run(Action.fadeIn(0.3))
    }

    fun containsContinueButton(px: Float, py: Float): Boolean = continueButton.contains(px, py)
    fun containsRestartButton(px: Float, py: Float): Boolean = restartButton.contains(px, py)
}

/// One "icon + required amount" row in the objectives list.
private class ObjectiveRequirementNode(private val dp: Float) : Node() {
    private val icon = SpriteNode()
    private val countLabel = LabelNode().apply {
        vAlign = LabelNode.VAlign.CENTER
        hAlign = LabelNode.HAlign.LEFT
        color = WHITE
    }

    init {
        addChild(icon)
        addChild(countLabel)
    }

    fun configure(texture: Texture, total: Int) {
        icon.texture = texture
        countLabel.text = "x$total"
    }

    fun layout(iconSize: Float, fontSize: Float) {
        icon.setSize(iconSize, iconSize)
        icon.setPosition(-iconSize / 2 - 6 * dp, 0f)
        countLabel.fontSize = fontSize
        countLabel.setPosition(iconSize / 2 + 2 * dp, 0f)
    }
}

/// Full-screen "To Do!" overlay shown when a level first loads: a box listing
/// the level's objectives, a Back button that returns to level select, and a
/// Continue button that dismisses the overlay so play can begin.
class ObjectivesOverlayNode(private val dp: Float) : Node() {
    companion object {
        /// `objectiveboxTop`/`Middle`/`Bottom`'s own pixel dimensions (790x64
        /// each), so the top/bottom caps can be scaled from the box's on-screen
        /// width without stretching, the same way `PauseOverlayNode` derives
        /// `pausebox.png`'s height.
        private const val BOX_NATIVE_WIDTH = 790f
        private const val BOX_CAP_NATIVE_HEIGHT = 64f
    }

    private val dimOverlay = SpriteNode(color = DIM)

    /// Three-piece box - matches `PauseOverlayNode`'s pausebox styling, but
    /// stretches vertically to fit however many objectives a level has instead
    /// of using one fixed-aspect image. The top/bottom caps keep their native
    /// aspect ratio; the middle stretches to fill whatever's left.
    private val boxTop = SpriteNode(Textures.image("objectiveboxTop"))
    private val boxMiddle = SpriteNode(Textures.image("objectiveboxMiddle"))
    private val boxBottom = SpriteNode(Textures.image("objectiveboxBottom"))

    private val title = titleLabel("To Do!", 72 * dp).apply { vAlign = LabelNode.VAlign.TOP }
    private val objectivesContainer = Node()
    private val backButton = SpriteNode(Textures.image("backbutton"))
    private val continueButton = SpriteNode(Textures.image("nextbutton"))
    private var rowNodes: List<ObjectiveRequirementNode> = emptyList()
    private var lastArea: SafeArea? = null

    init {
        isHidden = true
        zPosition = 150f
        addChild(dimOverlay)
        addChild(boxTop)
        addChild(boxMiddle)
        addChild(boxBottom)
        addChild(title)
        addChild(objectivesContainer)
        addChild(backButton)
        addChild(continueButton)
    }

    /// Rebuilds the objectives list. Called once per level load, before `show()`.
    fun configure(objectives: List<Objective>) {
        objectivesContainer.removeAllChildren()
        rowNodes = objectives.map { objective ->
            ObjectiveRequirementNode(dp).also {
                it.configure(Textures.objective(objective.kind), objective.total)
                objectivesContainer.addChild(it)
            }
        }
        lastArea?.let(::layout)
    }

    /// Content is laid out within the safe area; the dim overlay still covers
    /// the whole screen, including under the status bar/cutout.
    fun layout(area: SafeArea) {
        val width = area.width
        val height = area.height
        lastArea = area
        area.coverScreen(this, dimOverlay)

        val boxWidth = width * 0.82f
        val innerHorizontalPadding = 40 * dp
        val innerTopPadding = 20 * dp
        val innerBottomPadding = 20 * dp
        val titleToRowsGap = 16 * dp

        // "To Do!" is short, but keep the shrink-to-fit guard in case a future
        // localization runs long.
        val maxTitleWidth = boxWidth - innerHorizontalPadding * 2
        var titleFontSize = min(width * 0.14f, 72 * dp)
        while (true) {
            title.fontSize = titleFontSize
            if (title.measureWidth() <= maxTitleWidth || titleFontSize <= 20 * dp) break
            titleFontSize -= 4 * dp
        }

        val iconSize = min(width * 0.14f, 64 * dp)
        val rowHeight = iconSize * 1.3f
        val rowsHeight = rowNodes.size * rowHeight
        val titleHeight = title.measureHeight()
        val contentHeight = titleHeight + if (rowNodes.isEmpty()) 0f else titleToRowsGap + rowsHeight

        val capScale = boxWidth / BOX_NATIVE_WIDTH
        val capHeight = BOX_CAP_NATIVE_HEIGHT * capScale
        val middleHeight = max(contentHeight + innerTopPadding + innerBottomPadding, capHeight * 0.5f)
        val boxHeight = capHeight * 2 + middleHeight

        val buttonSize = width * 0.2f
        val buttonY = height / 2 - buttonSize * 0.75f - 24 * dp
        val continueButtonTopEdge = buttonY - buttonSize * 1.5f / 2

        // Centre the box in the space between the safe area's top edge and the
        // Continue button.
        val topBoundary = -height / 2
        val bottomBoundary = continueButtonTopEdge - 20 * dp
        val availableCenterY = (topBoundary + bottomBoundary) / 2
        val boxTopEdgeY = availableCenterY - boxHeight / 2

        boxTop.setSize(boxWidth, capHeight)
        boxTop.setPosition(0f, boxTopEdgeY + capHeight / 2)
        boxMiddle.setSize(boxWidth, middleHeight)
        boxMiddle.setPosition(0f, boxTopEdgeY + capHeight + middleHeight / 2)
        boxBottom.setSize(boxWidth, capHeight)
        boxBottom.setPosition(0f, boxTopEdgeY + capHeight + middleHeight + capHeight / 2)

        title.setPosition(0f, boxTopEdgeY + capHeight + innerTopPadding)

        val rowsTopY = title.y + titleHeight + titleToRowsGap
        layoutObjectiveRows(rowsTopY, iconSize, rowHeight)

        backButton.setSize(buttonSize, buttonSize)
        backButton.setPosition(-width * 0.18f, buttonY)
        continueButton.setSize(buttonSize * 1.5f, buttonSize * 1.5f)
        continueButton.setPosition(width * 0.18f, buttonY)
    }

    private fun layoutObjectiveRows(topY: Float, iconSize: Float, rowHeight: Float) {
        rowNodes.forEachIndexed { index, row ->
            row.layout(iconSize, iconSize * 0.5f)
            row.setPosition(0f, topY + rowHeight / 2 + index * rowHeight)
        }
    }

    fun show() {
        isHidden = false
        alpha = 0f
        run(Action.fadeIn(0.3))
    }

    fun hide() {
        run(Action.sequence(Action.fadeOut(0.25), Action.run { isHidden = true }))
    }

    fun containsBackButton(px: Float, py: Float): Boolean = backButton.contains(px, py)
    fun containsContinueButton(px: Float, py: Float): Boolean = continueButton.contains(px, py)
}

/// Full-screen pause menu: a black 70%-alpha scrim behind `pausebox.png`, with a
/// "Paused" title, music/sound-effect on-off toggles, and back/restart/quit
/// buttons to resume, replay the level, or quit to the first screen.
///
/// Shown while `GameScene.isPaused` is true, so everything here is shown/hidden
/// directly rather than with actions - a paused scene never evaluates them.
class PauseOverlayNode(private val dp: Float) : Node() {
    companion object {
        /// `pausebox.png`'s own aspect ratio (790x526), so it's sized from
        /// screen width without stretching.
        private const val PAUSE_BOX_ASPECT = 526f / 790f
    }

    private val dimOverlay = SpriteNode(color = 0xB3000000.toInt()) // black at 70% alpha
    private val pauseBox = SpriteNode(Textures.image("pausebox"))
    private val title = LabelNode().apply {
        text = "Paused"
        color = WHITE
    }

    private val musicToggleBackground = toggleBackground()
    private val musicToggleLabel = LabelNode().apply { color = WHITE }
    private val soundToggleBackground = toggleBackground()
    private val soundToggleLabel = LabelNode().apply { color = WHITE }

    private val resumeButton = SpriteNode(Textures.image("backbutton"))
    private val restartButton = SpriteNode(Textures.image("rewindbutton"))
    private val quitButton = SpriteNode(Textures.image("quitbutton"))

    private fun toggleBackground() = RoundedRectNode(0f, 0f, 10 * dp).apply {
        fillColor = 0x59000000 // black at 35% alpha
        strokeColor = 0x99FFFFFF.toInt() // white at 60% alpha
        lineWidth = 1.5f * dp
    }

    init {
        isHidden = true
        zPosition = 150f
        addChild(dimOverlay)
        addChild(pauseBox)
        addChild(title)
        addChild(musicToggleBackground)
        addChild(musicToggleLabel)
        addChild(soundToggleBackground)
        addChild(soundToggleLabel)
        addChild(resumeButton)
        addChild(restartButton)
        addChild(quitButton)
        refreshToggleLabels()
    }

    /// Content is laid out within the safe area; the dim overlay still covers
    /// the whole screen, including under the status bar/cutout.
    fun layout(area: SafeArea) {
        val width = area.width
        val height = area.height
        area.coverScreen(this, dimOverlay)

        val boxWidth = width * 0.82f
        val boxHeight = boxWidth * PAUSE_BOX_ASPECT
        val boxY = -height * 0.06f
        pauseBox.setSize(boxWidth, boxHeight)
        pauseBox.setPosition(0f, boxY)

        title.fontSize = min(boxWidth * 0.14f, 48 * dp)
        title.setPosition(0f, boxY - boxHeight * 0.26f)

        val toggleWidth = boxWidth * 0.64f
        val toggleHeight = 40 * dp
        val toggleFontSize = min(toggleHeight * 0.5f, 20 * dp)
        val musicY = boxY + boxHeight * 0.06f
        val soundY = musicY + toggleHeight + 14 * dp
        for ((background, label, y) in listOf(
            Triple(musicToggleBackground, musicToggleLabel, musicY),
            Triple(soundToggleBackground, soundToggleLabel, soundY),
        )) {
            background.width = toggleWidth
            background.height = toggleHeight
            background.setPosition(0f, y)
            label.fontSize = toggleFontSize
            label.setPosition(0f, y)
        }

        // Same bottom-of-screen placement formula as `ObjectivesOverlayNode`'s
        // own back/continue pair, but with restart/quit sharing a row above the
        // centered back (resume) button below them.
        val buttonSize = width * 0.2f
        val spacing = buttonSize + 16 * dp
        val backRowY = height / 2 - buttonSize * 0.75f - 24 * dp
        val topRowY = backRowY - buttonSize - 16 * dp

        restartButton.setSize(buttonSize, buttonSize)
        restartButton.setPosition(spacing, topRowY)
        quitButton.setSize(buttonSize, buttonSize)
        quitButton.setPosition(-spacing, topRowY)

        resumeButton.setSize(buttonSize * 1.5f, buttonSize * 1.5f)
        resumeButton.setPosition(0f, backRowY)
    }

    private fun refreshToggleLabels() {
        musicToggleLabel.text = "Music: ${if (AudioManager.isMusicEnabled) "On" else "Off"}"
        soundToggleLabel.text = "Sound FX: ${if (AudioManager.isSoundEffectsEnabled) "On" else "Off"}"
    }

    fun show() {
        refreshToggleLabels()
        removeAllActions()
        isHidden = false
        alpha = 1f
    }

    fun hide() {
        removeAllActions()
        isHidden = true
    }

    fun toggleMusic() {
        AudioManager.isMusicEnabled = !AudioManager.isMusicEnabled
        refreshToggleLabels()
    }

    fun toggleSoundEffects() {
        AudioManager.isSoundEffectsEnabled = !AudioManager.isSoundEffectsEnabled
        refreshToggleLabels()
    }

    /// Hit tests in this node's own coordinate space.
    fun containsMusicToggle(px: Float, py: Float): Boolean = musicToggleBackground.contains(px, py)
    fun containsSoundToggle(px: Float, py: Float): Boolean = soundToggleBackground.contains(px, py)
    fun containsResumeButton(px: Float, py: Float): Boolean = resumeButton.contains(px, py)
    fun containsRestartButton(px: Float, py: Float): Boolean = restartButton.contains(px, py)
    fun containsQuitButton(px: Float, py: Float): Boolean = quitButton.contains(px, py)
}

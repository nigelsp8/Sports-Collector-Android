package com.nigelspeight.sportscollector.game

import android.graphics.Bitmap
import com.nigelspeight.sportscollector.engine.Objective
import com.nigelspeight.sportscollector.level.Level
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()
private const val DIM = 0xBF000000.toInt() // black at 75% alpha

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

/// The spinning sunburst backdrop shown behind the win card, replacing the normal
/// panning `BackgroundNode`. iOS blurs it live with a `CIGaussianBlur`; since a
/// Gaussian blur is rotation-invariant, blurring the texture once up front (by
/// downsampling and letting bilinear upscaling smooth it) gives the same look.
class WinBackgroundNode : Node() {
    private val sprite = SpriteNode(blurred(Textures.image("bkgwin")))

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

    private fun blurred(texture: Texture): Texture {
        var bitmap = texture.bitmap
        repeat(2) {
            bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
        }
        return Texture(bitmap)
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

/// Purely cosmetic hint graphic for the tutorial phase - the actual input
/// restriction lives in `GameEngine.tutorialRect`.
class TutorialOverlayNode(dp: Float) : Node() {
    private val hand = SpriteNode(Textures.image("hand")).apply {
        setSize(90 * dp, 76 * dp)
        alpha = 0f
        zPosition = 50f
    }

    init {
        addChild(hand)
    }

    fun show(px: Float, py: Float) {
        hand.setPosition(px, py)
        hand.removeAllActions()
        hand.alpha = 0f
        hand.run(
            Action.sequence(
                Action.fadeAlphaTo(0.85f, 0.3),
                Action.repeatForever(Action.sequence(Action.scaleTo(1.12f, 0.5), Action.scaleTo(1f, 0.5))),
            ),
        )
    }

    fun hide() {
        hand.removeAllActions()
        hand.run(Action.fadeOut(0.3))
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

    fun layout(width: Float, height: Float) {
        dimOverlay.setSize(width, height)

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
        continueButton.setSize(buttonSize, buttonSize)
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
/// Continue button that returns to level select.
class LoseOverlayNode(private val dp: Float) : Node() {
    private val dimOverlay = SpriteNode(color = DIM)
    private val title = titleLabel("Unlucky!", 80 * dp)
    private val loseImage = SpriteNode(Textures.image("lose"))
    private val continueButton = SpriteNode(Textures.image("nextbutton"))

    init {
        isHidden = true
        zPosition = 150f
        addChild(dimOverlay)
        addChild(title)
        addChild(loseImage)
        addChild(continueButton)
    }

    fun layout(width: Float, height: Float) {
        dimOverlay.setSize(width, height)

        val loseSize = width * 0.75f
        val loseY = -height * 0.04f
        loseImage.setSize(loseSize, loseSize)
        loseImage.setPosition(0f, loseY)

        val titleFontSize = min(width * 0.16f, 80 * dp)
        title.fontSize = titleFontSize
        title.setPosition(0f, loseY - loseSize / 2 - titleFontSize * 0.3f)

        val buttonSize = width * 0.2f
        continueButton.setSize(buttonSize, buttonSize)
        continueButton.setPosition(0f, height / 2 - buttonSize * 0.75f - 24 * dp)
    }

    fun show() {
        isHidden = false
        alpha = 0f
        run(Action.fadeIn(0.3))
    }

    fun containsContinueButton(px: Float, py: Float): Boolean = continueButton.contains(px, py)
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

/// Full-screen "What You Need!" overlay shown when a level first loads: a list
/// of the level's objectives, a Back button that returns to level select, and a
/// Continue button that dismisses the overlay so play can begin.
class ObjectivesOverlayNode(private val dp: Float) : Node() {
    private val dimOverlay = SpriteNode(color = DIM)
    private val title = titleLabel("What You Need!", 72 * dp)
    private val objectivesContainer = Node()
    private val backButton = SpriteNode(Textures.image("backbutton"))
    private val continueButton = SpriteNode(Textures.image("nextbutton"))
    private var rowNodes: List<ObjectiveRequirementNode> = emptyList()
    private var lastWidth = 0f
    private var lastHeight = 0f

    init {
        isHidden = true
        zPosition = 150f
        addChild(dimOverlay)
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
        if (lastWidth > 0) layoutObjectiveRows(lastWidth)
    }

    fun layout(width: Float, height: Float) {
        lastWidth = width
        lastHeight = height
        dimOverlay.setSize(width, height)

        // "What You Need!" is long enough that a fixed fraction-of-width font
        // size overflowed the screen - shrink to fit instead.
        val maxTitleWidth = width * 0.9f
        var titleFontSize = min(width * 0.14f, 72 * dp)
        while (true) {
            title.fontSize = titleFontSize
            if (title.measureWidth() <= maxTitleWidth || titleFontSize <= 20 * dp) break
            titleFontSize -= 4 * dp
        }
        title.setPosition(0f, -height * 0.28f)

        val buttonSize = width * 0.2f
        val buttonY = height / 2 - buttonSize * 0.75f - 24 * dp
        backButton.setSize(buttonSize, buttonSize)
        backButton.setPosition(-width * 0.18f, buttonY)
        continueButton.setSize(buttonSize, buttonSize)
        continueButton.setPosition(width * 0.18f, buttonY)

        layoutObjectiveRows(width)
    }

    private fun layoutObjectiveRows(width: Float) {
        if (rowNodes.isEmpty()) return
        val iconSize = min(width * 0.14f, 64 * dp)
        val rowHeight = iconSize * 1.3f
        val totalHeight = rowNodes.size * rowHeight
        val startY = -(totalHeight / 2 - rowHeight / 2)
        rowNodes.forEachIndexed { index, row ->
            row.layout(iconSize, iconSize * 0.5f)
            row.setPosition(0f, startY + index * rowHeight)
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

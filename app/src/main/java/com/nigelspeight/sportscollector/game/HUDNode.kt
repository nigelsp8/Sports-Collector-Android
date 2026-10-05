package com.nigelspeight.sportscollector.game

import android.graphics.PointF
import com.nigelspeight.sportscollector.engine.Objective
import com.nigelspeight.sportscollector.engine.ObjectiveKind
import kotlin.math.max

/// One "icon + remaining count" pair in the objectives row, mirroring the
/// original's `drawAtlas`-of-tile-type + `drawText`-of-amount HUD readout.
private class ObjectiveIconNode(dp: Float) : Node() {
    val icon = SpriteNode()
    val countLabel = LabelNode(20 * dp).apply {
        vAlign = LabelNode.VAlign.CENTER
        hAlign = LabelNode.HAlign.LEFT
        zPosition = 1f
        color = 0xFFFF0000.toInt()
        strokeColor = 0xFFFFFFFF.toInt()
        strokeWidthPercent = 5f
    }
    var kind: ObjectiveKind? = null

    init {
        icon.zPosition = 0f
        addChild(icon)
        addChild(countLabel)
    }

    fun setCount(value: Int) {
        countLabel.text = value.toString()
    }
}

class HUDNode(private val dp: Float) : Node() {
    private val gamebar = SpriteNode(Textures.gamebar)
    private val levelBadge = SpriteNode(Textures.image("HUD_lives"))
    private val scoreBadge = SpriteNode(Textures.image("HUD_score"))
    private val movesBadge = SpriteNode(Textures.image("HUD_moves"))

    private val levelCaptionLabel = LabelNode(11 * dp)
    private val levelValueLabel = LabelNode(22 * dp)
    private val scoreValueLabel = LabelNode(30 * dp)
    private val movesCaptionLabel = LabelNode(11 * dp)
    private val movesValueLabel = LabelNode(22 * dp)
    private val objectivesContainer = Node()
    private var objectiveIconNodes: List<ObjectiveIconNode> = emptyList()
    private var objectivesRowY = 0f
    private val pauseButton = SpriteNode(Textures.image("pausebutton"))

    /// Debug aid: a tappable button (see `GameScene.cycleDebugSpeed`) that cycles
    /// the falling/animation speed so a human can watch cascades step by step.
    /// Visible, matching the current iOS build - set `SHOW_SPEED_BUTTON` false to hide.
    private val speedButtonBackground = RoundedRectNode(160 * dp, 32 * dp, 8 * dp)
    private val speedLabel = LabelNode(14 * dp)

    companion object {
        const val SHOW_SPEED_BUTTON = true
        private const val PAUSE_BUTTON_PRESSED_SCALE = 0.95f

        // From the iOS asset catalog's named colours (same in light and dark).
        /// "LEVEL"/"MOVES" captions - `HudLevelValue`.
        private val CAPTION_COLOR = rgb(60, 72, 124)
        /// Level and moves numbers - `HudLevel`.
        private val LEVEL_COLOR = rgb(0, 105, 235)
        /// Score number - `ScoreValue`.
        private val SCORE_COLOR = rgb(255, 255, 192)

        private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    init {
        gamebar.zPosition = 0f
        addChild(gamebar)

        for (badge in listOf(levelBadge, scoreBadge, movesBadge)) {
            badge.zPosition = 1f
            addChild(badge)
        }

        levelCaptionLabel.text = "LEVEL"
        levelCaptionLabel.color = CAPTION_COLOR
        levelValueLabel.color = LEVEL_COLOR
        scoreValueLabel.color = SCORE_COLOR
        movesCaptionLabel.text = "MOVES"
        movesCaptionLabel.color = CAPTION_COLOR
        movesValueLabel.color = LEVEL_COLOR

        for (label in listOf(levelCaptionLabel, levelValueLabel, scoreValueLabel, movesCaptionLabel, movesValueLabel)) {
            label.vAlign = LabelNode.VAlign.CENTER
            label.zPosition = 2f
            addChild(label)
        }
        levelCaptionLabel.hAlign = LabelNode.HAlign.LEFT
        levelValueLabel.hAlign = LabelNode.HAlign.RIGHT
        movesCaptionLabel.hAlign = LabelNode.HAlign.RIGHT
        movesValueLabel.hAlign = LabelNode.HAlign.LEFT
        scoreValueLabel.hAlign = LabelNode.HAlign.CENTER

        objectivesContainer.zPosition = 2f
        addChild(objectivesContainer)

        pauseButton.zPosition = 3f
        addChild(pauseButton)

        speedButtonBackground.fillColor = 0x59000000
        speedButtonBackground.strokeColor = 0x80FFFFFF.toInt()
        speedButtonBackground.lineWidth = dp
        speedButtonBackground.zPosition = 10f
        speedButtonBackground.isHidden = !SHOW_SPEED_BUTTON
        speedLabel.color = 0xFFFFFFFF.toInt()
        speedLabel.vAlign = LabelNode.VAlign.CENTER
        speedLabel.zPosition = 11f
        speedLabel.text = "Speed: 1x"
        speedLabel.isHidden = !SHOW_SPEED_BUTTON
        addChild(speedButtonBackground)
        addChild(speedLabel)
    }

    fun layout(width: Float, height: Float) {
        val barWidth = width * 0.9f
        val halfScreen = width * 0.5f

        // Bottom zone: just the gamebar backdrop + pause button, no badges.
        val barHeight = barWidth * (198f / 1024f)
        val barBottomInset = 20 * dp
        val barCenterY = height / 2 - barHeight / 2 - barBottomInset

        gamebar.setSize(barWidth, barHeight)
        gamebar.setPosition(0f, barCenterY)

        // Sits on the bar's gold tab, same relative spot as the original's pause button.
        val pauseButtonHeight = barHeight * 0.6f
        pauseButton.setSize(pauseButtonHeight, pauseButtonHeight)
        pauseButton.setPosition(barWidth * 0.39f, barCenterY - 2 * dp)

        // Top zone: level/score/moves badges, no bar backdrop behind them.
        val sideBadgeWidth = width * 0.30f
        val sideBadgeHeight = sideBadgeWidth * (130f / 390f)
        val halfSideBadgeWidth = sideBadgeWidth / 2

        val centerBadgeWidth = width * 0.43f
        val centerBadgeHeight = centerBadgeWidth * (150f / 430f)
        val topInset = 12 * dp
        val topRowHeight = max(sideBadgeHeight, centerBadgeHeight)
        val topRowY = -height / 2 + topRowHeight / 2 + topInset
        val levelWidth = centerBadgeWidth / 3
        val labelY = topRowY - sideBadgeHeight * 0.06f

        levelBadge.setSize(sideBadgeWidth, sideBadgeHeight)
        levelBadge.setPosition(-halfScreen + halfSideBadgeWidth, topRowY)
        levelCaptionLabel.setPosition(-halfScreen + levelWidth / 4, labelY)
        levelValueLabel.setPosition(-halfScreen + sideBadgeWidth - levelWidth / 3, labelY)

        movesBadge.setSize(sideBadgeWidth, sideBadgeHeight)
        movesBadge.setPosition(halfScreen - halfSideBadgeWidth, topRowY)
        movesCaptionLabel.setPosition(halfScreen - levelWidth / 4, labelY)
        movesValueLabel.setPosition(halfScreen - sideBadgeWidth + levelWidth / 3, labelY)

        scoreBadge.setSize(centerBadgeWidth, centerBadgeHeight)
        scoreBadge.setPosition(0f, topRowY)
        scoreValueLabel.setPosition(0f, topRowY - centerBadgeHeight * 0.03f)

        objectivesRowY = barCenterY
        layoutObjectiveIcons()

        val bottom = height / 2 - 24 * dp
        speedButtonBackground.setPosition(0f, bottom)
        speedLabel.setPosition(0f, bottom)
    }

    /// Updates the debug speed button's text. See `GameScene.cycleDebugSpeed`.
    fun setSpeedText(text: String) {
        speedLabel.text = text
    }

    /// Hit test for the debug speed button, in this node's own coordinate space.
    fun containsSpeedToggle(px: Float, py: Float): Boolean = speedButtonBackground.contains(px, py)

    /// Hit test for the pause button, in this node's own coordinate space.
    fun containsPauseToggle(px: Float, py: Float): Boolean = pauseButton.contains(px, py)

    /// Direct scale set, not an animated action: the scene stops evaluating
    /// actions while paused, so a tween here would freeze mid-flight.
    fun setPauseButtonPressed(pressed: Boolean) {
        pauseButton.setScale(if (pressed) PAUSE_BUTTON_PRESSED_SCALE else 1f)
    }

    fun update(level: Int, score: Int, moves: Int, objectives: List<Objective>) {
        levelValueLabel.text = level.toString()
        scoreValueLabel.text = score.toString()
        movesValueLabel.text = moves.toString()

        if (objectiveIconNodes.size != objectives.size) {
            objectiveIconNodes.forEach { it.removeFromParent() }
            objectiveIconNodes = objectives.map { objective ->
                ObjectiveIconNode(dp).also { node ->
                    node.kind = objective.kind
                    // Only seeded here, at creation - subsequent frames must NOT
                    // stomp the label with the engine's already-decremented value;
                    // see `collect`, which is the only other place this count
                    // changes, timed to a tile's HUD-bound flight.
                    node.setCount(objective.remaining)
                    objectivesContainer.addChild(node)
                }
            }
        }

        for ((node, objective) in objectiveIconNodes.zip(objectives)) {
            node.kind = objective.kind
            node.icon.texture = Textures.objective(objective.kind)
        }

        layoutObjectiveIcons()
    }

    /// Scene-space position of the icon tracking `kind`, so a caller can animate
    /// a collected tile/jelly sprite flying toward it.
    fun scenePosition(kind: ObjectiveKind): PointF? {
        val node = objectiveIconNodes.firstOrNull { it.kind == kind } ?: return null
        return node.convertToRoot(0f, 0f)
    }

    /// Lands a HUD-bound collection flight: sets the icon's count to its new
    /// value and gives the icon a small pop, timed to the moment the flying
    /// sprite arrives rather than the moment it was matched.
    fun collect(kind: ObjectiveKind, remaining: Int) {
        val node = objectiveIconNodes.firstOrNull { it.kind == kind } ?: return
        node.setCount(remaining)
        node.icon.run(Action.sequence(Action.scaleTo(1.3f, 0.08), Action.scaleTo(1f, 0.12)), "objectivePop")
    }

    private fun layoutObjectiveIcons() {
        objectivesContainer.setPosition(0f, objectivesRowY)

        val left = gamebar.width / 2 - gamebar.width * 0.11f
        val spacing = gamebar.width * 0.155f
        if (objectiveIconNodes.isEmpty()) return

        val iconSize = gamebar.height * 0.6f
        objectiveIconNodes.forEachIndexed { index, node ->
            node.setPosition(-left + index * spacing, -2 * dp)
            node.icon.setSize(iconSize, iconSize)
            node.countLabel.setPosition(-28 * dp, -14 * dp)
        }
    }
}

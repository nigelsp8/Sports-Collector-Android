import SpriteKit

/// One "icon + remaining count" pair in the objectives row, mirroring the
/// original's `drawAtlas`-of-tile-type + `drawText`-of-amount HUD readout.
private final class ObjectiveIconNode: SKNode {
    let icon = SKSpriteNode()
    let countLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")
    var kind: ObjectiveKind?

    override init() {
        super.init()
        icon.zPosition = 0
        addChild(icon)

        countLabel.verticalAlignmentMode = .center
        countLabel.horizontalAlignmentMode = .left
        countLabel.zPosition = 1
        addChild(countLabel)
    }

    required init?(coder: NSCoder) {
        fatalError("ObjectiveIconNode does not support storyboard instantiation")
    }

    func setCount(_ value: Int) {
        countLabel.attributedText = NSAttributedString(
            string: "\(value)",
            attributes: [
                .font: UIFont(name: "AvenirNext-Bold", size: 20) as Any,
                .foregroundColor: UIColor.red,
                .strokeColor: UIColor.white,
                .strokeWidth: -5,
            ]
        )
    }
}

final class HUDNode: SKNode {
    private let gamebar: SKSpriteNode
    private let levelBadge = SKSpriteNode(imageNamed: "HUD_lives")
    private let scoreBadge = SKSpriteNode(imageNamed: "HUD_score")
    private let movesBadge = SKSpriteNode(imageNamed: "HUD_moves")

    private let levelCaptionLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")
    private let levelValueLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")
    private let scoreValueLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")
    private let movesCaptionLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")
    private let movesValueLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")
    private let objectivesContainer = SKNode()
    private var objectiveIconNodes: [ObjectiveIconNode] = []
    private let tileTextures = TileTextureProvider()
    private var objectivesRowY: CGFloat = 0
    private var objectivesRowWidth: CGFloat = 0
    private let pauseButton = SKSpriteNode(imageNamed: "pausebutton")

    /// Debug aid: a tappable button (see `GameScene.cycleDebugSpeed`) that cycles
    /// the falling/animation speed so a human can watch cascades step by step.
    /// Temporarily hidden - see `init`.
    private let speedButtonBackground = SKShapeNode(rectOf: CGSize(width: 160, height: 32), cornerRadius: 8)
    private let speedLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")

    /// `gamebar.png` is a 1024x1024 atlas whose only painted content is the top
    /// ~19.36% strip (the rest is unused canvas) - matches the original's own
    /// UV crop (`hudtverts2` in `DrPopperViewController.m`'s `drawBackgroundOverlay`).
    private static let barTexture: SKTexture = {
        let full = SKTexture(imageNamed: "gamebar")
        let cropHeight: CGFloat = 0.19359375
        return SKTexture(rect: CGRect(x: 0, y: 1 - cropHeight, width: 1, height: cropHeight), in: full)
    }()

    override init() {
        gamebar = SKSpriteNode(texture: HUDNode.barTexture)
        super.init()

        gamebar.zPosition = 0
        addChild(gamebar)

        for badge in [levelBadge, scoreBadge, movesBadge] {
            badge.zPosition = 1
            addChild(badge)
        }

        let captionColor = UIColor(named:"HudLevelValue") //UIColor(red: 129.0 / 255, green: 145.0 / 255, blue: 142.0 / 255, alpha: 1)
        let levelColor = UIColor(named:"HudLevel") //UIColor(red: 187.0 / 255, green: 108.0 / 255, blue: 41.0 / 255, alpha: 1)
        let scoreColor = UIColor(named:"ScoreValue") //UIColor(red: 192.0 / 255, green: 147.0 / 255, blue: 46.0 / 255, alpha: 1)

        levelCaptionLabel.text = "LEVEL"
        levelCaptionLabel.fontSize = 11
        levelCaptionLabel.fontColor = captionColor

        levelValueLabel.fontSize = 22
        levelValueLabel.fontColor = levelColor

        scoreValueLabel.fontSize = 30
        scoreValueLabel.fontColor = scoreColor

        movesCaptionLabel.text = "MOVES"
        movesCaptionLabel.fontSize = 11
        movesCaptionLabel.fontColor = captionColor

        movesValueLabel.fontSize = 22
        movesValueLabel.fontColor = levelColor

        for label in [levelCaptionLabel, levelValueLabel, scoreValueLabel, movesCaptionLabel, movesValueLabel]
        {
            label.verticalAlignmentMode = .center
            label.zPosition = 2
            addChild(label)
        }
        
        levelCaptionLabel.horizontalAlignmentMode = .left
        levelValueLabel.horizontalAlignmentMode = .right
        movesCaptionLabel.horizontalAlignmentMode = .right
        movesValueLabel.horizontalAlignmentMode = .left
        scoreValueLabel.horizontalAlignmentMode = .center

        objectivesContainer.zPosition = 2
        addChild(objectivesContainer)

        pauseButton.zPosition = 3
        addChild(pauseButton)

        // Debug
        speedButtonBackground.fillColor = UIColor(white: 0, alpha: 0.35)
        speedButtonBackground.strokeColor = UIColor(white: 1, alpha: 0.5)
        speedButtonBackground.lineWidth = 1
        speedButtonBackground.zPosition = 10
        speedButtonBackground.isHidden = false //true
        speedLabel.fontSize = 14
        speedLabel.fontColor = .white
        speedLabel.verticalAlignmentMode = .center
        speedLabel.zPosition = 11
        speedLabel.text = "Speed: 1x"
        speedLabel.isHidden = false //true
        addChild(speedButtonBackground)
        addChild(speedLabel)
    }

    required init?(coder: NSCoder) {
        fatalError("HUDNode does not support storyboard instantiation")
    }

    func layout(in size: CGSize) {
        let barWidth = size.width * 0.9
        let halfScreen = size.width * 0.5

        // Bottom zone: just the gamebar backdrop + pause button, no badges.
        let barHeight = barWidth * (198.0 / 1024.0)
        let barBottomInset: CGFloat = 20
        let barCenterY = -size.height / 2 + barHeight / 2 + barBottomInset

        gamebar.size = CGSize(width: barWidth, height: barHeight)
        gamebar.position = CGPoint(x: 0, y: barCenterY)

        // Sits on the bar's gold tab, same relative spot as the original's
        // PNG_PAUSEBUTTON draw call.
        let pauseButtonHeight = barHeight * 0.6
        pauseButton.size = CGSize(width: pauseButtonHeight, height: pauseButtonHeight)
        pauseButton.position = CGPoint(x: (barWidth * 0.39), y: barCenterY+2)

        // Top zone: level/score/moves badges, no bar backdrop behind them.
        let sideBadgeWidth = size.width * 0.30
        let sideBadgeHeight = sideBadgeWidth * (130.0 / 390.0)
        let halfSideBadgeWidth = sideBadgeWidth / 2
        
        let centerBadgeWidth = size.width * 0.43
        let centerBadgeHeight = centerBadgeWidth * (150.0 / 430.0)
        //let sideOffset: CGFloat = 0 //barWidth * 0.28
        let topInset: CGFloat = 12
        let topRowHeight = max(sideBadgeHeight, centerBadgeHeight)
        let topRowY = size.height / 2 - topRowHeight / 2 - topInset
        let levelWidth = centerBadgeWidth / 3

        levelBadge.size = CGSize(width: sideBadgeWidth, height: sideBadgeHeight)
        levelBadge.position = CGPoint(x: -halfScreen + halfSideBadgeWidth, y: topRowY)
        levelCaptionLabel.position = CGPoint(x: -halfScreen+(levelWidth/4), y: topRowY + sideBadgeHeight * 0.06)
        levelValueLabel.position = CGPoint(x: -halfScreen+sideBadgeWidth-(levelWidth/3), y: topRowY + sideBadgeHeight * 0.06)
        
        movesBadge.size = CGSize(width: sideBadgeWidth, height: sideBadgeHeight)
        movesBadge.position = CGPoint(x: halfScreen - halfSideBadgeWidth, y: topRowY)
        movesCaptionLabel.position = CGPoint(x: halfScreen - (levelWidth/4), y:  topRowY + sideBadgeHeight * 0.06)
        movesValueLabel.position = CGPoint(x: halfScreen-sideBadgeWidth + (levelWidth/3), y: topRowY + sideBadgeHeight * 0.06)

        scoreBadge.size = CGSize(width: centerBadgeWidth, height: centerBadgeHeight)
        scoreBadge.position = CGPoint(x: 0, y: topRowY)
        scoreValueLabel.position = CGPoint(x: 0, y: topRowY+(centerBadgeHeight*0.03))

        objectivesRowY = barCenterY
        objectivesRowWidth = size.width - 32
        layoutObjectiveIcons()

        // debug texts etc
        let bottom = -size.height / 2 + 24
        speedButtonBackground.position = CGPoint(x: 0, y: bottom)
        speedLabel.position = CGPoint(x: 0, y: bottom)
    }

    /// Updates the debug speed button's text. See `GameScene.cycleDebugSpeed`.
    func setSpeedText(_ text: String) {
        speedLabel.text = text
    }

    /// Hit test for the debug speed button, in this node's own coordinate space
    /// (i.e. pass `touch.location(in: hud)`).
    func containsSpeedToggle(_ pointInHUD: CGPoint) -> Bool {
        speedButtonBackground.contains(pointInHUD)
    }

    /// Hit test for the pause button, in this node's own coordinate space
    /// (i.e. pass `touch.location(in: hud)`).
    func containsPauseToggle(_ pointInHUD: CGPoint) -> Bool {
        pauseButton.contains(pointInHUD)
    }

    private static let pauseButtonPressedScale: CGFloat = 0.95

    /// Direct scale set, not an animated `SKAction`: `GameScene.togglePause`
    /// flips `isPaused` synchronously right after a press, and once that's
    /// true the scene never evaluates queued actions - a tween here would
    /// just freeze mid-flight instead of completing.
    func setPauseButtonPressed(_ pressed: Bool) {
        pauseButton.setScale(pressed ? HUDNode.pauseButtonPressedScale : 1.0)
    }

    func update(level: Int, score: Int, moves: Int, objectives: [Objective]) {
        levelValueLabel.text = "\(level)"
        scoreValueLabel.text = "\(score)"
        movesValueLabel.text = "\(moves)"

        if objectiveIconNodes.count != objectives.count {
            objectiveIconNodes.forEach { $0.removeFromParent() }
            objectiveIconNodes = objectives.map { objective in
                let node = ObjectiveIconNode()
                node.kind = objective.kind
                // Only seeded here, at creation - subsequent frames must NOT
                // stomp the label with the engine's already-decremented value;
                // see `collect(kind:remaining:)`, which is the only other
                // place this count changes, timed to a tile's HUD-bound flight.
                node.setCount(objective.remaining)
                objectivesContainer.addChild(node)
                return node
            }
        }

        for (node, objective) in zip(objectiveIconNodes, objectives) {
            node.kind = objective.kind
            node.icon.texture = texture(for: objective.kind)
        }

        layoutObjectiveIcons()
    }

    private func texture(for kind: ObjectiveKind) -> SKTexture {
        switch kind {
        case .collect(let type): tileTextures.texture(for: type)
        case .clearJelly: tileTextures.jellyOverlayTexture
        }
    }

    /// Scene-space position of the icon tracking `kind`, so a caller can animate
    /// a collected tile/jelly sprite flying toward it. `nil` if this HUD isn't
    /// currently tracking that objective.
    func scenePosition(for kind: ObjectiveKind, in scene: SKScene) -> CGPoint? {
        guard let node = objectiveIconNodes.first(where: { $0.kind == kind }) else { return nil }
        return node.convert(.zero, to: scene)
    }

    /// Lands a HUD-bound collection flight: sets the icon's count to its new
    /// (already-decremented) value and gives the icon a small pop, timed to
    /// the moment the flying sprite arrives rather than the moment it was matched.
    func collect(kind: ObjectiveKind, remaining: Int) {
        guard let node = objectiveIconNodes.first(where: { $0.kind == kind }) else { return }
        node.setCount(remaining)
        node.icon.run(.sequence([.scale(to: 1.3, duration: 0.08), .scale(to: 1.0, duration: 0.12)]), withKey: "objectivePop")
    }

    private func layoutObjectiveIcons() {
        objectivesContainer.position = CGPoint(x: 0, y: objectivesRowY)
        
        let left = (gamebar.size.width / 2) - gamebar.size.width * 0.11
        let spacing = gamebar.size.width * 0.155

        let count = objectiveIconNodes.count
        guard count > 0 else { return }

        let iconSize: CGFloat = gamebar.size.height * 0.6
        //let maxSpacing: CGFloat = 72
        //let spacing = count > 1 ? min(maxSpacing, objectivesRowWidth / CGFloat(count)) : maxSpacing
        //let totalWidth = spacing * CGFloat(count - 1)

        for (index, node) in objectiveIconNodes.enumerated() {
            //node.position = CGPoint(x: -totalWidth / 2 + CGFloat(index) * spacing, y: 3)
            node.position = CGPoint(x: -left + CGFloat(index) * spacing, y: 2)
            node.icon.size = CGSize(width: iconSize, height: iconSize)
            //node.countLabel.position = CGPoint(x: iconSize / 2 + 4, y: 0)
            node.countLabel.position = CGPoint(x: -28, y: 14)
        }
    }
}

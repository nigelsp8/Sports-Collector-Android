import SpriteKit

/// One "icon + required amount" row in the objectives list, mirroring
/// `HUDNode`'s `ObjectiveIconNode` but laid out for a full-screen list rather
/// than a compact HUD row.
private final class ObjectiveRequirementNode: SKNode {
    private let icon = SKSpriteNode()
    private let countLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")

    override init() {
        super.init()
        countLabel.verticalAlignmentMode = .center
        countLabel.horizontalAlignmentMode = .left
        countLabel.fontColor = .white
        addChild(icon)
        addChild(countLabel)
    }

    required init?(coder: NSCoder) {
        fatalError("ObjectiveRequirementNode does not support storyboard instantiation")
    }

    func configure(texture: SKTexture, total: Int) {
        icon.texture = texture
        countLabel.text = "x\(total)"
    }

    func layout(iconSize: CGFloat, fontSize: CGFloat) {
        icon.size = CGSize(width: iconSize, height: iconSize)
        icon.position = CGPoint(x: -iconSize / 2 - 6, y: 0)
        countLabel.fontSize = fontSize
        countLabel.position = CGPoint(x: iconSize / 2 + 2, y: 0)
    }
}

/// Full-screen "What You Need!" overlay shown when a level first loads:
/// darkens the screen behind a vertical list of the level's objectives (icon
/// + required amount), a Back button that pops back to level select, and a
/// Continue button that dismisses the overlay so play can begin. `GameScene`
/// shows this immediately on load and gates touch input the same way it does
/// for the win/lose overlays.
final class ObjectivesOverlayNode: SKNode {
    private let dimOverlay = SKSpriteNode(color: SKColor(white: 0, alpha: 0.75), size: .zero)
    private let titleLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")
    private let objectivesContainer = SKNode()
    private let backButton = SKSpriteNode(imageNamed: "backbutton")
    private let continueButton = SKSpriteNode(imageNamed: "nextbutton")
    private var rowNodes: [ObjectiveRequirementNode] = []
    private var lastSize: CGSize = .zero

    override init() {
        super.init()
        isHidden = true
        zPosition = 150

        titleLabel.verticalAlignmentMode = .baseline
        titleLabel.horizontalAlignmentMode = .center

        addChild(dimOverlay)
        addChild(titleLabel)
        addChild(objectivesContainer)
        addChild(backButton)
        addChild(continueButton)
    }

    required init?(coder: NSCoder) {
        fatalError("ObjectivesOverlayNode does not support storyboard instantiation")
    }

    /// Rebuilds the objectives list. Called once per level load, before `show()`.
    func configure(objectives: [Objective], textures: TileTextureProvider) {
        objectivesContainer.removeAllChildren()
        rowNodes = objectives.map { objective in
            let row = ObjectiveRequirementNode()
            row.configure(texture: texture(for: objective.kind, textures: textures), total: objective.total)
            objectivesContainer.addChild(row)
            return row
        }
        if lastSize != .zero {
            layoutObjectiveRows(in: lastSize)
        }
    }

    private func texture(for kind: ObjectiveKind, textures: TileTextureProvider) -> SKTexture {
        switch kind {
        case .collect(let type): textures.texture(for: type)
        case .clearJelly: textures.jellyOverlayTexture
        }
    }

    func layout(in size: CGSize) {
        lastSize = size
        dimOverlay.size = size

        // "What You Need!" is long enough that a fixed fraction-of-width font
        // size (the approach `WinOverlayNode`/`LoseOverlayNode` use for their
        // much shorter titles) overflowed the screen - shrink to fit instead.
        let maxTitleWidth = size.width * 0.9
        var titleFontSize = min(size.width * 0.14, 72)
        repeat {
            titleLabel.attributedText = NSAttributedString(
                string: "What You Need!",
                attributes: [
                    .font: UIFont(name: "AvenirNext-Bold", size: titleFontSize) as Any,
                    .foregroundColor: UIColor.white,
                    .strokeColor: UIColor.black,
                    .strokeWidth: -4,
                ]
            )
            if titleLabel.frame.width <= maxTitleWidth || titleFontSize <= 20 { break }
            titleFontSize -= 4
        } while true
        titleLabel.position = CGPoint(x: 0, y: size.height * 0.28)

        let buttonSize = size.width * 0.2
        let buttonY = -size.height / 2 + buttonSize * 0.75 + 24
        backButton.size = CGSize(width: buttonSize, height: buttonSize)
        backButton.position = CGPoint(x: -size.width * 0.18, y: buttonY)
        continueButton.size = CGSize(width: buttonSize, height: buttonSize)
        continueButton.position = CGPoint(x: size.width * 0.18, y: buttonY)

        layoutObjectiveRows(in: size)
    }

    private func layoutObjectiveRows(in size: CGSize) {
        guard !rowNodes.isEmpty else { return }
        let iconSize = min(size.width * 0.14, 64)
        let rowHeight = iconSize * 1.3
        let totalHeight = CGFloat(rowNodes.count) * rowHeight
        let startY = totalHeight / 2 - rowHeight / 2
        for (index, row) in rowNodes.enumerated() {
            row.layout(iconSize: iconSize, fontSize: iconSize * 0.5)
            row.position = CGPoint(x: 0, y: startY - CGFloat(index) * rowHeight)
        }
    }

    func show() {
        isHidden = false
        alpha = 0
        run(.fadeIn(withDuration: 0.3))
    }

    func hide() {
        run(.sequence([.fadeOut(withDuration: 0.25), .run { [weak self] in self?.isHidden = true }]))
    }

    /// Hit test for the Back button, in this node's own coordinate space
    /// (i.e. pass `touch.location(in: objectivesOverlay)`).
    func containsBackButton(_ pointInOverlay: CGPoint) -> Bool {
        backButton.contains(pointInOverlay)
    }

    /// Hit test for the Continue button, in this node's own coordinate space.
    func containsContinueButton(_ pointInOverlay: CGPoint) -> Bool {
        continueButton.contains(pointInOverlay)
    }
}

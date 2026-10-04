import SpriteKit

/// Full-screen "lose" overlay: darkens the screen behind a lose card with a
/// Continue button that pops back to level select. Mirrors `WinOverlayNode`
/// but has no star rating and no dedicated spinning backdrop - the normal
/// `BackgroundNode` and board stay as-is, just dimmed underneath this node.
final class LoseOverlayNode: SKNode {
    private let dimOverlay = SKSpriteNode(color: SKColor(white: 0, alpha: 0.75), size: .zero)
    private let titleLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")
    private let loseImage = SKSpriteNode(imageNamed: "lose")
    private let continueButton = SKSpriteNode(imageNamed: "nextbutton")

    override init() {
        super.init()
        isHidden = true
        zPosition = 150

        titleLabel.verticalAlignmentMode = .baseline
        titleLabel.horizontalAlignmentMode = .center

        addChild(dimOverlay)
        addChild(titleLabel)
        addChild(loseImage)
        addChild(continueButton)
    }

    required init?(coder: NSCoder) {
        fatalError("LoseOverlayNode does not support storyboard instantiation")
    }

    func layout(in size: CGSize) {
        dimOverlay.size = size

        let loseSize = size.width * 0.75
        loseImage.size = CGSize(width: loseSize, height: loseSize)
        loseImage.position = CGPoint(x: 0, y: size.height * 0.04)

        let titleFontSize = min(size.width * 0.16, 80)
        titleLabel.attributedText = NSAttributedString(
            string: "Unlucky!",
            attributes: [
                .font: UIFont(name: "AvenirNext-Bold", size: titleFontSize) as Any,
                .foregroundColor: UIColor.white,
                .strokeColor: UIColor.black,
                .strokeWidth: -4,
            ]
        )
        titleLabel.position = CGPoint(x: 0, y: loseImage.position.y + loseSize / 2 + titleFontSize * 0.3)

        let buttonSize = size.width * 0.2
        continueButton.size = CGSize(width: buttonSize, height: buttonSize)
        continueButton.position = CGPoint(x: 0, y: -size.height / 2 + buttonSize * 0.75 + 24)
    }

    func show() {
        isHidden = false
        alpha = 0
        run(.fadeIn(withDuration: 0.3))
    }

    /// Hit test for the Continue button, in this node's own coordinate space
    /// (i.e. pass `touch.location(in: loseOverlay)`).
    func containsContinueButton(_ pointInOverlay: CGPoint) -> Bool {
        continueButton.contains(pointInOverlay)
    }
}

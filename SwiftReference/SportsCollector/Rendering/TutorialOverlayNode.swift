import SpriteKit

/// Purely cosmetic hint graphic for the tutorial phase - the actual input
/// restriction lives in `GameEngine.tutorialRect`. Shows a pulsing hand near the
/// restricted area until the player makes their first valid match.
final class TutorialOverlayNode: SKNode {
    private let hand = SKSpriteNode(imageNamed: "hand")

    override init() {
        super.init()
        hand.size = CGSize(width: 90, height: 76)
        hand.alpha = 0
        hand.zPosition = 50
        addChild(hand)
    }

    required init?(coder: NSCoder) {
        fatalError("TutorialOverlayNode does not support storyboard instantiation")
    }

    func show(at point: CGPoint) {
        hand.position = point
        hand.removeAllActions()
        hand.alpha = 0
        hand.run(.sequence([
            .fadeAlpha(to: 0.85, duration: 0.3),
            .repeatForever(.sequence([
                .scale(to: 1.12, duration: 0.5),
                .scale(to: 1.0, duration: 0.5),
            ])),
        ]))
    }

    func hide() {
        hand.removeAllActions()
        hand.run(.fadeOut(withDuration: 0.3))
    }
}

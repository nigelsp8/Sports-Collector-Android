import SpriteKit

/// A centered text banner for transient messages ("No More Moves!") or
/// persistent end-of-level messages ("You Win!", "Out of Moves").
final class BannerNode: SKLabelNode {
    override init() {
        super.init()
        fontName = "AvenirNext-Bold"
        fontSize = 32
        fontColor = .white
        alpha = 0
        zPosition = 100
    }

    required init?(coder: NSCoder) {
        fatalError("BannerNode does not support storyboard instantiation")
    }

    func show(_ message: String, duration: TimeInterval = 1.2) {
        text = message
        removeAllActions()
        run(.sequence([
            .fadeIn(withDuration: 0.2),
            .wait(forDuration: duration),
            .fadeOut(withDuration: 0.3),
        ]))
    }

    func showPersistently(_ message: String) {
        text = message
        removeAllActions()
        run(.fadeIn(withDuration: 0.3))
    }

    func hide() {
        removeAllActions()
        run(.fadeOut(withDuration: 0.2))
    }

    /// Sets visibility by directly setting `alpha` rather than running a fade
    /// action - needed when the scene itself may be `isPaused` (e.g. a pause
    /// menu banner), since a paused scene never evaluates queued actions.
    func setVisibleInstantly(_ visible: Bool, message: String? = nil) {
        removeAllActions()
        if let message {
            text = message
        }
        alpha = visible ? 1 : 0
    }
}

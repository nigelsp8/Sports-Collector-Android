import SpriteKit

/// Full-scene background, reproducing the original game's `drawBackground` technique
/// (`Reference/itag/DrPopperViewController.m`): the source art is authored larger than
/// the visible window (here a 2048x2048 texture where only a 64%-wide x 75%-tall crop
/// is ever shown), and that crop is slowly panned along an elliptical sinusoidal path
/// (`bkg_x = 0.18 * sin(angle)`, `bkg_y = 0.125 * cos(angle)`). Oversizing the sprite and
/// panning its position reproduces the same look without needing manual UV manipulation.
final class BackgroundNode: SKSpriteNode {
    private static let cropWidthFraction: CGFloat = 0.64
    private static let cropHeightFraction: CGFloat = 0.75
    private static let panAmplitudeX: CGFloat = 0.18
    private static let panAmplitudeY: CGFloat = 0.125
    private static let angularSpeed: CGFloat = 2.7 * .pi / 180 // matches original's ~2.7 deg/sec drift rate

    init(level: Level) {
        var lt = (level.mapNumber / 17)
        if lt > 5 { lt -= 6 }
        var texture:SKTexture?
        if lt == 0 { texture = SKTexture(imageNamed: "bkg1b") }
        if lt == 1 { texture = SKTexture(imageNamed: "bkg2b") }
        if lt == 2 { texture = SKTexture(imageNamed: "bkg3b") }
        if lt == 3 { texture = SKTexture(imageNamed: "bkg4b") }
        if lt == 4 { texture = SKTexture(imageNamed: "bkg5b") }
        if lt == 5 { texture = SKTexture(imageNamed: "bkg6b") }
        super.init(texture: texture, color: .clear, size: texture!.size())
        zPosition = -100
        //alpha = 0.5
    }

    required init?(coder: NSCoder) {
        fatalError("BackgroundNode does not support storyboard instantiation")
    }

    func layout(in size: CGSize) {
        self.size = CGSize(width: size.width / Self.cropWidthFraction, height: size.height / Self.cropHeightFraction)
    }

    func update(elapsedTime: TimeInterval) {
        let angle = CGFloat(elapsedTime) * Self.angularSpeed
        position = CGPoint(
            x: size.width * Self.panAmplitudeX * sin(angle),
            y: size.height * Self.panAmplitudeY * cos(angle)
        )
    }
}

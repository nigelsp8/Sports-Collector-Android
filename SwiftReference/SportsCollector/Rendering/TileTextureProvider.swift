import SpriteKit
import UIKit

/// Wraps the `Pills` texture atlas (copied from the original's Art/Pills folder).
/// File names already match one-to-one with what we need, so this is just a
/// TileType -> filename lookup.
struct TileTextureProvider {
    private let atlas = SKTextureAtlas(named: "Pills")

    func texture(for type: TileType) -> SKTexture {
        atlas.textureNamed(fileName(for: type))
    }

    /// For UIKit contexts (the level editor's tile palette) that can't use an
    /// `SKTexture` directly - goes through the real atlas rather than guessing
    /// at the loose PNGs' on-disk/bundle paths.
    func image(for type: TileType) -> UIImage {
        UIImage(cgImage: texture(for: type).cgImage())
    }

    var jellyOverlayTexture: SKTexture { atlas.textureNamed("bkgtile1") }
    var wallHorizontalTexture: SKTexture { atlas.textureNamed("wall-horizontal") }
    var wallVerticalTexture: SKTexture { atlas.textureNamed("wall-vertical") }
    
    private func fileName(for type: TileType) -> String {
        switch type {
        case .pinkTablet: "frisbee"             // BLUE         "pinkpill"
        case .orangeTablet: "rugbyball"         // BROWN        "orangepill"
        case .yellowTablet: "boxingglove"       // RED          "yellowpill"
        case .greenTablet: "tennisball"         // GREEN        "greenpill"
        case .purpleTablet: "refsshirt"         // BLACK/WHITE  "purplepill"
        case .blueTablet: "soccerballcoloured"  // WHITE/BLACK  "bluepill"
            
        case .orangeBluePill: "rugbyballx2"
        case .pinkGreenPill: "frisbeex2"
        case .purpleYellowPill: "refsshirtx2"
        
        case .pinkBacteria: "trophy1"
        case .orangeBacteria: "trophies"
        case .yellowBacteria: "boxinggloves"
        case .greenBacteria: "chequeredflags"
        case .purpleBacteria: "goalkeepernet"
        case .blueBacteria: "timingwatch"
        
        case .solidStage1: "solid1"             // new graphics
        case .solidStage2: "solid2"             // new graphics
        case .solidStage3: "solid3"             // new graphics
        }
    }
}

import SpriteKit
import UIKit

/// The level JSON references gradient background images (grad_1/2/3.jpg) that
/// don't exist anywhere in the recovered art assets. Rather than needing real
/// image files, this renders an equivalent gradient texture in code.
enum BackgroundGradientFactory {
    private static let palettes: [String: [UIColor]] = [
        "grad_1.jpg": [UIColor(red: 0.55, green: 0.80, blue: 0.98, alpha: 1), UIColor(red: 0.16, green: 0.38, blue: 0.75, alpha: 1)],
        "grad_2.jpg": [UIColor(red: 0.98, green: 0.78, blue: 0.55, alpha: 1), UIColor(red: 0.80, green: 0.32, blue: 0.32, alpha: 1)],
        "grad_3.jpg": [UIColor(red: 0.78, green: 0.93, blue: 0.72, alpha: 1), UIColor(red: 0.18, green: 0.52, blue: 0.36, alpha: 1)],
    ]
    private static let defaultPalette = [UIColor(white: 0.82, alpha: 1), UIColor(white: 0.52, alpha: 1)]

    static func texture(for filename: String, size: CGSize) -> SKTexture {
        let colors = palettes[filename] ?? defaultPalette
        let renderer = UIGraphicsImageRenderer(size: size)
        let image = renderer.image { context in
            let cgColors = colors.map(\.cgColor) as CFArray
            guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: cgColors, locations: nil) else { return }
            context.cgContext.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: 0, y: size.height), options: [])
        }
        return SKTexture(image: image)
    }
}

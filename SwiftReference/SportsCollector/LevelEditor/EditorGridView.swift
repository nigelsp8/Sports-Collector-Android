import UIKit

/// The editable board grid: a fixed-size grid of tappable buttons, one per
/// cell. No `UICollectionView` - there's no existing grid-picker precedent in
/// this project to extend, and a bounded 9x9 grid doesn't need the machinery.
final class EditorGridView: UIView {
    static let cellSize: CGFloat = 36

    var onCellTapped: ((GridPoint) -> Void)?

    private var buttons: [UIButton] = []
    private var width = 0
    private var height = 0

    private let edgeTag = 101
    private let markerTag = 102

    func configure(width: Int, height: Int) {
        self.width = width
        self.height = height
        subviews.forEach { $0.removeFromSuperview() }
        buttons.removeAll()

        let rowsStack = UIStackView()
        rowsStack.axis = .vertical
        rowsStack.spacing = 1
        rowsStack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(rowsStack)
        NSLayoutConstraint.activate([
            rowsStack.topAnchor.constraint(equalTo: topAnchor),
            rowsStack.leadingAnchor.constraint(equalTo: leadingAnchor),
            rowsStack.trailingAnchor.constraint(equalTo: trailingAnchor),
            rowsStack.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        for row in 0..<height {
            let rowStack = UIStackView()
            rowStack.axis = .horizontal
            rowStack.spacing = 1
            rowsStack.addArrangedSubview(rowStack)
            for col in 0..<width {
                let button = UIButton(type: .custom)
                button.layer.borderWidth = 0.5
                button.imageView?.contentMode = .scaleAspectFit
                button.translatesAutoresizingMaskIntoConstraints = false
                NSLayoutConstraint.activate([
                    button.widthAnchor.constraint(equalToConstant: Self.cellSize),
                    button.heightAnchor.constraint(equalToConstant: Self.cellSize),
                ])
                let point = GridPoint(row: row, col: col)
                button.addAction(UIAction { [weak self] _ in self?.onCellTapped?(point) }, for: .touchUpInside)
                rowStack.addArrangedSubview(button)
                buttons.append(button)
            }
        }
    }

    func refresh(with level: EditableLevel, textureProvider: TileTextureProvider) {
        for row in 0..<height {
            for col in 0..<width {
                let point = GridPoint(row: row, col: col)
                configure(button: buttons[row * width + col], with: level[point], textureProvider: textureProvider)
            }
        }
    }

    private func configure(
        button: UIButton, with spec: EditableLevel.EditableCellSpec, textureProvider: TileTextureProvider
    ) {
        for overlay in button.subviews where overlay.tag == edgeTag || overlay.tag == markerTag {
            overlay.removeFromSuperview()
        }

        guard spec.isActive else {
            button.backgroundColor = .black
            button.layer.borderColor = UIColor.darkGray.cgColor
            button.setImage(nil, for: .normal)
            return
        }

        button.backgroundColor = spec.hasJelly ? UIColor.systemBlue.withAlphaComponent(0.3) : .white
        button.layer.borderColor = UIColor.black.cgColor
        button.setImage(spec.fixedTile.map(textureProvider.image(for:)), for: .normal)

        if spec.hasWallBelow { addEdge(to: button, bottom: true) }
        if spec.hasWallRight { addEdge(to: button, bottom: false) }
        if spec.isSpawnPoint { addMarker(to: button, color: .systemGreen, leading: true) }
        if spec.isExit { addMarker(to: button, color: .systemRed, leading: false) }
    }

    private func addEdge(to button: UIButton, bottom: Bool) {
        let edge = UIView()
        edge.tag = edgeTag
        edge.backgroundColor = .systemOrange
        edge.translatesAutoresizingMaskIntoConstraints = false
        button.addSubview(edge)
        if bottom {
            NSLayoutConstraint.activate([
                edge.leadingAnchor.constraint(equalTo: button.leadingAnchor),
                edge.trailingAnchor.constraint(equalTo: button.trailingAnchor),
                edge.bottomAnchor.constraint(equalTo: button.bottomAnchor),
                edge.heightAnchor.constraint(equalToConstant: 3),
            ])
        } else {
            NSLayoutConstraint.activate([
                edge.topAnchor.constraint(equalTo: button.topAnchor),
                edge.bottomAnchor.constraint(equalTo: button.bottomAnchor),
                edge.trailingAnchor.constraint(equalTo: button.trailingAnchor),
                edge.widthAnchor.constraint(equalToConstant: 3),
            ])
        }
    }

    /// `leading`: green spawn marker at top-leading, red exit marker at top-trailing.
    private func addMarker(to button: UIButton, color: UIColor, leading: Bool) {
        let marker = UIView()
        marker.tag = markerTag
        marker.backgroundColor = color
        marker.layer.cornerRadius = 3
        marker.translatesAutoresizingMaskIntoConstraints = false
        button.addSubview(marker)
        NSLayoutConstraint.activate([
            marker.widthAnchor.constraint(equalToConstant: 6),
            marker.heightAnchor.constraint(equalToConstant: 6),
            marker.topAnchor.constraint(equalTo: button.topAnchor, constant: 2),
            leading
                ? marker.leadingAnchor.constraint(equalTo: button.leadingAnchor, constant: 2)
                : marker.trailingAnchor.constraint(equalTo: button.trailingAnchor, constant: -2),
        ])
    }
}

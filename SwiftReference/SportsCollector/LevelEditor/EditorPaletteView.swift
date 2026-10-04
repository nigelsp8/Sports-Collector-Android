import UIKit

/// Tool-mode picker (`EditorTool`) plus, only in `.tile` mode, a horizontal
/// palette of every `TileType` (via `TileTextureProvider`'s art) and a "None"
/// button for clearing a cell's fixed tile.
final class EditorPaletteView: UIView {
    var onToolChanged: ((EditorTool) -> Void)?
    var onTileSelected: ((TileType?) -> Void)?

    private(set) var selectedTool: EditorTool = .active
    private(set) var selectedTile: TileType?

    private let segmentedControl: UISegmentedControl
    private let tileScrollView = UIScrollView()
    private let tileStack = UIStackView()
    private var tileButtons: [UIButton] = []

    private let textureProvider = TileTextureProvider()

    override init(frame: CGRect) {
        segmentedControl = UISegmentedControl(items: EditorTool.allCases.map(\.title))
        super.init(frame: frame)
        setUp()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not implemented") }

    private func setUp() {
        let outerStack = UIStackView(arrangedSubviews: [segmentedControl, tileScrollView])
        outerStack.axis = .vertical
        outerStack.spacing = 8
        outerStack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(outerStack)
        NSLayoutConstraint.activate([
            outerStack.topAnchor.constraint(equalTo: topAnchor),
            outerStack.leadingAnchor.constraint(equalTo: leadingAnchor),
            outerStack.trailingAnchor.constraint(equalTo: trailingAnchor),
            outerStack.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        segmentedControl.selectedSegmentIndex = 0
        segmentedControl.addAction(UIAction { [weak self] _ in self?.toolChanged() }, for: .valueChanged)

        tileScrollView.showsHorizontalScrollIndicator = false
        tileScrollView.translatesAutoresizingMaskIntoConstraints = false
        tileScrollView.heightAnchor.constraint(equalToConstant: 48).isActive = true
        tileStack.axis = .horizontal
        tileStack.spacing = 6
        tileStack.translatesAutoresizingMaskIntoConstraints = false
        tileScrollView.addSubview(tileStack)
        NSLayoutConstraint.activate([
            tileStack.topAnchor.constraint(equalTo: tileScrollView.topAnchor),
            tileStack.bottomAnchor.constraint(equalTo: tileScrollView.bottomAnchor),
            tileStack.leadingAnchor.constraint(equalTo: tileScrollView.leadingAnchor),
            tileStack.trailingAnchor.constraint(equalTo: tileScrollView.trailingAnchor),
            tileStack.heightAnchor.constraint(equalTo: tileScrollView.heightAnchor),
        ])

        addTileButton(for: nil)
        for type in TileType.allCases {
            addTileButton(for: type)
        }
        updateTileScrollVisibility()
        highlightSelectedTile()
    }

    private func addTileButton(for type: TileType?) {
        let button = UIButton(type: .custom)
        button.layer.borderWidth = 1
        button.layer.cornerRadius = 4
        button.accessibilityLabel = type?.editorDisplayName ?? "None"
        if let type {
            button.setImage(textureProvider.image(for: type), for: .normal)
            button.imageView?.contentMode = .scaleAspectFit
        } else {
            button.setTitle("None", for: .normal)
            button.setTitleColor(.label, for: .normal)
            button.titleLabel?.font = .systemFont(ofSize: 11)
        }
        button.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            button.widthAnchor.constraint(equalToConstant: 44),
            button.heightAnchor.constraint(equalToConstant: 44),
        ])
        button.addAction(UIAction { [weak self] _ in self?.tileTapped(type) }, for: .touchUpInside)
        tileStack.addArrangedSubview(button)
        tileButtons.append(button)
    }

    private func toolChanged() {
        selectedTool = EditorTool.allCases[segmentedControl.selectedSegmentIndex]
        updateTileScrollVisibility()
        onToolChanged?(selectedTool)
    }

    private func tileTapped(_ type: TileType?) {
        selectedTile = type
        highlightSelectedTile()
        onTileSelected?(type)
    }

    private func updateTileScrollVisibility() {
        tileScrollView.isHidden = selectedTool != .tile
    }

    private func highlightSelectedTile() {
        for (index, button) in tileButtons.enumerated() {
            let type: TileType? = index == 0 ? nil : TileType.allCases[index - 1]
            let isSelected = type == selectedTile
            button.layer.borderColor = (isSelected ? UIColor.systemBlue : UIColor.systemGray3).cgColor
            button.layer.borderWidth = isSelected ? 3 : 1
        }
    }
}

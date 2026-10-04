import UIKit

/// One row per `Level.activeBlocks` entry (blocks-to-win / spawn-percentage,
/// plus delete), and an "Add Objective" action sheet for `TileType`s not yet
/// present.
final class EditorObjectivesSectionView: UIView {
    var onObjectivesChanged: (([TileType: ActiveBlockSpec]) -> Void)?
    /// Supplies the presenting view controller for the "Add Objective" action sheet.
    weak var presentingViewController: UIViewController?

    private var activeBlocks: [TileType: ActiveBlockSpec] = [:]
    private let rowsStack = UIStackView()
    private let addButton = UIButton(type: .system)
    private let textureProvider = TileTextureProvider()

    override init(frame: CGRect) {
        super.init(frame: frame)
        setUp()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not implemented") }

    func update(activeBlocks: [TileType: ActiveBlockSpec]) {
        self.activeBlocks = activeBlocks
        rebuildRows()
    }

    private func setUp() {
        rowsStack.axis = .vertical
        rowsStack.spacing = 6

        addButton.setTitle("Add Objective", for: .normal)
        addButton.addAction(UIAction { [weak self] _ in self?.presentAddObjective() }, for: .touchUpInside)

        let outer = UIStackView(arrangedSubviews: [rowsStack, addButton])
        outer.axis = .vertical
        outer.spacing = 8
        outer.translatesAutoresizingMaskIntoConstraints = false
        addSubview(outer)
        NSLayoutConstraint.activate([
            outer.topAnchor.constraint(equalTo: topAnchor),
            outer.leadingAnchor.constraint(equalTo: leadingAnchor),
            outer.trailingAnchor.constraint(equalTo: trailingAnchor),
            outer.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
    }

    private func rebuildRows() {
        rowsStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        for type in activeBlocks.keys.sorted(by: { $0.rawValue < $1.rawValue }) {
            rowsStack.addArrangedSubview(makeRow(for: type))
        }
    }

    private func makeRow(for type: TileType) -> UIView {
        let spec = activeBlocks[type] ?? ActiveBlockSpec(blocksToWin: 0, spawnPercentage: 0)

        let icon = UIImageView(image: textureProvider.image(for: type))
        icon.contentMode = .scaleAspectFit
        icon.isAccessibilityElement = true
        icon.accessibilityLabel = type.editorDisplayName
        icon.widthAnchor.constraint(equalToConstant: 32).isActive = true
        icon.heightAnchor.constraint(equalToConstant: 32).isActive = true

        let winField = Self.makeField(text: String(spec.blocksToWin))
        let percentField = Self.makeField(text: String(spec.spawnPercentage))

        let applyEdit: () -> Void = { [weak self] in
            guard let self else { return }
            let win = Int(winField.text ?? "") ?? 0
            let percent = Int(percentField.text ?? "") ?? 0
            self.activeBlocks[type] = ActiveBlockSpec(blocksToWin: win, spawnPercentage: percent)
            self.onObjectivesChanged?(self.activeBlocks)
        }
        winField.addAction(UIAction { _ in applyEdit() }, for: .editingChanged)
        percentField.addAction(UIAction { _ in applyEdit() }, for: .editingChanged)

        let deleteButton = UIButton(type: .system)
        deleteButton.setImage(UIImage(systemName: "trash"), for: .normal)
        deleteButton.addAction(UIAction { [weak self] _ in self?.remove(type) }, for: .touchUpInside)

        let row = UIStackView(arrangedSubviews: [icon, winField, percentField, deleteButton])
        row.axis = .horizontal
        row.spacing = 8
        winField.widthAnchor.constraint(equalToConstant: 56).isActive = true
        percentField.widthAnchor.constraint(equalToConstant: 56).isActive = true
        return row
    }

    private static func makeField(text: String) -> UITextField {
        let field = UITextField()
        field.keyboardType = .numberPad
        field.borderStyle = .roundedRect
        field.textAlignment = .center
        field.text = text
        field.addDoneButtonToolbar()
        return field
    }

    private func remove(_ type: TileType) {
        activeBlocks.removeValue(forKey: type)
        rebuildRows()
        onObjectivesChanged?(activeBlocks)
    }

    private func presentAddObjective() {
        let available = TileType.allCases.filter { activeBlocks[$0] == nil }
        guard !available.isEmpty else { return }
        // `UIAlertController` action-sheet rows can't show a per-row image, so
        // this picks via a dedicated icon grid instead of a text list.
        let items = available.map { (type: $0, image: textureProvider.image(for: $0)) }
        let picker = IconGridPickerViewController(items: items) { [weak self] type in
            guard let self else { return }
            self.activeBlocks[type] = ActiveBlockSpec(blocksToWin: 0, spawnPercentage: 0)
            self.rebuildRows()
            self.onObjectivesChanged?(self.activeBlocks)
        }
        let nav = UINavigationController(rootViewController: picker)
        nav.sheetPresentationController?.detents = [.medium()]
        presentingViewController?.present(nav, animated: true)
    }
}

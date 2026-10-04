import UIKit

/// Moves-allowed, star thresholds (with a live validity check and a one-tap
/// fallback derived from `StarThreshold.fallback(movesAllowed:)`), and a
/// background picker limited to the three gradient filenames
/// `BackgroundGradientFactory` actually renders.
final class EditorSettingsSectionView: UIView {
    var onMovesChanged: ((Int) -> Void)?
    var onThresholdsChanged: (((one: Int, two: Int, three: Int)) -> Void)?
    var onBackgroundChanged: ((String) -> Void)?

    private static let backgroundFilenames = ["grad_1.jpg", "grad_2.jpg", "grad_3.jpg"]

    private let movesField = EditorSettingsSectionView.makeNumberField()
    private let oneField = EditorSettingsSectionView.makeNumberField()
    private let twoField = EditorSettingsSectionView.makeNumberField()
    private let threeField = EditorSettingsSectionView.makeNumberField()
    private let validityLabel = UILabel()
    private let fallbackButton = UIButton(type: .system)
    private let backgroundControl = UISegmentedControl(items: ["Blue", "Orange", "Green"])

    private var movesAllowed = 1

    override init(frame: CGRect) {
        super.init(frame: frame)
        setUp()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not implemented") }

    func update(movesAllowed: Int, starThresholds: (one: Int, two: Int, three: Int), backgroundImageFilename: String) {
        self.movesAllowed = movesAllowed
        movesField.text = String(movesAllowed)
        oneField.text = String(starThresholds.one)
        twoField.text = String(starThresholds.two)
        threeField.text = String(starThresholds.three)
        if let index = Self.backgroundFilenames.firstIndex(of: backgroundImageFilename) {
            backgroundControl.selectedSegmentIndex = index
        } else {
            backgroundControl.selectedSegmentIndex = UISegmentedControl.noSegment
        }
        updateValidity()
    }

    private func setUp() {
        let movesRow = labeledRow(title: "Moves Allowed", field: movesField)
        let oneRow = labeledRow(title: "1 Star", field: oneField)
        let twoRow = labeledRow(title: "2 Star", field: twoField)
        let threeRow = labeledRow(title: "3 Star", field: threeField)

        validityLabel.font = .systemFont(ofSize: 12)
        validityLabel.textColor = .systemRed
        validityLabel.numberOfLines = 0

        fallbackButton.setTitle("Use Fallback Thresholds", for: .normal)
        fallbackButton.addAction(UIAction { [weak self] _ in self?.applyFallback() }, for: .touchUpInside)

        let backgroundLabel = UILabel()
        backgroundLabel.text = "Background"
        backgroundLabel.font = .systemFont(ofSize: 14, weight: .medium)

        let stack = UIStackView(arrangedSubviews: [
            movesRow, oneRow, twoRow, threeRow, validityLabel, fallbackButton, backgroundLabel, backgroundControl,
        ])
        stack.axis = .vertical
        stack.spacing = 8
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        movesField.addAction(UIAction { [weak self] _ in self?.movesEdited() }, for: .editingChanged)
        for field in [oneField, twoField, threeField] {
            field.addAction(UIAction { [weak self] _ in self?.thresholdsEdited() }, for: .editingChanged)
        }
        backgroundControl.addAction(UIAction { [weak self] _ in self?.backgroundEdited() }, for: .valueChanged)
    }

    private func labeledRow(title: String, field: UITextField) -> UIStackView {
        let label = UILabel()
        label.text = title
        label.font = .systemFont(ofSize: 14)
        label.setContentHuggingPriority(.defaultHigh, for: .horizontal)
        let row = UIStackView(arrangedSubviews: [label, field])
        row.axis = .horizontal
        row.spacing = 8
        return row
    }

    private static func makeNumberField() -> UITextField {
        let field = UITextField()
        field.keyboardType = .numberPad
        field.borderStyle = .roundedRect
        field.textAlignment = .right
        field.addDoneButtonToolbar()
        return field
    }

    private func movesEdited() {
        movesAllowed = Int(movesField.text ?? "") ?? movesAllowed
        onMovesChanged?(movesAllowed)
    }

    private func thresholdsEdited() {
        updateValidity()
        onThresholdsChanged?(currentThresholds())
    }

    private func backgroundEdited() {
        guard backgroundControl.selectedSegmentIndex != UISegmentedControl.noSegment else { return }
        onBackgroundChanged?(Self.backgroundFilenames[backgroundControl.selectedSegmentIndex])
    }

    private func applyFallback() {
        let fallback = StarThreshold.fallback(movesAllowed: movesAllowed)
        oneField.text = String(fallback.one)
        twoField.text = String(fallback.two)
        threeField.text = String(fallback.three)
        updateValidity()
        onThresholdsChanged?(fallback)
    }

    private func currentThresholds() -> (one: Int, two: Int, three: Int) {
        (Int(oneField.text ?? "") ?? 0, Int(twoField.text ?? "") ?? 0, Int(threeField.text ?? "") ?? 0)
    }

    private func updateValidity() {
        let thresholds = currentThresholds()
        let isValid = StarThreshold.isValidTriple(thresholds)
        validityLabel.text = isValid
            ? nil
            : "Thresholds must be positive and strictly increasing, or gameplay falls back to a computed value."
    }
}

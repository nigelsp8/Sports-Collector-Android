import UIKit

/// Root level editor screen: a board grid, a tool/tile palette, level-settings
/// fields, and an objectives list, composed in one scrollable stack. Pushed
/// from `LevelSelectViewController`'s per-row detail-disclosure button.
final class LevelEditorViewController: UIViewController {
    private let mapID: Int
    private let resourceName: String
    private let editStore: LevelEditStore
    private var editableLevel: EditableLevel
    private let textureProvider = TileTextureProvider()

    private let gridView = EditorGridView()
    private let paletteView = EditorPaletteView()
    private let settingsView = EditorSettingsSectionView()
    private let objectivesView = EditorObjectivesSectionView()
    private let scrollView = UIScrollView()

    init(level: Level, mapID: Int, resourceName: String, editStore: LevelEditStore = .shared) {
        self.mapID = mapID
        self.resourceName = resourceName
        self.editStore = editStore
        self.editableLevel = EditableLevel(level: level)
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not implemented") }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Edit Level \(mapID)"
        view.backgroundColor = .systemBackground
        setUpLayout()
        setUpNavigationItems()
        setUpCallbacks()
        setUpKeyboardAvoidance()
        refreshAll()
    }

    private func setUpLayout() {
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])

        let gridContainer = UIView()
        gridContainer.addSubview(gridView)
        gridView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            gridView.topAnchor.constraint(equalTo: gridContainer.topAnchor),
            gridView.bottomAnchor.constraint(equalTo: gridContainer.bottomAnchor),
            gridView.centerXAnchor.constraint(equalTo: gridContainer.centerXAnchor),
        ])

        let stack = UIStackView(arrangedSubviews: [
            gridContainer, paletteView, divider(), settingsView, divider(), objectivesView,
        ])
        stack.axis = .vertical
        stack.spacing = 16
        stack.isLayoutMarginsRelativeArrangement = true
        stack.layoutMargins = UIEdgeInsets(top: 16, left: 16, bottom: 32, right: 16)
        stack.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: scrollView.topAnchor),
            stack.bottomAnchor.constraint(equalTo: scrollView.bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: scrollView.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: scrollView.trailingAnchor),
            stack.widthAnchor.constraint(equalTo: scrollView.widthAnchor),
        ])

        gridView.configure(width: editableLevel.width, height: editableLevel.height)
        objectivesView.presentingViewController = self
    }

    /// Number fields near the bottom of the stack (settings, objectives) would
    /// otherwise end up underneath the keyboard, with no way to see what's
    /// being typed. Resizes the scroll view's bottom inset to the keyboard's
    /// height and scrolls the focused field above it.
    private func setUpKeyboardAvoidance() {
        NotificationCenter.default.addObserver(
            self, selector: #selector(keyboardWillShow), name: UIResponder.keyboardWillShowNotification, object: nil
        )
        NotificationCenter.default.addObserver(
            self, selector: #selector(keyboardWillHide), name: UIResponder.keyboardWillHideNotification, object: nil
        )
    }

    @objc private func keyboardWillShow(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let keyboardFrame = (userInfo[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue
        else { return }

        let keyboardFrameInView = view.convert(keyboardFrame, from: nil)
        let bottomInset = max(0, view.bounds.maxY - keyboardFrameInView.minY)
        scrollView.contentInset.bottom = bottomInset
        scrollView.verticalScrollIndicatorInsets.bottom = bottomInset

        guard let fieldView = view.firstResponderView() else { return }
        let fieldFrame = fieldView.convert(fieldView.bounds, to: scrollView)
        scrollView.scrollRectToVisible(fieldFrame.insetBy(dx: 0, dy: -16), animated: true)
    }

    @objc private func keyboardWillHide(_ notification: Notification) {
        scrollView.contentInset.bottom = 0
        scrollView.verticalScrollIndicatorInsets.bottom = 0
    }

    private func divider() -> UIView {
        let line = UIView()
        line.backgroundColor = .separator
        line.heightAnchor.constraint(equalToConstant: 1).isActive = true
        return line
    }

    private func setUpNavigationItems() {
        let moreMenu = UIMenu(children: [
            UIAction(title: "Save", image: UIImage(systemName: "square.and.arrow.down")) { [weak self] _ in
                self?.saveTapped()
            },
            UIAction(title: "Reset to Original", image: UIImage(systemName: "arrow.counterclockwise"), attributes: .destructive) { [weak self] _ in
                self?.resetTapped()
            },
            UIAction(title: "Export", image: UIImage(systemName: "square.and.arrow.up")) { [weak self] _ in
                self?.exportTapped()
            },
        ])
        let moreItem = UIBarButtonItem(image: UIImage(systemName: "ellipsis.circle"), menu: moreMenu)
        let playItem = UIBarButtonItem(
            image: UIImage(systemName: "play.circle"), style: .plain, target: self, action: #selector(playTestTapped)
        )
        navigationItem.rightBarButtonItems = [moreItem, playItem]
    }

    private func setUpCallbacks() {
        gridView.onCellTapped = { [weak self] point in self?.cellTapped(point) }

        settingsView.onMovesChanged = { [weak self] moves in self?.editableLevel.movesAllowed = moves }
        settingsView.onThresholdsChanged = { [weak self] thresholds in self?.editableLevel.starThresholds = thresholds }
        settingsView.onBackgroundChanged = { [weak self] filename in self?.editableLevel.backgroundImageFilename = filename }

        objectivesView.onObjectivesChanged = { [weak self] blocks in self?.editableLevel.activeBlocks = blocks }
    }

    private func refreshAll() {
        gridView.refresh(with: editableLevel, textureProvider: textureProvider)
        settingsView.update(
            movesAllowed: editableLevel.movesAllowed,
            starThresholds: editableLevel.starThresholds,
            backgroundImageFilename: editableLevel.backgroundImageFilename
        )
        objectivesView.update(activeBlocks: editableLevel.activeBlocks)
    }

    private func cellTapped(_ point: GridPoint) {
        var spec = editableLevel[point]
        switch paletteView.selectedTool {
        case .active: spec.isActive.toggle()
        case .wallBelow: spec.hasWallBelow.toggle()
        case .wallRight: spec.hasWallRight.toggle()
        case .spawn: spec.isSpawnPoint.toggle()
        case .exit: spec.isExit.toggle()
        case .jelly: spec.hasJelly.toggle()
        case .tile: spec.fixedTile = paletteView.selectedTile
        }
        editableLevel[point] = spec
        gridView.refresh(with: editableLevel, textureProvider: textureProvider)
    }

    private func saveTapped() {
        do {
            try editStore.save(editableLevel.makeLevel(), resourceName: resourceName)
            presentAlert(title: "Saved", message: "This level's edits will now load in place of the original.")
        } catch {
            presentAlert(title: "Save Failed", message: error.localizedDescription)
        }
    }

    private func resetTapped() {
        do {
            try editStore.resetToOriginal(resourceName: resourceName)
            let reloaded = try LevelLoader.loadLevel(resourceName: resourceName, level: mapID, editStore: editStore)
            editableLevel = EditableLevel(level: reloaded)
            gridView.configure(width: editableLevel.width, height: editableLevel.height)
            refreshAll()
            presentAlert(title: "Reset", message: "Reverted to the original level data.")
        } catch {
            presentAlert(title: "Reset Failed", message: error.localizedDescription)
        }
    }

    private func exportTapped() {
        do {
            let data = try LevelEncoder.encode(editableLevel.makeLevel())
            let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent("\(resourceName).txt")
            try data.write(to: tempURL, options: .atomic)
            let activity = UIActivityViewController(activityItems: [tempURL], applicationActivities: nil)
            activity.popoverPresentationController?.barButtonItem = navigationItem.rightBarButtonItems?.first
            present(activity, animated: true)
        } catch {
            presentAlert(title: "Export Failed", message: error.localizedDescription)
        }
    }

    @objc private func playTestTapped() {
        let gameViewController = ViewController()
        gameViewController.mapID = mapID
        gameViewController.injectedLevel = editableLevel.makeLevel()
        navigationController?.pushViewController(gameViewController, animated: true)
    }

    private func presentAlert(title: String, message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}

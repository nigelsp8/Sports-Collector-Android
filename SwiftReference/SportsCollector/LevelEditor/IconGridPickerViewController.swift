import UIKit

/// Icon-only grid picker. `UIAlertController` action sheets can't show a
/// custom image per action, so "Add Objective" presents this instead - tiles
/// are picked by their actual art rather than by name.
final class IconGridPickerViewController: UIViewController {
    private let items: [(type: TileType, image: UIImage)]
    private let onSelect: (TileType) -> Void
    private var collectionView: UICollectionView!

    init(items: [(type: TileType, image: UIImage)], onSelect: @escaping (TileType) -> Void) {
        self.items = items
        self.onSelect = onSelect
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Add Objective"
        view.backgroundColor = .systemBackground
        navigationItem.rightBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .cancel, target: self, action: #selector(cancelTapped)
        )

        let layout = UICollectionViewFlowLayout()
        layout.itemSize = CGSize(width: 64, height: 64)
        layout.minimumInteritemSpacing = 12
        layout.minimumLineSpacing = 12
        layout.sectionInset = UIEdgeInsets(top: 16, left: 16, bottom: 16, right: 16)

        collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.backgroundColor = .clear
        collectionView.dataSource = self
        collectionView.delegate = self
        collectionView.register(IconCell.self, forCellWithReuseIdentifier: IconCell.reuseID)
        collectionView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(collectionView)
        NSLayoutConstraint.activate([
            collectionView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            collectionView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    @objc private func cancelTapped() {
        dismiss(animated: true)
    }
}

extension IconGridPickerViewController: UICollectionViewDataSource, UICollectionViewDelegate {
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        items.count
    }

    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: IconCell.reuseID, for: indexPath) as! IconCell
        let item = items[indexPath.item]
        cell.configure(image: item.image, accessibilityLabel: item.type.editorDisplayName)
        return cell
    }

    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        onSelect(items[indexPath.item].type)
        dismiss(animated: true)
    }
}

private final class IconCell: UICollectionViewCell {
    static let reuseID = "IconCell"
    private let imageView = UIImageView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        contentView.layer.borderWidth = 1
        contentView.layer.borderColor = UIColor.systemGray3.cgColor
        contentView.layer.cornerRadius = 6
        imageView.contentMode = .scaleAspectFit
        imageView.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(imageView)
        NSLayoutConstraint.activate([
            imageView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 6),
            imageView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -6),
            imageView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 6),
            imageView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -6),
        ])
        isAccessibilityElement = true
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not implemented") }

    func configure(image: UIImage, accessibilityLabel: String) {
        imageView.image = image
        self.accessibilityLabel = accessibilityLabel
    }
}

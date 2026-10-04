import Foundation

/// Persists level editor saves as override files in the app's Documents
/// directory, keyed by the on-disk resource number `LevelLoader.loadLevel`
/// is actually called with (not `mapID`/map slot - `ViewController.levelOrder`
/// means those are different numbers). `LevelLoader` checks here before
/// falling back to the bundled resource, so a save is picked up by normal
/// gameplay for free.
struct LevelEditStore {
    static let shared = LevelEditStore()

    private let root: URL

    init(root: URL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]) {
        self.root = root
    }

    private var directory: URL { root.appendingPathComponent("Levels", isDirectory: true) }

    private func fileURL(forResourceName name: String) -> URL {
        directory.appendingPathComponent("\(name).txt")
    }

    func hasOverride(forResourceName name: String) -> Bool {
        FileManager.default.fileExists(atPath: fileURL(forResourceName: name).path)
    }

    func overrideData(forResourceName name: String) -> Data? {
        try? Data(contentsOf: fileURL(forResourceName: name))
    }

    func save(_ level: Level, resourceName: String) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let data = try LevelEncoder.encode(level)
        try data.write(to: fileURL(forResourceName: resourceName), options: .atomic)
    }

    func resetToOriginal(resourceName: String) throws {
        let url = fileURL(forResourceName: resourceName)
        guard FileManager.default.fileExists(atPath: url.path) else { return }
        try FileManager.default.removeItem(at: url)
    }
}

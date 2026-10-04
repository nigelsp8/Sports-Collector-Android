import Testing
import Foundation
@testable import Sports_Collector

struct LevelEditStoreTests {

    /// Each test gets its own throwaway root so saves never touch the real
    /// Documents directory or collide between tests.
    private func makeStore() -> (store: LevelEditStore, root: URL) {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        return (LevelEditStore(root: root), root)
    }

    @Test func overrideDataIsNilForANeverSavedResource() {
        let (store, _) = makeStore()
        #expect(store.hasOverride(forResourceName: "1") == false)
        #expect(store.overrideData(forResourceName: "1") == nil)
    }

    @Test func saveThenLoadRoundTrips() throws {
        let (store, _) = makeStore()
        let level = try LevelLoader.loadLevel(resourceName: "1", level: 1)

        try store.save(level, resourceName: "1")

        #expect(store.hasOverride(forResourceName: "1"))
        let data = try #require(store.overrideData(forResourceName: "1"))
        let reloaded = try LevelLoader.decodeLevel(from: data, level: 1)
        #expect(reloaded.id == level.id)
        #expect(reloaded.cells.count == level.cells.count)
    }

    @Test func resetToOriginalRemovesTheOverride() throws {
        let (store, _) = makeStore()
        let level = try LevelLoader.loadLevel(resourceName: "1", level: 1)
        try store.save(level, resourceName: "1")
        #expect(store.hasOverride(forResourceName: "1"))

        try store.resetToOriginal(resourceName: "1")

        #expect(store.hasOverride(forResourceName: "1") == false)
    }

    @Test func resetToOriginalIsANoOpWhenNoOverrideExists() throws {
        let (store, _) = makeStore()
        try store.resetToOriginal(resourceName: "1")
        #expect(store.hasOverride(forResourceName: "1") == false)
    }
}

import Foundation

/// The outer wrapper each bundled level file decodes to: a one-element JSON array
/// whose `levelXML` field is itself a JSON string requiring a second decode pass.
/// Other envelope fields (creator name, aggregate win/loss stats, etc.) came from a
/// now-defunct backend and are intentionally not decoded here.
struct LevelFileEnvelope: Codable {
    let levelID: String
    let levelXML: String
}

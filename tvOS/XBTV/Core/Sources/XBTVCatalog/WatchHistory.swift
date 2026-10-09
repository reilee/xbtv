import Foundation

public struct WatchRecord: Identifiable, Codable, Sendable {
    public var id: String { video.id }
    public let video: Video
    public let episode: Episode
    public let lineID: String
    public let lineTitle: String
    public let seconds: Double
    public let duration: Double
    public let updatedAt: Date
    public var resumeSeconds: Double {
        guard seconds.isFinite, seconds > 0, duration.isFinite,
              duration <= 0 || seconds < duration - 30 else { return 0 }
        return seconds
    }
    public init(video: Video, episode: Episode, lineID: String, lineTitle: String,
                seconds: Double, duration: Double, updatedAt: Date = .now) {
        self.video = video; self.episode = episode; self.lineID = lineID; self.lineTitle = lineTitle
        self.seconds = seconds.isFinite ? max(0, seconds) : 0
        self.duration = duration.isFinite ? max(0, duration) : 0; self.updatedAt = updatedAt
    }
}

/// One record per title, at most twelve; only playback progress writes a record.
public struct WatchHistory: Sendable {
    public private(set) var records: [WatchRecord]
    public init(records: [WatchRecord] = []) {
        var seen = Set<String>()
        self.records = Array(records.sorted { $0.updatedAt > $1.updatedAt }.filter { seen.insert($0.id).inserted }.prefix(12))
    }
    public mutating func upsert(_ record: WatchRecord) {
        records.removeAll { $0.id == record.id }; records.insert(record, at: 0)
        records = Array(records.prefix(12))
    }
    public mutating func remove(id: String) { records.removeAll { $0.id == id } }
}

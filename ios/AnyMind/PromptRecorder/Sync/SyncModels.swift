import Foundation

struct SyncChange: Codable {
    let id: String
    let content: String
    let systemTags: [String]
    let userTags: [String]
    let createdAt: String
    let updatedAt: String
    let deleted: Bool
    let baseRev: Int64?

    enum CodingKeys: String, CodingKey {
        case id
        case content
        case systemTags = "system_tags"
        case userTags = "user_tags"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
        case deleted
        case baseRev = "base_rev"
    }
}

struct SyncPushRequest: Codable {
    let spaceId: String
    let spaceSecret: String
    let deviceId: String
    let changes: [SyncChange]

    enum CodingKeys: String, CodingKey {
        case spaceId = "space_id"
        case spaceSecret = "space_secret"
        case deviceId = "device_id"
        case changes
    }
}

struct SyncPushResult: Codable {
    let id: String
    let serverRev: Int64
    let serverUpdatedAt: String
    let conflict: Bool

    enum CodingKeys: String, CodingKey {
        case id
        case serverRev = "server_rev"
        case serverUpdatedAt = "server_updated_at"
        case conflict
    }
}

struct SyncPushResponse: Codable {
    let results: [SyncPushResult]
    let serverRevMax: Int64

    enum CodingKeys: String, CodingKey {
        case results
        case serverRevMax = "server_rev_max"
    }
}

struct SyncPullRequest: Codable {
    let spaceId: String
    let spaceSecret: String
    let sinceRev: Int64
    let limit: Int

    enum CodingKeys: String, CodingKey {
        case spaceId = "space_id"
        case spaceSecret = "space_secret"
        case sinceRev = "since_rev"
        case limit
    }
}

struct SyncPullChange: Codable {
    let id: String
    let content: String
    let systemTags: [String]
    let userTags: [String]
    let createdAt: String
    let updatedAt: String
    let deleted: Bool
    let serverRev: Int64
    let serverUpdatedAt: String

    enum CodingKeys: String, CodingKey {
        case id
        case content
        case systemTags = "system_tags"
        case userTags = "user_tags"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
        case deleted
        case serverRev = "server_rev"
        case serverUpdatedAt = "server_updated_at"
    }
}

struct SyncPullResponse: Codable {
    let changes: [SyncPullChange]
    let serverRevMax: Int64

    enum CodingKeys: String, CodingKey {
        case changes
        case serverRevMax = "server_rev_max"
    }
}

struct SpaceCreateRequest: Codable {
    let name: String?
}

struct SpaceCreateResponse: Codable {
    let spaceId: String
    let spaceSecret: String
    let createdAt: String

    enum CodingKeys: String, CodingKey {
        case spaceId = "space_id"
        case spaceSecret = "space_secret"
        case createdAt = "created_at"
    }
}

import Foundation

final class SyncClient {
    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func push(baseURL: URL, request: SyncPushRequest) async throws -> SyncPushResponse {
        try await post(path: "/sync/push", baseURL: baseURL, body: request)
    }

    func pull(baseURL: URL, request: SyncPullRequest) async throws -> SyncPullResponse {
        try await post(path: "/sync/pull", baseURL: baseURL, body: request)
    }

    func createSpace(baseURL: URL, name: String?) async throws -> SpaceCreateResponse {
        try await post(path: "/spaces", baseURL: baseURL, body: SpaceCreateRequest(name: name))
    }

    private func post<T: Codable, R: Codable>(path: String, baseURL: URL, body: T) async throws -> R {
        let url = baseURL.appendingPathComponent(path)
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)

        let (data, response) = try await session.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw SyncError.httpStatus(http.statusCode)
        }
        return try JSONDecoder().decode(R.self, from: data)
    }
}

enum SyncError: Error {
    case missingConfig
    case httpStatus(Int)
}

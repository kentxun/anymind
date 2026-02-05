import SwiftUI

struct SettingsView: View {
    @AppStorage("sync_enabled") private var syncEnabled: Bool = false
    @AppStorage("sync_server_url") private var serverURL: String = ""
    @AppStorage("sync_space_id") private var spaceId: String = ""
    @AppStorage("sync_space_secret") private var spaceSecret: String = ""
    @AppStorage("sync_device_id") private var deviceId: String = ""
    @AppStorage("sync_on_launch") private var syncOnLaunch: Bool = false
    @State private var createStatus: String = ""
    @State private var isCreating: Bool = false
    @State private var showSecret: Bool = false

    private let syncClient = SyncClient()

    var body: some View {
        Form {
            Toggle("Enable cloud sync", isOn: $syncEnabled)

            if syncEnabled {
                TextField("Server URL", text: $serverURL)
                TextField("Space ID", text: $spaceId)
                if showSecret {
                    TextField("Space Secret", text: $spaceSecret)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                } else {
                    SecureField("Space Secret", text: $spaceSecret)
                }
                Toggle("Show Space Secret", isOn: $showSecret)
                Toggle("Sync on launch", isOn: $syncOnLaunch)
                Button(isCreating ? "Creating…" : "Create New Space") {
                    createSpace()
                }
                .disabled(isCreating || serverURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                if !createStatus.isEmpty {
                    Text(createStatus)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                if !deviceId.isEmpty {
                    Text("Device ID: \(deviceId)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func createSpace() {
        let trimmed = serverURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let baseURL = URL(string: trimmed), !trimmed.isEmpty else {
            createStatus = "Invalid server URL"
            return
        }
        isCreating = true
        createStatus = "Creating space…"
        Task {
            do {
                let response = try await syncClient.createSpace(baseURL: baseURL, name: nil)
                await MainActor.run {
                    spaceId = response.spaceId
                    spaceSecret = response.spaceSecret
                    createStatus = "Space created at \(response.createdAt)"
                    isCreating = false
                }
            } catch {
                await MainActor.run {
                    createStatus = "Create failed: \(error)"
                    isCreating = false
                }
            }
        }
    }
}

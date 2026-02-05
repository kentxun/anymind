import SwiftUI

struct RecordEditorView: View {
    let record: Record
    let onSave: (String, String) -> Void

    @State private var content: String = ""
    @State private var lastSavedContent: String = ""
    @State private var saveStatus: String = ""
    @State private var clearStatusTask: Task<Void, Never>? = nil
    @EnvironmentObject private var store: RecordStore
    private let autoSaveTimer = Timer.publish(every: 30, on: .main, in: .common).autoconnect()

    var body: some View {
        let parsedTags = TagParser.splitTags(TagParser.extractTags(from: content))
        let allTags = Array(Set(parsedTags.system + parsedTags.user + record.systemTags + record.userTags)).sorted()
        let syncIcon = SyncStatus.icon(
            updatedAt: record.updatedAt,
            lastSyncAt: record.lastSyncAt,
            syncEnabled: record.syncEnabled
        )
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Updated: \(DateFormatters.shortDateTime.string(from: record.updatedAt))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Image(systemName: syncIcon.name)
                    .font(.caption)
                    .foregroundStyle(syncIcon.color)
                    .help(syncIcon.label)
                Spacer()
                if !saveStatus.isEmpty {
                    Label(saveStatus, systemImage: saveStatus == "Saved" || saveStatus == "Auto-saved" ? "checkmark.circle.fill" : "info.circle")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Button("Save") {
                    saveIfNeeded(reason: .manual)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }

            Toggle(isOn: Binding(
                get: { record.syncEnabled },
                set: { enabled in
                    store.setSyncEnabled(for: record, enabled: enabled)
                }
            )) {
                HStack(spacing: 6) {
                    Image(systemName: "icloud")
                        .font(.caption)
                    Text("Sync to cloud")
                    Text(record.syncEnabled ? "On" : "Off")
                        .foregroundStyle(.secondary)
                }
            }
            .font(.caption)
            .toggleStyle(.checkbox)
            .controlSize(.small)

            VStack(alignment: .leading, spacing: 6) {
                Text("Content")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.secondary.opacity(0.08))
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.secondary.opacity(0.2), lineWidth: 1)
                        )
                    TextEditor(text: $content)
                        .font(.body)
                        .scrollContentBackground(.hidden)
                        .padding(8)
                }
            }

            TagSummaryView(
                tags: allTags,
                onTagTap: { tag in
                    store.applyTagFilter(tag)
                }
            )
        }
        .padding()
        .onAppear {
            content = record.content
            lastSavedContent = record.content
        }
        .onChange(of: record.id) { _ in
            content = record.content
            lastSavedContent = record.content
            saveStatus = ""
        }
        .onReceive(autoSaveTimer) { _ in
            saveIfNeeded(reason: .auto)
        }
        .focusedValue(\.saveAction, { saveIfNeeded(reason: .manual) })
        .onDisappear {
            saveIfNeeded(reason: .switching)
            clearStatusTask?.cancel()
            clearStatusTask = nil
        }
    }
}

struct TagSummaryView: View {
    let tags: [String]
    let onTagTap: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !tags.isEmpty {
                TagSectionView(
                    title: "Tags",
                    tags: tags,
                    accent: .blue,
                    onTagTap: onTagTap
                )
            }
        }
    }
}

private enum SaveReason {
    case manual
    case auto
    case switching
}

private extension RecordEditorView {
    func saveIfNeeded(reason: SaveReason) {
        let dirty = content != lastSavedContent
        guard dirty else {
            if reason == .manual {
                showSaveStatus("No changes")
            }
            return
        }
        onSave(record.id, content)
        lastSavedContent = content
        switch reason {
        case .auto:
            showSaveStatus("Auto-saved")
        case .manual:
            showSaveStatus("Saved")
        case .switching:
            break
        }
    }

    func showSaveStatus(_ message: String) {
        saveStatus = message
        clearStatusTask?.cancel()
        clearStatusTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            if saveStatus == message {
                saveStatus = ""
            }
        }
    }
}

struct TagSectionView: View {
    let title: String
    let tags: [String]
    let accent: Color
    let onTagTap: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(tags, id: \.self) { tag in
                        Button {
                            onTagTap(tag)
                        } label: {
                            TagChipView(tag: tag, accent: accent, size: .regular)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

import SwiftUI

struct ContentView: View {
    @StateObject private var store = RecordStore()
    @State private var showDateFilter = false
    @State private var showSettings = false
    @State private var path: [String] = []

    var body: some View {
        NavigationStack(path: $path) {
            List {
                Section {
                    TagFilterPanel()
                        .environmentObject(store)
                } header: {
                    Text("Search tags")
                }

                Section {
                    ForEach(store.records) { record in
                        NavigationLink(value: record.id) {
                            RecordRowView(record: record)
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                store.deleteRecord(id: record.id)
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                    }
                } header: {
                    HStack {
                        Text("History")
                        Spacer()
                        Text("\(store.records.count)")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("AnyMind")
            .searchable(text: $store.searchText, placement: .navigationBarDrawer(displayMode: .automatic), prompt: "Search content")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        showDateFilter = true
                    } label: {
                        Label("Dates", systemImage: "calendar")
                    }
                }
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        showSettings = true
                    } label: {
                        Image(systemName: "gearshape")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        store.syncNow()
                    } label: {
                        Image(systemName: "arrow.triangle.2.circlepath")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        store.createRecord()
                        if let id = store.selectedRecordId {
                            path.append(id)
                        }
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showDateFilter) {
                DateFilterSheet(
                    groupingMode: $store.groupingMode,
                    selectedGroupKey: $store.selectedGroupKey,
                    groups: store.groups
                )
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
            }
            .sheet(isPresented: $showSettings) {
                NavigationStack {
                    SettingsView()
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                Button("Done") {
                                    showSettings = false
                                }
                            }
                        }
                }
            }
            .navigationDestination(for: String.self) { id in
                RecordDetailView(recordId: id)
                    .environmentObject(store)
            }
        }
        .onChange(of: store.searchText) { _ in
            store.reloadRecords()
        }
        .onChange(of: store.tagFilterMode) { _ in
            store.reloadRecords()
        }
        .onChange(of: store.selectedGroupKey) { _ in
            store.reloadRecords()
        }
        .onChange(of: store.groupingMode) { _ in
            store.selectedGroupKey = nil
            store.reloadAll()
        }
    }
}

struct TagFilterPanel: View {
    @EnvironmentObject private var store: RecordStore

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            TextField("Search tags", text: $store.tagSearchText)
                .textFieldStyle(.roundedBorder)

            if !store.selectedTags.isEmpty {
                HStack {
                    Text("Selected")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Spacer()
                    if store.selectedTags.count > 1 {
                        Picker("Mode", selection: $store.tagFilterMode) {
                            ForEach(TagFilterMode.allCases) { mode in
                                Text(mode.title).tag(mode)
                            }
                        }
                        .pickerStyle(.segmented)
                        .frame(maxWidth: 160)
                    }
                    Button("Clear") {
                        store.clearTags()
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                }

                let columns = [GridItem(.adaptive(minimum: 120), spacing: 8)]
                LazyVGrid(columns: columns, alignment: .leading, spacing: 8) {
                    ForEach(store.selectedTags, id: \.self) { tag in
                        HStack(spacing: 4) {
                            TagChipView(tag: tag, accent: .blue)
                            Button {
                                store.removeTag(tag)
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }

            Divider()
                .opacity(0.4)

            TagListSectionView(
                title: "Tags",
                tags: store.filteredTagSummaries(),
                accent: .blue,
                onAppendTag: { tag in
                    store.applyTagFilter(tag)
                }
            )
        }
        .padding(.vertical, 4)
    }
}

struct TagListSectionView: View {
    let title: String
    let tags: [TagSummary]
    let accent: Color
    let onAppendTag: (String) -> Void
    @State private var showAll: Bool = false
    private let maxVisible: Int = 18

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Text(title)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text("\(tags.count)")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Spacer()
                if tags.count > maxVisible {
                    Button(showAll ? "Less" : "More") {
                        showAll.toggle()
                    }
                    .font(.caption2)
                    .buttonStyle(.borderless)
                }
            }
            if !tags.isEmpty {
                let visibleTags = showAll ? tags : Array(tags.prefix(maxVisible))
                if showAll {
                    ScrollView(.vertical) {
                        FlowLayout(spacing: 4, lineSpacing: 4) {
                            ForEach(visibleTags) { tag in
                                Button {
                                    onAppendTag(tag.name)
                                } label: {
                                    HStack(spacing: 4) {
                                        TagChipView(tag: tag.name, accent: accent, size: .regular)
                                        Text("\(tag.count)")
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.vertical, 2)
                    }
                    .frame(maxHeight: 220)
                } else {
                    FlowLayout(spacing: 4, lineSpacing: 4) {
                        ForEach(visibleTags) { tag in
                            Button {
                                onAppendTag(tag.name)
                            } label: {
                                HStack(spacing: 4) {
                                    TagChipView(tag: tag.name, accent: accent, size: .regular)
                                    Text("\(tag.count)")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.vertical, 2)
                }
            } else {
                Text("No tags yet.")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

struct DateFilterSheet: View {
    @Binding var groupingMode: GroupingMode
    @Binding var selectedGroupKey: String?
    let groups: [GroupSummary]
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("Grouping", selection: $groupingMode) {
                        ForEach(GroupingMode.allCases) { mode in
                            Text(mode.title).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)
                } header: {
                    Text("Grouping")
                }

                Section {
                    Button {
                        selectedGroupKey = nil
                        dismiss()
                    } label: {
                        HStack {
                            Text("All")
                            Spacer()
                            if selectedGroupKey == nil {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(.blue)
                            }
                        }
                    }

                    ForEach(groups) { group in
                        Button {
                            selectedGroupKey = group.id
                            dismiss()
                        } label: {
                            HStack {
                                Text(group.id)
                                Spacer()
                                Text("\(group.count)")
                                    .foregroundStyle(.secondary)
                                if selectedGroupKey == group.id {
                                    Image(systemName: "checkmark")
                                        .foregroundStyle(.blue)
                                }
                            }
                        }
                    }
                } header: {
                    Text("Dates")
                }
            }
            .navigationTitle("Filter Dates")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
    }
}

struct RecordDetailView: View {
    @EnvironmentObject private var store: RecordStore
    let recordId: String

    var body: some View {
        Group {
            if let record = store.selectedRecord, record.id == recordId {
                RecordEditorView(record: record) { id, content in
                    store.saveRecord(id: id, content: content)
                }
            } else {
                ProgressView("Loading…")
            }
        }
        .navigationTitle("Record")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            store.selectRecord(id: recordId)
        }
    }
}

struct FlowLayout: Layout {
    let spacing: CGFloat
    let lineSpacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var size = CGSize.zero
        var lineWidth: CGFloat = 0
        var lineHeight: CGFloat = 0

        for subview in subviews {
            let subviewSize = subview.sizeThatFits(.unspecified)
            if lineWidth + subviewSize.width > maxWidth {
                size.width = max(size.width, lineWidth)
                size.height += lineHeight + lineSpacing
                lineWidth = subviewSize.width + spacing
                lineHeight = subviewSize.height
            } else {
                lineWidth += subviewSize.width + spacing
                lineHeight = max(lineHeight, subviewSize.height)
            }
        }

        size.width = max(size.width, lineWidth)
        size.height += lineHeight
        return size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var lineHeight: CGFloat = 0

        for subview in subviews {
            let subviewSize = subview.sizeThatFits(.unspecified)
            if x + subviewSize.width > bounds.maxX {
                x = bounds.minX
                y += lineHeight + lineSpacing
                lineHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(width: subviewSize.width, height: subviewSize.height))
            x += subviewSize.width + spacing
            lineHeight = max(lineHeight, subviewSize.height)
        }
    }
}

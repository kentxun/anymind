import SwiftUI

struct ContentView: View {
    @StateObject private var store = RecordStore()
    @State private var splitVisibility: NavigationSplitViewVisibility = .doubleColumn

    private var sidebarIdealWidth: CGFloat { 300 }
    private var contentIdealWidth: CGFloat { splitVisibility == .all ? 300 : 500 }
    private var detailIdealWidth: CGFloat { splitVisibility == .all ? 400 : 500 }

    var body: some View {
        NavigationSplitView(columnVisibility: $splitVisibility) {
            GroupSidebarView(
                groupingMode: $store.groupingMode,
                selectedGroupKey: $store.selectedGroupKey,
                groups: store.groups
            )
            .navigationSplitViewColumnWidth(min: 200, ideal: sidebarIdealWidth, max: 360)
            .onChange(of: store.selectedGroupKey) { _ in
                store.selectRecord(id: nil)
                store.reloadRecords()
            }
            .onChange(of: store.groupingMode) { _ in
                store.selectedGroupKey = nil
                store.reloadAll()
            }
        } content: {
            VStack(spacing: 0) {
                TagFilterBar(
                    selectedTags: store.selectedTags,
                    tagFilterMode: $store.tagFilterMode,
                    tagSearchText: $store.tagSearchText,
                    tags: store.filteredTagSummaries(),
                    onRemoveTag: { store.removeTag($0) },
                    onClearTags: { store.clearTags() },
                    onAppendTag: { tag in
                        if !store.selectedTags.contains(tag) {
                            store.selectedTags.append(tag)
                            store.selectedTags.sort()
                            store.reloadRecords()
                        }
                    }
                )
                .padding(.horizontal)
                .padding(.top, 8)

                List(selection: $store.selectedRecordId) {
                    ForEach(store.records) { record in
                        RecordRowView(record: record)
                            .tag(record.id)
                    }
                }
            }
            .navigationSplitViewColumnWidth(min: 260, ideal: contentIdealWidth, max: 520)
            .searchable(text: $store.searchText, placement: .toolbar, prompt: "Search content")
            .onChange(of: store.searchText) { _ in
                store.reloadRecords()
            }
            .onChange(of: store.tagFilterMode) { _ in
                store.reloadRecords()
            }
            .onChange(of: store.selectedRecordId) { id in
                store.selectRecord(id: id)
            }
        } detail: {
            if let record = store.selectedRecord {
                RecordEditorView(record: record) { id, content in
                    store.saveRecord(id: id, content: content)
                }
                .id(record.id)
                .environmentObject(store)
            } else {
                ContentUnavailableView("No record selected", systemImage: "doc.text")
            }
        }
        .navigationSplitViewColumnWidth(min: 320, ideal: detailIdealWidth, max: 900)
        .toolbar {
            ToolbarItemGroup {
                Button {
                    store.syncNow()
                } label: {
                    Label("Sync", systemImage: "arrow.triangle.2.circlepath")
                }

                Button {
                    store.createRecord()
                } label: {
                    Label("New", systemImage: "plus")
                }

                Button {
                    store.deleteSelectedRecord()
                } label: {
                    Label("Delete", systemImage: "trash")
                }
                .disabled(store.selectedRecordId == nil)
            }
        }
        .overlay(alignment: .bottomLeading) {
            if !store.syncStatus.isEmpty {
                Text(store.syncStatus)
                    .font(.caption)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(.ultraThinMaterial)
                    .cornerRadius(8)
                    .padding()
            }
        }
    }
}

struct TagFilterBar: View {
    let selectedTags: [String]
    @Binding var tagFilterMode: TagFilterMode
    @Binding var tagSearchText: String
    let tags: [TagSummary]
    let onRemoveTag: (String) -> Void
    let onClearTags: () -> Void
    let onAppendTag: (String) -> Void

    var body: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    TextField("Search tags", text: $tagSearchText)
                        .textFieldStyle(.roundedBorder)
                    Text("\(tags.count)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                if !selectedTags.isEmpty {
                    HStack {
                        Text("Selected")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Spacer()
                        if selectedTags.count > 1 {
                            Menu {
                                Button("AND") { tagFilterMode = .and }
                                Button("OR") { tagFilterMode = .or }
                            } label: {
                                HStack(spacing: 4) {
                                    Text(tagFilterMode.title)
                                    Image(systemName: "chevron.down")
                                }
                                .font(.caption)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color.secondary.opacity(0.12))
                                .clipShape(Capsule())
                            }
                            .menuStyle(.borderlessButton)
                        }
                        Button("Clear") {
                            onClearTags()
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                        .disabled(selectedTags.isEmpty)
                    }

                    let columns = [GridItem(.adaptive(minimum: 120), spacing: 8)]
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 8) {
                        ForEach(selectedTags, id: \.self) { tag in
                            HStack(spacing: 4) {
                                TagChipView(tag: tag, accent: .blue)
                                Button {
                                    onRemoveTag(tag)
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

                TagListSectionView(title: "Tags", tags: tags, accent: .blue, onAppendTag: onAppendTag)
            }
            .padding(.vertical, 2)
        } label: {
            Label("Tag Filters", systemImage: "line.3.horizontal.decrease.circle")
                .font(.caption)
        }
    }
}

struct GroupSidebarView: View {
    @Binding var groupingMode: GroupingMode
    @Binding var selectedGroupKey: String?
    let groups: [GroupSummary]
    @State private var showGroups: Bool = true

    var body: some View {
        List(selection: $selectedGroupKey) {
            HStack(spacing: 8) {
                Button {
                    showGroups.toggle()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: showGroups ? "chevron.down" : "chevron.right")
                            .font(.caption2)
                        Text("Groups")
                    }
                }
                .buttonStyle(.plain)

                Picker("", selection: $groupingMode) {
                    ForEach(GroupingMode.allCases) { mode in
                        Text(mode.title).tag(mode)
                    }
                }
                .pickerStyle(.menu)
                .labelsHidden()

                Spacer()
            }
            .selectionDisabled(true)

            if showGroups {
                Text("All")
                    .tag(Optional<String>.none)
                ForEach(groups) { group in
                    HStack {
                        Text(group.id)
                        Spacer()
                        Text("\(group.count)")
                            .foregroundStyle(.secondary)
                    }
                    .tag(Optional(group.id))
                }
            }
        }
        .listStyle(.sidebar)
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

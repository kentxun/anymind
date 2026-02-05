# AnyMind (macOS)

Minimal SwiftUI macOS client (local-only MVP). Uses SQLite for storage and FTS5 for search.

## Open in Xcode
1. Open Xcode
2. File > Open... > select `Mac/PromptRecorder/PromptRecorder.xcodeproj`
3. Run the `AnyMind` target

## Notes
- Database location: `~/Library/Application Support/AnyMind/prompt.sqlite3`
- Search and tag filtering are performed locally.
- Sync: enable in Settings and use the toolbar Sync button.
  - Optional: enable "Sync on launch" to auto-sync when the app starts.
  - Use "Create New Space" to generate Space ID/Secret from the server.

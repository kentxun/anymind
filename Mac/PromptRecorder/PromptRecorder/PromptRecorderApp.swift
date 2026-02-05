import SwiftUI

@main
struct AnyMindApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        Settings {
            SettingsView()
        }
        .commands {
            SaveCommands()
        }
    }
}

private struct SaveActionKey: FocusedValueKey {
    typealias Value = () -> Void
}

extension FocusedValues {
    var saveAction: (() -> Void)? {
        get { self[SaveActionKey.self] }
        set { self[SaveActionKey.self] = newValue }
    }
}

private struct SaveCommands: Commands {
    @FocusedValue(\.saveAction) private var saveAction

    var body: some Commands {
        CommandGroup(replacing: .saveItem) {
            Button("Save") {
                saveAction?()
            }
            .keyboardShortcut("s", modifiers: [.command])
            .disabled(saveAction == nil)
        }
    }
}

import Foundation

enum AppPaths {
    static var appSupportDirectory: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let newDir = base.appendingPathComponent("AnyMind", isDirectory: true)
        let legacyDir = base.appendingPathComponent("PromptRecorder", isDirectory: true)
        var dir = newDir

        if !FileManager.default.fileExists(atPath: newDir.path) {
            if FileManager.default.fileExists(atPath: legacyDir.path) {
                do {
                    try FileManager.default.moveItem(at: legacyDir, to: newDir)
                } catch {
                    dir = legacyDir
                }
            } else {
                try? FileManager.default.createDirectory(at: newDir, withIntermediateDirectories: true)
            }
        }
        return dir
    }

    static var databaseURL: URL {
        appSupportDirectory.appendingPathComponent("prompt.sqlite3")
    }
}

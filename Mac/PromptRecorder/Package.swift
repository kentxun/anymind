// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "PromptRecorder",
    platforms: [
        .macOS(.v14)
    ],
    products: [
        .executable(name: "PromptRecorder", targets: ["PromptRecorder"])
    ],
    dependencies: [],
    targets: [
        .executableTarget(
            name: "PromptRecorder",
            dependencies: [],
            path: "Sources/PromptRecorder",
            linkerSettings: [
                .linkedFramework("SwiftUI"),
                .linkedFramework("AppKit"),
                .linkedLibrary("sqlite3")
            ]
        )
    ]
)

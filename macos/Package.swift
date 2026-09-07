// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "SyncItMac",
    platforms: [.macOS(.v13)],
    targets: [
        .executableTarget(name: "SyncItMac", path: "Sources")
    ]
)

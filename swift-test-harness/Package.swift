// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "SwiftTestHarness",
    platforms: [
        .macOS(.v15),
    ],
    dependencies: [
        .package(name: "BTreeKotlin", path: "../build/SPMPackage/macosArm64/Debug")
    ],
    targets: [
        .testTarget(
            name: "SwiftTestHarnessTests",
            dependencies: [
                .product(name: "BTreeKotlinLibrary", package: "BTreeKotlin")
            ],
            linkerSettings: [
                .unsafeFlags([
                    "-L", "../build/swift-test",
                    "-lBTreeKotlin",
                ]),
            ]
        ),
    ]
)
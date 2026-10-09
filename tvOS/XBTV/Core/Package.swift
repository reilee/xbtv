// swift-tools-version: 6.0
import PackageDescription
let package = Package(
    name: "XBTVCatalog",
    platforms: [.macOS(.v13), .tvOS(.v18)],
    products: [.library(name: "XBTVCatalog", targets: ["XBTVCatalog"]),
               .executable(name: "catalog-smoke", targets: ["CatalogSmoke"])],
    dependencies: [.package(url: "https://github.com/scinfu/SwiftSoup.git", exact: "2.11.3")],
    targets: [
        .target(name: "XBTVCatalog", dependencies: [.product(name: "SwiftSoup", package: "SwiftSoup")]),
        .executableTarget(name: "CatalogSmoke", dependencies: ["XBTVCatalog"]),
        .testTarget(name: "XBTVCatalogTests", dependencies: ["XBTVCatalog"])
    ]
)

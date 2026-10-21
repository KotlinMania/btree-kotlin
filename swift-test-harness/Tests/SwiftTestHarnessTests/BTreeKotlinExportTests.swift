#if canImport(Testing)
import Testing
import BTreeKotlin

@Suite("BTreeKotlin Swift Export Suite")
struct BTreeKotlinExportTests {
    @Test("Swift module loads cleanly")
    func swiftModuleLoads() {
        #expect(Bool(true), "BTreeKotlin swift module imported cleanly")
    }

    @Test("Bridge singletons are accessible")
    func bridgeSingletons() {
        _ = CopiedMapExtend.shared
        _ = CopiedSetExtend.shared
        _ = MutableMapIntoIter.shared
        _ = SharedMapIntoIter.shared
        _ = SharedSetIntoIter.shared
        #expect(Bool(true), "Singletons accessed without runtime trap")
    }
}
#elseif canImport(XCTest)
import XCTest
import BTreeKotlin

final class BTreeKotlinExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "BTreeKotlin swift module imported cleanly")
    }

    func testBridgeSingletons() {
        _ = CopiedMapExtend.shared
        _ = CopiedSetExtend.shared
        _ = MutableMapIntoIter.shared
        _ = SharedMapIntoIter.shared
        _ = SharedSetIntoIter.shared
        XCTAssertTrue(true, "Singletons accessed without runtime trap")
    }
}
#endif

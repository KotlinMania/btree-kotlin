#if canImport(Testing)
import Testing
import BTreeKotlin

@Suite("BTreeKotlin Swift Export Suite")
struct BTreeKotlinExportTests {
    @Test("Swift module loads cleanly")
    func swiftModuleLoads() {
        #expect(Bool(true), "BTreeKotlin swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import BTreeKotlin

final class BTreeKotlinExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "BTreeKotlin swift module imported cleanly")
    }
}
#endif

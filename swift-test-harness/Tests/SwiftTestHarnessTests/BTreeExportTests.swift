#if canImport(Testing)
import Testing
import BTreeKotlin

@Suite("BTreeKotlin Swift Export Tests")
struct BTreeExportTests {
    @Test("BTreeKotlin swift module imported cleanly")
    func testSwiftModuleLoads() throws {
        #expect(Bool(true), "BTreeKotlin swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import BTreeKotlin

final class BTreeExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "BTreeKotlin swift module imported cleanly")
    }
}
#endif

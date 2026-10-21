#if canImport(Testing)
import Testing
import BTreeKotlin

@Suite("BTreeKotlin Swift Export Tests")
struct BTreeExportTests {
    @Test("BTreeKotlin swift module imported cleanly")
    func testSwiftModuleLoads() throws {
        #expect(Bool(true), "BTreeKotlin swift module imported cleanly")
    }

    @Test("OneSidedRangeBound enum cases, raw values, and descriptions")
    func testOneSidedRangeBoundCases() throws {
        #expect(OneSidedRangeBound.allCases.count == 3)
        #expect(OneSidedRangeBound.StartInclusive.rawValue == 0)
        #expect(OneSidedRangeBound.StartInclusive.description == "StartInclusive")
        #expect(OneSidedRangeBound.End.rawValue == 1)
        #expect(OneSidedRangeBound.End.description == "End")
        #expect(OneSidedRangeBound.EndInclusive.rawValue == 2)
        #expect(OneSidedRangeBound.EndInclusive.description == "EndInclusive")

        #expect(OneSidedRangeBound("StartInclusive") == .StartInclusive)
        #expect(OneSidedRangeBound("End") == .End)
        #expect(OneSidedRangeBound("EndInclusive") == .EndInclusive)
        #expect(OneSidedRangeBound("Unknown") == nil)

        #expect(OneSidedRangeBound(rawValue: 0) == .StartInclusive)
        #expect(OneSidedRangeBound(rawValue: 1) == .End)
        #expect(OneSidedRangeBound(rawValue: 2) == .EndInclusive)
        #expect(OneSidedRangeBound(rawValue: 99) == nil)
    }
}
#elseif canImport(XCTest)
import XCTest
import BTreeKotlin

final class BTreeExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "BTreeKotlin swift module imported cleanly")
    }

    func testOneSidedRangeBoundCases() throws {
        XCTAssertEqual(OneSidedRangeBound.allCases.count, 3)
        XCTAssertEqual(OneSidedRangeBound.StartInclusive.rawValue, 0)
        XCTAssertEqual(OneSidedRangeBound.StartInclusive.description, "StartInclusive")
        XCTAssertEqual(OneSidedRangeBound.End.rawValue, 1)
        XCTAssertEqual(OneSidedRangeBound.End.description, "End")
        XCTAssertEqual(OneSidedRangeBound.EndInclusive.rawValue, 2)
        XCTAssertEqual(OneSidedRangeBound.EndInclusive.description, "EndInclusive")

        XCTAssertEqual(OneSidedRangeBound("StartInclusive"), .StartInclusive)
        XCTAssertEqual(OneSidedRangeBound("End"), .End)
        XCTAssertEqual(OneSidedRangeBound("EndInclusive"), .EndInclusive)
        XCTAssertNil(OneSidedRangeBound("Unknown"))

        XCTAssertEqual(OneSidedRangeBound(rawValue: 0), .StartInclusive)
        XCTAssertEqual(OneSidedRangeBound(rawValue: 1), .End)
        XCTAssertEqual(OneSidedRangeBound(rawValue: 2), .EndInclusive)
        XCTAssertNil(OneSidedRangeBound(rawValue: 99))
    }
}
#endif

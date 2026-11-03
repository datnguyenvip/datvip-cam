import XCTest
import StoreKitTest

@MainActor
final class AdFreeStoreTests: XCTestCase {
    func testVerifiedNonConsumableSurvivesRecreationAndRefundRevokesIt() async throws {
        let session = try SKTestSession(configurationFileNamed: "RemoveAds")
        session.disableDialogs = true
        session.clearTransactions()
        defer { session.clearTransactions() }
        let store = AdFreeStore()
        await store.refresh()
        XCTAssertFalse(store.owned)
        let transaction = try await session.buyProduct(identifier: AdFreeStore.productID)
        await store.refresh()
        XCTAssertTrue(store.owned)
        let reopened = AdFreeStore()
        await reopened.refresh()
        XCTAssertTrue(reopened.owned)
        try session.refundTransaction(identifier: transaction.identifier)
        await reopened.refresh()
        XCTAssertFalse(reopened.owned)
    }
    func testPendingPaymentDoesNotRemoveAds() async throws {
        let session = try SKTestSession(configurationFileNamed: "RemoveAds")
        session.disableDialogs = true
        session.clearTransactions()
        session.askToBuyEnabled = true
        defer { session.askToBuyEnabled = false; session.clearTransactions() }
        let store = AdFreeStore()
        await store.loadProduct()
        XCTAssertNotNil(store.product)
        await store.buy()
        await store.refresh()
        XCTAssertFalse(store.owned)
    }
}

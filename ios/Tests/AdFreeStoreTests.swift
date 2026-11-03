import XCTest
import StoreKitTest
@testable import DatvipCam

@MainActor
@available(iOS 17.0, *)
final class AdFreeStoreTests: XCTestCase {
    private func waitForOwnership(_ expected: Bool, store: AdFreeStore) async throws {
        // StoreKit's test server publishes entitlement changes asynchronously.
        for _ in 0..<40 {
            await store.refresh()
            if store.owned == expected { return }
            try await Task.sleep(nanoseconds: 250_000_000)
        }
        XCTAssertEqual(store.owned, expected)
    }
    func testVerifiedNonConsumableSurvivesRecreationAndRefundRevokesIt() async throws {
        let session = try SKTestSession(configurationFileNamed: "RemoveAds")
        session.resetToDefaultState()
        session.disableDialogs = true
        session.clearTransactions()
        defer { session.clearTransactions() }
        let store = AdFreeStore()
        await store.refresh()
        XCTAssertFalse(store.owned)
        let transaction = try await session.buyProduct(identifier: AdFreeStore.productID)
        try await waitForOwnership(true, store: store)
        let reopened = AdFreeStore()
        try await waitForOwnership(true, store: reopened)
        try session.refundTransaction(identifier: UInt(transaction.id))
        try await waitForOwnership(false, store: reopened)
    }
    func testPendingPaymentDoesNotRemoveAds() async throws {
        let session = try SKTestSession(configurationFileNamed: "RemoveAds")
        session.resetToDefaultState()
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

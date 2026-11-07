import SwiftUI
import StoreKit

@MainActor
final class AdFreeStore: ObservableObject {
    static let productID = "remove_ads"
    @Published private(set) var owned = false
    @Published private(set) var checked = false
    @Published private(set) var product: Product?
    @Published private(set) var busy = false
    @Published var message: String?
    private var updates: Task<Void, Never>?

    init() {
        updates = Task { [weak self] in
            for await result in Transaction.updates {
                guard let self = self else { return }
                if case .verified(let transaction) = result, transaction.productID == Self.productID {
                    await self.refresh()
                    await transaction.finish()
                }
            }
        }
        Task { await refresh(); await loadProduct() }
    }
    deinit { updates?.cancel() }
    func refresh() async {
        var entitled = false
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result,
               transaction.productID == Self.productID,
               transaction.productType == .nonConsumable,
               transaction.revocationDate == nil { entitled = true }
        }
        owned = entitled; checked = true
    }
    func loadProduct() async {
        do { product = try await Product.products(for: [Self.productID]).first }
        catch { product = nil }
    }
    func buy() async {
        guard !owned, !busy, let product = product else { return }
        busy = true; defer { busy = false }
        do {
            switch try await product.purchase() {
            case .success(let result):
                guard case .verified(let transaction) = result,
                      transaction.productID == Self.productID,
                      transaction.revocationDate == nil else { message = AppLanguage.text("Mua hàng không thành công"); return }
                await refresh(); await transaction.finish()
            case .pending: message = AppLanguage.text("Mua hàng đang chờ xác nhận")
            case .userCancelled: break
            @unknown default: break
            }
        } catch { message = AppLanguage.text("Mua hàng không thành công") }
    }
    func restore() async {
        guard !busy else { return }
        busy = true; defer { busy = false }
        do {
            try await AppStore.sync()
            await refresh(); await loadProduct()
            message = AppLanguage.text(owned ? "Đã khôi phục mua hàng" : "Không tìm thấy giao dịch bỏ quảng cáo")
        } catch { message = AppLanguage.text("Cửa hàng chưa sẵn sàng") }
    }
}

struct AdFreeSettingsSection: View {
    var body: some View {
        EmptyView()
    }
}
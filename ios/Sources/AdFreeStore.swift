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
    @EnvironmentObject private var store: AdFreeStore
    @State private var showPurchaseConfirmation = false

    var body: some View {
        Section(AppLanguage.text("Xóa quảng cáo")) {
            if store.owned {
                Text(AppLanguage.text("Quảng cáo đã được xóa"))
            } else {
                Button(AppLanguage.text("Mua Pro - Xóa quảng cáo")) {
                    showPurchaseConfirmation = true
                }
                .disabled(store.busy || store.product == nil)
            }

            Button(AppLanguage.text("Khôi phục mua hàng")) {
                Task { await store.restore() }
            }
            .disabled(store.busy)

            if store.busy {
                ProgressView()
            }
        }
        .task {
            await store.loadProduct()
        }
        .confirmationDialog(
            AppLanguage.text("Xóa quảng cáo"),
            isPresented: $showPurchaseConfirmation,
            titleVisibility: .visible
        ) {
            if let product = store.product {
                Button(
                    AppLanguage.text("Mua một lần: {0}", product.displayPrice)
                ) {
                    Task { await store.buy() }
                }
            }

            Button(AppLanguage.text("Hủy"), role: .cancel) {}
        } message: {
            Text(AppLanguage.text("Mua một lần để xóa quảng cáo vĩnh viễn."))
        }
        .alert(
            AppLanguage.text("Xóa quảng cáo"),
            isPresented: Binding(
                get: { store.message != nil },
                set: { if !$0 { store.message = nil } }
            )
        ) {
            Button(AppLanguage.text("Đóng"), role: .cancel) {
                store.message = nil
            }
        } message: {
            Text(store.message ?? "")
        }
    }
}
import SwiftUI

/// A single immutable payload drives both presentation and the activity controller's first render.
struct ShareRequest: Identifiable {
    let id = UUID()
    let items: [Any]

    init(files: [URL] = [], links: String) {
        var payload: [Any] = files.map { $0 as Any }
        payload += links.split(separator: "\n").compactMap { URL(string: String($0)) }.map { $0 as Any }
        items = payload
    }
}

private struct ExportDay: Identifiable {
    let date: Date
    let records: [PhotoRec]
    var id: Date { date }
    var title: String {
        if Calendar.current.isDateInToday(date) { return "Hôm nay" }
        if Calendar.current.isDateInYesterday(date) { return "Hôm qua" }
        let format = DateFormatter(); format.locale = Locale(identifier: "vi_VN"); format.dateFormat = "dd/MM/yyyy"
        return format.string(from: date)
    }
}

struct GalleryView: View {
    @Environment(\.dismiss) private var dismiss
    let model: CameraModel
    @State private var records = CameraModel.loadRecs().sorted { $0.time > $1.time }
    @State private var selection = Set<String>()
    @State private var last: PhotoRec?
    @State private var request: ShareRequest?
    @State private var preparing = false
    @State private var message: String?
    @State private var showLinks = false
    private let accent = Color(red: 0, green: 0.58, blue: 0.95)

    private var chosen: [PhotoRec] { records.filter { selection.contains($0.id) } }
    private var links: String { model.batchText(chosen) }
    private var days: [ExportDay] {
        Dictionary(grouping: records) { Calendar.current.startOfDay(for: $0.time) }
            .map { ExportDay(date: $0.key, records: $0.value) }.sorted { $0.date > $1.date }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                if records.isEmpty { Text("Chưa có ảnh/video. Hãy chụp hoặc quay trước.").foregroundColor(.secondary).padding(40) }
                ForEach(days) { day in
                    VStack(spacing: 8) {
                        Button {
                            let ids = Set(day.records.map { $0.id })
                            if ids.isSubset(of: selection) { selection.subtract(ids) } else { selection.formUnion(ids) }
                        } label: {
                            HStack {
                                Text("\(day.title) (\(day.records.count))").font(.headline).foregroundColor(.primary)
                                Spacer()
                                Image(systemName: Set(day.records.map { $0.id }).isSubset(of: selection) ? "checkmark.circle.fill" : "circle")
                                    .foregroundColor(accent).font(.title2)
                            }.padding(.horizontal).padding(.top, 12)
                        }.buttonStyle(.plain)
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 3), count: 3), spacing: 3) {
                            ForEach(day.records) { record in
                                Thumb(file: record.file, video: record.video == true).aspectRatio(1, contentMode: .fit)
                                    .overlay(alignment: .bottomLeading) {
                                        if record.video == true { Image(systemName: "play.circle.fill").font(.title2).foregroundColor(.white).shadow(radius: 2).padding(6) }
                                    }
                                    .overlay(alignment: .topTrailing) {
                                        Image(systemName: selection.contains(record.id) ? "checkmark.circle.fill" : "circle")
                                            .font(.title2).foregroundColor(selection.contains(record.id) ? accent : .white)
                                            .background(Circle().fill(Color.black.opacity(0.25))).padding(6)
                                    }
                                    .contentShape(Rectangle())
                                    .onTapGesture { if selection.contains(record.id) { selection.remove(record.id) } else { selection.insert(record.id) }; last = record }
                                    .accessibilityLabel(record.video == true ? "Video" : "Ảnh")
                                    .accessibilityAddTraits(selection.contains(record.id) ? [.isButton, .isSelected] : [.isButton])
                            }
                        }
                    }
                }
            }
            .navigationTitle("Xuất & Chia sẻ").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Đóng") { dismiss() } }
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        Button(selection.count == records.count ? "Bỏ chọn tất cả" : "Chọn tất cả") {
                            selection = selection.count == records.count ? [] : Set(records.map { $0.id })
                        }
                        Button("Chọn cùng vị trí") {
                            guard let ref = last else { return }
                            records.filter { $0.id == ref.id || CameraModel.near(ref, $0) }.forEach { selection.insert($0.id) }
                        }.disabled(last == nil)
                    } label: { Image(systemName: "ellipsis.circle") }
                }
            }
            .safeAreaInset(edge: .bottom) { exportActions }
            .disabled(preparing)
        }
        .sheet(item: $request) { payload in ShareSheet(items: payload.items) }
        .sheet(isPresented: $showLinks) { linksPanel }
        .alert("Xuất & Chia sẻ", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("Đóng", role: .cancel) { message = nil }
        } message: { Text(message ?? "") }
    }

    private var exportActions: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("\(chosen.count) ảnh/video đã chọn").font(.headline)
            if !selection.isEmpty {
                Text(links.isEmpty ? "Tệp đã chọn không có vị trí GPS." : "Link Maps sẽ kèm theo và được chép sẵn để dán nếu ứng dụng nhận chỉ lấy ảnh/video.")
                    .font(.caption).foregroundColor(.secondary)
            }
            if preparing { ProgressView("Đang chuẩn bị tệp…") }
            HStack(spacing: 12) {
                Button { prepareShare() } label: { Label("Chia sẻ", systemImage: "square.and.arrow.up").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).tint(accent).disabled(selection.isEmpty || preparing)
                Button { showLinks = true } label: { Label("Liên kết Maps", systemImage: "link").frame(maxWidth: .infinity) }
                    .buttonStyle(.bordered).disabled(links.isEmpty || preparing)
            }
        }.padding().frame(maxWidth: .infinity, alignment: .leading).background(.bar)
    }

    private var linksPanel: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(links).textSelection(.enabled).font(.callout)
                    Button("Sao chép link Maps") { UIPasteboard.general.string = links }.buttonStyle(.borderedProminent)
                    ShareLink(item: links) { Label("Chia sẻ link Maps", systemImage: "square.and.arrow.up") }.buttonStyle(.bordered)
                    Text("Nếu ứng dụng nhận chỉ gửi ảnh/video, dán link này vào cuộc trò chuyện sau khi gửi.").font(.caption).foregroundColor(.secondary)
                }.padding()
            }.navigationTitle("Liên kết Maps").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Xong") { showLinks = false } } }
        }.presentationDetents([.medium, .large])
    }

    private func prepareShare() {
        guard !preparing, !chosen.isEmpty else { return }
        // Freeze selection and links before any asynchronous work or sheet presentation.
        let selected = chosen
        let linkText = model.batchText(selected)
        preparing = true
        Task { @MainActor in
            let files = selected.map { CameraModel.photosDir.appendingPathComponent($0.file) }
            let valid = await Task.detached(priority: .userInitiated) {
                files.allSatisfy { url in
                    guard FileManager.default.isReadableFile(atPath: url.path),
                          let values = try? url.resourceValues(forKeys: [.fileSizeKey]) else { return false }
                    return (values.fileSize ?? 0) > 0
                }
            }.value
            preparing = false
            guard valid else { message = "Một tệp đã chọn không còn đọc được. Hãy bỏ chọn tệp đó rồi thử lại."; return }
            UIPasteboard.general.string = linkText
            request = ShareRequest(files: files, links: linkText)
        }
    }
}

import SwiftUI
import AVFoundation
import CoreImage
import CoreLocation
import CryptoKit
import ImageIO
import Photos
import UniformTypeIdentifiers

struct PhotoRec: Codable, Identifiable { var file: String; var time: Date; var lat: Double?; var lon: Double?; var video: Bool? = nil; var photosAssetID: String? = nil; var id: String { file } }

extension UIImage {
    func normalized() -> UIImage {
        if imageOrientation == .up { return self }
        let f = UIGraphicsImageRendererFormat(); f.scale = 1
        return UIGraphicsImageRenderer(size: size, format: f).image { _ in draw(in: CGRect(origin: .zero, size: size)) }
    }
    func cropped(to aspect: CGFloat) -> UIImage {
        let w = size.width, h = size.height
        var cw = w, ch = h
        if w / h > aspect { cw = h * aspect } else { ch = w / aspect }
        let r = CGRect(x: (w - cw) / 2, y: (h - ch) / 2, width: cw, height: ch)
        guard let cg = cgImage?.cropping(to: r) else { return self }
        return UIImage(cgImage: cg)
    }
}

final class CameraModel: NSObject, ObservableObject, CLLocationManagerDelegate, AVCapturePhotoCaptureDelegate,
                         AVCaptureVideoDataOutputSampleBufferDelegate, AVCaptureAudioDataOutputSampleBufferDelegate {
    private let shutterFeedback = ShutterFeedback()
    let session = AVCaptureSession()
    private let output = AVCapturePhotoOutput()
    private let lm = CLLocationManager()
    private let geocoder = CLGeocoder()
    private var lastGeo = Date.distantPast
    private var front = false
    private var shotOrient: UIDeviceOrientation = .portrait
    // quay video
    let videoOut = AVCaptureVideoDataOutput()
    let audioOut = AVCaptureAudioDataOutput()
    private let queue = DispatchQueue(label: "datvip.capture")
    private let ciContext = CIContext()
    private var writer: AVAssetWriter?
    private var videoInput: AVAssetWriterInput?
    private var audioInput: AVAssetWriterInput?
    private var adaptor: AVAssetWriterInputPixelBufferAdaptor?
    private var recording = false, recWriterReady = false
    private var recURL: URL?
    private var recStartDate = Date()
    private var recLoc: CLLocation?
    private var recCode = ""
    @Published private var previewVerificationCode = ""
    private var cropRect = CGRect.zero
    private var outSize = CGSize.zero
    private var overlayCI: CIImage?
    private var lastOverlay = Date.distantPast
    private var timer: Timer?
    @Published var videoMode = false
    @Published var isRecording = false
    @Published var recSeconds = 0
    @Published var recOrient: UIDeviceOrientation = .portrait
    var uiOrient: UIDeviceOrientation { isRecording ? recOrient : devOrient }
    var previewAspect: CGFloat = 3.0 / 4.0
    @Published var loc: CLLocation?
    @Published var heading: Double?
    @Published var autoAddr = ""
    @Published var devOrient: UIDeviceOrientation = .portrait

    func str(_ k: String) -> String { UserDefaults.standard.string(forKey: k) ?? "" }
    func flag(_ k: String) -> Bool { UserDefaults.standard.object(forKey: k) as? Bool ?? true }
    static var logoURL: URL { FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("logo.png") }
    static var photosDir: URL {
        let d = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("photos")
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }
    static var indexURL: URL { photosDir.appendingPathComponent("index.json") }
    static func loadRecs() -> [PhotoRec] {
        guard let d = try? Data(contentsOf: indexURL), let r = try? JSONDecoder().decode([PhotoRec].self, from: d) else { return [] }
        return r
    }

    @discardableResult
    static func deleteRecords(_ selected: [PhotoRec]) throws -> Int {
        let selectedIDs = Set(selected.map(\.id))
        guard !selectedIDs.isEmpty else { return 0 }

        let existing = loadRecs()
        let targets = existing.filter { selectedIDs.contains($0.id) }
        guard !targets.isEmpty else { return 0 }

        let fm = FileManager.default
        let directory = photosDir.standardizedFileURL
        let trash = directory.appendingPathComponent(
            ".delete-\(UUID().uuidString)",
            isDirectory: true
        )

        try fm.createDirectory(
            at: trash,
            withIntermediateDirectories: true
        )

        var moved: [(original: URL, temporary: URL)] = []
        var deletedIDs = Set<String>()

        do {
            for record in targets {
                let original = directory
                    .appendingPathComponent(record.file)
                    .standardizedFileURL

                guard original.deletingLastPathComponent() == directory else {
                    throw CocoaError(.fileReadNoPermission)
                }

                if fm.fileExists(atPath: original.path) {
                    let temporary = trash.appendingPathComponent(
                        UUID().uuidString
                    )

                    try fm.moveItem(
                        at: original,
                        to: temporary
                    )

                    moved.append((original, temporary))
                }

                deletedIDs.insert(record.id)
            }

            let remaining = existing.filter {
                !deletedIDs.contains($0.id)
            }

            let data = try JSONEncoder().encode(remaining)
            try data.write(to: indexURL, options: .atomic)

        } catch {
            for item in moved.reversed() {
                if fm.fileExists(atPath: item.temporary.path) {
                    try? fm.moveItem(
                        at: item.temporary,
                        to: item.original
                    )
                }
            }

            try? fm.removeItem(at: trash)
            throw error
        }

        try? fm.removeItem(at: trash)
        return deletedIDs.count
    }
    private static let photosIndexQueue =
        DispatchQueue(label: "com.datvip.cam.photos-index")

    @discardableResult
    static func appendPhotoRecord(_ record: PhotoRec) -> Bool {
        photosIndexQueue.sync {
            var records = loadRecs()

            guard !records.contains(where: { $0.id == record.id }) else {
                return false
            }

            records.insert(record, at: 0)

            guard let data = try? JSONEncoder().encode(records) else {
                return false
            }

            do {
                try data.write(to: indexURL, options: .atomic)
                return true
            } catch {
                return false
            }
        }
    }
    enum PhotosAssetStatus {
        case exists
        case missing
        case unknown
    }

    static func photosAssetStatus(
        for record: PhotoRec
    ) -> PhotosAssetStatus {
        guard let assetID = record.photosAssetID,
              !assetID.isEmpty else {
            return .unknown
        }

        let authorization = PHPhotoLibrary.authorizationStatus(
            for: .readWrite
        )

        guard authorization == .authorized else {
            return .unknown
        }

        let assets = PHAsset.fetchAssets(
            withLocalIdentifiers: [assetID],
            options: nil
        )

        return assets.count > 0 ? .exists : .missing
    }
    static func updatePhotosAssetID(
        file: String,
        assetID: String
    ) {
        photosIndexQueue.sync {
            var records = loadRecs()

            guard let index = records.firstIndex(
                where: { $0.file == file }
            ) else { return }

            records[index].photosAssetID = assetID

            guard let data = try? JSONEncoder().encode(records) else {
                return
            }

            try? data.write(to: indexURL, options: .atomic)
        }
    }
    override init() {
        super.init()
        UIDevice.current.beginGeneratingDeviceOrientationNotifications()
        NotificationCenter.default.addObserver(forName: UIDevice.orientationDidChangeNotification, object: nil, queue: .main) { [weak self] _ in
            let o = UIDevice.current.orientation
            if o == .portrait || o == .portraitUpsideDown || o == .landscapeLeft || o == .landscapeRight { self?.devOrient = o }
        }
    }

    // MARK: Camera
    func start() {
        AVCaptureDevice.requestAccess(for: .video) { ok in
            guard ok else { return }
            DispatchQueue.global().async { self.configure(.back); self.session.startRunning() }
        }
        lm.delegate = self
        lm.desiredAccuracy = kCLLocationAccuracyBest
        lm.requestWhenInUseAuthorization()
        lm.startUpdatingLocation()
        if CLLocationManager.headingAvailable() { lm.startUpdatingHeading() }
    }
    private func configure(_ pos: AVCaptureDevice.Position) {
        session.beginConfiguration()
        let preset: AVCaptureSession.Preset = videoMode ? .hd1920x1080 : .photo
        if session.canSetSessionPreset(preset) { session.sessionPreset = preset }
        session.inputs.forEach { session.removeInput($0) }
        if let d = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: pos),
           let i = try? AVCaptureDeviceInput(device: d), session.canAddInput(i) { session.addInput(i) }
        if videoMode, AVCaptureDevice.authorizationStatus(for: .audio) == .authorized,
           let a = AVCaptureDevice.default(for: .audio), let ai = try? AVCaptureDeviceInput(device: a), session.canAddInput(ai) { session.addInput(ai) }
        if !session.outputs.contains(output), session.canAddOutput(output) { session.addOutput(output) }
        if !session.outputs.contains(videoOut), session.canAddOutput(videoOut) {
            videoOut.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
            videoOut.alwaysDiscardsLateVideoFrames = true
            videoOut.setSampleBufferDelegate(self, queue: queue)
            session.addOutput(videoOut)
        }
        if !session.outputs.contains(audioOut), session.canAddOutput(audioOut) {
            audioOut.setSampleBufferDelegate(self, queue: queue)
            session.addOutput(audioOut)
        }
        session.commitConfiguration()
    }
    func flip() { front.toggle(); DispatchQueue.global().async { self.configure(self.front ? .front : .back) } }
    func shoot() {
        shotOrient = devOrient
        if let conn = output.connection(with: .video), conn.isVideoOrientationSupported {
            switch devOrient {
            case .landscapeLeft: conn.videoOrientation = .landscapeRight
            case .landscapeRight: conn.videoOrientation = .landscapeLeft
            case .portraitUpsideDown: conn.videoOrientation = .portraitUpsideDown
            default: conn.videoOrientation = .portrait
            }
        }
        output.capturePhoto(with: AVCapturePhotoSettings(), delegate: self)
    }

    // MARK: Quay video (dấu giờ, GPS được vẽ trực tiếp vào từng khung hình)
    func setVideoMode(_ on: Bool) {
        guard !isRecording, on != videoMode else { return }
        videoMode = on
        if on { AVCaptureDevice.requestAccess(for: .audio) { _ in self.reconfigure() } } else { reconfigure() }
    }
    private func reconfigure() { DispatchQueue.global().async { self.configure(self.front ? .front : .back) } }
    private func avOrientation(_ o: UIDeviceOrientation) -> AVCaptureVideoOrientation {
        switch o {
        case .landscapeLeft: return .landscapeRight
        case .landscapeRight: return .landscapeLeft
        case .portraitUpsideDown: return .portraitUpsideDown
        default: return .portrait
        }
    }
    func toggleRecord() { if isRecording { stopRecording() } else { startRecording() } }
    private func startRecording() {
        guard videoMode, !isRecording else { return }
        let o = devOrient, l = loc
        recOrient = o; isRecording = true; recSeconds = 0
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in self?.recSeconds += 1 }
        queue.async {
            if let conn = self.videoOut.connection(with: .video), conn.isVideoOrientationSupported { conn.videoOrientation = self.avOrientation(o) }
            self.recURL = FileManager.default.temporaryDirectory.appendingPathComponent("rec_\(UUID().uuidString).mp4")
            self.recStartDate = Date(); self.recLoc = l; self.recCode = self.code(self.recStartDate, l)
            let activeCode = self.recCode
            DispatchQueue.main.async { self.previewVerificationCode = activeCode }
            self.recWriterReady = false; self.overlayCI = nil; self.lastOverlay = .distantPast
            self.recording = true
        }
    }
    private func stopRecording() {
        timer?.invalidate(); isRecording = false
        queue.async {
            guard self.recording else { return }
            self.recording = false
            guard self.recWriterReady, let wr = self.writer, let url = self.recURL else { return }
            self.recWriterReady = false
            self.videoInput?.markAsFinished(); self.audioInput?.markAsFinished()
            let d = self.recStartDate, l = self.recLoc
            wr.finishWriting { self.finishVideo(url, d, l) }
        }
    }
    private func setupWriter(_ pb: CVPixelBuffer, _ pts: CMTime) -> Bool {
        let w = CGFloat(CVPixelBufferGetWidth(pb)), h = CGFloat(CVPixelBufferGetHeight(pb))
        let aspect: CGFloat = w > h ? 4.0 / 3.0 : 3.0 / 4.0
        var cw = w, ch = h
        if cw / ch > aspect { cw = ch * aspect } else { ch = cw / aspect }
        let ow = CGFloat(Int(cw) & ~1), oh = CGFloat(Int(ch) & ~1)
        cropRect = CGRect(x: ((w - ow) / 2).rounded(), y: ((h - oh) / 2).rounded(), width: ow, height: oh)
        outSize = CGSize(width: ow, height: oh)
        guard let url = recURL, let wr = try? AVAssetWriter(outputURL: url, fileType: .mp4) else { return false }
        let vi = AVAssetWriterInput(mediaType: .video, outputSettings: [
            AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: Int(ow), AVVideoHeightKey: Int(oh),
            AVVideoCompressionPropertiesKey: [AVVideoAverageBitRateKey: 6_000_000]])
        vi.expectsMediaDataInRealTime = true
        let ad = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: vi, sourcePixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String: Int(ow), kCVPixelBufferHeightKey as String: Int(oh)])
        guard wr.canAdd(vi) else { return false }
        wr.add(vi)
        var ai: AVAssetWriterInput?
        if session.inputs.contains(where: { ($0 as? AVCaptureDeviceInput)?.device.hasMediaType(.audio) == true }) {
            let a = AVAssetWriterInput(mediaType: .audio, outputSettings: [AVFormatIDKey: kAudioFormatMPEG4AAC, AVNumberOfChannelsKey: 1, AVSampleRateKey: 44100, AVEncoderBitRateKey: 64000])
            a.expectsMediaDataInRealTime = true
            if wr.canAdd(a) { wr.add(a); ai = a }
        }
        guard wr.startWriting() else { return false }
        wr.startSession(atSourceTime: pts)
        writer = wr; videoInput = vi; audioInput = ai; adaptor = ad
        return true
    }
    func captureOutput(_ output: AVCaptureOutput, didOutput sb: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard recording else { return }
        if output === audioOut {
            if recWriterReady, let ai = audioInput, ai.isReadyForMoreMediaData { ai.append(sb) }
            return
        }
        guard let pb = CMSampleBufferGetImageBuffer(sb) else { return }
        let pts = CMSampleBufferGetPresentationTimeStamp(sb)
        if !recWriterReady {
            if setupWriter(pb, pts) { recWriterReady = true } else { recording = false; return }
        }
        guard let vi = videoInput, vi.isReadyForMoreMediaData, let ad = adaptor, let pool = ad.pixelBufferPool else { return }
        if overlayCI == nil || Date().timeIntervalSince(lastOverlay) >= 1 {
            var L = lines(Date(), loc)
            L.rows.append(AppLanguage.text("Mã xác thực: ") + recCode)
            overlayCI = CIImage(image: overlayImage(outSize, L)); lastOverlay = Date()
        }
        var out: CVPixelBuffer?
        CVPixelBufferPoolCreatePixelBuffer(nil, pool, &out)
        guard let ob = out else { return }
        let src = CIImage(cvPixelBuffer: pb).cropped(to: cropRect).transformed(by: CGAffineTransform(translationX: -cropRect.minX, y: -cropRect.minY))
        ciContext.render((overlayCI ?? CIImage.empty()).composited(over: src), to: ob)
        ad.append(ob, withPresentationTime: pts)
    }
    private func finishVideo(_ tmp: URL, _ d: Date, _ l: CLLocation?) {
        let f = DateFormatter(); f.dateFormat = "yyyyMMdd_HHmmss"
        let name = "DatvipCam_" + f.string(from: d) + "_" + UUID().uuidString + ".mp4"
        let dst = CameraModel.photosDir.appendingPathComponent(name)

        do { try FileManager.default.moveItem(at: tmp, to: dst) } catch { return }
        guard CameraModel.appendPhotoRecord(PhotoRec(file: name, time: d, lat: l?.coordinate.latitude, lon: l?.coordinate.longitude, video: true)) else { return }
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { st in
            guard st == .authorized || st == .limited else { return }
            var createdAssetID: String?

            PHPhotoLibrary.shared().performChanges {
                let r = PHAssetCreationRequest.forAsset()
                r.addResource(with: .video, fileURL: dst, options: nil)
                r.location = l
                createdAssetID = r.placeholderForCreatedAsset?.localIdentifier
            } completionHandler: { success, error in
                if success, let assetID = createdAssetID {
                    CameraModel.updatePhotosAssetID(
                        file: name,
                        assetID: assetID
                    )
                }
            }
        }
    }

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        guard error == nil, let data = photo.fileDataRepresentation(), let img = UIImage(data: data) else { return }
        DispatchQueue.main.async { self.shutterFeedback.play() }
        let d = Date(), l = loc
        let land = shotOrient == .landscapeLeft || shotOrient == .landscapeRight
        let aspect = land ? 1 / previewAspect : previewAspect
        DispatchQueue.global(qos: .userInitiated).async {
            let base = img.normalized().cropped(to: aspect)
            var L = self.lines(d, l)
            L.rows.append(AppLanguage.text("Mã xác thực: ") + self.code(d, l))
            let out = self.stamp(base, L)
            if let jpg = self.jpeg(out, l) { self.save(jpg, d, l) }
        }
    }
    private func jpeg(_ img: UIImage, _ l: CLLocation?) -> Data? {
        guard let cg = img.cgImage else { return nil }
        let data = NSMutableData()
        guard let dest = CGImageDestinationCreateWithData(data, UTType.jpeg.identifier as CFString, 1, nil) else { return nil }
        var props: [CFString: Any] = [kCGImageDestinationLossyCompressionQuality: 0.93]
        if let l = l {
            let la = l.coordinate.latitude, lo = l.coordinate.longitude
            props[kCGImagePropertyGPSDictionary] = [
                kCGImagePropertyGPSLatitude: abs(la), kCGImagePropertyGPSLatitudeRef: la >= 0 ? "N" : "S",
                kCGImagePropertyGPSLongitude: abs(lo), kCGImagePropertyGPSLongitudeRef: lo >= 0 ? "E" : "W",
                kCGImagePropertyGPSAltitude: abs(l.altitude), kCGImagePropertyGPSAltitudeRef: l.altitude >= 0 ? 0 : 1
            ] as [CFString: Any]
        }
        CGImageDestinationAddImage(dest, cg, props as CFDictionary)
        return CGImageDestinationFinalize(dest) ? (data as Data) : nil
    }
    private func save(_ data: Data, _ d: Date, _ l: CLLocation?) {
        // bản trong app (để xem lại và chia sẻ hàng loạt)
        let f = DateFormatter(); f.dateFormat = "yyyyMMdd_HHmmss"
        let name = "DatvipCam_" + f.string(from: d) + "_" + UUID().uuidString + ".jpg"
        try? data.write(to: CameraModel.photosDir.appendingPathComponent(name))
        guard CameraModel.appendPhotoRecord(PhotoRec(file: name, time: d, lat: l?.coordinate.latitude, lon: l?.coordinate.longitude)) else { return }
        // bản trong thư viện Ảnh
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { st in
            guard st == .authorized || st == .limited else { return }
            var createdAssetID: String?

            PHPhotoLibrary.shared().performChanges {
                let r = PHAssetCreationRequest.forAsset()
                r.addResource(with: .photo, data: data, options: nil)
                r.location = l
                createdAssetID = r.placeholderForCreatedAsset?.localIdentifier
            } completionHandler: { success, error in
                if success, let assetID = createdAssetID {
                    CameraModel.updatePhotosAssetID(
                        file: name,
                        assetID: assetID
                    )
                }
            }
        }
    }

    // MARK: Nội dung dấu
    func dirLink(_ l: CLLocation) -> String {
        String(format: "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=driving", l.coordinate.latitude, l.coordinate.longitude)
    }
    func lines(_ d: Date, _ l: CLLocation?) -> StampLines {
        var head: String?, date: String?
        if flag("time") {
            let f = DateFormatter(); f.locale = AppLanguage.locale
            f.dateFormat = "HH:mm:ss"; head = f.string(from: d)
            f.dateFormat = "EEEE, dd/MM/yyyy"
            let off = TimeZone.current.secondsFromGMT(for: d) / 3600
            date = f.string(from: d).capitalized + " (GMT\(off >= 0 ? "+" : "")\(off))"
        }
        let project: String? = str("proj").isEmpty ? nil : AppLanguage.text("Nhiệm vụ: ") + str("proj")
        let addr = str("addr").isEmpty ? autoAddr : str("addr")
        let address: String? = addr.isEmpty ? nil : addr
        var custom: [String] = []
        for i in 1...4 {
            let lb = str("f\(i)l"), v = str("f\(i)v")
            if !v.isEmpty { custom.append(lb.isEmpty ? v : "\(lb): \(v)") }
        }
        var rows: [String] = []
        if flag("gps") {
            if let l = l {
                rows.append("📍 " + String(format: AppLanguage.text("Vĩ độ %.6f, Kinh độ %.6f"), locale: Locale(identifier: "en_US_POSIX"), l.coordinate.latitude, l.coordinate.longitude) + " (±\(Int(l.horizontalAccuracy))m)")
            } else { rows.append(AppLanguage.text("📍 Chưa có toạ độ GPS")) }
        }
        if flag("alt"), let l = l, l.verticalAccuracy >= 0 { rows.append(AppLanguage.text("Độ cao {0} m", String(describing: (Int(l.altitude))))) }
        if flag("hdg"), let h = heading { rows.append(AppLanguage.text("Hướng {0}° {1}", String(describing: (Int(h))), AppLanguage.direction(h))) }
        if !str("note").isEmpty { rows.append(AppLanguage.text("Ghi chú: ") + str("note")) }
        return StampLines(head: head, date: date, project: project, address: address, custom: custom, rows: rows)
    }
    func previewLines(_ d: Date) -> StampLines {
        var content = lines(d, loc)
        // Include the verification row when measuring preview, just as exports do.
        content.rows.append(AppLanguage.text("Mã xác thực: ") + (isRecording && !previewVerificationCode.isEmpty ? previewVerificationCode : code(d, loc)))
        return content
    }
    private func code(_ d: Date, _ l: CLLocation?) -> String {
        let raw = "\(Int(d.timeIntervalSince1970 * 1000))|" + (l.map { String(format: "%.6f,%.6f", $0.coordinate.latitude, $0.coordinate.longitude) } ?? "-")
        return SHA256.hash(data: Data(raw.utf8)).prefix(4).map { String(format: "%02X", $0) }.joined()
    }
    func shareText(_ d: Date, _ l: CLLocation?) -> String {
        l.map { dirLink($0) } ?? ""
    }
    /// Lời nhắn cho nhiều ảnh: gom các ảnh cùng vị trí (≤100 m) và gắn một link Google Maps cho mỗi nhóm
    func batchText(_ recs: [PhotoRec]) -> String {
        var groups: [[PhotoRec]] = []
        for r in recs {
            if let i = groups.firstIndex(where: { CameraModel.near($0[0], r) }) { groups[i].append(r) } else { groups.append([r]) }
        }
        return groups.compactMap { group -> String? in
            guard let la = group[0].lat, let lo = group[0].lon else { return nil }
            return dirLink(CLLocation(latitude: la, longitude: lo))
        }.joined(separator: "\n")
    }
    static func near(_ a: PhotoRec, _ b: PhotoRec) -> Bool {
        guard let la = a.lat, let lo = a.lon, let lb = b.lat, let ob = b.lon else { return false }
        return CLLocation(latitude: la, longitude: lo).distance(from: CLLocation(latitude: lb, longitude: ob)) < 100
    }

    // MARK: Vẽ dấu lên ảnh
    func stamp(_ img: UIImage, _ L: StampLines) -> UIImage {
        let fmt = UIGraphicsImageRendererFormat(); fmt.scale = 1
        return UIGraphicsImageRenderer(size: img.size, format: fmt).image { ctx in
            img.draw(at: .zero)
            self.drawStamp(ctx.cgContext, img.size, L)
        }
    }
    /// Ảnh chỉ có phần dấu (nền trong suốt) để ghép lên từng khung hình video
    func overlayImage(_ size: CGSize, _ L: StampLines) -> UIImage {
        guard size.width > 0, size.height > 0 else { return UIImage() }
        let fmt = UIGraphicsImageRendererFormat(); fmt.scale = 1
        return UIGraphicsImageRenderer(size: size, format: fmt).image { ctx in self.drawStamp(ctx.cgContext, size, L) }
    }
    private func drawStamp(_ c: CGContext, _ canvas: CGSize, _ L: StampLines) {
        let factor = min(canvas.width, canvas.height) / 1200
        guard factor > 0 else { return }
        // Measure and draw text at the same design size at every output resolution.
        c.saveGState()
        defer { c.restoreGState() }
        c.scaleBy(x: factor, y: factor)
        let size = CGSize(width: canvas.width / factor, height: canvas.height / factor)
        let w = size.width, h = size.height, u = min(w, h) / 100
        guard w > 0, h > 0 else { return }

        // Watermark remains independent of the information panel.
        if flag("wmOn"), !str("wm").isEmpty {
            let wm = str("wm") as NSString
            let at: [NSAttributedString.Key: Any] = [
                .font: UIFont.boldSystemFont(ofSize: u * 4),
                .foregroundColor: UIColor(white: 1, alpha: 0.27)
            ]
            let tw = wm.size(withAttributes: at).width + u * 12
            let sp = u * 16, dd = max(w, h)
            c.saveGState()
            c.translateBy(x: w / 2, y: h / 2)
            c.rotate(by: -.pi / 6)
            var y = -dd, row = 0
            while y < dd {
                var x = -dd + (row % 2 == 1 ? tw / 2 : 0)
                while x < dd {
                    wm.draw(at: CGPoint(x: x, y: y), withAttributes: at)
                    x += tw
                }
                y += sp
                row += 1
            }
            c.restoreGState()
        }

        let logo = flag("logoOn") && StampPreferences.logoOpacity > 0 ? LogoImage.load() : nil
        guard logo != nil || L.project != nil || !L.orderedBody.isEmpty else { return }
        let layout = StampLayout.make(size: size, content: L, logoSize: logo?.size)
        let decorationUnit = u * StampPreferences.displaySize
        let path = UIBezierPath(roundedRect: layout.rect, cornerRadius: decorationUnit * 1.6)
        c.saveGState()
        c.setShadow(offset: CGSize(width: 0, height: decorationUnit * 0.3), blur: decorationUnit * 1.2,
                    color: UIColor(white: 0, alpha: 0.5).cgColor)
        StampPreferences.background.setFill()
        path.fill()
        c.restoreGState()

        c.saveGState()
        path.addClip()
        UIColor(red: 1, green: 0.42, blue: 0.10, alpha: StampPreferences.backgroundOpacity).setFill()
        UIRectFill(CGRect(x: layout.rect.minX, y: layout.rect.minY, width: decorationUnit * 1.4, height: layout.rect.height))
        if let logo = logo, let rect = layout.logoRect { logo.draw(in: rect, blendMode: .normal, alpha: StampPreferences.logoOpacity) }
        let paragraph = NSMutableParagraphStyle()
        paragraph.lineBreakMode = .byWordWrapping
        for text in layout.texts {
            (text.value as NSString).draw(with: text.rect,
                options: [.usesLineFragmentOrigin, .usesFontLeading],
                attributes: [.font: text.font, .foregroundColor: text.color, .paragraphStyle: paragraph], context: nil)
        }
        c.restoreGState()
    }

    // MARK: GPS, la bàn
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locs: [CLLocation]) {
        guard let l = locs.last else { return }
        loc = l
        if Date().timeIntervalSince(lastGeo) > 60 {
            lastGeo = Date()
            geocoder.reverseGeocodeLocation(l, preferredLocale: AppLanguage.locale) { pm, _ in
                guard let p = pm?.first else { return }
                let a = [p.subThoroughfare, p.thoroughfare, p.subLocality, p.locality, p.administrativeArea].compactMap { $0 }.joined(separator: ", ")
                DispatchQueue.main.async { self.autoAddr = a }
            }
        }
    }
    func locationManager(_ manager: CLLocationManager, didUpdateHeading h: CLHeading) {
        heading = h.trueHeading >= 0 ? h.trueHeading : h.magneticHeading
    }
}

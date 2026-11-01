import SwiftUI
import AVFoundation
import CoreImage
import CoreLocation
import CryptoKit
import ImageIO
import Photos
import UniformTypeIdentifiers

struct Row { let t: String; let size: CGFloat; let bold: Bool; let color: UIColor }
typealias StampLines = (head: String?, date: String?, project: String?, address: String?, custom: [String], rows: [String])
struct PhotoRec: Codable, Identifiable { var file: String; var time: Date; var lat: Double?; var lon: Double?; var video: Bool? = nil; var id: String { file } }

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

    private let dirs = ["B", "ĐB", "Đ", "ĐN", "N", "TN", "T", "TB"]
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

import SwiftUI
import CoreMotion

/// Sensor coordinates stay independent of UIKit's rotation lock.
struct CameraOrientationFilter {
    private(set) var angle = 0.0
    private(set) var tilt: Double? = nil
    private var x = 0.0, y = -1.0
    private var initialized = false
    private var candidate = 0.0
    private var candidateSince = 0.0

    mutating func update(x rawX: Double, y rawY: Double, time: Double) {
        guard rawX.isFinite, rawY.isFinite else { return }
        if !initialized { x = rawX; y = rawY; initialized = true }
        else { x += 0.18 * (rawX - x); y += 0.18 * (rawY - y) }
        guard hypot(x, y) > 0.45 else { tilt = nil; candidate = angle; candidateSince = time; return }
        let measured = atan2(-x, -y) * 180 / .pi
        let nearest = (measured / 90).rounded() * 90
        let normalized = nearest == -180 ? 180 : nearest
        if abs(measured - nearest) <= 30 {
            if candidate != normalized { candidate = normalized; candidateSince = time }
            if time - candidateSince >= 0.25 { angle = candidate }
        } else { candidate = angle; candidateSince = time }
        var error = measured - angle
        while error > 180 { error -= 360 }
        while error < -180 { error += 360 }
        tilt = error
    }
}

final class CameraMotion: ObservableObject {
    @Published private(set) var angle = 0.0
    @Published private(set) var tilt: Double?
    private let manager = CMMotionManager()
    private var filter = CameraOrientationFilter()
    func start() {
        #if DEBUG
        if ProcessInfo.processInfo.environment["CAMERA_HT_UI_TESTING"] == "1",
           let value = ProcessInfo.processInfo.environment["CAMERA_HT_TEST_ANGLE"],
           let testAngle = Double(value), [0.0, 90.0, -90.0, 180.0].contains(testAngle) {
            angle = testAngle; tilt = 0
            return
        }
        #endif
        guard !manager.isAccelerometerActive, manager.isAccelerometerAvailable else { return }
        manager.accelerometerUpdateInterval = 1.0 / 20
        manager.startAccelerometerUpdates(to: .main) { [weak self] data, _ in
            guard let self = self, let data = data else { return }
            self.filter.update(x: data.acceleration.x, y: data.acceleration.y, time: data.timestamp)
            if self.angle != self.filter.angle { self.angle = self.filter.angle }
            self.tilt = self.filter.tilt
        }
    }
    func stop() { manager.stopAccelerometerUpdates() }
    deinit { manager.stopAccelerometerUpdates() }
    var orientation: UIDeviceOrientation {
        switch angle { case 90: return .landscapeLeft; case -90: return .landscapeRight
        case 180: return .portraitUpsideDown; default: return .portrait }
    }
}

struct CompositionGuides: View {
    let tilt: Double?
    var body: some View {
        GeometryReader { g in
            Path { path in
                for i in 1...2 {
                    let x = g.size.width * CGFloat(i) / 3, y = g.size.height * CGFloat(i) / 3
                    path.move(to: CGPoint(x: x, y: 0)); path.addLine(to: CGPoint(x: x, y: g.size.height))
                    path.move(to: CGPoint(x: 0, y: y)); path.addLine(to: CGPoint(x: g.size.width, y: y))
                }
            }.stroke(Color.white.opacity(0.45), lineWidth: 0.7)
            if let tilt = tilt {
                ZStack {
                    Capsule().fill(Color.white.opacity(0.6)).frame(width: 90, height: 1)
                    HStack(spacing: 5) {
                        Capsule().frame(width: 35, height: 3)
                        Circle().frame(width: 7, height: 7)
                        Capsule().frame(width: 35, height: 3)
                    }.foregroundColor(abs(tilt) <= 2 ? .green : .yellow)
                        .rotationEffect(.degrees(-tilt))
                }.position(x: g.size.width / 2, y: g.size.height / 2)
            }
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
}

import SwiftUI
import UIKit
import MapKit

struct LevelStyle {
    var color: UInt32 = 0xFFFFFF00
    var length: Double = 1
    var fraction: Double { length.isFinite ? min(1, max(0.15, length)) : 1 }
    var uiColor: UIColor { UIColor(red: CGFloat((color >> 16) & 255)/255, green: CGFloat((color >> 8) & 255)/255, blue: CGFloat(color & 255)/255, alpha: 1) }
    static func read() -> LevelStyle {
        let d = UserDefaults.standard
        return LevelStyle(color: d.object(forKey: "levelColor") == nil ? 0xFFFFFF00 : UInt32(truncatingIfNeeded: d.integer(forKey: "levelColor")), length: d.object(forKey: "levelLength") == nil ? 1 : d.double(forKey: "levelLength"))
    }
}

enum CompassMath {
    static let mountains = ["Tý","Quý","Sửu","Cấn","Dần","Giáp","Mão","Ất","Thìn","Tốn","Tỵ","Bính","Ngọ","Đinh","Mùi","Khôn","Thân","Canh","Dậu","Tân","Tuất","Càn","Hợi","Nhâm"]
    static func normalize(_ value: Double) -> Double { (value.truncatingRemainder(dividingBy: 360)+360).truncatingRemainder(dividingBy: 360) }
    static func mountain(_ heading: Double) -> String { mountains[Int((normalize(heading)+7.5)/15)%24] }
}

struct LevelToolsSettings: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage("levelColor") private var color = Int(0xFFFFFF00)
    @AppStorage("levelLength") private var length = 1.0
    var body: some View {
        NavigationView {
            Form {
                Section(AppLanguage.text("Chiều dài thước")) {
                    Slider(value: $length, in: 0.15...1).accessibilityIdentifier("level-length")
                    Text("\(Int(length*100))%")
                }
                Section(AppLanguage.text("Màu thước")) {
                    HStack {
                        ForEach([0xFFFFFF00,0xFFFFFFFF,0xFFFF0000,0xFF00FF00,0xFF00FFFF], id: \.self) { value in
                            Button { color = value } label: {
                                Circle().fill(Color(LevelStyle(color: UInt32(value)).uiColor))
                                    .frame(width: 36,height: 36).overlay(Circle().stroke(color == value ? Color.orange : Color.gray, lineWidth: 3))
                            }.buttonStyle(.plain).accessibilityLabel(String(format: "#%06X",value & 0xFFFFFF))
                        }
                    }
                }
                Text(AppLanguage.text("Áp dụng cho khung ngắm, ảnh và video. Nhấn giữ nút chữ thập để chỉnh nhanh."))
            }.navigationTitle(AppLanguage.text("Tùy chỉnh thước"))
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button(AppLanguage.text("Xong")) { dismiss() } } }
        }.preferredColorScheme(.dark)
    }
}

struct BubbleLevel: View {
    let x: Double?
    let y: Double?
    let angle: Double
    var body: some View {
        GeometryReader { g in
            let a = angle * .pi/180
            let bx = (x ?? 0)*cos(a) - (y ?? 0)*sin(a)
            let by = (x ?? 0)*sin(a) + (y ?? 0)*cos(a)
            let magnitude = hypot(bx,by)
            let scale = 34.0/max(1,magnitude*3)
            VStack(spacing: 4) {
                ZStack {
                    Circle().fill(Color.black.opacity(0.65))
                    Circle().stroke(Color.white,lineWidth: 1)
                    Circle().stroke(Color.white,lineWidth: 1).frame(width: 24,height: 24)
                    Rectangle().fill(Color.white.opacity(0.7)).frame(height: 1)
                    Rectangle().fill(Color.white.opacity(0.7)).frame(width: 1)
                    if x != nil && y != nil {
                        Circle().fill(magnitude < 0.035 ? Color.green : Color.yellow).frame(width: 18,height: 18)
                            .offset(x: bx*3*scale,y: by*3*scale)
                    }
                }.frame(width: 90,height: 90)
                Text(x == nil ? AppLanguage.text("Chưa có dữ liệu cảm biến") : String(format:"X %.1f°  Y %.1f°",asin(min(1,max(-1,bx)))*180 / .pi,asin(min(1,max(-1,by)))*180 / .pi))
                    .font(.caption2).foregroundColor(.white).padding(3).background(Color.black.opacity(0.6))
            }.position(x:g.size.width/2,y:g.size.height*0.24)
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
}

struct SatelliteMap: UIViewRepresentable {
    let coordinate: CLLocationCoordinate2D?
    let recenter: Int
    func makeCoordinator() -> Coordinator { Coordinator() }
    final class Coordinator { var initialized = false; var recenter = -1 }
    func makeUIView(context: Context) -> MKMapView {
        let view = MKMapView(); view.mapType = .hybrid; view.isRotateEnabled = false; view.isPitchEnabled = false
        view.showsUserLocation = true
        view.setRegion(MKCoordinateRegion(center: CLLocationCoordinate2D(latitude: 16, longitude: 106), latitudinalMeters: 1_500_000, longitudinalMeters: 1_500_000), animated: false)
        return view
    }
    func updateUIView(_ view: MKMapView, context: Context) {
        guard let coordinate = coordinate else { return }
        if !context.coordinator.initialized || context.coordinator.recenter != recenter {
            view.setRegion(MKCoordinateRegion(center: coordinate, latitudinalMeters: 300, longitudinalMeters: 300), animated: context.coordinator.initialized)
            context.coordinator.initialized = true; context.coordinator.recenter = recenter
        }
    }
}

struct FengShuiCompass: View {
    @ObservedObject var model: CameraModel
    @Environment(\.dismiss) private var dismiss
    @State private var satellite = true
    @State private var dial = true
    @State private var locked: Double?
    @State private var recenter = 0
    @State private var now = Date()
    private var heading: Double? { locked ?? (now.timeIntervalSince(model.compassSampleAt) < 2 ? (satellite ? model.compassTrueHeading : model.compassHeading) : nil) }
    var body: some View {
        NavigationView {
            VStack(spacing: 8) {
                Text(heading.map { String(format:"%.1f° · %@\n↔ %.1f° · %@",$0,CompassMath.mountain($0),CompassMath.normalize($0+180),CompassMath.mountain($0+180)) } ?? AppLanguage.text("Chưa có hướng la bàn"))
                    .font(.title2.monospacedDigit()).multilineTextAlignment(.center).foregroundColor(.yellow)
                GeometryReader { g in
                    ZStack {
                        Color.black
                        if satellite { SatelliteMap(coordinate: model.loc?.coordinate, recenter: recenter) }
                        if dial { CompassDial(heading: heading, overMap: satellite).frame(width: min(g.size.width*0.84,g.size.height*0.82),height:min(g.size.width*0.84,g.size.height*0.82)).allowsHitTesting(false) }
                    }
                }
                Text(satellite ? AppLanguage.text("Bắc thật ở trên") : AppLanguage.text("Bắc ở trên · Hướng từ")).font(.caption)
                Text(AppLanguage.text(model.compassReliable ? "Giữ điện thoại nằm phẳng khi đọc hướng." : "Tránh kim loại, di chuyển máy hình số 8 để hiệu chỉnh."))
                    .font(.caption).multilineTextAlignment(.center)
                HStack {
                    Button(AppLanguage.text("Vòng la bàn")) { dial.toggle() }.buttonStyle(.bordered)
                    Button(AppLanguage.text("Vệ tinh")) { locked = nil; satellite.toggle() }.buttonStyle(.bordered)
                }
                HStack {
                    Button(AppLanguage.text(locked == nil ? "Khóa hướng" : "Mở khóa")) { locked = locked == nil ? heading : nil }
                        .disabled(heading == nil).buttonStyle(.bordered)
                    Button(AppLanguage.text("Vị trí của tôi")) { recenter += 1 }.buttonStyle(.bordered)
                }
                if satellite && model.loc == nil { Text(AppLanguage.text("Đang định vị…")).font(.caption) }
            }.padding(.bottom,12).background(Color.black).foregroundColor(.white)
                .navigationTitle(AppLanguage.text("La bàn phong thủy"))
                .toolbar { ToolbarItem(placement:.confirmationAction) { Button(AppLanguage.text("Xong")) { dismiss() } } }
        }.preferredColorScheme(.dark)
            .onReceive(Timer.publish(every: 0.25, on: .main, in: .common).autoconnect()) { now = $0 }
    }
}

struct CompassDial: View {
    let heading: Double?
    var overMap = false
    var body: some View {
        Canvas { c, size in
            let r = min(size.width,size.height)*0.48; let center=CGPoint(x:size.width/2,y:size.height/2)
            func point(_ degree:Double,_ radius:Double) -> CGPoint { let a=(degree-90) * .pi/180; return CGPoint(x:center.x+cos(a)*radius,y:center.y+sin(a)*radius) }
            c.fill(Path(ellipseIn:CGRect(x:center.x-r,y:center.y-r,width:r*2,height:r*2)),with:.color(.black.opacity(overMap ? 0.10 : 0.78)))
            if overMap { c.addFilter(.shadow(color: .black, radius: 2)) }
            for fraction in [1.0,0.70] { c.stroke(Path(ellipseIn:CGRect(x:center.x-r*fraction,y:center.y-r*fraction,width:r*2*fraction,height:r*2*fraction)),with:.color(.yellow),lineWidth:1) }
            for i in 0..<120 {
                var line=Path(); line.move(to:point(Double(i)*3,r*(i%5 == 0 ? 0.90 : 0.95))); line.addLine(to:point(Double(i)*3,r))
                c.stroke(line,with:.color(.yellow),lineWidth:1)
            }
            for (i,name) in CompassMath.mountains.enumerated() { c.draw(Text(name).font(.system(size:r*0.085,weight:.bold)).foregroundColor(i%6 == 0 ? .red : .yellow),at:point(Double(i)*15,r*0.80)) }
            for (i,name) in ["B","Đ","N","T"].enumerated() { c.draw(Text(name).font(.system(size:r*0.12,weight:.bold)).foregroundColor(.white),at:point(Double(i)*90,r*0.57)) }
            if let h=heading {
                for (angle,color) in [(h,Color.red),(h+180,Color.white)] { var arrow=Path(); arrow.move(to:point(angle,r*0.60)); arrow.addLine(to:point(angle+90,r*0.07)); arrow.addLine(to:point(angle-90,r*0.07)); arrow.closeSubpath(); c.fill(arrow,with:.color(color)) }
            }
        }
    }
}

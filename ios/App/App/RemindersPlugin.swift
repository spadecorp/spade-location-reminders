import AVFoundation
import Capacitor
import CoreLocation
import UIKit
import UserNotifications

class ReminderViewController: CAPBridgeViewController {
    override func capacitorDidLoad() {
        bridge?.registerPluginInstance(RemindersPlugin())
    }
}

// The OS notification request is the authoritative saved reminder on iOS.
@objc(RemindersPlugin)
final class RemindersPlugin: CAPPlugin, CAPBridgedPlugin, NotificationHandlerProtocol {
    let identifier = "RemindersPlugin"
    let jsName = "Reminders"
    let pluginMethods: [CAPPluginMethod] = ["list", "save", "remove", "settings"].map {
        CAPPluginMethod(name: $0, returnType: CAPPluginReturnPromise)
    }
    private let center = UNUserNotificationCenter.current()
    private var renderers = [String: SpeechClip]()
    private let prefix = "spade."
    private var sounds: URL {
        FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask)[0].appendingPathComponent("Sounds", isDirectory: true)
    }

    override func load() {
        bridge?.notificationRouter.localNotificationHandler = self
    }
    func willPresent(notification: UNNotification) -> UNNotificationPresentationOptions { [.banner, .list, .sound] }
    func didReceive(response: UNNotificationResponse) {}

    @objc func list(_ call: CAPPluginCall) {
        center.getPendingNotificationRequests { requests in
            let rows = requests.compactMap { $0.content.userInfo["reminder"] as? [String: Any] }
            self.center.getNotificationSettings { settings in
                var warning = ""
                if !rows.isEmpty && settings.authorizationStatus == .denied {
                    warning = "Notifications are disabled. Enable them in Settings to receive arrival alerts."
                } else if !rows.isEmpty && [.denied, .restricted].contains(CLLocationManager().authorizationStatus) {
                    warning = "Location access is disabled. Allow location while using the app in Settings."
                }
                call.resolve(["items": rows, "warning": warning])
            }
        }
    }

    @objc func save(_ call: CAPPluginCall) {
        guard let id = call.getString("id"), UUID(uuidString: id) != nil,
              let task = call.getString("task"), !task.isEmpty, task.count <= 120,
              let lat = call.getDouble("lat"), let lng = call.getDouble("lng"),
              let radius = call.getInt("radius"), (100...3000).contains(radius),
              let speak = call.getBool("speak") else { call.reject("Invalid reminder."); return }
        let point = CLLocationCoordinate2D(latitude: lat, longitude: lng)
        guard CLLocationCoordinate2DIsValid(point), CLLocationManager.isMonitoringAvailable(for: CLCircularRegion.self)
        else { call.reject("Location monitoring is unavailable."); return }
        let manager = CLLocationManager()
        guard [.authorizedWhenInUse, .authorizedAlways].contains(manager.authorizationStatus),
              manager.accuracyAuthorization == .fullAccuracy else {
            call.reject("Enable Precise Location and While Using the App in Settings."); return
        }
        guard Double(radius) <= manager.maximumRegionMonitoringDistance else {
            call.reject("This phone requires a smaller radius."); return
        }
        let item: [String: Any] = ["id":id, "task":task, "lat":lat, "lng":lng, "radius":radius, "speak":speak]
        center.requestAuthorization(options: [.alert, .sound]) { ok, error in
            guard ok else { call.reject(error?.localizedDescription ?? "Allow notifications in Settings."); return }
            self.center.getPendingNotificationRequests { requests in
                let old = requests.first { $0.identifier == self.prefix + id }
                guard old != nil || requests.filter({ $0.trigger is UNLocationNotificationTrigger }).count < 20 else {
                    call.reject("Complete a reminder before adding another monitored location (limit 20)."); return
                }
                let region = CLCircularRegion(center: point, radius: Double(radius), identifier: self.prefix + id)
                region.notifyOnEntry = true
                region.notifyOnExit = false
                let content = UNMutableNotificationContent()
                content.title = "Location reminder"
                content.body = task
                content.userInfo = ["reminder": item]
                let schedule: (String?) -> Void = { name in
                    if let name = name {
                        content.sound = UNNotificationSound(named: UNNotificationSoundName(rawValue: name))
                        content.userInfo["clip"] = name
                    } else { content.sound = .default }
                    let request = UNNotificationRequest(identifier: self.prefix + id, content: content,
                        trigger: UNLocationNotificationTrigger(region: region, repeats: true))
                    self.center.add(request) { error in
                        if let error = error {
                            self.deleteClip(name)
                            call.reject(error.localizedDescription)
                        } else {
                            self.deleteClip(old?.content.userInfo["clip"] as? String)
                            call.resolve()
                        }
                    }
                }
                guard speak else { schedule(nil); return }
                DispatchQueue.main.async {
                    let name = "spade-" + UUID().uuidString + ".caf"
                    do { try FileManager.default.createDirectory(at: self.sounds, withIntermediateDirectories: true) }
                    catch { call.reject(error.localizedDescription); return }
                    let renderer = SpeechClip()
                    self.renderers[name] = renderer
                    renderer.write(task, to: self.sounds.appendingPathComponent(name)) { error in
                        self.renderers.removeValue(forKey: name)
                        if let error = error { self.deleteClip(name); call.reject(error); return }
                        schedule(name)
                    }
                }
            }
        }
    }

    private func deleteClip(_ name: String?) {
        guard let name = name, name.hasPrefix("spade-"), name == (name as NSString).lastPathComponent else { return }
        try? FileManager.default.removeItem(at: sounds.appendingPathComponent(name))
    }

    @objc func remove(_ call: CAPPluginCall) {
        guard let id = call.getString("id"), UUID(uuidString: id) != nil else { call.reject("Invalid reminder ID."); return }
        let key = prefix + id
        center.getPendingNotificationRequests { requests in
            let old = requests.first { $0.identifier == key }
            self.center.removePendingNotificationRequests(withIdentifiers: [key])
            self.center.removeDeliveredNotifications(withIdentifiers: [key])
            self.deleteClip(old?.content.userInfo["clip"] as? String)
            call.resolve()
        }
    }
    @objc func settings(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            guard let url = URL(string: UIApplication.openSettingsURLString) else { call.reject("Settings unavailable."); return }
            UIApplication.shared.open(url) { opened in
                if opened { call.resolve() } else { call.reject("Settings unavailable.") }
            }
        }
    }
}

private final class SpeechClip {
    private let synth = AVSpeechSynthesizer()
    private var file: AVAudioFile?
    private var frames: AVAudioFramePosition = 0
    private var completed = false
    private var watchdog: DispatchWorkItem?

    func write(_ text: String, to url: URL, completion: @escaping (String?) -> Void) {
        func finish(_ problem: String?) {
            guard !completed else { return }
            completed = true
            watchdog?.cancel()
            watchdog = nil
            file = nil
            DispatchQueue.main.async {
                if problem != nil { self.synth.stopSpeaking(at: .immediate) }
                completion(problem)
            }
        }
        let queue = DispatchQueue(label: "com.spadecorp.reminders.clip")
        let timeout = DispatchWorkItem { finish("Speech was unavailable. Try again or turn off Read aloud.") }
        watchdog = timeout
        queue.asyncAfter(deadline: .now() + 20, execute: timeout)
        let utterance = AVSpeechUtterance(string: text)
        synth.write(utterance) { buffer in
            // Finish writing this buffer before the synthesizer reuses it.
            queue.sync {
                guard !self.completed, let pcm = buffer as? AVAudioPCMBuffer else { return }
                if pcm.frameLength == 0 {
                    finish(self.frames == 0 ? "The speech engine produced no audio. Try again or turn off Read aloud." : nil)
                    return
                }
                self.frames += AVAudioFramePosition(pcm.frameLength)
                guard Double(self.frames) / pcm.format.sampleRate < 29.5 else {
                    finish("Shorten the task to fit the 30-second spoken alert, or turn off Read aloud."); return
                }
                do {
                    if self.file == nil {
                        var format = pcm.format.settings
                        format[AVFormatIDKey] = kAudioFormatLinearPCM
                        format[AVLinearPCMBitDepthKey] = 16
                        format[AVLinearPCMIsFloatKey] = false
                        self.file = try AVAudioFile(forWriting: url, settings: format,
                            commonFormat: pcm.format.commonFormat, interleaved: pcm.format.isInterleaved)
                    }
                    try self.file?.write(from: pcm)
                } catch { finish("Could not create the spoken reminder.") }
            }
        }
    }
}

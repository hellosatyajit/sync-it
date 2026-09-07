import AppKit
import CryptoKit
import Foundation

@MainActor
final class SyncEngine {
    var onStatus: ((SyncStatus) -> Void)?
    private(set) var status: SyncStatus = .unconfigured { didSet { onStatus?(status) } }
    private let defaults = UserDefaults.standard
    private let pasteboard = NSPasteboard.general
    private var timer: Timer?
    private var lastChangeCount = NSPasteboard.general.changeCount
    private var lastAppliedDigest = ""
    private var cursor = ""
    private var busy = false
    private var cachedPhrase = ""
    private var cachedKey: SymmetricKey?

    var relayURL: String { defaults.string(forKey: "relayURL") ?? "" }
    var phrase: String { SecretStore.load() }
    private var deviceID: String {
        if let value = defaults.string(forKey: "deviceID") { return value }
        let value = "mac_" + UUID().uuidString.replacingOccurrences(of: "-", with: "")
        defaults.set(value, forKey: "deviceID")
        return value
    }

    func configure(relayURL: String, phrase: String) {
        defaults.set(relayURL.trimmingCharacters(in: CharacterSet(charactersIn: "/ \n\t")), forKey: "relayURL")
        SecretStore.save(phrase)
        cachedPhrase = ""
        cachedKey = nil
        cursor = ""
        start()
    }

    func start() {
        timer?.invalidate()
        guard URL(string: relayURL) != nil, phrase.count >= 8 else { status = .unconfigured; return }
        status = .connecting
        timer = Timer.scheduledTimer(withTimeInterval: 1.25, repeats: true) { [weak self] _ in
            Task { @MainActor in await self?.tick() }
        }
        Task { await tick() }
    }

    func sendNow() { Task { await captureAndSend(force: true) } }

    private func tick() async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        if pasteboard.changeCount != lastChangeCount { await captureAndSend(force: false) }
        await receive()
    }

    private func captureAndSend(force: Bool) async {
        lastChangeCount = pasteboard.changeCount
        var type: String
        var value: String
        if let text = pasteboard.string(forType: .string), !text.isEmpty {
            type = "text"; value = text
        } else if let image = NSImage(pasteboard: pasteboard),
                  let tiff = image.tiffRepresentation,
                  let bitmap = NSBitmapImageRep(data: tiff),
                  let png = bitmap.representation(using: .png, properties: [:]) {
            type = "image/png"; value = png.base64EncodedString()
        } else { return }
        let digest = Data(SHA256.hash(data: Data((type + value).utf8))).base64EncodedString()
        if !force && digest == lastAppliedDigest { lastAppliedDigest = ""; return }
        let payload = ClipboardPayload(id: UUID().uuidString, type: type, value: value, createdAt: Int64(Date().timeIntervalSince1970 * 1000))
        do {
            let envelope = try SyncCrypto.encrypt(payload, key: encryptionKey())
            var request = try makeRequest(path: "/v1/messages", method: "POST")
            request.httpBody = try JSONEncoder().encode(envelope)
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            let (_, response) = try await URLSession.shared.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 201 else { throw URLError(.badServerResponse) }
            status = .connected
        } catch { status = .error("Send failed") }
    }

    private func receive() async {
        do {
            let encoded = cursor.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
            let (data, response) = try await URLSession.shared.data(for: makeRequest(path: "/v1/messages?after=\(encoded)", method: "GET"))
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
            let messages = try JSONDecoder().decode(MessageList.self, from: data).messages
            guard !messages.isEmpty else { status = .connected; return }
            let key = encryptionKey()
            for message in messages {
                cursor = max(cursor, message.id)
                if let payload = try? SyncCrypto.decrypt(message, key: key) { apply(payload) }
            }
            status = .connected
        } catch { status = .error("Relay unavailable") }
    }

    private func apply(_ payload: ClipboardPayload) {
        pasteboard.clearContents()
        if payload.type == "text" {
            pasteboard.setString(payload.value, forType: .string)
        } else if payload.type.hasPrefix("image/"), let data = Data(base64Encoded: payload.value), let image = NSImage(data: data) {
            pasteboard.writeObjects([image])
        } else { return }
        lastAppliedDigest = Data(SHA256.hash(data: Data((payload.type + payload.value).utf8))).base64EncodedString()
        lastChangeCount = pasteboard.changeCount
    }

    private func makeRequest(path: String, method: String) throws -> URLRequest {
        guard let url = URL(string: relayURL + path) else { throw URLError(.badURL) }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)
        request.httpMethod = method
        request.setValue(SyncCrypto.channelID(phrase: phrase), forHTTPHeaderField: "X-SyncIt-Channel")
        request.setValue(deviceID, forHTTPHeaderField: "X-SyncIt-Device")
        return request
    }

    private func encryptionKey() -> SymmetricKey {
        let current = phrase
        if cachedPhrase != current || cachedKey == nil {
            cachedPhrase = current
            cachedKey = SyncCrypto.deriveKey(phrase: current)
        }
        return cachedKey!
    }
}

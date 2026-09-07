import Foundation

struct ClipboardPayload: Codable {
    let id: String
    let type: String
    let value: String
    let createdAt: Int64
}

struct EncryptedEnvelope: Codable {
    let version: Int
    let nonce: String
    let ciphertext: String
}

struct RelayMessage: Codable {
    let id: String
    let sender: String
    let createdAt: Int64
    let version: Int
    let nonce: String
    let ciphertext: String
}

struct MessageList: Codable { let messages: [RelayMessage] }

enum SyncStatus: Equatable {
    case unconfigured, connecting, connected, error(String)

    var label: String {
        switch self {
        case .unconfigured: return "Not configured"
        case .connecting: return "Connecting…"
        case .connected: return "Connected"
        case .error(let detail): return detail
        }
    }
}


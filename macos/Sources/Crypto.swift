import Foundation
import CryptoKit

enum SyncCryptoError: Error { case invalidEnvelope }

enum SyncCrypto {
    static func channelID(phrase: String) -> String {
        Data(SHA256.hash(data: Data(("syncit-channel-v1:" + phrase).utf8))).base64URLEncodedString()
    }

    static func deriveKey(phrase: String) -> SymmetricKey {
        let password = Data(phrase.utf8)
        let salt = Data("syncit-e2ee-v1".utf8)
        let key = pbkdf2(password: password, salt: salt, iterations: 120_000, length: 32)
        return SymmetricKey(data: key)
    }

    static func encrypt(_ payload: ClipboardPayload, key: SymmetricKey) throws -> EncryptedEnvelope {
        let plaintext = try JSONEncoder().encode(payload)
        let box = try AES.GCM.seal(plaintext, using: key)
        guard let combined = box.combined else { throw SyncCryptoError.invalidEnvelope }
        return EncryptedEnvelope(version: 1, nonce: box.nonce.withUnsafeBytes { Data($0) }.base64EncodedString(), ciphertext: combined.base64EncodedString())
    }

    static func decrypt(_ message: RelayMessage, key: SymmetricKey) throws -> ClipboardPayload {
        guard message.version == 1, let data = Data(base64Encoded: message.ciphertext) else { throw SyncCryptoError.invalidEnvelope }
        return try JSONDecoder().decode(ClipboardPayload.self, from: AES.GCM.open(try AES.GCM.SealedBox(combined: data), using: key))
    }

    private static func pbkdf2(password: Data, salt: Data, iterations: Int, length: Int) -> Data {
        let key = SymmetricKey(data: password)
        var output = Data()
        var block: UInt32 = 1
        while output.count < length {
            var index = block.bigEndian
            var input = salt
            withUnsafeBytes(of: &index) { input.append(contentsOf: $0) }
            var u = Data(HMAC<SHA256>.authenticationCode(for: input, using: key))
            var result = u
            if iterations > 1 {
                for _ in 2...iterations {
                    u = Data(HMAC<SHA256>.authenticationCode(for: u, using: key))
                    for i in result.indices { result[i] ^= u[i] }
                }
            }
            output.append(result)
            block += 1
        }
        return output.prefix(length)
    }
}

private extension Data {
    func base64URLEncodedString() -> String {
        base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }
}


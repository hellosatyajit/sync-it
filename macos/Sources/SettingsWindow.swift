import AppKit

@MainActor
final class SettingsWindowController: NSWindowController {
    private let relayField = NSTextField()
    private let phraseField = NSSecureTextField()
    private let engine: SyncEngine

    init(engine: SyncEngine) {
        self.engine = engine
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 480, height: 230), styleMask: [.titled, .closable], backing: .buffered, defer: false)
        window.title = "Sync It Settings"
        window.center()
        super.init(window: window)
        buildUI()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    private func buildUI() {
        relayField.stringValue = engine.relayURL
        relayField.placeholderString = "https://your-relay.example.com"
        phraseField.stringValue = engine.phrase
        phraseField.placeholderString = "A long private pairing phrase"
        let save = NSButton(title: "Save and connect", target: self, action: #selector(saveSettings))
        save.bezelStyle = .rounded
        let note = NSTextField(wrappingLabelWithString: "Enter the exact same relay URL and pairing phrase on both devices. The phrase stays in your Mac keychain and encrypts every clipboard item.")
        note.textColor = .secondaryLabelColor
        let grid = NSGridView(views: [
            [NSTextField(labelWithString: "Relay URL"), relayField],
            [NSTextField(labelWithString: "Pairing phrase"), phraseField]
        ])
        grid.rowSpacing = 14; grid.columnSpacing = 14
        let stack = NSStackView(views: [grid, note, save])
        stack.orientation = .vertical; stack.alignment = .trailing; stack.spacing = 18
        stack.translatesAutoresizingMaskIntoConstraints = false
        window?.contentView?.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: window!.contentView!.leadingAnchor, constant: 24),
            stack.trailingAnchor.constraint(equalTo: window!.contentView!.trailingAnchor, constant: -24),
            stack.topAnchor.constraint(equalTo: window!.contentView!.topAnchor, constant: 24),
            relayField.widthAnchor.constraint(equalToConstant: 320), phraseField.widthAnchor.constraint(equalToConstant: 320)
        ])
    }

    @objc private func saveSettings() {
        engine.configure(relayURL: relayField.stringValue, phrase: phraseField.stringValue)
        close()
    }
}


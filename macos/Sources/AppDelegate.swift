import AppKit

@main
@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {
    private let engine = SyncEngine()
    private var statusItem: NSStatusItem!
    private var settings: SettingsWindowController!
    private let statusMenuItem = NSMenuItem(title: "Not configured", action: nil, keyEquivalent: "")

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
        settings = SettingsWindowController(engine: engine)
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
        statusItem.button?.image = NSImage(systemSymbolName: "arrow.left.arrow.right.circle", accessibilityDescription: "Sync It")
        let menu = NSMenu()
        statusMenuItem.isEnabled = false
        menu.addItem(statusMenuItem)
        menu.addItem(NSMenuItem(title: "Send clipboard now", action: #selector(sendNow), keyEquivalent: "s"))
        menu.addItem(.separator())
        menu.addItem(NSMenuItem(title: "Settings…", action: #selector(openSettings), keyEquivalent: ","))
        menu.addItem(NSMenuItem(title: "Quit Sync It", action: #selector(quit), keyEquivalent: "q"))
        for item in menu.items where item.action != nil { item.target = self }
        statusItem.menu = menu
        engine.onStatus = { [weak self] status in self?.statusMenuItem.title = status.label }
        engine.start()
        if engine.phrase.isEmpty { openSettings() }
    }

    @objc private func sendNow() { engine.sendNow() }
    @objc private func openSettings() { NSApp.activate(ignoringOtherApps: true); settings.showWindow(nil); settings.window?.makeKeyAndOrderFront(nil) }
    @objc private func quit() { NSApp.terminate(nil) }

    func application(_ application: NSApplication, open urls: [URL]) {
        for url in urls where url.scheme == "syncit" {
            if url.host == "send" { engine.sendNow() }
            if url.host == "settings" { openSettings() }
        }
    }
}

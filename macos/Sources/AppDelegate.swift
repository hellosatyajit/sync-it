import AppKit

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {
    private let engine = SyncEngine()
    private var statusItem: NSStatusItem!
    private var settings: SettingsWindowController!
    private let statusMenuItem = NSMenuItem(title: "Not configured", action: nil, keyEquivalent: "")

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        settings = SettingsWindowController(engine: engine)
        settings.onClose = { NSApp.setActivationPolicy(.accessory) }
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
        statusItem.button?.image = NSImage(systemSymbolName: "arrow.left.arrow.right.circle", accessibilityDescription: "Sync It")
        statusItem.button?.toolTip = "Sync It"
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
        DispatchQueue.main.async { [weak self] in self?.openSettings() }
    }

    @objc private func sendNow() { engine.sendNow() }
    @objc private func openSettings() {
        NSApp.setActivationPolicy(.regular)
        settings.showWindow(nil)
        settings.window?.center()
        settings.window?.makeKeyAndOrderFront(nil)
        settings.window?.orderFrontRegardless()
        NSApp.activate(ignoringOtherApps: true)
    }
    @objc private func quit() { NSApp.terminate(nil) }

    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool {
        openSettings()
        return true
    }

}

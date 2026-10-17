import SwiftUI

@main
struct VPNClientApp: App {
    @StateObject private var store = ProfileStore()
    @StateObject private var vpn = VPNController()
    @State private var importError: String?

    var body: some Scene {
        WindowGroup {
            HomeView()
                .environmentObject(store)
                .environmentObject(vpn)
                .task { await store.refreshStale() }
                .onOpenURL { url in Task { await handle(url) } }
                .alert("Не удалось импортировать", isPresented: .constant(importError != nil)) {
                    Button("OK") { importError = nil }
                } message: {
                    Text(importError ?? "")
                }
        }
    }

    /// vpnclient://add/<ссылка подписки или vless://...> — импорт из бота/сайта в один тап.
    private func handle(_ url: URL) async {
        let prefix = "vpnclient://add/"
        let raw = url.absoluteString
        guard raw.hasPrefix(prefix) else { return }
        let payload = String(raw.dropFirst(prefix.count))
        let input = payload.removingPercentEncoding ?? payload
        do {
            try await store.add(input: input)
        } catch {
            importError = error.localizedDescription
        }
    }
}

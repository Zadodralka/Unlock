import Foundation
import NetworkExtension

/// Управление системным VPN-профилем и запуск/остановка туннеля.
@MainActor
final class VPNController: ObservableObject {
    @Published private(set) var status: NEVPNStatus = .invalid
    @Published private(set) var connectedSince: Date?
    @Published var lastError: String?

    private var manager: NETunnelProviderManager?
    private var statusObserver: NSObjectProtocol?
    /// Сервер, к которому подключиться после остановки текущего туннеля (смена сервера).
    private var pendingServer: ServerNode?

    var isActive: Bool { status == .connected || status == .connecting || status == .reasserting }

    init() {
        Task { await loadManager() }
    }

    func toggle(server: ServerNode?) async {
        if isActive || status == .disconnecting {
            disconnect()
        } else if let server {
            await connect(to: server)
        } else {
            lastError = "Добавьте подписку или сервер"
        }
    }

    /// Подключиться к серверу; если туннель уже поднят — переподключиться.
    func connect(to server: ServerNode) async {
        lastError = nil
        if isActive {
            pendingServer = server
            manager?.connection.stopVPNTunnel()
            return
        }
        do {
            try Data(server.outboundJSON.utf8).write(to: AppGroup.activeOutboundURL, options: .atomic)

            let manager = try await preparedManager(serverAddress: server.address)
            try manager.connection.startVPNTunnel()
        } catch {
            lastError = error.localizedDescription
        }
    }

    func disconnect() {
        pendingServer = nil
        manager?.connection.stopVPNTunnel()
    }

    // MARK: - Внутреннее

    private func loadManager() async {
        do {
            let managers = try await NETunnelProviderManager.loadAllFromPreferences()
            if let existing = managers.first {
                manager = existing
                observe(existing)
            }
        } catch {
            lastError = error.localizedDescription
        }
    }

    /// Создаёт или обновляет VPN-профиль. При первом сохранении iOS спросит разрешение.
    private func preparedManager(serverAddress: String) async throws -> NETunnelProviderManager {
        let manager = self.manager ?? NETunnelProviderManager()
        let proto = (manager.protocolConfiguration as? NETunnelProviderProtocol) ?? NETunnelProviderProtocol()
        proto.providerBundleIdentifier = (Bundle.main.bundleIdentifier ?? "") + ".tunnel"
        proto.serverAddress = serverAddress
        manager.protocolConfiguration = proto
        manager.localizedDescription = "Unlock"
        manager.isEnabled = true

        try await manager.saveToPreferences()
        // Без повторной загрузки первый запуск после сохранения иногда падает
        try await manager.loadFromPreferences()

        self.manager = manager
        observe(manager)
        return manager
    }

    private func observe(_ manager: NETunnelProviderManager) {
        if let statusObserver { NotificationCenter.default.removeObserver(statusObserver) }
        statusObserver = NotificationCenter.default.addObserver(
            forName: .NEVPNStatusDidChange, object: manager.connection, queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.statusChanged() }
        }
        statusChanged()
    }

    private func statusChanged() {
        guard let connection = manager?.connection else { return }
        status = connection.status
        connectedSince = connection.connectedDate

        if status == .disconnected {
            if let server = pendingServer {
                pendingServer = nil
                Task { await connect(to: server) }
            } else {
                // Ошибку старта туннеля система отдаёт только через fetchLastDisconnectError
                connection.fetchLastDisconnectError { [weak self] error in
                    guard let error else { return }
                    Task { @MainActor in self?.lastError = error.localizedDescription }
                }
            }
        }
    }
}

extension NEVPNStatus {
    var title: String {
        switch self {
        case .connected: return "Подключено"
        case .connecting: return "Подключение…"
        case .disconnecting: return "Отключение…"
        case .reasserting: return "Переподключение…"
        case .disconnected, .invalid: return "Не подключено"
        @unknown default: return "—"
        }
    }
}

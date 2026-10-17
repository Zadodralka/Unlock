import Foundation

/// Подписки, серверы, выбранный сервер и результаты пинга. Хранится в App Group.
@MainActor
final class ProfileStore: ObservableObject {
    @Published private(set) var subscriptions: [Subscription] = []
    @Published var selectedServerID: UUID? {
        didSet { AppGroup.defaults.set(selectedServerID?.uuidString, forKey: Self.selectedKey) }
    }
    /// Задержка в мс; -1 — сервер не ответил.
    @Published private(set) var pings: [UUID: Int] = [:]
    @Published private(set) var isPinging = false
    @Published private(set) var refreshingIDs: Set<UUID> = []

    private static let selectedKey = "selectedServerID"
    private static let manualTitle = "Мои серверы"

    init() {
        if let data = try? Data(contentsOf: AppGroup.profilesURL),
           let saved = try? JSONDecoder().decode([Subscription].self, from: data) {
            subscriptions = saved
        }
        selectedServerID = AppGroup.defaults.string(forKey: Self.selectedKey).flatMap(UUID.init(uuidString:))
    }

    var selectedServer: ServerNode? {
        let all = subscriptions.flatMap(\.servers)
        return all.first { $0.id == selectedServerID } ?? all.first
    }

    func subscription(containing server: ServerNode) -> Subscription? {
        subscriptions.first { $0.servers.contains(server) }
    }

    // MARK: - Добавление

    /// Ссылка подписки (http/https) или одна/несколько ссылок vless://, trojan:// и т.п.
    func add(input: String) async throws {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.hasPrefix("http://") || text.hasPrefix("https://") {
            if let existing = subscriptions.first(where: { $0.url == text }) {
                try await refresh(existing.id)
                return
            }
            var subscription = Subscription(title: URL(string: text)?.host ?? "Подписка", url: text)
            try await load(into: &subscription)
            subscriptions.append(subscription)
            if selectedServerID == nil { selectedServerID = subscription.servers.first?.id }
        } else {
            let servers = try Self.parseServers(text)
            if let index = subscriptions.firstIndex(where: { $0.url == nil }) {
                subscriptions[index].servers.append(contentsOf: servers)
            } else {
                subscriptions.append(Subscription(title: Self.manualTitle, url: nil, servers: servers))
            }
            if selectedServerID == nil { selectedServerID = servers.first?.id }
        }
        save()
    }

    func refresh(_ id: UUID) async throws {
        guard var subscription = subscriptions.first(where: { $0.id == id }), subscription.url != nil else { return }
        refreshingIDs.insert(id)
        defer { refreshingIDs.remove(id) }

        let selectedName = subscription.servers.first { $0.id == selectedServerID }?.name
        try await load(into: &subscription)
        guard let index = subscriptions.firstIndex(where: { $0.id == id }) else { return }
        subscriptions[index] = subscription
        // После обновления у серверов новые id — сохраняем выбор по имени
        if let selectedName, let same = subscription.servers.first(where: { $0.name == selectedName }) {
            selectedServerID = same.id
        }
        save()
    }

    /// Обновляет подписки, у которых истёк интервал обновления. Ошибки молча пропускаем.
    func refreshStale() async {
        for subscription in subscriptions where subscription.isStale {
            try? await refresh(subscription.id)
        }
    }

    func delete(_ id: UUID) {
        subscriptions.removeAll { $0.id == id }
        save()
    }

    func deleteServer(_ server: ServerNode) {
        for index in subscriptions.indices {
            subscriptions[index].servers.removeAll { $0.id == server.id }
        }
        subscriptions.removeAll { $0.url == nil && $0.servers.isEmpty }
        save()
    }

    // MARK: - Пинг

    /// Пингуем в фоне пачками по 5 (ограничение libXray).
    func pingAll() async {
        guard !isPinging else { return }
        isPinging = true
        defer { isPinging = false }
        pings = [:]

        let servers = subscriptions.flatMap(\.servers)
        for start in stride(from: 0, to: servers.count, by: 5) {
            let chunk = Array(servers[start..<min(start + 5, servers.count)])
            let outbounds = chunk.map(\.outboundJSON)
            let results = await Task.detached(priority: .userInitiated) {
                (try? XrayCore.ping(outbounds: outbounds)) ?? []
            }.value
            for (server, result) in zip(chunk, results) {
                pings[server.id] = result.success ? result.delay : -1
            }
        }
    }

    // MARK: - Внутреннее

    private func load(into subscription: inout Subscription) async throws {
        guard let url = subscription.url else { return }
        let response = try await SubscriptionClient.fetch(url)
        let body = response.body
        let servers = try await Task.detached { try Self.parseServers(body) }.value

        subscription.servers = servers
        subscription.updatedAt = Date()
        if let title = response.title { subscription.title = title }
        subscription.usage = response.usage
        subscription.supportURL = response.supportURL
        subscription.announce = response.announce
        if let hours = response.updateIntervalHours, hours > 0 { subscription.updateIntervalHours = hours }
    }

    nonisolated private static func parseServers(_ text: String) throws -> [ServerNode] {
        let servers = try XrayCore.parseShareLinks(text).map(ServerNode.init(outbound:))
        if servers.isEmpty { throw XrayCore.Failure(message: "Не найдено ни одного сервера") }
        return servers
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(subscriptions) else { return }
        try? data.write(to: AppGroup.profilesURL, options: .atomic)
    }
}

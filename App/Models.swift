import Foundation

/// Один сервер: имя из ссылки и outbound-объект Xray в виде JSON-текста.
struct ServerNode: Codable, Identifiable, Hashable {
    var id = UUID()
    var name: String
    var protocolName: String
    var address: String
    var port: Int
    var transport: String
    var outboundJSON: String

    init(outbound: [String: Any]) throws {
        let settings = outbound["settings"] as? [String: Any] ?? [:]
        let stream = outbound["streamSettings"] as? [String: Any] ?? [:]
        name = (outbound["tag"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "Без названия"
        protocolName = outbound["protocol"] as? String ?? "?"
        address = settings["address"] as? String ?? ""
        port = (settings["port"] as? NSNumber)?.intValue ?? 0
        let network = stream["network"] as? String ?? "raw"
        let security = stream["security"] as? String ?? "none"
        transport = security == "none" ? network : "\(network)+\(security)"
        let data = try JSONSerialization.data(withJSONObject: outbound, options: [.sortedKeys])
        outboundJSON = String(decoding: data, as: UTF8.self)
    }
}

/// Данные из заголовка subscription-userinfo.
struct SubscriptionUsage: Codable, Hashable {
    var upload: Int64 = 0
    var download: Int64 = 0
    var total: Int64 = 0
    var expire: Date?

    var used: Int64 { upload + download }
}

struct Subscription: Codable, Identifiable, Hashable {
    var id = UUID()
    var title: String
    /// nil — группа серверов, добавленных отдельными ссылками.
    var url: String?
    var servers: [ServerNode] = []
    var updatedAt: Date?
    var usage: SubscriptionUsage?
    var supportURL: String?
    var announce: String?
    /// Интервал автообновления из profile-update-interval, в часах.
    var updateIntervalHours: Int = 12

    var isStale: Bool {
        guard url != nil else { return false }
        guard let updatedAt else { return true }
        return Date().timeIntervalSince(updatedAt) > TimeInterval(updateIntervalHours * 3600)
    }
}

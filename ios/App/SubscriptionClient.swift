import Foundation
import UIKit

/// Загрузка подписки по ссылке. Заголовки x-hwid / x-device-* — те же, что шлёт Happ:
/// по ним Remnawave и другие панели считают устройства и применяют лимит.
enum SubscriptionClient {
    struct Response {
        var body: String
        var title: String?
        var usage: SubscriptionUsage?
        var supportURL: String?
        var announce: String?
        var updateIntervalHours: Int?
    }

    enum FetchError: LocalizedError {
        case badURL
        case http(Int)
        case notASubscription

        var errorDescription: String? {
            switch self {
            case .badURL: return "Некорректная ссылка"
            case .http(let code): return "Сервер подписки ответил \(code)"
            case .notASubscription: return "По ссылке нет подписки (пришла веб-страница)"
            }
        }
    }

    static func fetch(_ urlString: String) async throws -> Response {
        guard let url = URL(string: urlString.trimmingCharacters(in: .whitespacesAndNewlines)),
              url.scheme == "https" || url.scheme == "http"
        else { throw FetchError.badURL }

        var request = URLRequest(url: url, timeoutInterval: 20)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        let device = await DeviceInfo.current()
        request.setValue("VPNClient/\(DeviceInfo.appVersion)", forHTTPHeaderField: "User-Agent")
        request.setValue(device.hwid, forHTTPHeaderField: "x-hwid")
        request.setValue("iOS", forHTTPHeaderField: "x-device-os")
        request.setValue(device.osVersion, forHTTPHeaderField: "x-ver-os")
        request.setValue(device.model, forHTTPHeaderField: "x-device-model")

        let (data, response) = try await URLSession.shared.data(for: request)
        let http = response as? HTTPURLResponse
        if let code = http?.statusCode, !(200..<300).contains(code) { throw FetchError.http(code) }

        let body = String(decoding: data, as: UTF8.self)
        if body.range(of: "<html", options: .caseInsensitive) != nil { throw FetchError.notASubscription }

        func header(_ name: String) -> String? {
            (http?.value(forHTTPHeaderField: name)).flatMap { $0.isEmpty ? nil : $0 }
        }
        return Response(
            body: body,
            title: header("profile-title").map(decodeBase64Prefixed),
            usage: header("subscription-userinfo").map(parseUsage),
            supportURL: header("support-url"),
            announce: header("announce").map(decodeBase64Prefixed),
            updateIntervalHours: header("profile-update-interval").flatMap { Int($0) }
        )
    }

    /// Значения вида "base64:0J/RgNC40LLQtdGC" — так передают не-ASCII текст в заголовках.
    private static func decodeBase64Prefixed(_ value: String) -> String {
        guard value.hasPrefix("base64:"),
              let data = Data(base64Encoded: String(value.dropFirst("base64:".count))),
              let text = String(data: data, encoding: .utf8)
        else { return value }
        return text
    }

    /// "upload=0; download=123; total=1000; expire=1700000000"
    private static func parseUsage(_ value: String) -> SubscriptionUsage {
        var usage = SubscriptionUsage()
        for part in value.split(separator: ";") {
            let pair = part.split(separator: "=", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces) }
            guard pair.count == 2, let number = Int64(pair[1]) else { continue }
            switch pair[0].lowercased() {
            case "upload": usage.upload = number
            case "download": usage.download = number
            case "total": usage.total = number
            case "expire": usage.expire = number > 0 ? Date(timeIntervalSince1970: TimeInterval(number)) : nil
            default: break
            }
        }
        return usage
    }
}

enum DeviceInfo {
    struct Snapshot {
        var hwid: String
        var model: String
        var osVersion: String
    }

    static var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
    }

    /// Постоянный идентификатор устройства для лимита устройств в панели.
    static var hwid: String {
        let key = "hwid"
        if let saved = AppGroup.defaults.string(forKey: key) { return saved }
        let value = UUID().uuidString.lowercased()
        AppGroup.defaults.set(value, forKey: key)
        return value
    }

    @MainActor
    static func current() -> Snapshot {
        var info = utsname()
        uname(&info)
        let machine = withUnsafeBytes(of: &info.machine) { raw in
            String(decoding: raw.prefix(while: { $0 != 0 }), as: UTF8.self)
        }
        return Snapshot(hwid: hwid, model: machine, osVersion: UIDevice.current.systemVersion)
    }
}

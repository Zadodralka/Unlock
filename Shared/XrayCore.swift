import Foundation
import LibXray

/// Тонкая обёртка над единственной точкой входа libXray — `CGoInvoke`.
/// Запрос и ответ — JSON: {"apiVersion":3,"method":...,"payload":{...}} -> {"success","data","error"}.
enum XrayCore {
    struct Failure: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }

    struct PingResult {
        let success: Bool
        let delay: Int
    }

    @discardableResult
    static func invoke(_ method: String, payload: [String: Any] = [:]) throws -> Any? {
        let request: [String: Any] = ["apiVersion": 3, "method": method, "payload": payload]
        let requestData = try JSONSerialization.data(withJSONObject: request)
        let requestText = String(decoding: requestData, as: UTF8.self)

        let responseText: String = requestText.withCString { pointer in
            guard let result = CGoInvoke(UnsafeMutablePointer(mutating: pointer)) else { return "" }
            defer { CGoFree(result) }
            return String(cString: result)
        }

        guard
            let object = try? JSONSerialization.jsonObject(with: Data(responseText.utf8)),
            let response = object as? [String: Any]
        else {
            throw Failure(message: "libXray: неожиданный ответ")
        }
        guard response["success"] as? Bool == true else {
            let message = response["error"] as? String
            throw Failure(message: message?.isEmpty == false ? message! : "libXray: ошибка \(method)")
        }
        return response["data"]
    }

    static func version() -> String {
        let data = try? invoke("xrayVersion") as? [String: Any]
        return data?["version"] as? String ?? "?"
    }

    /// Ссылки vless://, vmess://, trojan://, ss://, hy2:// (по одной на строку),
    /// base64-подписка или Xray JSON -> список outbound-объектов.
    static func parseShareLinks(_ text: String) throws -> [[String: Any]] {
        let data = try invoke("convertShareLinksToXrayJson", payload: ["text": text]) as? [String: Any]
        return data?["outbounds"] as? [[String: Any]] ?? []
    }

    /// Реальная задержка через сервер (HTTP-запрос через outbound). Не больше 5 за вызов.
    static func ping(outbounds: [String], url: String = "https://www.gstatic.com/generate_204",
                     timeoutSeconds: Int = 5) throws -> [PingResult] {
        let configs: [[String: Any]] = try outbounds.map { json in
            var outbound = try JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any] ?? [:]
            outbound["tag"] = "ping"
            let config = try JSONSerialization.data(withJSONObject: ["outbounds": [outbound]])
            return ["xrayJson": String(decoding: config, as: UTF8.self), "outboundTag": "ping"]
        }
        let data = try invoke("pingBatch", payload: [
            "configs": configs, "timeout": timeoutSeconds, "url": url,
        ]) as? [String: Any]
        let results = data?["results"] as? [[String: Any]] ?? []
        return results.map {
            PingResult(success: $0["success"] as? Bool ?? false,
                       delay: ($0["delay"] as? NSNumber)?.intValue ?? -1)
        }
    }

    static func run(config: [String: Any]) throws {
        let data = try JSONSerialization.data(withJSONObject: config)
        try invoke("runXray", payload: ["xrayJson": String(decoding: data, as: UTF8.self)])
    }

    static func stop() {
        _ = try? invoke("stopXray")
    }
}

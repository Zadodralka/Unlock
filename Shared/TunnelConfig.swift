import Foundation

/// Сетевые параметры туннеля и полный конфиг Xray вокруг outbound выбранного сервера.
enum TunnelConfig {
    static let mtu = 1500
    static let tunAddress = "172.19.0.1"
    static let tunSubnetMask = "255.255.255.252"
    /// DNS, который получает система. Запросы к нему перехватывает Xray и
    /// резолвит через прокси, так что провайдер их не видит.
    static let dnsServers = ["1.1.1.1", "8.8.8.8"]

    /// Локальные сети идут мимо туннеля (принтеры, роутер, AirPlay).
    static let excludedIPv4: [(String, String)] = [
        ("10.0.0.0", "255.0.0.0"),
        ("100.64.0.0", "255.192.0.0"),
        ("127.0.0.0", "255.0.0.0"),
        ("169.254.0.0", "255.255.0.0"),
        ("172.16.0.0", "255.240.0.0"),
        ("192.168.0.0", "255.255.0.0"),
        ("224.0.0.0", "240.0.0.0"),
    ]

    static func xrayConfig(outbound: [String: Any], tunFD: Int32, logPath: String) -> [String: Any] {
        var proxy = outbound
        proxy["tag"] = "proxy"

        return [
            "log": ["loglevel": "warning", "access": "none", "error": logPath],
            // Xray-core берёт дескриптор utun из NetworkExtension из этой переменной
            "env": ["xray.tun.fd": String(tunFD)],
            "dns": [
                "servers": dnsServers,
                // Без AAAA приложения не пытаются ходить по IPv6, который в туннель не заведён
                "queryStrategy": "UseIPv4",
            ],
            "inbounds": [[
                "tag": "tun",
                "protocol": "tun",
                "port": 0,
                "settings": ["name": "utun", "mtu": mtu],
                "sniffing": ["enabled": true, "destOverride": ["http", "tls"]],
            ]],
            // Первый outbound — маршрут по умолчанию
            "outbounds": [
                proxy,
                ["tag": "direct", "protocol": "freedom"],
                ["tag": "dns-out", "protocol": "dns"],
            ],
            "routing": [
                "domainStrategy": "AsIs",
                "rules": [
                    ["type": "field", "inboundTag": ["tun"], "port": "53", "outboundTag": "dns-out"],
                    ["type": "field", "ip": excludedIPv4.map { cidr($0.0, $0.1) }, "outboundTag": "direct"],
                ],
            ],
        ]
    }

    private static func cidr(_ address: String, _ mask: String) -> String {
        let bits = mask.split(separator: ".").compactMap { UInt8($0) }.map { $0.nonzeroBitCount }.reduce(0, +)
        return "\(address)/\(bits)"
    }
}

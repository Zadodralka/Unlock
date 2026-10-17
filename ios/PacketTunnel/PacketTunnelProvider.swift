import Darwin
import Foundation
import NetworkExtension

/// Процесс туннеля: поднимает utun-интерфейс через NetworkExtension и запускает
/// внутри него Xray с TUN-inbound, которому передаётся дескриптор utun.
final class PacketTunnelProvider: NEPacketTunnelProvider {
    private enum TunnelError: LocalizedError {
        case noServer
        case badServerConfig
        case noTunDescriptor

        var errorDescription: String? {
            switch self {
            case .noServer: return "Сервер не выбран"
            case .badServerConfig: return "Повреждён конфиг сервера"
            case .noTunDescriptor: return "Не удалось получить дескриптор utun"
            }
        }
    }

    override func startTunnel(options: [String: NSObject]?, completionHandler: @escaping (Error?) -> Void) {
        let outbound: [String: Any]
        do {
            guard let data = try? Data(contentsOf: AppGroup.activeOutboundURL) else { throw TunnelError.noServer }
            guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                throw TunnelError.badServerConfig
            }
            outbound = object
        } catch {
            completionHandler(error)
            return
        }

        try? Data().write(to: AppGroup.xrayLogURL)

        setTunnelNetworkSettings(makeNetworkSettings(outbound: outbound)) { [weak self] error in
            guard let self else { return }
            if let error {
                completionHandler(error)
                return
            }
            guard let fd = self.tunnelFileDescriptor else {
                completionHandler(TunnelError.noTunDescriptor)
                return
            }
            do {
                let config = TunnelConfig.xrayConfig(outbound: outbound, tunFD: fd,
                                                     logPath: AppGroup.xrayLogURL.path)
                try XrayCore.run(config: config)
                completionHandler(nil)
            } catch {
                completionHandler(error)
            }
        }
    }

    override func stopTunnel(with reason: NEProviderStopReason, completionHandler: @escaping () -> Void) {
        XrayCore.stop()
        completionHandler()
    }

    private func makeNetworkSettings(outbound: [String: Any]) -> NEPacketTunnelNetworkSettings {
        let server = (outbound["settings"] as? [String: Any])?["address"] as? String ?? "127.0.0.1"
        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: server)
        settings.mtu = NSNumber(value: TunnelConfig.mtu)

        let ipv4 = NEIPv4Settings(addresses: [TunnelConfig.tunAddress], subnetMasks: [TunnelConfig.tunSubnetMask])
        ipv4.includedRoutes = [NEIPv4Route.default()]
        ipv4.excludedRoutes = TunnelConfig.excludedIPv4.map { NEIPv4Route(destinationAddress: $0.0, subnetMask: $0.1) }
        settings.ipv4Settings = ipv4

        let dns = NEDNSSettings(servers: TunnelConfig.dnsServers)
        dns.matchDomains = [""]  // весь DNS — через туннель
        settings.dnsSettings = dns
        return settings
    }

    /// Дескриптор utun, который NetworkExtension создал для этого туннеля.
    /// Публичного API нет — перебираем дескрипторы процесса и ищем сокет utun
    /// (так же делают WireGuard и примеры Xray-core).
    private var tunnelFileDescriptor: Int32? {
        var name = [CChar](repeating: 0, count: Int(IFNAMSIZ))
        for fd: Int32 in 0...1024 {
            var length = socklen_t(name.count)
            // SYSPROTO_CONTROL = 2, UTUN_OPT_IFNAME = 2
            if getsockopt(fd, 2, 2, &name, &length) == 0, String(cString: name).hasPrefix("utun") {
                return fd
            }
        }
        return nil
    }
}

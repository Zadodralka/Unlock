import Foundation

/// Общие файлы приложения и туннеля. Оба процесса видят один контейнер App Group,
/// его идентификатор подставляется в Info.plist из build-настройки APP_GROUP_ID.
enum AppGroup {
    static let identifier: String =
        Bundle.main.object(forInfoDictionaryKey: "AppGroupID") as? String ?? ""

    static var containerURL: URL {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier)
            ?? FileManager.default.temporaryDirectory
    }

    /// Outbound выбранного сервера (JSON-объект Xray), который поднимает туннель.
    static var activeOutboundURL: URL { containerURL.appendingPathComponent("active-outbound.json") }

    /// Подписки и серверы.
    static var profilesURL: URL { containerURL.appendingPathComponent("profiles.json") }

    /// Журнал ядра Xray из туннеля — показывается в настройках приложения.
    static var xrayLogURL: URL { containerURL.appendingPathComponent("xray.log") }

    static var defaults: UserDefaults { UserDefaults(suiteName: identifier) ?? .standard }
}

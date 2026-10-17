import SwiftUI

/// О приложении, идентификатор устройства и журнал ядра для отладки.
struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var xrayVersion = "…"
    @State private var log = ""

    var body: some View {
        NavigationStack {
            List {
                Section("Приложение") {
                    LabeledContent("Версия", value: DeviceInfo.appVersion)
                    LabeledContent("Xray-core", value: xrayVersion)
                    LabeledContent("HWID") {
                        Text(DeviceInfo.hwid).font(.caption.monospaced()).textSelection(.enabled)
                    }
                }

                Section {
                    if log.isEmpty {
                        Text("Пусто. Журнал появится после подключения.").foregroundStyle(.secondary)
                    } else {
                        Text(log)
                            .font(.caption2.monospaced())
                            .textSelection(.enabled)
                    }
                } header: {
                    HStack {
                        Text("Журнал Xray")
                        Spacer()
                        Button("Обновить") { loadLog() }.textCase(nil)
                        if !log.isEmpty {
                            Button("Копировать") { UIPasteboard.general.string = log }.textCase(nil)
                        }
                    }
                }
            }
            .navigationTitle("Настройки")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }
                }
            }
            .task {
                loadLog()
                xrayVersion = await Task.detached { XrayCore.version() }.value
            }
        }
    }

    private func loadLog() {
        let text = (try? String(contentsOf: AppGroup.xrayLogURL, encoding: .utf8)) ?? ""
        // Хвост, чтобы не тормозил длинный журнал
        log = String(text.suffix(8000))
    }
}

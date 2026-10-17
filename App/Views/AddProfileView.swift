import SwiftUI

/// Добавление подписки или серверов: вставить вручную или из буфера обмена.
struct AddProfileView: View {
    @EnvironmentObject private var store: ProfileStore
    @Environment(\.dismiss) private var dismiss
    @State private var input = ""
    @State private var isLoading = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("https://… или vless://…", text: $input, axis: .vertical)
                        .lineLimit(3...8)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .font(.callout.monospaced())
                    Button {
                        input = UIPasteboard.general.string ?? ""
                    } label: {
                        Label("Вставить из буфера", systemImage: "doc.on.clipboard")
                    }
                } footer: {
                    Text("Ссылка подписки загрузит все её серверы и будет обновляться. Ссылки на отдельные серверы можно вставить по одной на строку.")
                }

                if let error {
                    Section { Text(error).foregroundStyle(.red) }
                }
            }
            .navigationTitle("Добавить")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isLoading {
                        ProgressView()
                    } else {
                        Button("Добавить") { Task { await add() } }
                            .disabled(input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
            }
        }
    }

    private func add() async {
        isLoading = true
        error = nil
        defer { isLoading = false }
        do {
            try await store.add(input: input)
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
    }
}

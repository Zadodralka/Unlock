import NetworkExtension
import SwiftUI

/// Главный экран: большая кнопка подключения и список серверов по подпискам.
struct HomeView: View {
    @EnvironmentObject private var store: ProfileStore
    @EnvironmentObject private var vpn: VPNController
    @State private var showAdd = false
    @State private var showSettings = false
    @State private var refreshError: String?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ConnectCard()
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }

                if store.subscriptions.isEmpty {
                    Section {
                        Button {
                            showAdd = true
                        } label: {
                            Label("Добавить подписку", systemImage: "plus.circle.fill")
                        }
                    } footer: {
                        Text("Вставьте ссылку подписки (https://…) или ссылку на сервер (vless://, vmess://, trojan://, ss://, hy2://).")
                    }
                }

                ForEach(store.subscriptions) { subscription in
                    SubscriptionSection(subscription: subscription, refreshError: $refreshError)
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Unlock")
            .refreshable {
                for subscription in store.subscriptions where subscription.url != nil {
                    do { try await store.refresh(subscription.id) } catch { refreshError = error.localizedDescription }
                }
            }
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button { showSettings = true } label: { Image(systemName: "gearshape") }
                }
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button {
                        Task { await store.pingAll() }
                    } label: {
                        if store.isPinging { ProgressView() } else { Image(systemName: "bolt.horizontal") }
                    }
                    .disabled(store.subscriptions.isEmpty || store.isPinging)

                    Button { showAdd = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $showAdd) { AddProfileView() }
            .sheet(isPresented: $showSettings) { SettingsView() }
            .alert("Ошибка", isPresented: .constant(refreshError != nil)) {
                Button("OK") { refreshError = nil }
            } message: {
                Text(refreshError ?? "")
            }
        }
    }
}

private struct ConnectCard: View {
    @EnvironmentObject private var store: ProfileStore
    @EnvironmentObject private var vpn: VPNController

    var body: some View {
        VStack(spacing: 16) {
            Button {
                Task { await vpn.toggle(server: store.selectedServer) }
            } label: {
                ZStack {
                    Circle()
                        .fill(buttonColor.gradient)
                        .frame(width: 150, height: 150)
                        .shadow(color: buttonColor.opacity(0.4), radius: 16)
                    if vpn.status == .connecting || vpn.status == .disconnecting || vpn.status == .reasserting {
                        ProgressView().tint(.white).scaleEffect(1.6)
                    } else {
                        Image(systemName: "power")
                            .font(.system(size: 54, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                }
            }
            .buttonStyle(.plain)

            VStack(spacing: 4) {
                Text(vpn.status.title).font(.headline)
                if vpn.status == .connected, let since = vpn.connectedSince {
                    Text(since, style: .timer).font(.subheadline.monospacedDigit()).foregroundStyle(.secondary)
                }
                if let server = store.selectedServer {
                    Text(server.name).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                }
            }

            if let error = vpn.lastError {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
    }

    private var buttonColor: Color {
        switch vpn.status {
        case .connected: return .green
        case .connecting, .reasserting: return .orange
        default: return .gray
        }
    }
}

private struct SubscriptionSection: View {
    @EnvironmentObject private var store: ProfileStore
    @EnvironmentObject private var vpn: VPNController
    let subscription: Subscription
    @Binding var refreshError: String?

    var body: some View {
        Section {
            if let announce = subscription.announce {
                Label(announce, systemImage: "megaphone").font(.footnote)
            }
            ForEach(subscription.servers) { server in
                ServerRow(server: server, ping: store.pings[server.id],
                          isSelected: server.id == store.selectedServer?.id)
                    .contentShape(Rectangle())
                    .onTapGesture { select(server) }
                    .swipeActions {
                        if subscription.url == nil {
                            Button("Удалить", role: .destructive) { store.deleteServer(server) }
                        }
                    }
            }
        } header: {
            HStack {
                Text(subscription.title)
                Spacer()
                if store.refreshingIDs.contains(subscription.id) {
                    ProgressView().controlSize(.small)
                }
                Menu {
                    if subscription.url != nil {
                        Button {
                            Task {
                                do { try await store.refresh(subscription.id) } catch { refreshError = error.localizedDescription }
                            }
                        } label: { Label("Обновить", systemImage: "arrow.clockwise") }
                        Button {
                            UIPasteboard.general.string = subscription.url
                        } label: { Label("Скопировать ссылку", systemImage: "doc.on.doc") }
                    }
                    if let support = subscription.supportURL, let url = URL(string: support) {
                        Link(destination: url) { Label("Поддержка", systemImage: "questionmark.circle") }
                    }
                    Button(role: .destructive) {
                        store.delete(subscription.id)
                    } label: { Label("Удалить", systemImage: "trash") }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .textCase(nil)
            }
        } footer: {
            if let usage = subscription.usage {
                UsageFooter(usage: usage)
            }
        }
    }

    private func select(_ server: ServerNode) {
        guard server.id != store.selectedServer?.id else { return }
        store.selectedServerID = server.id
        if vpn.isActive {
            Task { await vpn.connect(to: server) }
        }
    }
}

private struct ServerRow: View {
    let server: ServerNode
    let ping: Int?
    let isSelected: Bool

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
            VStack(alignment: .leading, spacing: 2) {
                Text(server.name).lineLimit(1)
                Text("\(server.protocolName.uppercased()) · \(server.transport)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if let ping {
                Text(ping < 0 ? "—" : "\(ping) мс")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(pingColor(ping))
            }
        }
    }

    private func pingColor(_ ping: Int) -> Color {
        switch ping {
        case ..<0: return .red
        case ..<300: return .green
        case ..<800: return .orange
        default: return .red
        }
    }
}

private struct UsageFooter: View {
    let usage: SubscriptionUsage

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("Трафик: \(format(usage.used)) из \(usage.total > 0 ? format(usage.total) : "∞")")
            if let expire = usage.expire {
                Text("Действует до \(expire.formatted(date: .long, time: .omitted))")
            }
        }
    }

    private func format(_ bytes: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: bytes, countStyle: .binary)
    }
}

//
//  UnlockedDashboardView.swift
//  Orange Cloud
//
//  TrollStore unlocked build dashboard: keep the landing tab stable by avoiding
//  system integrations and automatic dashboard data fan-out.
//

import SwiftUI

struct UnlockedDashboardView: View {

    @Environment(AuthManager.self) private var auth
    let session: SessionStore

    private var accountName: String {
        session.selectedAccount?.name ?? String(localized: "未选择账号")
    }

    private var statusText: String {
        if session.isLoadingAccounts { return String(localized: "正在加载账号") }
        if let error = session.error { return error }
        if session.selectedAccount == nil { return String(localized: "当前身份下没有账号") }
        return String(localized: "已解锁 Pro")
    }

    private var statusColor: Color {
        session.error == nil ? Color.secondary : Color.red
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(accountName)
                            .font(.title2.bold())
                            .lineLimit(2)
                        Text(statusText)
                            .font(.subheadline)
                            .foregroundStyle(statusColor)
                    }
                    .padding(.vertical, 6)
                }

                Section("快速进入") {
                    routeButton("域名", systemImage: "globe", module: .zones)
                    routeButton("Workers", systemImage: "bolt.fill", module: .workers)
                    routeButton("存储", systemImage: "externaldrive", module: .storage)
                    routeButton("设置", systemImage: "gear", module: .settings)
                }

                Section("授权") {
                    LabeledContent("Scopes", value: "\(auth.currentSession?.scopes.count ?? 0)")
                    LabeledContent("会话", value: auth.currentSession?.label ?? "—")
                }
            }
            .navigationTitle("概览")
            .refreshable {
                recordBreadcrumb("refresh begin")
                await session.ensureAccounts()
                recordBreadcrumb("refresh end")
            }
        }
        .task {
            recordBreadcrumb("task")
        }
        .onAppear {
            recordBreadcrumb("appear")
        }
        .onChange(of: session.accounts.count) {
            recordBreadcrumb("accounts count changed")
        }
        .onChange(of: session.selectedAccount?.id) {
            recordBreadcrumb("selected account changed")
        }
    }

    private func routeButton(_ title: String, systemImage: String, module: AppModule) -> some View {
        Button {
            recordBreadcrumb("route \(module.rawValue)")
            AppRouter.shared.pendingModule = module
        } label: {
            Label(title, systemImage: systemImage)
        }
    }

    private func recordBreadcrumb(_ event: String) {
        CrashReporter.recordBreadcrumb(
            "UnlockedDashboard \(event) accounts=\(session.accounts.count)"
            + " selectedAccount=\(session.selectedAccount != nil)"
            + " loading=\(session.isLoadingAccounts)"
            + " error=\(session.error != nil)"
            + " scopes=\(auth.grantedScopes.count)"
        )
    }
}

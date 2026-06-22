//
//  MainTabView.swift
//  Orange Cloud
//
//  iPhone 底部 Tab，iPad 自动侧边栏（sidebarAdaptable，iOS 18+）；iOS 17 回退经典 TabView。
//  iOS 26 自动 Liquid Glass TabBar。
//

import SwiftUI

struct MainTabView: View {

    @Environment(SessionStore.self) private var session
    @Environment(AuthManager.self) private var auth
    @State private var selectedTab: AppTab = .dashboard
    private let router = AppRouter.shared

    var body: some View {
        tabContainer
            .task {
                recordBreadcrumb("task")
                consumePendingRoute()
                await session.ensureAccounts()
                recordBreadcrumb("accounts ensured")
            }
            .onChange(of: router.pendingModule) {
                recordBreadcrumb("pending route changed")
                consumePendingRoute()
            }
            .onChange(of: selectedTab) {
                recordBreadcrumb("selected tab changed")
            }
            .onAppear {
                recordBreadcrumb("appear")
            }
    }

    @ViewBuilder
    private var tabContainer: some View {
        if #available(iOS 18.0, *) {
            // iOS 18+：值式 Tab + 侧边栏自适应（iPad 自动侧边栏）
            TabView(selection: $selectedTab) {
                Tab("概览", systemImage: "square.grid.2x2", value: AppTab.dashboard) {
                    dashboardTab
                }
                Tab("域名", systemImage: "globe", value: AppTab.zones) {
                    zonesTab
                }
                Tab("Workers", systemImage: "bolt.fill", value: AppTab.workers) {
                    workersTab
                }
                Tab("存储", systemImage: "externaldrive", value: AppTab.storage) {
                    storageTab
                }
                Tab("设置", systemImage: "gear", value: AppTab.settings) {
                    settingsTab
                }
            }
            .tabViewStyle(.sidebarAdaptable)
        } else {
            // iOS 17：经典 TabView（底部 Tab；iPad 不走侧边栏自适应）
            TabView(selection: $selectedTab) {
                dashboardTab
                    .tabItem { Label("概览", systemImage: "square.grid.2x2") }
                    .tag(AppTab.dashboard)
                zonesTab
                    .tabItem { Label("域名", systemImage: "globe") }
                    .tag(AppTab.zones)
                workersTab
                    .tabItem { Label("Workers", systemImage: "bolt.fill") }
                    .tag(AppTab.workers)
                storageTab
                    .tabItem { Label("存储", systemImage: "externaldrive") }
                    .tag(AppTab.storage)
                settingsTab
                    .tabItem { Label("设置", systemImage: "gear") }
                    .tag(AppTab.settings)
            }
        }
    }

    // MARK: - Tab 内容（两套 TabView 写法共用）

    // 各资源 Tab 用 .id(selectedAccount) 绑定当前账号：账号切换时整页重建，
    // 让按账号过滤的 @Query 谓词刷新、列表数据重新拉取（资源跟着选中账号走）。

    @ViewBuilder private var dashboardTab: some View {
        #if OPENSOURCE_UNLOCKED
        UnlockedDashboardView(session: session)
        #else
        DashboardView(session: session)
            .id(session.selectedAccount?.id)
        #endif
    }

    @ViewBuilder private var zonesTab: some View {
        ZoneListView(session: session)
            .id(session.selectedAccount?.id)
    }

    @ViewBuilder private var workersTab: some View {
        // Tab 恒显示（可发现性），无权限时整页锁定态
        if auth.hasScope("workers-scripts.read") {
            WorkerListView(session: session)
                .id(session.selectedAccount?.id)
        } else {
            NavigationStack {
                PermissionDeniedView(
                    featureName: "Workers",
                    requiredScope: "workers-scripts.read"
                )
                .navigationTitle("Workers")
            }
        }
    }

    @ViewBuilder private var storageTab: some View {
        StorageView(session: session)
            .id(session.selectedAccount?.id)
    }

    @ViewBuilder private var settingsTab: some View {
        SettingsView()
    }

    /// App Intent（Siri/快捷指令/Spotlight）发起的跳转
    private func consumePendingRoute() {
        guard let module = router.pendingModule else { return }
        CrashReporter.recordBreadcrumb("MainTab consume route module=\(module.rawValue)")
        router.pendingModule = nil
        selectedTab = switch module {
        case .dashboard: .dashboard
        case .zones:     .zones
        case .workers:   .workers
        case .storage:   .storage
        case .settings:  .settings
        }
    }

    enum AppTab: Hashable {
        case dashboard, zones, workers, storage, settings
    }

    private func recordBreadcrumb(_ event: String) {
        CrashReporter.recordBreadcrumb(
            "MainTab \(event) tab=\(selectedTab) accounts=\(session.accounts.count)"
            + " selectedAccount=\(session.selectedAccount != nil)"
            + " loading=\(session.isLoadingAccounts)"
            + " error=\(session.error != nil)"
            + " scopes=\(auth.grantedScopes.count)"
        )
    }
}

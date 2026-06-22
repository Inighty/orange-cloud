//
//  Orange_CloudApp.swift
//  Orange Cloud
//
//  Created by 陳柘 on 2026/6/10.
//

import SwiftUI
import SwiftData
import TipKit
import CoreSpotlight
import ActivityKit

@main
struct Orange_CloudApp: App {

    @State private var authManager: AuthManager?
    @State private var lastCrashReport: CrashReport?
    @Environment(\.scenePhase) private var scenePhase

    @AppStorage(AppAppearance.storageKey) private var appearanceRaw = AppAppearance.system.rawValue

    init() {
        CrashReporter.install()
        BackgroundRefresh.register()
        let pendingCrash = CrashReporter.pendingReport()
        _lastCrashReport = State(initialValue: pendingCrash)
        WhatsNewGate.suppressAtLaunch = pendingCrash != nil
        _authManager = State(initialValue: pendingCrash == nil ? Self.startApplication() : nil)
    }

    var body: some Scene {
        WindowGroup {
            rootContent
        }
        .onChange(of: scenePhase) {
            AppLog.app.info("scenePhase -> \(String(describing: scenePhase))")
            if scenePhase == .background {
                BackgroundRefresh.schedule()
            }
        }
    }

    @ViewBuilder
    private var rootContent: some View {
        if let report = lastCrashReport {
            CrashReportView(report: report) {
                enterApplicationAfterCrashReport()
            }
            .preferredColorScheme(AppAppearance(rawValue: appearanceRaw)?.colorScheme)
        } else if let authManager {
            applicationContent(authManager: authManager)
        } else {
            LaunchingApplicationView()
                .preferredColorScheme(AppAppearance(rawValue: appearanceRaw)?.colorScheme)
                .task {
                    startApplicationIfNeeded()
                }
        }
    }

    private func applicationContent(authManager: AuthManager) -> some View {
        ContentView()
            .environment(authManager)
            .environment(EntitlementStore.shared)
            .tint(.ocOrange)
            .preferredColorScheme(AppAppearance(rawValue: appearanceRaw)?.colorScheme)
            .onContinueUserActivity(CSSearchableItemActionType) { activity in
                handleSpotlightTap(activity)
            }
            .modelContainer(CacheContainer.shared)
    }

    private func enterApplicationAfterCrashReport() {
        CrashReporter.clearPendingReport()
        CrashReporter.recordBreadcrumb("CrashReport enter application tapped")
        WhatsNewGate.suppressAtLaunch = false
        lastCrashReport = nil
    }

    private func startApplicationIfNeeded() {
        guard authManager == nil else { return }
        authManager = Self.startApplication()
    }

    private static func startApplication() -> AuthManager {
        CrashReporter.recordBreadcrumb("AppStart begin")
        let manager = AuthManager()
        CrashReporter.recordBreadcrumb("AppStart auth manager created")
        WhatsNewGate.wasLoggedInAtLaunch = manager.isLoggedIn
        CrashReporter.recordBreadcrumb("AppStart whats new gate updated")
        BackgroundRefresh.setAuthManager(manager)
        CrashReporter.recordBreadcrumb("AppStart background refresh auth attached")
        WatchSessionManager.shared.start(authManager: manager)
        CrashReporter.recordBreadcrumb("AppStart watch session started")
        EntitlementStore.shared.start()
        CrashReporter.recordBreadcrumb("AppStart entitlement store started")
        reapOrphanTailActivities()
        CrashReporter.recordBreadcrumb("AppStart orphan activities reaped")
        try? Tips.configure()
        CrashReporter.recordBreadcrumb("AppStart tips configured")
        AppLog.logLaunch(
            loggedIn: manager.isLoggedIn,
            sessionCount: manager.sessions.count,
            iCloudSync: manager.iCloudSyncEnabled
        )
        CrashReporter.recordBreadcrumb("AppStart launch logged")
        return manager
    }

    /// 收尸：结束上次进程残留的 tail Live Activity。冷启动时没有任何 VM 持有引用，
    /// 屏上若还挂着卡片，必是崩溃 / 强杀遗留的孤儿——逐个 .immediate 结束。
    private static func reapOrphanTailActivities() {
        for activity in Activity<TailActivityAttributes>.activities {
            Task { await activity.end(nil, dismissalPolicy: .immediate) }
        }
    }

    /// Spotlight 搜索结果点击：跳到对应模块（Zone/DNS 都归属 Zones Tab）
    private func handleSpotlightTap(_ activity: NSUserActivity) {
        guard activity.userInfo?[CSSearchableItemActivityIdentifier] is String else { return }
        AppRouter.shared.pendingModule = .zones
    }
}

private struct CrashReportView: View {

    let report: CrashReport
    let onDismiss: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                Text(report.text)
                    .font(.system(.caption, design: .monospaced))
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
            }
            .navigationTitle("上次崩溃日志")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("进入应用", action: onDismiss)
                }
            }
        }
    }
}

private struct LaunchingApplicationView: View {

    var body: some View {
        ProgressView("正在进入应用")
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

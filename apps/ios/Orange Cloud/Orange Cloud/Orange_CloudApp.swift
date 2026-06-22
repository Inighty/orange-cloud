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

    @State private var authManager: AuthManager
    @State private var lastCrashReport: CrashReport?
    @Environment(\.scenePhase) private var scenePhase

    @AppStorage(AppAppearance.storageKey) private var appearanceRaw = AppAppearance.system.rawValue

    init() {
        CrashReporter.install()
        let pendingCrash = CrashReporter.pendingReport()
        _lastCrashReport = State(initialValue: pendingCrash)
        let manager = AuthManager()
        _authManager = State(initialValue: manager)
        WhatsNewGate.suppressAtLaunch = pendingCrash != nil
        WhatsNewGate.wasLoggedInAtLaunch = manager.isLoggedIn
        BackgroundRefresh.register(authManager: manager)
        WatchSessionManager.shared.start(authManager: manager)
        EntitlementStore.shared.start()
        Self.reapOrphanTailActivities()
        try? Tips.configure()
        AppLog.logLaunch(
            loggedIn: manager.isLoggedIn,
            sessionCount: manager.sessions.count,
            iCloudSync: manager.iCloudSyncEnabled
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(authManager)
                .environment(EntitlementStore.shared)
                .tint(.ocOrange)   // 全局品牌橙（Cloudflare #F48120）
                .preferredColorScheme(AppAppearance(rawValue: appearanceRaw)?.colorScheme)
                .onContinueUserActivity(CSSearchableItemActionType) { activity in
                    handleSpotlightTap(activity)
                }
                .sheet(item: $lastCrashReport) { report in
                    CrashReportSheet(report: report) {
                        CrashReporter.clearPendingReport()
                        WhatsNewGate.suppressAtLaunch = false
                        lastCrashReport = nil
                    }
                }
        }
        .modelContainer(CacheContainer.shared)
        .onChange(of: scenePhase) {
            AppLog.app.info("scenePhase -> \(String(describing: scenePhase))")
            if scenePhase == .background {
                BackgroundRefresh.schedule()
            }
        }
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

private struct CrashReportSheet: View {

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
                    Button("已读", action: onDismiss)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

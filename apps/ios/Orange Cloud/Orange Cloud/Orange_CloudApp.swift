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
    @Environment(\.scenePhase) private var scenePhase

    @AppStorage(AppAppearance.storageKey) private var appearanceRaw = AppAppearance.system.rawValue

    let sharedModelContainer = CacheContainer.shared

    init() {
        let manager = AuthManager()
        _authManager = State(initialValue: manager)
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
        }
        .modelContainer(sharedModelContainer)
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

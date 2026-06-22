//
//  BackgroundRefresh.swift
//  Orange Cloud
//
//  BGAppRefreshTask：后台静默刷新 OAuth Token，避免用户回到 App 时 Token 已过期。
//  标识符登记在 Info.plist 的 BGTaskSchedulerPermittedIdentifiers。
//

import Foundation
import BackgroundTasks

@MainActor
enum BackgroundRefresh {

    static let taskIdentifier = "jiamin.chen.Orange-Cloud.refresh"
    private static var didRegister = false
    private static weak var authManager: AuthManager?

    /// App 启动时注册（必须在 didFinishLaunching 前，App.init 中调用）
    static func register() {
        guard !didRegister else { return }
        didRegister = true
        let success = BGTaskScheduler.shared.register(forTaskWithIdentifier: taskIdentifier, using: nil) { task in
            guard let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            let work = Task { @MainActor in
                await run(task: refreshTask)
            }
            refreshTask.expirationHandler = {
                AppLog.background.error("BGAppRefresh expired (system cut off)")
                work.cancel()
                refreshTask.setTaskCompleted(success: false)
            }
        }
        AppLog.background.info("BGAppRefresh register success=\(success)")
    }

    static func setAuthManager(_ manager: AuthManager) {
        authManager = manager
        AppLog.background.info("BGAppRefresh auth manager attached")
    }

    /// 进入后台时调度（系统决定实际执行时机）
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 4 * 3600)   // 至少 4 小时后
        do {
            try BGTaskScheduler.shared.submit(request)
            AppLog.background.info("scheduled BGAppRefresh (earliest +4h)")
        } catch {
            AppLog.background.error("schedule BGAppRefresh failed: \(error.localizedDescription)")
        }
    }

    private static func run(task refreshTask: BGAppRefreshTask) async {
        schedule()
        guard let authManager else {
            AppLog.background.error("BGAppRefresh fired without AuthManager")
            refreshTask.setTaskCompleted(success: false)
            return
        }
        AppLog.background.notice("BGAppRefresh fired, loggedIn=\(authManager.isLoggedIn)")
        if authManager.isLoggedIn {
            _ = try? await authManager.refreshAccessToken()
            // 顺带做通知检测（Zone 状态变化 / Worker 错误）
            await AppNotifications.runBackgroundChecks(authManager: authManager)
        }
        refreshTask.setTaskCompleted(success: true)
        AppLog.background.info("BGAppRefresh completed")
    }
}

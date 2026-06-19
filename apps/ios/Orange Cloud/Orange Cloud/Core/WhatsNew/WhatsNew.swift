//
//  WhatsNew.swift
//  Orange Cloud
//
//  版本更新后的「新功能」展示：内容按版本curated，启动后比对 lastSeen 决定是否弹。
//  新增一个版本只需往 WhatsNewContent.releases 追加一条（version 用 MARKETING_VERSION）。
//

import Foundation

nonisolated struct WhatsNewItem: Identifiable, Sendable {
    let id = UUID()
    let icon:   String
    let title:  String
    let detail: String
}

nonisolated struct WhatsNewRelease: Sendable {
    let version: String
    let items:   [WhatsNewItem]
}

nonisolated enum WhatsNewContent {
    static let releases: [WhatsNewRelease] = [
        WhatsNewRelease(version: "1.3.0", items: [
            WhatsNewItem(
                icon:   "globe.badge.chevron.right",
                title:  String(localized: "添加域名"),
                detail: String(localized: "在 App 里把已注册的域名加入账号，并拿到要去注册商处配置的名称服务器。")
            ),
            WhatsNewItem(
                icon:   "network",
                title:  String(localized: "Tunnel 管理"),
                detail: String(localized: "不再只是查看——新建隧道、获取连接令牌与命令、配置公共主机名路由。")
            ),
            WhatsNewItem(
                icon:   "cylinder.split.1x2",
                title:  String(localized: "D1 数据库管理"),
                detail: String(localized: "直接新建 D1 数据库，或在原样确认库名后安全删除。")
            ),
            WhatsNewItem(
                icon:   "key",
                title:  String(localized: "变量与密钥"),
                detail: String(localized: "管理 Worker 的环境变量与密钥，随手增删改。")
            ),
            WhatsNewItem(
                icon:   "clock",
                title:  String(localized: "定时触发器"),
                detail: String(localized: "查看与增删 Cron 触发器，让 Worker 按计划自动运行。")
            ),
            WhatsNewItem(
                icon:   "globe",
                title:  String(localized: "域名与路由"),
                detail: String(localized: "管理 workers.dev 子域、自定义域与路由，掌控 Worker 的访问入口。")
            ),
        ]),
        // 1.1.0 / 1.2.0 因审核延误未公开发布，自上个公开版本起的全部新变动统一并入 1.2.1。
        WhatsNewRelease(version: "1.2.1", items: [
            WhatsNewItem(
                icon:   "applewatch",
                title:  String(localized: "Apple Watch App"),
                detail: String(localized: "把域名状态与流量概览带上手腕，还能添加到表盘复杂功能随时一瞥。")
            ),
            WhatsNewItem(
                icon:   "curlybraces",
                title:  String(localized: "Snippets"),
                detail: String(localized: "在域名详情查看、编辑、新建 Cloudflare 边缘代码片段，并管理触发规则——轻量版 Workers，Pro 解锁。")
            ),
            WhatsNewItem(
                icon:   "accessibility",
                title:  String(localized: "全面无障碍"),
                detail: String(localized: "VoiceOver、更大字体、不只靠颜色区分、足够对比度全面达标，配合系统辅助功能更顺手。")
            ),
            WhatsNewItem(
                icon:   "character.bubble",
                title:  String(localized: "更多语言"),
                detail: String(localized: "新增西班牙语、韩语、葡萄牙语，现已支持九种语言。")
            ),
            WhatsNewItem(
                icon:   "arrow.clockwise",
                title:  String(localized: "刷新更省心"),
                detail: String(localized: "刷新失败不再弹窗打断，下拉刷新更稳定可靠。")
            ),
        ]),
    ]
}

nonisolated enum WhatsNewStore {

    static var currentVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
    }

    /// 数值分段比较：a 是否比 b 新（"1.1.0" > "1.0.1"，"1.10" > "1.9"）
    static func isNewer(_ a: String, than b: String) -> Bool {
        a.compare(b, options: .numeric) == .orderedDescending
    }

    /// (seen, current] 区间内所有版本的新功能，新版在前
    static func items(after seen: String, upTo current: String) -> [WhatsNewItem] {
        WhatsNewContent.releases
            .filter { isNewer($0.version, than: seen) && !isNewer($0.version, than: current) }
            .sorted { isNewer($0.version, than: $1.version) }
            .flatMap(\.items)
    }
}

/// 启动时拍一张「是否已登录」的快照，用于区分老用户升级 vs 全新安装：
/// 老用户升级到首个带 What's New 的版本（lastSeen 为空但启动即已登录）要补看一次；
/// 全新安装首次登录后不打扰。由 App.init 在创建 AuthManager 后写入。
enum WhatsNewGate {
    static var wasLoggedInAtLaunch = false
}

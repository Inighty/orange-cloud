//
//  Zone.swift
//  Orange Cloud
//

import Foundation

nonisolated struct Zone: Codable, Identifiable, Hashable, Sendable {
    let id:          String
    let name:        String
    let status:      String          // "active" | "pending" | "paused" 等
    let plan:        ZonePlan?
    let nameServers: [String]?

    enum CodingKeys: String, CodingKey {
        case id, name, status, plan
        case nameServers = "name_servers"
    }
}

nonisolated struct ZonePlan: Codable, Hashable, Sendable {
    let name: String
}

/// POST /zones 请求体。type 为 "full"（Cloudflare 作权威 DNS，需在注册商换 NS）
/// 或 "partial"（CNAME 接入，Business+ 才可用）；本 App 仅走 full。
nonisolated struct CreateZoneRequest: Codable, Sendable {
    let name:    String
    let type:    String
    let account: AccountRef

    nonisolated struct AccountRef: Codable, Sendable {
        let id: String
    }
}

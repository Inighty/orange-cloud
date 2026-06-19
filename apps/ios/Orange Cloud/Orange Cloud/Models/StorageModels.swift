//
//  StorageModels.swift
//  Orange Cloud
//
//  P2 存储模块：R2 / D1 / KV 的数据模型。
//

import Foundation

// MARK: - R2

/// GET /accounts/{id}/r2/buckets 的 result 是 { buckets: [...] }（注意不是数组）
nonisolated struct R2BucketList: Codable, Sendable {
    let buckets: [R2Bucket]
}

nonisolated struct R2Bucket: Codable, Identifiable, Hashable, Sendable {
    let name:         String
    let creationDate: String?
    let location:     String?
    let storageClass: String?

    var id: String { name }

    enum CodingKeys: String, CodingKey {
        case name, location
        case creationDate = "creation_date"
        case storageClass = "storage_class"
    }
}

nonisolated struct R2Object: Codable, Identifiable, Hashable, Sendable {
    let key:          String
    let etag:         String?
    let lastModified: String?
    let size:         Int?
    let httpMetadata: R2HTTPMetadata?
    let storageClass: String?

    var id: String { key }

    enum CodingKeys: String, CodingKey {
        case key, etag, size
        case lastModified = "last_modified"
        case httpMetadata = "http_metadata"
        case storageClass = "storage_class"
    }
}

nonisolated struct R2HTTPMetadata: Codable, Hashable, Sendable {
    let contentType: String?

    enum CodingKeys: String, CodingKey {
        case contentType = "contentType"   // R2 对象元数据是 camelCase
    }
}

// MARK: - R2 账号级指标（GET /accounts/{id}/r2/metrics，r2 read scope 即可）

nonisolated struct R2AccountMetrics: Codable, Sendable {
    let standard:         R2ClassMetrics?
    let infrequentAccess: R2ClassMetrics?

    /// 免费额度只计 Standard 存储
    var standardBytes: Int {
        (standard?.published?.totalBytes ?? 0) + (standard?.unpublished?.totalBytes ?? 0)
    }

    var standardObjects: Int {
        (standard?.published?.objects ?? 0) + (standard?.unpublished?.objects ?? 0)
    }
}

nonisolated struct R2ClassMetrics: Codable, Sendable {
    let published:   R2MetricsSnapshot?
    let unpublished: R2MetricsSnapshot?
}

nonisolated struct R2MetricsSnapshot: Codable, Sendable {
    let objects:      Int?
    let payloadSize:  Int?
    let metadataSize: Int?

    var totalBytes: Int { (payloadSize ?? 0) + (metadataSize ?? 0) }
}

// MARK: - D1

nonisolated struct D1Database: Codable, Identifiable, Hashable, Sendable {
    let uuid:      String
    let name:      String
    let version:   String?
    let createdAt: String?
    let fileSize:  Int?
    let numTables: Int?

    var id: String { uuid }

    enum CodingKeys: String, CodingKey {
        case uuid, name, version
        case createdAt = "created_at"
        case fileSize  = "file_size"
        case numTables = "num_tables"
    }
}

nonisolated struct D1QueryRequest: Codable, Sendable {
    let sql:    String
    let params: [String]?    // 参数化查询（行编辑用，避免拼接注入）
}

/// POST /accounts/{id}/d1/database 的请求体。primaryLocationHint 为空时
/// 编码器自动省略该字段（Optional 走 encodeIfPresent），由 Cloudflare 就近放置。
nonisolated struct D1CreateRequest: Codable, Sendable {
    let name:                String
    let primaryLocationHint: String?

    enum CodingKeys: String, CodingKey {
        case name
        case primaryLocationHint = "primary_location_hint"
    }
}

/// PRAGMA table_info 解析后的列结构
nonisolated struct D1Column: Identifiable, Sendable {
    let name:         String
    let type:         String
    let isPrimaryKey: Bool

    var id: String { name }
}

/// POST /query 的 result 是 [D1QueryResult]（每条语句一个结果）
nonisolated struct D1QueryResult: Codable, Sendable {
    let results: [[String: JSONValue]]?
    let success: Bool
    let meta:    D1QueryMeta?
}

nonisolated struct D1QueryMeta: Codable, Sendable {
    let duration:    Double?
    let changes:     Int?
    let lastRowId:   Int?
    let rowsRead:    Int?
    let rowsWritten: Int?

    enum CodingKeys: String, CodingKey {
        case duration, changes
        case lastRowId   = "last_row_id"
        case rowsRead    = "rows_read"
        case rowsWritten = "rows_written"
    }
}

// MARK: - KV

nonisolated struct KVNamespace: Codable, Identifiable, Hashable, Sendable {
    let id:    String
    let title: String
}

nonisolated struct KVKey: Codable, Identifiable, Hashable, Sendable {
    let name:       String
    let expiration: Int?     // Unix 秒

    var id: String { name }

    var expirationDate: Date? {
        expiration.map { Date(timeIntervalSince1970: TimeInterval($0)) }
    }
}

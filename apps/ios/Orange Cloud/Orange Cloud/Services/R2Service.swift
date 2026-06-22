//
//  R2Service.swift
//  Orange Cloud
//

import Foundation

struct R2Service {

    private let client: CFAPIClient

    init(client: CFAPIClient) {
        self.client = client
    }

    /// Bucket 列表（result 是 { buckets: [...] } 包装）
    func listBuckets(accountId: String) async throws -> [R2Bucket] {
        let response: CFAPIResponse<R2BucketList> = try await client.get(
            "accounts/\(accountId)/r2/buckets",
            queryItems: [URLQueryItem(name: "per_page", value: "100")]
        )
        guard response.success, let list = response.result else {
            throw response.toAPIError()
        }
        return list.buckets
    }

    /// 下载对象内容（原始字节）。key 含特殊字符需预编码。
    func getObjectData(accountId: String, bucketName: String, key: String) async throws -> Data {
        try await client.getRaw(
            "accounts/\(accountId)/r2/buckets/\(bucketName)/objects/\(Self.encodeKey(key))"
        )
    }

    /// 上传对象（原始字节 + Content-Type）
    func putObject(
        accountId: String,
        bucketName: String,
        key: String,
        data: Data,
        contentType: String
    ) async throws {
        let response: CFAPIResponse<EmptyResponse> = try await client.putRaw(
            "accounts/\(accountId)/r2/buckets/\(bucketName)/objects/\(Self.encodeKey(key))",
            body: data,
            contentType: contentType
        )
        guard response.success else {
            throw response.toAPIError()
        }
    }

    /// 删除对象
    func deleteObject(accountId: String, bucketName: String, key: String) async throws {
        try await client.delete(
            "accounts/\(accountId)/r2/buckets/\(bucketName)/objects/\(Self.encodeKey(key))"
        )
    }

    /// R2 key 可含 / 空格等任意字符，必须显式百分号编码（路径视为已编码）
    private nonisolated static func encodeKey(_ key: String) -> String {
        key.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? key
    }

    /// 账号级存储指标（与 Dashboard 同源的当前快照，比 GraphQL 采样更准）
    func accountMetrics(accountId: String) async throws -> R2AccountMetrics {
        let response: CFAPIResponse<R2AccountMetrics> = try await client.get(
            "accounts/\(accountId)/r2/metrics"
        )
        guard response.success, let metrics = response.result else {
            throw response.toAPIError()
        }
        return metrics
    }

    /// 对象列表（游标分页，一次一页）
    func listObjects(_ options: R2ObjectListOptions) async throws -> R2ObjectPage {
        var queryItems = [
            URLQueryItem(name: "per_page", value: "100"),
            URLQueryItem(name: "delimiter", value: "/")
        ]
        if !options.prefix.isEmpty {
            queryItems.append(URLQueryItem(name: "prefix", value: options.prefix))
        }
        if let cursor = options.cursor {
            queryItems.append(URLQueryItem(name: "cursor", value: cursor))
        }
        let response: CFAPIResponseArray<R2Object> = try await client.get(
            "accounts/\(options.accountId)/r2/buckets/\(options.bucketName)/objects",
            queryItems: queryItems
        )
        guard response.success else {
            throw response.toAPIError()
        }
        let isTruncated = response.resultInfo?.isTruncated ?? false
        return R2ObjectPage(
            objects: response.result ?? [],
            folderPrefixes: response.resultInfo?.delimited ?? [],
            nextCursor: isTruncated ? response.resultInfo?.cursor : nil
        )
    }
}

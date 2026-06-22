//
//  StorageViewModels.swift
//  Orange Cloud
//
//  P2 存储模块的 ViewModel 集合。存储数据实时拉取，不进 SwiftData
//  （低频浏览场景，缓存收益低）。
//

import Foundation
import Observation

// MARK: - R2

@Observable
@MainActor
final class R2BucketListViewModel {

    var buckets: [R2Bucket] = []
    var isLoading = false
    var error: String?

    private let service: R2Service

    init(service: R2Service) {
        self.service = service
    }

    func load(accountId: String) async {
        isLoading = true
        error = nil
        do {
            buckets = try await service.listBuckets(accountId: accountId)
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }
}

@Observable
@MainActor
final class R2ObjectListViewModel {

    var objects: [R2Object] = []
    var folders: [R2Folder] = []
    var currentPrefix = ""
    var isLoading = false
    var isLoadingMore = false
    var error: String?
    private(set) var nextCursor: String?

    var hasMore: Bool { nextCursor != nil }
    var isContentEmpty: Bool { folders.isEmpty && objects.isEmpty }
    var displayTitle: String {
        guard !currentPrefix.isEmpty else { return bucketName }
        return "\(bucketName)/\(currentPrefix.trimmingCharacters(in: CharacterSet(charactersIn: "/")))"
    }

    private let service: R2Service
    private let accountId: String
    private let bucketName: String

    init(service: R2Service, accountId: String, bucketName: String) {
        self.service = service
        self.accountId = accountId
        self.bucketName = bucketName
    }

    func load() async {
        isLoading = true
        error = nil
        do {
            let page = try await service.listObjects(listOptions())
            apply(page, reset: true)
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    func loadMore() async {
        guard let cursor = nextCursor, !isLoadingMore else { return }
        isLoadingMore = true
        do {
            let page = try await service.listObjects(listOptions(cursor: cursor))
            apply(page, reset: false)
        } catch {
            self.error = error.localizedDescription
        }
        isLoadingMore = false
    }

    func open(folder: R2Folder) async {
        guard currentPrefix != folder.prefix else { return }
        currentPrefix = folder.prefix
        await load()
    }

    func openParentFolder() async {
        guard !currentPrefix.isEmpty else { return }
        currentPrefix = R2Folder.parentPrefix(of: currentPrefix)
        await load()
    }

    private func listOptions(cursor: String? = nil) -> R2ObjectListOptions {
        R2ObjectListOptions(
            accountId: accountId,
            bucketName: bucketName,
            prefix: currentPrefix,
            cursor: cursor
        )
    }

    private func apply(_ page: R2ObjectPage, reset: Bool) {
        let pageFolders = R2Folder.makeList(from: page.folderPrefixes, parentPrefix: currentPrefix)
        folders = reset ? pageFolders : Self.mergedFolders(folders, pageFolders)
        objects = reset ? page.objects : objects + page.objects
        nextCursor = page.nextCursor
    }

    private static func mergedFolders(_ current: [R2Folder], _ incoming: [R2Folder]) -> [R2Folder] {
        Array(Set(current + incoming)).sorted { $0.prefix < $1.prefix }
    }

    // MARK: - 对象读写

    var isUploading = false
    var isDownloading = false
    var didUpload = false      // sensoryFeedback 触发器

    /// 下载对象到临时文件（QuickLook 预览用），文件名保留原始扩展名
    func downloadToTemp(object: R2Object) async -> URL? {
        guard !isDownloading else { return nil }
        isDownloading = true
        defer { isDownloading = false }
        do {
            let data = try await service.getObjectData(
                accountId: accountId, bucketName: bucketName, key: object.key
            )
            var filename = (object.key as NSString).lastPathComponent
            if filename.isEmpty { filename = "file" }
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent(UUID().uuidString, isDirectory: true)
                .appendingPathComponent(filename)
            try FileManager.default.createDirectory(
                at: url.deletingLastPathComponent(), withIntermediateDirectories: true
            )
            try data.write(to: url)
            return url
        } catch {
            self.error = error.localizedDescription
            return nil
        }
    }

    /// 上传成功后刷新首页列表
    func upload(data: Data, filename: String, contentType: String) async -> Bool {
        guard !isUploading else { return false }
        isUploading = true
        error = nil
        defer { isUploading = false }
        do {
            try await service.putObject(
                accountId: accountId, bucketName: bucketName,
                key: currentPrefix + filename, data: data, contentType: contentType
            )
            didUpload.toggle()
            await load()
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }

    /// 删除成功后从列表移除
    func delete(key: String) async -> Bool {
        do {
            try await service.deleteObject(accountId: accountId, bucketName: bucketName, key: key)
            objects.removeAll { $0.key == key }
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }
}

// MARK: - D1

@Observable
@MainActor
final class D1DatabaseListViewModel {

    var databases: [D1Database] = []
    var isLoading = false
    var error: String?
    var isCreating = false
    var didCreate = false      // sensoryFeedback 触发器
    var isDeleting = false
    var didDelete = false      // sensoryFeedback 触发器

    private let service: D1Service

    init(service: D1Service) {
        self.service = service
    }

    /// 创建数据库：成功后把新库插到列表顶端（新库为空，无需回填详情），返回 true。
    func create(accountId: String, name: String, locationHint: String?) async -> Bool {
        guard !isCreating else { return false }
        isCreating = true
        error = nil
        defer { isCreating = false }
        do {
            let created = try await service.createDatabase(
                accountId: accountId, name: name, locationHint: locationHint
            )
            databases.insert(created, at: 0)
            didCreate.toggle()
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }

    /// 删除数据库：成功后从列表移除，返回 true。不可恢复，调用前须经二次确认。
    func delete(accountId: String, database: D1Database) async -> Bool {
        guard !isDeleting else { return false }
        isDeleting = true
        error = nil
        defer { isDeleting = false }
        do {
            try await service.deleteDatabase(accountId: accountId, databaseId: database.uuid)
            databases.removeAll { $0.uuid == database.uuid }
            didDelete.toggle()
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }

    func load(accountId: String) async {
        isLoading = true
        error = nil
        do {
            let list = try await service.listDatabases(accountId: accountId)
            // 先上列表让行立即可见，再并发拉详情回填表数量与体积
            databases = list
            isLoading = false

            let service = self.service
            let details = await withTaskGroup(of: D1Database?.self) { group in
                for database in list {
                    group.addTask {
                        try? await service.getDatabase(accountId: accountId, databaseId: database.uuid)
                    }
                }
                var byId: [String: D1Database] = [:]
                for await detail in group {
                    if let detail { byId[detail.uuid] = detail }
                }
                return byId
            }
            if !details.isEmpty {
                databases = list.map { details[$0.uuid] ?? $0 }
            }
        } catch {
            self.error = error.localizedDescription
            isLoading = false
        }
    }
}

@Observable
@MainActor
final class D1QueryViewModel {

    var sql = "SELECT name FROM sqlite_master WHERE type='table';"
    var results: [D1QueryResult] = []
    var isRunning = false
    var error: String?
    var didRun = false      // sensoryFeedback 触发器

    /// 数据库内的用户表（排除 sqlite_* 与 D1 内部表）
    private(set) var tables: [String] = []
    private(set) var tablesLoaded = false

    private let service: D1Service
    private let accountId: String
    private let databaseId: String

    init(service: D1Service, accountId: String, databaseId: String) {
        self.service = service
        self.accountId = accountId
        self.databaseId = databaseId
    }

    func loadTables() async {
        guard !tablesLoaded else { return }
        let sql = """
        SELECT name FROM sqlite_master \
        WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE '_cf_%' \
        ORDER BY name
        """
        guard let results = try? await service.query(accountId: accountId, databaseId: databaseId, sql: sql) else { return }
        tables = (results.first?.results ?? []).compactMap { row in
            if case .string(let name) = row["name"] { return name }
            return nil
        }
        tablesLoaded = true
    }

    func run() async {
        let statement = sql.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !statement.isEmpty, !isRunning else { return }
        isRunning = true
        error = nil
        do {
            results = try await service.query(accountId: accountId, databaseId: databaseId, sql: statement)
            didRun.toggle()
        } catch {
            self.error = error.localizedDescription
            results = []
        }
        isRunning = false
    }
}

@Observable
@MainActor
final class D1TableViewModel {

    private(set) var columns: [D1Column] = []
    private(set) var rows: [[String: JSONValue]] = []
    private(set) var hasMore = false
    var isLoading = false
    var isSaving = false
    var error: String?
    var didSave = false

    /// 行编辑用的 rowid 键（别名避免与同名列冲突）
    static let rowidKey = "_oc_rowid_"

    private var offset = 0
    private let pageSize = 50
    private let service: D1Service
    private let accountId: String
    private let databaseId: String
    let tableName: String

    init(service: D1Service, accountId: String, databaseId: String, tableName: String) {
        self.service = service
        self.accountId = accountId
        self.databaseId = databaseId
        self.tableName = tableName
    }

    private var quotedTable: String {
        "\"" + tableName.replacingOccurrences(of: "\"", with: "\"\"") + "\""
    }

    private func quoted(_ identifier: String) -> String {
        "\"" + identifier.replacingOccurrences(of: "\"", with: "\"\"") + "\""
    }

    func load() async {
        isLoading = true
        error = nil
        do {
            if columns.isEmpty {
                let info = try await service.query(
                    accountId: accountId, databaseId: databaseId,
                    sql: "PRAGMA table_info(\(quotedTable))"
                )
                columns = (info.first?.results ?? []).compactMap { row in
                    guard case .string(let name) = row["name"] else { return nil }
                    let type = row["type"]?.displayText ?? ""
                    let pk = (row["pk"]?.displayText ?? "0") != "0"
                    return D1Column(name: name, type: type, isPrimaryKey: pk)
                }
            }
            offset = 0
            rows = try await fetchPage(offset: 0)
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    func loadMore() async {
        guard hasMore, !isLoading else { return }
        isLoading = true
        do {
            offset += pageSize
            rows.append(contentsOf: try await fetchPage(offset: offset))
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    private func fetchPage(offset: Int) async throws -> [[String: JSONValue]] {
        // 多取 1 行用于判断是否还有下一页
        let sql = "SELECT rowid AS \(Self.rowidKey), * FROM \(quotedTable) LIMIT \(pageSize + 1) OFFSET \(offset)"
        let results = try await service.query(accountId: accountId, databaseId: databaseId, sql: sql)
        var page = results.first?.results ?? []
        hasMore = page.count > pageSize
        if hasMore { page.removeLast() }
        return page
    }

    /// 仅更新变更列（参数化，rowid 定位）。成功返回 true 并重载当前数据。
    func updateRow(rowid: String, changes: [String: String]) async -> Bool {
        guard !changes.isEmpty, !isSaving else { return changes.isEmpty }
        isSaving = true
        error = nil
        defer { isSaving = false }
        do {
            let assignments = changes.keys.map { "\(quoted($0)) = ?" }.joined(separator: ", ")
            let sql = "UPDATE \(quotedTable) SET \(assignments) WHERE rowid = ?"
            let params = changes.keys.map { changes[$0]! } + [rowid]
            _ = try await service.query(accountId: accountId, databaseId: databaseId, sql: sql, params: params)
            didSave.toggle()
            await load()
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }

    func deleteRow(rowid: String) async -> Bool {
        error = nil
        do {
            _ = try await service.query(
                accountId: accountId, databaseId: databaseId,
                sql: "DELETE FROM \(quotedTable) WHERE rowid = ?",
                params: [rowid]
            )
            await load()
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }
}

// MARK: - KV

@Observable
@MainActor
final class KVNamespaceListViewModel {

    var namespaces: [KVNamespace] = []
    var isLoading = false
    var error: String?

    private let service: KVService

    init(service: KVService) {
        self.service = service
    }

    func load(accountId: String) async {
        isLoading = true
        error = nil
        do {
            namespaces = try await service.listNamespaces(accountId: accountId)
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }
}

@Observable
@MainActor
final class KVKeyListViewModel {

    var keys: [KVKey] = []
    var isLoading = false
    var isLoadingMore = false
    var error: String?
    private(set) var nextCursor: String?

    var hasMore: Bool { nextCursor != nil }

    private let service: KVService
    private let accountId: String
    private let namespaceId: String
    /// 进行中的加载任务（见 ZoneListViewModel：独立 Task 承载加载，避免下拉手势取消导致 .cancelled 误报）
    private var loadTask: Task<Void, Never>?

    init(service: KVService, accountId: String, namespaceId: String) {
        self.service = service
        self.accountId = accountId
        self.namespaceId = namespaceId
    }

    func load() async {
        // 复用进行中的加载，并把网络加载放进独立 Task：下拉手势 / searchable 取消
        // .refreshable 子任务时不波及加载，避免 URLError.cancelled 误报为加载失败
        if let loadTask {
            await loadTask.value
            return
        }
        let task = Task { [weak self] in
            guard let self else { return }
            await self.fetchKeys()
        }
        loadTask = task
        defer { loadTask = nil }
        await task.value
    }

    private func fetchKeys() async {
        isLoading = true
        error = nil
        do {
            let page = try await service.listKeys(accountId: accountId, namespaceId: namespaceId)
            keys = page.keys
            nextCursor = page.nextCursor
        } catch is CancellationError {
            // 任务取消属正常生命周期，不算加载失败
        } catch let urlError as URLError where urlError.code == .cancelled {
            // URLSession 把任务取消转成 .cancelled，同样不展示为错误
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    func loadMore() async {
        guard let cursor = nextCursor, !isLoadingMore else { return }
        isLoadingMore = true
        do {
            let page = try await service.listKeys(accountId: accountId, namespaceId: namespaceId, cursor: cursor)
            keys.append(contentsOf: page.keys)
            nextCursor = page.nextCursor
        } catch {
            self.error = error.localizedDescription
        }
        isLoadingMore = false
    }

    /// 删除成功返回 true 并从列表移除
    func delete(key: String) async -> Bool {
        do {
            try await service.deleteKey(accountId: accountId, namespaceId: namespaceId, key: key)
            keys.removeAll { $0.name == key }
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }
}

@Observable
@MainActor
final class KVValueViewModel {

    var valueText = ""
    var isBinary = false
    var byteCount = 0
    var isLoading = false
    var isSaving = false
    var error: String?
    var didSave = false     // sensoryFeedback 触发器

    private let service: KVService
    private let accountId: String
    private let namespaceId: String
    let key: String

    init(service: KVService, accountId: String, namespaceId: String, key: String) {
        self.service = service
        self.accountId = accountId
        self.namespaceId = namespaceId
        self.key = key
    }

    func load() async {
        isLoading = true
        error = nil
        do {
            let data = try await service.getValue(accountId: accountId, namespaceId: namespaceId, key: key)
            byteCount = data.count
            if let text = String(data: data, encoding: .utf8) {
                valueText = text
                isBinary = false
            } else {
                valueText = ""
                isBinary = true
            }
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    /// 保存成功返回 true
    func save() async -> Bool {
        guard !isBinary, !isSaving else { return false }
        isSaving = true
        error = nil
        do {
            try await service.putValue(accountId: accountId, namespaceId: namespaceId, key: key, value: valueText)
            byteCount = valueText.utf8.count
            didSave.toggle()
            isSaving = false
            return true
        } catch {
            self.error = error.localizedDescription
            isSaving = false
            return false
        }
    }
}

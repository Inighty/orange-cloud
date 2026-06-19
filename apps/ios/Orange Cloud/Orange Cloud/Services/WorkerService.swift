//
//  WorkerService.swift
//  Orange Cloud
//

import Foundation

struct WorkerService {

    private let client: CFAPIClient

    init(client: CFAPIClient) {
        self.client = client
    }

    /// 账号下全部 Workers 脚本（该端点不分页）
    func listScripts(accountId: String) async throws -> [WorkerScript] {
        let response: CFAPIResponseArray<WorkerScript> = try await client.get(
            "accounts/\(accountId)/workers/scripts"
        )
        guard response.success else {
            throw response.toAPIError()
        }
        return response.result ?? []
    }

    // MARK: - 脚本源码与设置

    /// 脚本源码（模块→multipart 解析为各模块；service worker→raw JS）
    func content(accountId: String, scriptName: String) async throws -> WorkerContent {
        let (data, response) = try await client.getRawResponse(
            "accounts/\(accountId)/workers/scripts/\(scriptName)/content"
        )
        let contentType = response.value(forHTTPHeaderField: "Content-Type")
        return WorkerContent.parse(data: data, contentType: contentType)
    }

    /// 脚本设置（绑定 + 兼容性日期/标志）
    func settings(accountId: String, scriptName: String) async throws -> WorkerSettings {
        let response: CFAPIResponse<WorkerSettings> = try await client.get(
            "accounts/\(accountId)/workers/scripts/\(scriptName)/settings"
        )
        guard response.success, let settings = response.result else { throw response.toAPIError() }
        return settings
    }

    /// 安全保存脚本代码：仅替换正文，全部绑定以 inherit 按名保留（密钥值读不到也能保住），
    /// 兼容性日期/标志沿用旧值；带 ?bindings_inherit=strict，缺绑定时直接报错而非静默丢弃。
    func uploadScript(
        accountId: String,
        scriptName: String,
        content: WorkerContent,
        newCode: String,
        settings: WorkerSettings
    ) async throws {
        guard let module = content.mainModule else {
            throw APIError.cloudflareError(code: 0, message: String(localized: "无法定位脚本主模块"))
        }
        let metadata = WorkerUploadMetadata(
            mainModule:         content.isModule ? module.name : nil,
            bodyPart:           content.isModule ? nil : module.name,
            compatibilityDate:  settings.compatibilityDate,
            compatibilityFlags: settings.compatibilityFlags,
            bindings:           settings.bindings.map { $0.asInherit() }
        )
        let response: CFAPIResponse<EmptyResponse> = try await client.multipartRequest(
            method: "PUT",
            "accounts/\(accountId)/workers/scripts/\(scriptName)",
            queryItems: [URLQueryItem(name: "bindings_inherit", value: "strict")],
            jsonPartName: "metadata",
            jsonPart: metadata,
            file: (name: module.name, contentType: module.contentType, content: Data(newCode.utf8))
        )
        guard response.success else { throw response.toAPIError() }
    }

    /// 改绑定（变量）：传入完整新 bindings（变更项为实体，其余 inherit），PATCH settings 不动代码。
    func patchSettings(
        accountId: String,
        scriptName: String,
        bindings: [WorkerBindingInput],
        settings: WorkerSettings
    ) async throws {
        let patch = WorkerSettingsPatch(
            bindings:           bindings,
            compatibilityDate:  settings.compatibilityDate,
            compatibilityFlags: settings.compatibilityFlags
        )
        let response: CFAPIResponse<EmptyResponse> = try await client.multipartRequest(
            method: "PATCH",
            "accounts/\(accountId)/workers/scripts/\(scriptName)/settings",
            jsonPartName: "settings",
            jsonPart: patch
        )
        guard response.success else { throw response.toAPIError() }
    }

    // MARK: - 密钥

    /// 密钥列表（仅名 + 类型，永不含值）
    func listSecrets(accountId: String, scriptName: String) async throws -> [WorkerSecret] {
        let response: CFAPIResponseArray<WorkerSecret> = try await client.get(
            "accounts/\(accountId)/workers/scripts/\(scriptName)/secrets"
        )
        guard response.success else { throw response.toAPIError() }
        return response.result ?? []
    }

    /// 新建 / 更新密钥
    func putSecret(accountId: String, scriptName: String, name: String, text: String) async throws {
        let response: CFAPIResponse<EmptyResponse> = try await client.put(
            "accounts/\(accountId)/workers/scripts/\(scriptName)/secrets",
            body: WorkerSecretInput(name: name, text: text)
        )
        guard response.success else { throw response.toAPIError() }
    }

    /// 删除密钥
    func deleteSecret(accountId: String, scriptName: String, name: String) async throws {
        try await client.delete("accounts/\(accountId)/workers/scripts/\(scriptName)/secrets/\(name)")
    }

    // MARK: - Cron 触发器

    func schedules(accountId: String, scriptName: String) async throws -> [WorkerSchedule] {
        let response: CFAPIResponse<WorkerSchedulesResult> = try await client.get(
            "accounts/\(accountId)/workers/scripts/\(scriptName)/schedules"
        )
        guard response.success else { throw response.toAPIError() }
        return response.result?.schedules ?? []
    }

    /// 整组替换 Cron（请求体是裸数组 [{cron}]；漏传即删）
    func putSchedules(accountId: String, scriptName: String, crons: [String]) async throws {
        let body = crons.map { WorkerScheduleInput(cron: $0) }
        let response: CFAPIResponse<WorkerSchedulesResult> = try await client.put(
            "accounts/\(accountId)/workers/scripts/\(scriptName)/schedules",
            body: body
        )
        guard response.success else { throw response.toAPIError() }
    }

    // MARK: - 域名 / 路由

    /// workers.dev 子域状态
    func subdomain(accountId: String, scriptName: String) async throws -> WorkerSubdomain {
        let response: CFAPIResponse<WorkerSubdomain> = try await client.get(
            "accounts/\(accountId)/workers/scripts/\(scriptName)/subdomain"
        )
        guard response.success, let sub = response.result else { throw response.toAPIError() }
        return sub
    }

    /// 切换 workers.dev 子域
    func setSubdomain(accountId: String, scriptName: String, enabled: Bool) async throws {
        let response: CFAPIResponse<EmptyResponse> = try await client.post(
            "accounts/\(accountId)/workers/scripts/\(scriptName)/subdomain",
            body: WorkerSubdomainInput(enabled: enabled)
        )
        guard response.success else { throw response.toAPIError() }
    }

    /// 该脚本的自定义域（按 service 过滤）
    func customDomains(accountId: String, scriptName: String) async throws -> [WorkerCustomDomain] {
        let response: CFAPIResponseArray<WorkerCustomDomain> = try await client.get(
            "accounts/\(accountId)/workers/domains",
            queryItems: [URLQueryItem(name: "service", value: scriptName)]
        )
        guard response.success else { throw response.toAPIError() }
        return response.result ?? []
    }

    /// 挂载自定义域到该脚本
    func attachDomain(accountId: String, scriptName: String, hostname: String, zoneId: String) async throws {
        let response: CFAPIResponse<WorkerCustomDomain> = try await client.put(
            "accounts/\(accountId)/workers/domains",
            body: WorkerCustomDomainInput(hostname: hostname, service: scriptName, zoneId: zoneId)
        )
        guard response.success else { throw response.toAPIError() }
    }

    /// 卸载自定义域
    func deleteDomain(accountId: String, domainId: String) async throws {
        try await client.delete("accounts/\(accountId)/workers/domains/\(domainId)")
    }

    /// zone 下全部 Worker 路由（调用方按 script 过滤到本脚本）
    func routes(zoneId: String) async throws -> [WorkerRoute] {
        let response: CFAPIResponseArray<WorkerRoute> = try await client.get(
            "zones/\(zoneId)/workers/routes"
        )
        guard response.success else { throw response.toAPIError() }
        return response.result ?? []
    }

    /// 新建路由（pattern → script）
    func createRoute(zoneId: String, pattern: String, scriptName: String) async throws {
        let response: CFAPIResponse<WorkerRoute> = try await client.post(
            "zones/\(zoneId)/workers/routes",
            body: WorkerRouteInput(pattern: pattern, script: scriptName)
        )
        guard response.success else { throw response.toAPIError() }
    }

    /// 删除路由
    func deleteRoute(zoneId: String, routeId: String) async throws {
        try await client.delete("zones/\(zoneId)/workers/routes/\(routeId)")
    }
}

//
//  StorageView.swift
//  Orange Cloud
//
//  存储 Tab（设计稿 storage.jsx）：SegmentedControl 在 R2 / D1 / KV 间切换，
//  各资源一组玻璃列表行，按 scope 分别门控。
//

import SwiftUI

enum StorageKind: String, CaseIterable, Identifiable {
    case r2, d1, kv

    var id: String { rawValue }
    var label: String { rawValue.uppercased() }

    var requiredScope: String {
        switch self {
        case .r2: "workers-r2.read"
        case .d1: "d1.read"
        case .kv: "workers-kv-storage.read"
        }
    }

    var featureName: String {
        switch self {
        case .r2: String(localized: "R2 对象存储")
        case .d1: String(localized: "D1 数据库")
        case .kv: String(localized: "KV 存储")
        }
    }
}

struct StorageView: View {

    @Environment(SessionStore.self) private var session
    @Environment(AuthManager.self) private var auth
    @Environment(EntitlementStore.self) private var entitlements

    @State private var kind: StorageKind = .r2
    @State private var r2ViewModel: R2BucketListViewModel
    @State private var d1ViewModel: D1DatabaseListViewModel
    @State private var kvViewModel: KVNamespaceListViewModel
    @State private var showD1Create = false
    @State private var databaseToDelete: D1Database?
    @State private var d1Denied = false

    /// 创建 / 删除 D1 数据库都需要写权限（读权限已是进入 D1 段的前置条件）
    private var canWriteD1: Bool { auth.hasScope("d1.write") }

    init(session: SessionStore) {
        _r2ViewModel = State(initialValue: R2BucketListViewModel(service: session.r2Service))
        _d1ViewModel = State(initialValue: D1DatabaseListViewModel(service: session.d1Service))
        _kvViewModel = State(initialValue: KVNamespaceListViewModel(service: session.kvService))
    }

    var body: some View {
        NavigationStack {
            Group {
                // 整模块 Pro 闸门：免费层不展示存储内容
                if entitlements.isPro {
                    proContent
                } else {
                    ProLockedView(feature: .storage)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .background { SkyBackground() }
            .navigationTitle("存储")
            .toolbar {
                // 仅 D1 段提供「创建数据库」入口（R2/KV 暂只读浏览）
                if entitlements.isPro, kind == .d1, auth.hasScope(StorageKind.d1.requiredScope) {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("创建数据库", systemImage: "plus") {
                            if canWriteD1 { showD1Create = true } else { d1Denied = true }
                        }
                    }
                }
            }
            .sheet(isPresented: $showD1Create) {
                D1CreateView(viewModel: d1ViewModel, accountId: session.selectedAccount?.id ?? "")
            }
            .sheet(item: $databaseToDelete) { database in
                D1DeleteConfirmView(database: database, viewModel: d1ViewModel, accountId: session.selectedAccount?.id ?? "")
            }
            .alert("权限不足", isPresented: $d1Denied) {
                Button("好", role: .cancel) {}
            } message: {
                Text("当前授权未包含 D1 写权限（d1.write）。\n请在设置中退出登录后重新授权以启用此功能。")
            }
            .sensoryFeedback(.success, trigger: d1ViewModel.didCreate)
            .sensoryFeedback(.success, trigger: d1ViewModel.didDelete)
        }
    }

    private var proContent: some View {
        VStack(spacing: 0) {
            Picker("资源类型", selection: $kind) {
                ForEach(StorageKind.allCases) { kind in
                    Text(kind.label).tag(kind)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.bottom, 8)

            if auth.hasScope(kind.requiredScope) {
                content
            } else {
                PermissionDeniedView(featureName: kind.featureName, requiredScope: kind.requiredScope)
                    .frame(maxHeight: .infinity)
            }
        }
        .task(id: kind) {
            await load()
        }
    }

    @ViewBuilder
    private var content: some View {
        switch kind {
        case .r2: r2List
        case .d1: d1List
        case .kv: kvList
        }
    }

    // MARK: - R2

    @ViewBuilder
    private var r2List: some View {
        if r2ViewModel.buckets.isEmpty && r2ViewModel.isLoading {
            loadingView
        } else if r2ViewModel.buckets.isEmpty {
            emptyView(icon: "archivebox", title: String(localized: "没有存储桶"), body: String(localized: "在 Cloudflare Dashboard 创建 R2 存储桶。"))
        } else {
            List(r2ViewModel.buckets) { bucket in
                NavigationLink {
                    R2ObjectListView(bucket: bucket, session: session)
                } label: {
                    StorageRow(
                        icon: "externaldrive", tint: .ocOrange, mono: false,
                        name: bucket.name,
                        sub: [bucket.location, WorkerScript.parseDate(bucket.creationDate).map { $0.formatted(.dateTime.year().month().day()) }]
                            .compactMap(\.self).joined(separator: " · ")
                    )
                }
                .glassRow()
            }
            .scrollContentBackground(.hidden)
            .refreshable { await load() }
        }
    }

    // MARK: - D1

    @ViewBuilder
    private var d1List: some View {
        if d1ViewModel.databases.isEmpty && d1ViewModel.isLoading {
            loadingView
        } else if d1ViewModel.databases.isEmpty {
            ContentUnavailableView {
                Label("没有数据库", systemImage: "cylinder")
            } description: {
                Text(canWriteD1 ? String(localized: "点击右上角 + 创建第一个数据库") : String(localized: "当前授权仅限读取，无法创建数据库"))
            } actions: {
                if canWriteD1 {
                    Button("创建数据库") { showD1Create = true }
                        .buttonStyle(.borderedProminent)
                        .tint(Color.ocOrangePressed)
                        .fontWeight(.bold)
                }
            }
            .frame(maxHeight: .infinity)
        } else {
            List(d1ViewModel.databases) { database in
                NavigationLink {
                    D1QueryView(database: database, session: session)
                } label: {
                    StorageRow(
                        icon: "cylinder", tint: .blue, mono: true,
                        name: database.name,
                        sub: [
                            database.fileSize.map { Int64($0).formatted(.byteCount(style: .file)) },
                            database.numTables.map { String(localized: "\($0) 张表") },
                        ].compactMap(\.self).joined(separator: " · ")
                    )
                }
                .swipeActions(edge: .trailing) {
                    Button(role: .destructive) {
                        if canWriteD1 { databaseToDelete = database } else { d1Denied = true }
                    } label: {
                        Label("删除", systemImage: "trash")
                    }
                }
                .glassRow()
            }
            .scrollContentBackground(.hidden)
            .refreshable { await load() }
        }
    }

    // MARK: - KV

    @ViewBuilder
    private var kvList: some View {
        if kvViewModel.namespaces.isEmpty && kvViewModel.isLoading {
            loadingView
        } else if kvViewModel.namespaces.isEmpty {
            emptyView(icon: "key", title: String(localized: "没有命名空间"), body: String(localized: "在 Cloudflare Dashboard 创建 KV 命名空间。"))
        } else {
            List(kvViewModel.namespaces) { namespace in
                NavigationLink {
                    KVKeyListView(namespace: namespace, session: session)
                } label: {
                    StorageRow(
                        icon: "key", tint: .green, mono: true,
                        name: namespace.title,
                        sub: namespace.id
                    )
                }
                .glassRow()
            }
            .scrollContentBackground(.hidden)
            .refreshable { await load() }
        }
    }

    // MARK: - 公共状态

    private var loadingView: some View {
        SkeletonList(rows: 7)
    }

    private func emptyView(icon: String, title: String, body bodyText: String) -> some View {
        ContentUnavailableView {
            Label(title, systemImage: icon)
        } description: {
            Text(bodyText)
        }
        .frame(maxHeight: .infinity)
    }

    private func load() async {
        guard auth.hasScope(kind.requiredScope) else { return }
        await session.ensureAccounts()
        guard let accountId = session.selectedAccount?.id else { return }
        switch kind {
        case .r2: await r2ViewModel.load(accountId: accountId)
        case .d1: await d1ViewModel.load(accountId: accountId)
        case .kv: await kvViewModel.load(accountId: accountId)
        }
    }
}

// MARK: - 存储行（设计稿 StorageRow）

private struct StorageRow: View {
    let icon: String
    let tint: Color
    let mono: Bool
    let name: String
    let sub: String

    var body: some View {
        HStack(spacing: 12) {
            TintIcon(systemImage: icon, color: tint)
            VStack(alignment: .leading, spacing: 2) {
                Text(name)
                    .font(mono ? .callout.weight(.semibold).monospaced() : .body.weight(.semibold))
                    .lineLimit(1)
                if !sub.isEmpty {
                    Text(sub)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
            }
        }
        .padding(.vertical, 2)
    }
}

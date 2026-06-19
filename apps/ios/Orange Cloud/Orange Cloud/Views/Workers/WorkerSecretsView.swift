//
//  WorkerSecretsView.swift
//  Orange Cloud
//
//  Worker 密钥（secret_text）+ 环境变量（plain_text）管理 + 只读绑定清单。
//  写操作按 workers-scripts.write 门控；改任一变量都整组回写（其余 inherit），不丢既有绑定。
//

import SwiftUI

struct WorkerSecretsView: View {

    @Environment(AuthManager.self) private var auth
    @State private var viewModel: WorkerBindingsViewModel
    @State private var sheet: EditorSheet?

    init(accountId: String, scriptName: String, session: SessionStore) {
        _viewModel = State(initialValue: WorkerBindingsViewModel(
            service: session.workerService, accountId: accountId, scriptName: scriptName
        ))
    }

    private var canWrite: Bool { auth.hasScope("workers-scripts.write") }

    var body: some View {
        Group {
            if !viewModel.loaded && viewModel.isLoading {
                SkeletonList(rows: 5, icon: .none, trailing: true)
            } else {
                List {
                    secretsSection
                    variablesSection
                    if !viewModel.otherBindings.isEmpty {
                        otherSection
                    }
                }
                .scrollContentBackground(.hidden)
                .refreshable { await viewModel.load() }
            }
        }
        .background { SkyBackground() }
        .navigationTitle("变量与密钥")
        .navigationBarTitleDisplayMode(.inline)
        .task { if !viewModel.loaded { await viewModel.load() } }
        .sheet(item: $sheet) { kind in
            WorkerValueEditorSheet(kind: kind, viewModel: viewModel)
        }
        .alert("出错了", isPresented: .init(
            get: { viewModel.error != nil && sheet == nil },
            set: { if !$0 { viewModel.error = nil } }
        )) {
            Button("好", role: .cancel) {}
        } message: {
            Text(viewModel.error ?? "")
        }
    }

    // MARK: - 密钥

    private var secretsSection: some View {
        Section {
            if viewModel.secrets.isEmpty {
                Text("暂无密钥").font(.callout).foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.secrets) { secret in
                    HStack(spacing: 12) {
                        TintIcon(systemImage: "key.fill", color: .ocOrange)
                        Text(secret.name).font(.callout.weight(.medium))
                        Spacer()
                    }
                    .swipeActions(edge: .trailing) {
                        if canWrite {
                            Button("删除", role: .destructive) {
                                Task { await viewModel.deleteSecret(secret) }
                            }
                        }
                    }
                }
            }
            if canWrite {
                Button {
                    sheet = .secret
                } label: {
                    Label("添加密钥", systemImage: "plus")
                }
            }
        } header: {
            Text("密钥")
        } footer: {
            Text(canWrite
                 ? String(localized: "密钥值出于安全无法读取，列表只显示名称。同名添加即覆盖。")
                 : String(localized: "当前授权仅可查看（缺少 workers-scripts.write）。"))
        }
        .glassRow()
    }

    // MARK: - 环境变量

    private var variablesSection: some View {
        Section {
            if viewModel.variables.isEmpty {
                Text("暂无变量").font(.callout).foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.variables) { binding in
                    Button {
                        if canWrite { sheet = .variable(binding) }
                    } label: {
                        HStack(spacing: 12) {
                            TintIcon(systemImage: "textformat", color: .ocOrange)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(binding.name).font(.callout.weight(.medium)).foregroundStyle(.primary)
                                Text(binding.text ?? "")
                                    .font(.caption.monospaced())
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                            Spacer()
                            if canWrite {
                                Image(systemName: "chevron.right")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.tertiary)
                            }
                        }
                    }
                    .disabled(!canWrite)
                    .swipeActions(edge: .trailing) {
                        if canWrite {
                            Button("删除", role: .destructive) {
                                Task { await viewModel.deleteVariable(binding) }
                            }
                        }
                    }
                }
            }
            if canWrite {
                Button {
                    sheet = .variable(nil)
                } label: {
                    Label("添加变量", systemImage: "plus")
                }
            }
        } header: {
            Text("环境变量")
        } footer: {
            Text("明文变量（plain_text），可读可改。改任一项不影响其它绑定。")
        }
        .glassRow()
    }

    // MARK: - 只读绑定

    private var otherSection: some View {
        Section {
            ForEach(viewModel.otherBindings) { binding in
                HStack(spacing: 12) {
                    TintIcon(systemImage: "cube", color: .gray)
                    Text(binding.name).font(.callout)
                    Spacer()
                    Text(binding.typeLabel).font(.caption).foregroundStyle(.secondary)
                }
            }
        } header: {
            Text("其它绑定（只读）")
        } footer: {
            Text("KV / D1 / R2 等资源绑定在此查看，编辑请用 Wrangler 或 Dashboard。")
        }
        .glassRow()
    }
}

// MARK: - 添加/编辑弹窗

/// 弹窗类型：新增密钥 / 新增或编辑变量（编辑时锁定名称）
private enum EditorSheet: Identifiable {
    case secret
    case variable(WorkerBinding?)

    var id: String {
        switch self {
        case .secret:            "secret"
        case .variable(let b):   "var-\(b?.name ?? "new")"
        }
    }
}

private struct WorkerValueEditorSheet: View {

    let kind: EditorSheet
    let viewModel: WorkerBindingsViewModel

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var value = ""

    private var isSecret: Bool { if case .secret = kind { return true }; return false }

    /// 编辑既有变量时锁定名称
    private var lockedName: String? {
        if case .variable(let binding) = kind, let binding { return binding.name }
        return nil
    }

    private var nameValid: Bool {
        let target = lockedName ?? name
        return target.range(of: "^[A-Za-z_][A-Za-z0-9_]*$", options: .regularExpression) != nil
    }

    private var canSave: Bool {
        nameValid && !value.isEmpty && !viewModel.isSaving
    }

    private var title: String {
        if isSecret { return String(localized: "添加密钥") }
        return lockedName == nil ? String(localized: "添加变量") : String(localized: "编辑变量")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if let lockedName {
                        Text(lockedName).font(.callout.monospaced()).foregroundStyle(.secondary)
                    } else {
                        TextField("NAME", text: $name)
                            .font(.callout.monospaced())
                            .textInputAutocapitalization(.characters)
                            .autocorrectionDisabled()
                    }
                } header: {
                    Text("名称")
                } footer: {
                    if lockedName == nil {
                        Text("字母、数字、下划线，且不以数字开头。")
                    }
                }

                Section {
                    if isSecret {
                        SecureField("值", text: $value)
                            .font(.callout.monospaced())
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    } else {
                        TextField("值", text: $value, axis: .vertical)
                            .font(.callout.monospaced())
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .lineLimit(1...6)
                    }
                } header: {
                    Text(isSecret ? String(localized: "值（保存后不可读取）") : String(localized: "值"))
                }

                if let error = viewModel.error {
                    Section { Text(error).font(.footnote).foregroundStyle(.red) }
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task { await save() }
                    } label: {
                        if viewModel.isSaving { ProgressView() } else { Text("保存").fontWeight(.semibold) }
                    }
                    .disabled(!canSave)
                }
            }
            .interactiveDismissDisabled(viewModel.isSaving)
            .onAppear {
                if case .variable(let binding) = kind, let binding {
                    value = binding.text ?? ""
                }
            }
        }
    }

    private func save() async {
        viewModel.error = nil
        let ok: Bool
        if isSecret {
            ok = await viewModel.addSecret(name: name, text: value)
        } else {
            ok = await viewModel.setVariable(name: lockedName ?? name, value: value)
        }
        if ok { dismiss() }
    }
}

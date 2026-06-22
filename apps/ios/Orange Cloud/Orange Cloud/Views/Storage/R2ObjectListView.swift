//
//  R2ObjectListView.swift
//  Orange Cloud
//
//  R2 对象列表（上传/删除/游标分页）→ 对象详情（QuickLook 预览）。
//  入口：StorageView 的 R2 段。
//

import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import QuickLook

struct R2ObjectListView: View {

    let bucket: R2Bucket

    @Environment(SessionStore.self) private var session
    @Environment(AuthManager.self) private var auth
    @State private var viewModel: R2ObjectListViewModel
    @State private var selectedObject: R2Object?
    @State private var objectToDelete: R2Object?
    @State private var showDenied = false
    @State private var photoItem: PhotosPickerItem?
    @State private var showPhotoPicker = false
    @State private var showFileImporter = false
    @State private var previewURL: URL?

    init(bucket: R2Bucket, session: SessionStore) {
        self.bucket = bucket
        _viewModel = State(initialValue: R2ObjectListViewModel(
            service: session.r2Service,
            accountId: session.selectedAccount?.id ?? "",
            bucketName: bucket.name
        ))
    }

    private var canWrite: Bool { auth.hasScope("workers-r2.write") }

    var body: some View {
        Group {
            if viewModel.isContentEmpty && viewModel.isLoading {
                SkeletonList(rows: 9, trailing: true)
            } else if viewModel.isContentEmpty && viewModel.currentPrefix.isEmpty {
                ContentUnavailableView {
                    Label(viewModel.currentPrefix.isEmpty ? "空存储桶" : "空文件夹", systemImage: "archivebox")
                } description: {
                    Text(emptyDescription)
                }
            } else {
                objectList
            }
        }
        .background { SkyBackground() }
        .navigationTitle(viewModel.displayTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if viewModel.isUploading {
                    ProgressView()
                } else {
                    Menu {
                        if canWrite {
                            Button {
                                showPhotoPicker = true
                            } label: {
                                Label("上传照片或视频", systemImage: "photo")
                            }
                            Button {
                                showFileImporter = true
                            } label: {
                                Label("上传文件", systemImage: "doc")
                            }
                        } else {
                            Button {
                                showDenied = true
                            } label: {
                                Label("需要 R2 写权限", systemImage: "lock")
                            }
                        }
                    } label: {
                        Label("上传", systemImage: "square.and.arrow.up")
                    }
                }
            }
        }
        .photosPicker(isPresented: $showPhotoPicker, selection: $photoItem, matching: .any(of: [.images, .videos]))
        .quickLookPreview($previewURL)
        .overlay {
            if viewModel.isDownloading {
                ZStack {
                    Color.black.opacity(0.15).ignoresSafeArea()
                    ProgressView("下载中…")
                        .padding(18)
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
                }
            }
        }
        .task { await viewModel.load() }
        .onChange(of: photoItem) {
            guard let item = photoItem else { return }
            photoItem = nil
            guard canWrite else { showDenied = true; return }
            Task { await uploadPhoto(item) }
        }
        .fileImporter(isPresented: $showFileImporter, allowedContentTypes: [.item]) { result in
            guard canWrite else { showDenied = true; return }
            if case .success(let url) = result {
                Task { await uploadFile(url) }
            }
        }
        .sheet(item: $selectedObject) { object in
            R2ObjectDetailView(object: object, viewModel: viewModel, canWrite: canWrite)
                .presentationDetents([.medium, .large])
        }
        .confirmationDialog(
            "删除对象",
            isPresented: .init(
                get: { objectToDelete != nil },
                set: { if !$0 { objectToDelete = nil } }
            ),
            titleVisibility: .visible
        ) {
            if let object = objectToDelete {
                Button("删除 \(object.key)", role: .destructive) {
                    Task { _ = await viewModel.delete(key: object.key) }
                }
            }
        } message: {
            Text("此操作不可撤销。")
        }
        .alert("权限不足", isPresented: $showDenied) {
            Button("好", role: .cancel) {}
        } message: {
            Text("当前授权未包含 R2 写权限（workers-r2.write）。\n请在设置中退出登录后重新授权以启用此功能。")
        }
        .alert("出错了", isPresented: .init(
            get: { viewModel.error != nil && selectedObject == nil },
            set: { if !$0 { viewModel.error = nil } }
        )) {
            Button("好", role: .cancel) {}
        } message: {
            Text(viewModel.error ?? "")
        }
        .sensoryFeedback(.success, trigger: viewModel.didUpload)
    }

    private var emptyDescription: String {
        if !viewModel.currentPrefix.isEmpty {
            return canWrite ? String(localized: "点击右上角上传第一个文件") : String(localized: "这个文件夹里还没有对象")
        }
        return canWrite ? String(localized: "点击右上角上传第一个文件") : String(localized: "这个存储桶里还没有对象")
    }

    /// 可预览：50 MB 以内（QuickLook 需要完整下载）
    private func previewable(_ object: R2Object) -> Bool {
        (object.size ?? 0) <= 50_000_000
    }

    /// 点击对象：可预览的直接下载打开，超限的退回详情页
    private func open(_ object: R2Object) {
        guard previewable(object) else {
            selectedObject = object
            return
        }
        guard !viewModel.isDownloading else { return }
        Task {
            previewURL = await viewModel.downloadToTemp(object: object)
        }
    }

    private var objectList: some View {
        List {
            folderRows
            objectRows
            loadMoreRow
        }
        .scrollContentBackground(.hidden)
        .refreshable { await viewModel.load() }
    }

    @ViewBuilder
    private var folderRows: some View {
        if !viewModel.currentPrefix.isEmpty {
            Button {
                Task { await viewModel.openParentFolder() }
            } label: {
                R2FolderRow(title: "..", subtitle: String(localized: "上级文件夹"), systemImage: "arrow.up")
            }
            .buttonStyle(.plain)
            .glassRow()
        }

        ForEach(viewModel.folders) { folder in
            Button {
                Task { await viewModel.open(folder: folder) }
            } label: {
                R2FolderRow(title: folder.name, subtitle: folder.prefix)
            }
            .buttonStyle(.plain)
            .glassRow()
        }
    }

    @ViewBuilder
    private var objectRows: some View {
            ForEach(viewModel.objects) { object in
                HStack(spacing: 8) {
                    Button {
                        open(object)
                    } label: {
                        R2ObjectRow(object: object)
                    }
                    .buttonStyle(.borderless)
                    .foregroundStyle(.primary)

                    Button {
                        selectedObject = object
                    } label: {
                        Image(systemName: "info.circle")
                            .foregroundStyle(Color.ocOrangeText)
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel("详细信息")
                }
                .contextMenu {
                    if previewable(object) {
                        Button {
                            open(object)
                        } label: {
                            Label("预览", systemImage: "eye")
                        }
                    }
                    Button {
                        selectedObject = object
                    } label: {
                        Label("详情", systemImage: "info.circle")
                    }
                    Button(role: .destructive) {
                        if canWrite {
                            objectToDelete = object
                        } else {
                            showDenied = true
                        }
                    } label: {
                        Label("删除", systemImage: "trash")
                    }
                }
                .swipeActions(edge: .trailing) {
                    Button(role: .destructive) {
                        if canWrite {
                            objectToDelete = object
                        } else {
                            showDenied = true
                        }
                    } label: {
                        Label("删除", systemImage: "trash")
                    }
                }
                .glassRow()
            }
    }

    @ViewBuilder
    private var loadMoreRow: some View {
            if viewModel.hasMore {
                Button {
                    Task { await viewModel.loadMore() }
                } label: {
                    if viewModel.isLoadingMore {
                        ProgressView().frame(maxWidth: .infinity)
                    } else {
                        Text("加载更多").frame(maxWidth: .infinity)
                    }
                }
                .glassRow()
            }
    }

    // MARK: - 上传

    private func uploadPhoto(_ item: PhotosPickerItem) async {
        guard let data = try? await item.loadTransferable(type: Data.self) else { return }
        let type = item.supportedContentTypes.first
        let ext = type?.preferredFilenameExtension ?? "bin"
        let mime = type?.preferredMIMEType ?? "application/octet-stream"
        let name = "upload-\(Date().formatted(.iso8601.year().month().day().timeSeparator(.omitted).time(includingFractionalSeconds: false))).\(ext)"
        _ = await viewModel.upload(data: data, filename: name, contentType: mime)
    }

    private func uploadFile(_ url: URL) async {
        let accessing = url.startAccessingSecurityScopedResource()
        defer { if accessing { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url) else { return }
        let mime = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType
            ?? "application/octet-stream"
        _ = await viewModel.upload(data: data, filename: url.lastPathComponent, contentType: mime)
    }
}

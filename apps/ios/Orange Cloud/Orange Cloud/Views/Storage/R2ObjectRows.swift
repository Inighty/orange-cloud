//
//  R2ObjectRows.swift
//  Orange Cloud
//

import SwiftUI
import UniformTypeIdentifiers
import QuickLook

struct R2FolderRow: View {
    let title: String
    let subtitle: String?
    let systemImage: String

    init(title: String, subtitle: String?, systemImage: String = "folder") {
        self.title = title
        self.subtitle = subtitle
        self.systemImage = systemImage
    }

    var body: some View {
        HStack(spacing: 12) {
            TintIcon(systemImage: systemImage, color: .ocOrange, size: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.callout)
                    .lineLimit(1)
                if let subtitle {
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
            }
            Spacer()
        }
        .contentShape(Rectangle())
    }
}

struct R2ObjectRow: View {
    let object: R2Object

    private var icon: String {
        let ext = (object.key as NSString).pathExtension.lowercased()
        if let type = UTType(filenameExtension: ext) {
            if type.conforms(to: .image) { return "photo" }
            if type.conforms(to: .movie) || type.conforms(to: .video) { return "film" }
            if type.conforms(to: .audio) { return "waveform" }
            if type.conforms(to: .pdf) { return "doc.richtext" }
            if type.conforms(to: .text) { return "doc.text" }
        }
        return "doc"
    }

    var body: some View {
        HStack(spacing: 12) {
            TintIcon(systemImage: icon, color: .ocOrange, size: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(object.key)
                    .font(.callout)
                    .lineLimit(1)
                    .truncationMode(.middle)
                objectMetadata
            }
            Spacer()
        }
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private var objectMetadata: some View {
        HStack(spacing: 6) {
            if let size = object.size {
                Text(Int64(size).formatted(.byteCount(style: .file)))
            }
            if let modified = WorkerScript.parseDate(object.lastModified) {
                Text(modified, format: .relative(presentation: .named))
            }
        }
        .font(.caption)
        .foregroundStyle(.secondary)
    }
}

// MARK: - 对象详情（元数据 + QuickLook 预览 + 删除）

struct R2ObjectDetailView: View {

    let object: R2Object
    let viewModel: R2ObjectListViewModel
    let canWrite: Bool

    @Environment(\.dismiss) private var dismiss
    @State private var previewURL: URL?
    @State private var showDeleteConfirm = false

    private var previewable: Bool {
        (object.size ?? 0) <= 50_000_000
    }

    var body: some View {
        NavigationStack {
            List {
                objectSection
                actionSection
            }
            .navigationTitle("对象详情")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                }
            }
            .quickLookPreview($previewURL)
            .confirmationDialog("删除对象？", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
                deleteConfirmationButton
            } message: {
                Text("此操作不可撤销。")
            }
        }
    }

    private var objectSection: some View {
        Section("对象") {
            LabeledContent("Key") {
                Text(object.key)
                    .font(.callout.monospaced())
                    .textSelection(.enabled)
                    .multilineTextAlignment(.trailing)
            }
            if let size = object.size {
                LabeledContent("大小", value: Int64(size).formatted(.byteCount(style: .file)))
            }
            if let contentType = object.httpMetadata?.contentType {
                LabeledContent("Content-Type", value: contentType)
            }
            if let storageClass = object.storageClass {
                LabeledContent("存储类型", value: storageClass)
            }
            if let etag = object.etag {
                LabeledContent("ETag") {
                    Text(etag)
                        .font(.caption.monospaced())
                        .textSelection(.enabled)
                }
            }
            if let modified = WorkerScript.parseDate(object.lastModified) {
                LabeledContent("修改时间") {
                    Text(modified, format: .dateTime.year().month().day().hour().minute())
                }
            }
        }
    }

    private var actionSection: some View {
        Section {
            Button {
                Task { previewURL = await viewModel.downloadToTemp(object: object) }
            } label: {
                HStack {
                    Label("预览", systemImage: "eye")
                    Spacer()
                    if viewModel.isDownloading {
                        ProgressView()
                    }
                }
            }
            .disabled(!previewable || viewModel.isDownloading)

            if canWrite {
                Button(role: .destructive) {
                    showDeleteConfirm = true
                } label: {
                    Label("删除对象", systemImage: "trash")
                }
            }
        } footer: {
            Text(previewFooter)
        }
    }

    private var previewFooter: String {
        if !previewable {
            return String(localized: "超过 50 MB 的对象暂不支持在 App 内预览。")
        }
        return String(localized: "图片、视频、PDF、Office 文档等均可预览（QuickLook）。")
    }

    @ViewBuilder
    private var deleteConfirmationButton: some View {
        Button("删除 \(object.key)", role: .destructive) {
            Task {
                if await viewModel.delete(key: object.key) {
                    dismiss()
                }
            }
        }
    }
}

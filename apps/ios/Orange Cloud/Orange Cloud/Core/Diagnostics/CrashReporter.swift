//
//  CrashReporter.swift
//  Orange Cloud
//
//  Best-effort crash capture for debugging device-only failures. iOS terminates
//  the process after fatal signals, so reports are shown on the next launch.
//

import Darwin
import Foundation

nonisolated struct CrashReport: Identifiable, Sendable {
    let id = UUID()
    let text: String
}

nonisolated enum CrashReporter {

    private static let directoryName = "Logs"
    private static let reportFileName = "last-crash.txt"
    private static let maxReportCharacters = 24_000
    private static let handledSignals: [Int32] = [
        SIGABRT, SIGILL, SIGSEGV, SIGFPE, SIGBUS, SIGPIPE, SIGTRAP,
    ]

    static func install() {
        NSSetUncaughtExceptionHandler(crashExceptionHandler)
        for signalCode in handledSignals {
            signal(signalCode, crashSignalHandler)
        }
    }

    static func pendingReport() -> CrashReport? {
        guard let text = currentReportText(), !text.isEmpty else { return nil }
        return CrashReport(text: text)
    }

    static func currentReportText() -> String? {
        let text = try? String(contentsOf: reportURL, encoding: .utf8)
        guard let text, !text.isEmpty else { return nil }
        return String(text.prefix(maxReportCharacters))
    }

    static func clearPendingReport() {
        try? FileManager.default.removeItem(at: reportURL)
    }

    fileprivate static func record(exception: NSException) {
        writeReport(
            title: "Uncaught NSException",
            details: [
                "name: \(exception.name.rawValue)",
                "reason: \(exception.reason ?? "nil")",
            ],
            stack: exception.callStackSymbols
        )
    }

    fileprivate static func record(signal signalCode: Int32) {
        writeReport(
            title: "Fatal Signal",
            details: [
                "signal: \(signalCode)",
                "name: \(signalName(signalCode))",
            ],
            stack: Thread.callStackSymbols
        )
    }

    private static var reportURL: URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent(directoryName, isDirectory: true)
            .appendingPathComponent(reportFileName)
    }

    private static func writeReport(title: String, details: [String], stack: [String]) {
        let report = formatReport(title: title, details: details, stack: stack)
        do {
            try FileManager.default.createDirectory(
                at: reportURL.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            try Data(report.utf8).write(to: reportURL, options: .atomic)
        } catch {
            NSLog("Orange Cloud crash report write failed: %@", error.localizedDescription)
        }
    }

    private static func formatReport(title: String, details: [String], stack: [String]) -> String {
        let header = [
            "Orange Cloud Crash Report",
            "capturedAt: \(ISO8601DateFormatter().string(from: Date()))",
            "type: \(title)",
        ]
        return (header + details + ["", "Call Stack:"] + stack).joined(separator: "\n")
    }

    private static func signalName(_ signalCode: Int32) -> String {
        switch signalCode {
        case SIGABRT: return "SIGABRT"
        case SIGILL:  return "SIGILL"
        case SIGSEGV: return "SIGSEGV"
        case SIGFPE:  return "SIGFPE"
        case SIGBUS:  return "SIGBUS"
        case SIGPIPE: return "SIGPIPE"
        case SIGTRAP: return "SIGTRAP"
        default:      return "UNKNOWN"
        }
    }
}

private func crashExceptionHandler(_ exception: NSException) {
    CrashReporter.record(exception: exception)
}

private func crashSignalHandler(_ signalCode: Int32) {
    Darwin.signal(signalCode, SIG_DFL)
    CrashReporter.record(signal: signalCode)
    raise(signalCode)
}

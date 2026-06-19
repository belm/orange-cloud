//
//  BackgroundRefresh.swift
//  Orange Cloud
//
//  BGAppRefreshTask：后台静默刷新 OAuth Token，避免用户回到 App 时 Token 已过期。
//  标识符登记在 Info.plist 的 BGTaskSchedulerPermittedIdentifiers。
//

import Foundation
import BackgroundTasks

@MainActor
enum BackgroundRefresh {

    static let taskIdentifier = "jiamin.chen.Orange-Cloud.refresh"

    /// App 启动时注册（必须在 didFinishLaunching 前，App.init 中调用）
    static func register(authManager: AuthManager) {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskIdentifier, using: nil) { task in
            guard let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            let work = Task { @MainActor in
                schedule()   // 链式排下一次
                AppLog.background.notice("BGAppRefresh fired, loggedIn=\(authManager.isLoggedIn)")
                if authManager.isLoggedIn {
                    _ = try? await authManager.refreshAccessToken()
                    // 顺带做通知检测（Zone 状态变化 / Worker 错误）
                    await AppNotifications.runBackgroundChecks(authManager: authManager)
                }
                refreshTask.setTaskCompleted(success: true)
                AppLog.background.info("BGAppRefresh completed")
            }
            refreshTask.expirationHandler = {
                AppLog.background.error("BGAppRefresh expired (system cut off)")
                work.cancel()
                refreshTask.setTaskCompleted(success: false)
            }
        }
    }

    /// 进入后台时调度（系统决定实际执行时机）
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 4 * 3600)   // 至少 4 小时后
        do {
            try BGTaskScheduler.shared.submit(request)
            AppLog.background.info("scheduled BGAppRefresh (earliest +4h)")
        } catch {
            AppLog.background.error("schedule BGAppRefresh failed: \(error.localizedDescription)")
        }
    }
}

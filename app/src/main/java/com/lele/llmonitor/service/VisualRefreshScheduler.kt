package com.lele.llmonitor.service

import kotlin.math.min

/**
 * 统一管理通知/小组件刷新节流与兜底策略。
 * - 外部触发（插拔/强刷/保活）时立即重置节流
 * - 电源状态变化时强制刷新
 * - 长时间无成功刷新时触发看门狗刷新
 */
class VisualRefreshScheduler(
    private val staleForceFloorMs: Long = 15_000L,
    private val defaultPollCeilingMs: Long = 10_000L
) {
    data class Decision(
        val shouldPublish: Boolean,
        val targetDelayMs: Long
    )

    private var lastNotificationTimeMs: Long = 0L
    private var lastWidgetTimeMs: Long = 0L
    private var lastHeartbeatTimeMs: Long = 0L
    private var lastPluggedState: Int? = null

    fun onExternalTrigger() {
        lastNotificationTimeMs = 0L
        lastWidgetTimeMs = 0L
        lastHeartbeatTimeMs = 0L
    }

    fun evaluate(
        nowElapsedMs: Long,
        notifyIntervalMs: Long,
        pluggedState: Int,
        pollCeilingMs: Long = defaultPollCeilingMs
    ): Decision {
        val powerStateChanged = lastPluggedState?.let { it != pluggedState } ?: false
        lastPluggedState = pluggedState

        val staleForceInterval = (notifyIntervalMs * 3L).coerceAtLeast(staleForceFloorMs)
        val shouldPublish =
            powerStateChanged ||
                nowElapsedMs - lastNotificationTimeMs >= notifyIntervalMs ||
                nowElapsedMs - lastWidgetTimeMs >= notifyIntervalMs ||
                nowElapsedMs - lastHeartbeatTimeMs >= staleForceInterval

        return Decision(
            shouldPublish = shouldPublish,
            targetDelayMs = min(notifyIntervalMs, pollCeilingMs)
        )
    }

    fun onPublishResult(
        nowElapsedMs: Long,
        notificationUpdated: Boolean,
        widgetUpdated: Boolean
    ) {
        if (notificationUpdated) {
            lastNotificationTimeMs = nowElapsedMs
        }
        if (widgetUpdated) {
            lastWidgetTimeMs = nowElapsedMs
        }
        if (notificationUpdated || widgetUpdated) {
            lastHeartbeatTimeMs = nowElapsedMs
        }
    }
}

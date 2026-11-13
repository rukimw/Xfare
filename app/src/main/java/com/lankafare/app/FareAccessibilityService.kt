package com.lankafare.app

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class FareAccessibilityService : AccessibilityService() {

    private var lastReadingKey: String = ""
    private var lastTriggerElapsedMs: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName !in ALLOWED_PACKAGES) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val root = rootInActiveWindow ?: event.source ?: return
        val collectedTexts = ArrayList<String>(24)

        try {
            collectVisibleText(root, collectedTexts, depth = 0)
        } finally {
            safeRecycle(root)
        }

        val reading = FareParser.parseScreenText(collectedTexts) ?: return

        val key = String.format(
            Locale.US,
            "%s|%.0f|%.1f",
            packageName,
            reading.totalFareLkr,
            reading.totalDistanceKm
        )

        val now = SystemClock.elapsedRealtime()
        if (key == lastReadingKey && (now - lastTriggerElapsedMs) < COOLDOWN_MS) {
            return
        }

        lastReadingKey = key
        lastTriggerElapsedMs = now

        OverlayManager.getInstance(applicationContext).showFareBanner(
            totalFareLkr = reading.totalFareLkr,
            totalDistanceKm = reading.totalDistanceKm,
            sourcePackage = packageName
        )
    }

    override fun onInterrupt() {
        OverlayManager.getInstance(applicationContext).dismissBanner()
    }

    override fun onDestroy() {
        super.onDestroy()
        OverlayManager.getInstance(applicationContext).dismissBanner()
    }

    private fun collectVisibleText(
        node: AccessibilityNodeInfo,
        out: MutableList<String>,
        depth: Int
    ) {
        if (depth > MAX_TREE_DEPTH) return
        if (!node.isVisibleToUser) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            out.add(text)
        } else {
            val contentDesc = node.contentDescription?.toString()?.trim()
            if (!contentDesc.isNullOrEmpty()) {
                out.add(contentDesc)
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectVisibleText(child, out, depth + 1)
            } finally {
                safeRecycle(child)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun safeRecycle(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            try {
                node.recycle()
            } catch (_: IllegalStateException) {
                // Node already recycled
            }
        }
    }

    companion object {
        private val ALLOWED_PACKAGES = setOf(
            "com.pickme.driver",
            "com.ubercab.driver",
            "lk.helago.driver"
        )
        private const val COOLDOWN_MS = 2_000L
        private const val MAX_TREE_DEPTH = 32
    }
}

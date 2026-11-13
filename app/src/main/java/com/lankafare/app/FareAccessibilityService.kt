package com.lankafare.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class FareAccessibilityService : AccessibilityService() {

    private lateinit var overlayManager: OverlayManager
    private var lastProcessTimeMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayManager = OverlayManager(applicationContext)
        KeepAliveService.ensureRunning(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val appLabel = SUPPORTED_PACKAGES[pkg] ?: return

        val now = System.currentTimeMillis()
        if (now - lastProcessTimeMs < THROTTLE_MS) return
        lastProcessTimeMs = now

        val root = rootInActiveWindow ?: return
        val lines = ArrayList<String>(48)
        try {
            collectVisibleText(root, lines, 0)
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }

        if (lines.isEmpty()) return

        val prefs = getSharedPreferences(OverlayManager.PREFS_NAME, Context.MODE_PRIVATE)
        val commissionPct = prefs.getFloat(OverlayManager.PREF_COMMISSION_PCT, 15f).toDouble()
        val greenThreshold = prefs.getFloat(OverlayManager.PREF_GREEN_THRESHOLD, 120f).toDouble()
        val amberThreshold = prefs.getFloat(OverlayManager.PREF_AMBER_THRESHOLD, 85f).toDouble()

        val evaluation = parseRideLines(
            appName = appLabel,
            lines = lines,
            commissionPct = commissionPct,
            greenThreshold = greenThreshold,
            amberThreshold = amberThreshold
        )

        if (evaluation != null) {
            overlayManager.showBanner(evaluation)
        }
    }

    override fun onInterrupt() {
        if (::overlayManager.isInitialized) {
            overlayManager.hideBanner()
        }
    }

    override fun onDestroy() {
        if (::overlayManager.isInitialized) {
            overlayManager.release()
        }
        super.onDestroy()
    }

    private fun collectVisibleText(
        node: AccessibilityNodeInfo,
        out: MutableList<String>,
        depth: Int
    ) {
        if (depth > MAX_NODE_DEPTH || out.size >= MAX_LINES) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            out.add(text)
        }
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty() && desc != text) {
            out.add(desc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectVisibleText(child, out, depth + 1)
            } finally {
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
    }

    companion object {
        private const val THROTTLE_MS = 280L
        private const val MAX_NODE_DEPTH = 18
        private const val MAX_LINES = 80

        val SUPPORTED_PACKAGES = mapOf(
            "com.pickme.driver" to "PickMe",
            "com.ubercab.driver" to "Uber",
            "lk.helago.driver" to "HelaGo"
        )

        private val WALLET_IGNORE_REGEX = Regex(
            "(wallet|balance|bal\\b|cash\\s*in\\s*hand|credit|float|owed|due|today|earned|earnings|bonus)",
            RegexOption.IGNORE_CASE
        )

        private val CURRENCY_PREFIX_REGEX = Regex(
            "(?:rs\\.?|lkr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
            RegexOption.IGNORE_CASE
        )

        private val CURRENCY_SUFFIX_REGEX = Regex(
            "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(?:rs\\.?|lkr)\\b",
            RegexOption.IGNORE_CASE
        )

        private val DISTANCE_REGEX = Regex(
            "([0-9]+(?:\\.[0-9]+)?)\\s*(km|m)(?!\\s*/\\s*h|\\w)",
            RegexOption.IGNORE_CASE
        )

        private val PICKUP_HINT_REGEX = Regex("(pickup|away|to\\s*pickup|approach)", RegexOption.IGNORE_CASE)
        private val TRIP_HINT_REGEX = Regex("(trip|drop|ride|dest|distance)", RegexOption.IGNORE_CASE)

        fun parseRideLines(
            appName: String,
            lines: List<String>,
            commissionPct: Double,
            greenThreshold: Double,
            amberThreshold: Double
        ): OverlayManager.FareEvaluation? {
            val fareCandidates = ArrayList<Double>()
            val pickupDistances = ArrayList<Double>()
            val tripDistances = ArrayList<Double>()
            val genericDistances = ArrayList<Double>()

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                // Ignore small wallet balances or summary earnings lines
                if (!WALLET_IGNORE_REGEX.containsMatchIn(line)) {
                    for (match in CURRENCY_PREFIX_REGEX.findAll(line)) {
                        val value = match.groupValues[1].replace(",", "").toDoubleOrNull()
                        if (value != null && value in 80.0..250_000.0) {
                            fareCandidates.add(value)
                        }
                    }
                    for (match in CURRENCY_SUFFIX_REGEX.findAll(line)) {
                        val value = match.groupValues[1].replace(",", "").toDoubleOrNull()
                        if (value != null && value in 80.0..250_000.0) {
                            fareCandidates.add(value)
                        }
                    }
                }

                // Extract distances (convert meters to km; ignore speeds like km/h)
                val isPickupLine = PICKUP_HINT_REGEX.containsMatchIn(line)
                val isTripLine = TRIP_HINT_REGEX.containsMatchIn(line)

                for (match in DISTANCE_REGEX.findAll(line)) {
                    val rawNum = match.groupValues[1].toDoubleOrNull() ?: continue
                    val unit = match.groupValues[2].lowercase(Locale.US)
                    val km = if (unit == "m") rawNum / 1000.0 else rawNum
                    if (km in 0.05..500.0) {
                        when {
                            isPickupLine && !isTripLine -> pickupDistances.add(km)
                            isTripLine && !isPickupLine -> tripDistances.add(km)
                            else -> genericDistances.add(km)
                        }
                    }
                }
            }

            // Require a valid gross fare (ignore sub-80 wallet remnants)
            val grossFare = fareCandidates.maxOrNull() ?: return null

            var pickupKm = pickupDistances.firstOrNull() ?: 0.0
            var tripKm = tripDistances.firstOrNull() ?: 0.0

            // Sum Pickup + Trip automatically when both appear (explicit or generic)
            if (pickupKm == 0.0 && tripKm == 0.0) {
                when {
                    genericDistances.size >= 2 -> {
                        pickupKm = genericDistances[0]
                        tripKm = genericDistances[1]
                    }
                    genericDistances.size == 1 -> {
                        tripKm = genericDistances[0]
                    }
                }
            } else if (pickupKm == 0.0 && genericDistances.isNotEmpty()) {
                pickupKm = genericDistances[0]
            } else if (tripKm == 0.0 && genericDistances.isNotEmpty()) {
                tripKm = genericDistances[0]
            }

            val totalKm = pickupKm + tripKm
            if (totalKm <= 0.05) return null

            val safeCommission = commissionPct.coerceIn(0.0, 50.0)
            val netPayout = grossFare * (1.0 - safeCommission / 100.0)
            val netPerKm = netPayout / totalKm

            val tier = when {
                netPerKm >= greenThreshold -> OverlayManager.ProfitTier.GREEN
                netPerKm >= amberThreshold -> OverlayManager.ProfitTier.AMBER
                else -> OverlayManager.ProfitTier.RED
            }

            return OverlayManager.FareEvaluation(
                appName = appName,
                grossFare = grossFare,
                netPayout = netPayout,
                pickupKm = pickupKm,
                tripKm = tripKm,
                totalKm = totalKm,
                netPerKm = netPerKm,
                commissionPct = safeCommission,
                tier = tier
            )
        }
    }
}

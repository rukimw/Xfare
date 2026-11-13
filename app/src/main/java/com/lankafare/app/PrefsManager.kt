package com.lankafare.app

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var commissionPercent: Float
        get() = prefs.getFloat(KEY_COMMISSION, DEFAULT_COMMISSION).coerceIn(0f, 95f)
        set(value) = prefs.edit().putFloat(KEY_COMMISSION, value.coerceIn(0f, 95f)).apply()

    var goodThresholdRsPerKm: Float
        get() = prefs.getFloat(KEY_GOOD_THRESHOLD, DEFAULT_GOOD_THRESHOLD).coerceAtLeast(1f)
        set(value) = prefs.edit().putFloat(KEY_GOOD_THRESHOLD, value.coerceAtLeast(1f)).apply()

    var badThresholdRsPerKm: Float
        get() = prefs.getFloat(KEY_BAD_THRESHOLD, DEFAULT_BAD_THRESHOLD).coerceAtLeast(0f)
        set(value) = prefs.edit().putFloat(KEY_BAD_THRESHOLD, value.coerceAtLeast(0f)).apply()

    companion object {
        private const val PREFS_NAME = "lankafare_prefs"
        private const val KEY_COMMISSION = "commission_percent"
        private const val KEY_GOOD_THRESHOLD = "good_threshold_rs_km"
        private const val KEY_BAD_THRESHOLD = "bad_threshold_rs_km"

        const val DEFAULT_COMMISSION = 15f
        const val DEFAULT_GOOD_THRESHOLD = 100f
        const val DEFAULT_BAD_THRESHOLD = 75f
    }
}

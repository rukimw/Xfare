package com.lankafare.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager

    private lateinit var badgeOverlayStatus: TextView
    private lateinit var badgeAccessibilityStatus: TextView
    private lateinit var badgeBatteryStatus: TextView

    private lateinit var inputCommission: EditText
    private lateinit var inputGoodThreshold: EditText
    private lateinit var inputBadThreshold: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsManager(this)

        badgeOverlayStatus = findViewById(R.id.badgeOverlayStatus)
        badgeAccessibilityStatus = findViewById(R.id.badgeAccessibilityStatus)
        badgeBatteryStatus = findViewById(R.id.badgeBatteryStatus)

        inputCommission = findViewById(R.id.inputCommission)
        inputGoodThreshold = findViewById(R.id.inputGoodThreshold)
        inputBadThreshold = findViewById(R.id.inputBadThreshold)

        setupPermissionCards()
        setupPreferencesInputs()
        setupTestOverlayButton()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatuses()
    }

    private fun setupPermissionCards() {
        findViewById<LinearLayout>(R.id.cardOverlayPermission).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivitySafe(intent, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        }

        findViewById<LinearLayout>(R.id.cardAccessibilityPermission).setOnClickListener {
            startActivitySafe(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<LinearLayout>(R.id.cardBatteryPermission).setOnClickListener {
            val requestIntent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(requestIntent)
            } catch (_: Exception) {
                startActivitySafe(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun setupPreferencesInputs() {
        inputCommission.setText(formatPrefValue(prefs.commissionPercent))
        inputGoodThreshold.setText(formatPrefValue(prefs.goodThresholdRsPerKm))
        inputBadThreshold.setText(formatPrefValue(prefs.badThresholdRsPerKm))

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                persistInputs()
            }
        }

        inputCommission.addTextChangedListener(watcher)
        inputGoodThreshold.addTextChangedListener(watcher)
        inputBadThreshold.addTextChangedListener(watcher)
    }

    private fun setupTestOverlayButton() {
        findViewById<Button>(R.id.btnTestOverlay).setOnClickListener {
            persistInputs()
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this,
                    "Please enable 'Display Over Other Apps' first.",
                    Toast.LENGTH_SHORT
                ).show()
                startActivitySafe(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
                return@setOnClickListener
            }

            // Sample Colombo ride: Rs. 1,350 total across 10.0 km
            OverlayManager.getInstance(applicationContext).showFareBanner(
                totalFareLkr = 1350.0,
                totalDistanceKm = 10.0,
                sourcePackage = "com.pickme.driver"
            )
        }
    }

    private fun persistInputs() {
        inputCommission.text.toString().toFloatOrNull()?.let {
            prefs.commissionPercent = it
        }
        inputGoodThreshold.text.toString().toFloatOrNull()?.let {
            prefs.goodThresholdRsPerKm = it
        }
        inputBadThreshold.text.toString().toFloatOrNull()?.let {
            prefs.badThresholdRsPerKm = it
        }
    }

    private fun refreshPermissionStatuses() {
        val overlayGranted = Settings.canDrawOverlays(this)
        val accessibilityGranted = isAccessibilityServiceEnabled()
        val batteryGranted = isIgnoringBatteryOptimizations()

        applyStatusBadge(badgeOverlayStatus, overlayGranted)
        applyStatusBadge(badgeAccessibilityStatus, accessibilityGranted)
        applyStatusBadge(badgeBatteryStatus, batteryGranted)
    }

    private fun applyStatusBadge(badge: TextView, isActive: Boolean) {
        if (isActive) {
            badge.setText(R.string.status_active)
            badge.setTextColor(ContextCompat.getColor(this, R.color.status_active_green))
            badge.setBackgroundResource(R.drawable.bg_badge_active)
        } else {
            badge.setText(R.string.status_required)
            badge.setTextColor(ContextCompat.getColor(this, R.color.status_required_red))
            badge.setBackgroundResource(R.drawable.bg_badge_required)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(this, FareAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val componentName = ComponentName.unflattenFromString(splitter.next())
            if (componentName != null && componentName == expectedComponent) {
                return true
            }
        }
        return false
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun startActivitySafe(primaryIntent: Intent, fallbackAction: String? = null) {
        try {
            startActivity(primaryIntent)
        } catch (_: Exception) {
            if (fallbackAction != null) {
                try {
                    startActivity(Intent(fallbackAction))
                } catch (_: Exception) {
                    Toast.makeText(this, "Unable to open system settings", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun formatPrefValue(value: Float): String {
        return if (value % 1f == 0f) value.toInt().toString() else value.toString()
    }
}

package com.lankafare.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.lankafare.app.R

class MainActivity : AppCompatActivity() {

    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvKeepAliveStatus: TextView
    private lateinit var tvBatteryStatus: TextView

    private lateinit var btnEnableAccessibility: Button
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnToggleKeepAlive: Button
    private lateinit var btnBatteryIgnore: Button
    private lateinit var btnXiaomiAutostart: Button
    private lateinit var btnMiuiOtherPermissions: Button

    private lateinit var switchGreenBeep: SwitchCompat
    private lateinit var etCommissionPct: EditText
    private lateinit var etGreenThreshold: EditText
    private lateinit var etAmberThreshold: EditText
    private lateinit var btnSaveSettings: Button
    private lateinit var btnTestOverlay: Button
    private lateinit var tvDeveloperEmail: TextView

    private lateinit var overlayManager: OverlayManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        overlayManager = OverlayManager(applicationContext)
        bindViews()
        loadPreferences()
        setupListeners()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshServiceStatuses()
        autoRecoverKeepAliveIfReady()
    }

    override fun onDestroy() {
        overlayManager.release()
        super.onDestroy()
    }

    private fun bindViews() {
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvKeepAliveStatus = findViewById(R.id.tvKeepAliveStatus)
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus)

        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnToggleKeepAlive = findViewById(R.id.btnToggleKeepAlive)
        btnBatteryIgnore = findViewById(R.id.btnBatteryIgnore)
        btnXiaomiAutostart = findViewById(R.id.btnXiaomiAutostart)
        btnMiuiOtherPermissions = findViewById(R.id.btnMiuiOtherPermissions)

        switchGreenBeep = findViewById(R.id.switchGreenBeep)
        etCommissionPct = findViewById(R.id.etCommissionPct)
        etGreenThreshold = findViewById(R.id.etGreenThreshold)
        etAmberThreshold = findViewById(R.id.etAmberThreshold)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        btnTestOverlay = findViewById(R.id.btnTestOverlay)
        tvDeveloperEmail = findViewById(R.id.tvDeveloperEmail)
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(OverlayManager.PREFS_NAME, Context.MODE_PRIVATE)
        switchGreenBeep.isChecked = prefs.getBoolean(OverlayManager.PREF_GREEN_BEEP_ENABLED, true)
        etCommissionPct.setText(prefs.getFloat(OverlayManager.PREF_COMMISSION_PCT, 15f).toInt().toString())
        etGreenThreshold.setText(prefs.getFloat(OverlayManager.PREF_GREEN_THRESHOLD, 120f).toInt().toString())
        etAmberThreshold.setText(prefs.getFloat(OverlayManager.PREF_AMBER_THRESHOLD, 85f).toInt().toString())
    }

    private fun setupListeners() {
        btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        btnToggleKeepAlive.setOnClickListener {
            if (KeepAliveService.isRunning) {
                KeepAliveService.stop(this)
            } else {
                KeepAliveService.start(this)
            }
            btnToggleKeepAlive.postDelayed({ refreshServiceStatuses() }, 250L)
        }

        btnBatteryIgnore.setOnClickListener {
            openBatteryOptimizationSettings()
        }

        btnXiaomiAutostart.setOnClickListener {
            openXiaomiAutostart()
        }

        btnMiuiOtherPermissions.setOnClickListener {
            openMiuiOtherPermissions()
        }

        switchGreenBeep.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences(OverlayManager.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(OverlayManager.PREF_GREEN_BEEP_ENABLED, isChecked)
                .apply()
        }

        btnSaveSettings.setOnClickListener {
            saveEngineThresholds()
        }

        btnTestOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant Floating Banner permission first", Toast.LENGTH_SHORT).show()
                btnGrantOverlay.performClick()
                return@setOnClickListener
            }
            saveEngineThresholds(silent = true)
            val prefs = getSharedPreferences(OverlayManager.PREFS_NAME, Context.MODE_PRIVATE)
            val comm = prefs.getFloat(OverlayManager.PREF_COMMISSION_PCT, 15f).toDouble()
            val greenThresh = prefs.getFloat(OverlayManager.PREF_GREEN_THRESHOLD, 120f).toDouble()
            val sampleGross = (greenThresh * 8.0) / (1.0 - comm / 100.0) * 1.15
            val netPayout = sampleGross * (1.0 - comm / 100.0)
            overlayManager.showBanner(
                OverlayManager.FareEvaluation(
                    appName = "PickMe",
                    grossFare = sampleGross,
                    netPayout = netPayout,
                    pickupKm = 1.5,
                    tripKm = 6.5,
                    totalKm = 8.0,
                    netPerKm = netPayout / 8.0,
                    commissionPct = comm,
                    tier = OverlayManager.ProfitTier.GREEN
                )
            )
        }

        tvDeveloperEmail.setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:rukimw@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "X FareLK v2.0 Pro Support")
            }
            try {
                startActivity(emailIntent)
            } catch (_: Exception) {
                Toast.makeText(this, "No email app found: rukimw@gmail.com", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveEngineThresholds(silent: Boolean = false) {
        val comm = etCommissionPct.text.toString().toFloatOrNull()?.coerceIn(0f, 50f) ?: 15f
        val green = etGreenThreshold.text.toString().toFloatOrNull()?.coerceAtLeast(10f) ?: 120f
        val amber = etAmberThreshold.text.toString().toFloatOrNull()?.coerceIn(5f, green) ?: 85f

        getSharedPreferences(OverlayManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(OverlayManager.PREF_COMMISSION_PCT, comm)
            .putFloat(OverlayManager.PREF_GREEN_THRESHOLD, green)
            .putFloat(OverlayManager.PREF_AMBER_THRESHOLD, amber)
            .apply()

        if (!silent) {
            Toast.makeText(this, "Fare thresholds saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshServiceStatuses() {
        val accEnabled = isAccessibilityServiceEnabled()
        tvAccessibilityStatus.text = if (accEnabled) {
            "Active • Reading PickMe, Uber & HelaGo"
        } else {
            "Disabled • Tap Enable"
        }
        tvAccessibilityStatus.setTextColor(
            Color.parseColor(if (accEnabled) "#1B5E20" else "#B71C1C")
        )
        btnEnableAccessibility.text = if (accEnabled) "Settings" else "Enable"

        val overlayGranted = Settings.canDrawOverlays(this)
        tvOverlayStatus.text = if (overlayGranted) {
            "Granted • Floating Banner Ready"
        } else {
            "Not Granted • Tap Grant"
        }
        tvOverlayStatus.setTextColor(
            Color.parseColor(if (overlayGranted) "#1B5E20" else "#B71C1C")
        )
        btnGrantOverlay.text = if (overlayGranted) "Manage" else "Grant"

        val fgsRunning = KeepAliveService.isRunning
        tvKeepAliveStatus.text = if (fgsRunning) {
            "Running • X FareLK Active"
        } else {
            "Stopped • Tap Start"
        }
        tvKeepAliveStatus.setTextColor(
            Color.parseColor(if (fgsRunning) "#1B5E20" else "#F57F17")
        )
        btnToggleKeepAlive.text = if (fgsRunning) "Stop" else "Start"

        val batteryUnrestricted = isIgnoringBatteryOptimizations()
        tvBatteryStatus.text = if (batteryUnrestricted) {
            "Unrestricted • Protected from Doze"
        } else {
            "Restricted • Allow Unrestricted"
        }
        tvBatteryStatus.setTextColor(
            Color.parseColor(if (batteryUnrestricted) "#1B5E20" else "#F57F17")
        )
        btnBatteryIgnore.text = if (batteryUnrestricted) "Done" else "Allow"
    }

    private fun autoRecoverKeepAliveIfReady() {
        if (isAccessibilityServiceEnabled() && !KeepAliveService.isRunning) {
            KeepAliveService.start(applicationContext)
            tvKeepAliveStatus.postDelayed({ refreshServiceStatuses() }, 300L)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = ComponentName(this, FareAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val component = splitter.next()
            if (component.equals(expectedService, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Exception) {
            openAppDetailsSettings()
        }
    }

    private fun openXiaomiAutostart() {
        val autostartIntent = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        }
        try {
            startActivity(autostartIntent)
        } catch (_: Exception) {
            openAppDetailsSettings()
        }
    }

    private fun openMiuiOtherPermissions() {
        val permEditorIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            putExtra("extra_pkgname", packageName)
        }
        try {
            startActivity(permEditorIntent)
        } catch (_: Exception) {
            val explicitMiuiIntent = Intent().apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", packageName)
            }
            try {
                startActivity(explicitMiuiIntent)
            } catch (_: Exception) {
                openAppDetailsSettings()
            }
        }
    }

    private fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_POST_NOTIFICATIONS
                )
            }
        }
    }

    companion object {
        private const val REQ_POST_NOTIFICATIONS = 1001
    }
}

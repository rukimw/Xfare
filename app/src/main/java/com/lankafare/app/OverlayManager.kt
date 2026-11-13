package com.lankafare.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

class OverlayManager private constructor(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = PrefsManager(context)

    private var overlayView: View? = null
    private val dismissRunnable = Runnable { dismissBanner() }

    fun showFareBanner(
        totalFareLkr: Double,
        totalDistanceKm: Double,
        sourcePackage: String = ""
    ) {
        if (totalDistanceKm <= 0.0) return

        mainHandler.post {
            if (!Settings.canDrawOverlays(context)) {
                return@post
            }

            val commission = prefs.commissionPercent.toDouble()
            val goodThreshold = prefs.goodThresholdRsPerKm.toDouble()
            val badThreshold = prefs.badThresholdRsPerKm.toDouble()

            val netFare = totalFareLkr * (1.0 - commission / 100.0)
            val netRatePerKm = netFare / totalDistanceKm

            val (bgColorHex, verdictLabel) = when {
                netRatePerKm >= goodThreshold -> COLOR_GREEN to "GOOD RIDE"
                netRatePerKm <= badThreshold -> COLOR_RED to "LOW RATE"
                else -> COLOR_AMBER to "FAIR RIDE"
            }

            val view = ensureOverlayView()
            bindBannerData(
                view = view,
                bgColorHex = bgColorHex,
                verdictLabel = verdictLabel,
                sourcePackage = sourcePackage,
                netRatePerKm = netRatePerKm,
                netFare = netFare,
                totalDistanceKm = totalDistanceKm
            )

            mainHandler.removeCallbacks(dismissRunnable)
            mainHandler.postDelayed(dismissRunnable, AUTO_DISMISS_MS)
        }
    }

    fun dismissBanner() {
        mainHandler.post {
            mainHandler.removeCallbacks(dismissRunnable)
            val view = overlayView ?: return@post
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) {
                    // Already detached
                }
            } finally {
                overlayView = null
            }
        }
    }

    private fun ensureOverlayView(): View {
        overlayView?.let { return it }

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_fare_banner, null)
        view.findViewById<ImageButton>(R.id.btnCloseOverlay).setOnClickListener {
            dismissBanner()
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val topMarginPx = (24 * context.resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = topMarginPx
        }

        windowManager.addView(view, params)
        overlayView = view
        return view
    }

    private fun bindBannerData(
        view: View,
        bgColorHex: String,
        verdictLabel: String,
        sourcePackage: String,
        netRatePerKm: Double,
        netFare: Double,
        totalDistanceKm: Double
    ) {
        val cardContainer = view.findViewById<LinearLayout>(R.id.overlayCardContainer)
        val bgDrawable = (cardContainer.background?.mutate() as? GradientDrawable)
            ?: GradientDrawable().apply {
                cornerRadius = 16f * context.resources.displayMetrics.density
            }
        bgDrawable.setColor(Color.parseColor(bgColorHex))
        cardContainer.background = bgDrawable

        val appLabel = when (sourcePackage) {
            "com.pickme.driver" -> "PICKME"
            "com.ubercab.driver" -> "UBER"
            "lk.helago.driver" -> "HELAGO"
            else -> "PREVIEW"
        }

        val intFormat = NumberFormat.getIntegerInstance(Locale.US)
        val roundedRate = netRatePerKm.roundToLong()
        val roundedNet = netFare.roundToLong()

        view.findViewById<TextView>(R.id.tvVerdictTag).text = "$verdictLabel · $appLabel"
        view.findViewById<TextView>(R.id.tvNetRatePerKm).text =
            "Rs. ${intFormat.format(roundedRate)} / km"
        view.findViewById<TextView>(R.id.tvNetPayout).text =
            "Net: Rs. ${intFormat.format(roundedNet)}"
        view.findViewById<TextView>(R.id.tvTotalDistance).text =
            String.format(Locale.US, "Dist: %.1f km", totalDistanceKm)
    }

    companion object {
        private const val COLOR_GREEN = "#1B5E20"
        private const val COLOR_AMBER = "#F57F17"
        private const val COLOR_RED = "#B71C1C"
        private const val AUTO_DISMISS_MS = 8_000L

        @Volatile
        private var instance: OverlayManager? = null

        fun getInstance(context: Context): OverlayManager {
            return instance ?: synchronized(this) {
                instance ?: OverlayManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

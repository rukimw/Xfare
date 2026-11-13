package lk.xfare.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.cardview.widget.CardView
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

class OverlayManager(private val context: Context) {

    enum class ProfitTier(val colorHex: String, val label: String) {
        GREEN("#1B5E20", "HIGH PROFIT"),
        AMBER("#F57F17", "MODERATE"),
        RED("#B71C1C", "LOW RATE")
    }

    data class FareEvaluation(
        val appName: String,
        val grossFare: Double,
        val netPayout: Double,
        val pickupKm: Double,
        val tripKm: Double,
        val totalKm: Double,
        val netPerKm: Double,
        val commissionPct: Double,
        val tier: ProfitTier
    )

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val rsFormatter = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 0
        isGroupingUsed = true
    }

    private var bannerView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var activeToneGenerator: ToneGenerator? = null
    private var lastBeepSignature: String? = null
    private var lastBeepTimestamp: Long = 0L

    private val autoHideRunnable = Runnable { hideBanner() }

    @SuppressLint("ClickableViewAccessibility")
    fun showBanner(eval: FareEvaluation) {
        if (!Settings.canDrawOverlays(context)) return

        mainHandler.post {
            ensureBannerAttached()
            val view = bannerView ?: return@post

            val card = view.findViewById<CardView>(R.id.overlayCard)
            val tvHeader = view.findViewById<TextView>(R.id.tvBannerHeader)
            val tvNetPerKm = view.findViewById<TextView>(R.id.tvNetPerKm)
            val tvNetPayout = view.findViewById<TextView>(R.id.tvNetPayout)
            val tvGrossFare = view.findViewById<TextView>(R.id.tvGrossFare)
            val tvTotalKm = view.findViewById<TextView>(R.id.tvTotalKm)
            val tvBreakdown = view.findViewById<TextView>(R.id.tvDistanceBreakdown)

            card.setCardBackgroundColor(Color.parseColor(eval.tier.colorHex))
            tvHeader.text = "${eval.appName.uppercase(Locale.US)} • ${eval.tier.label}"
            tvNetPerKm.text = String.format(Locale.US, "Rs %.2f", eval.netPerKm)
            tvNetPayout.text = "Rs ${rsFormatter.format(eval.netPayout.roundToInt())}"
            tvGrossFare.text = "Rs ${rsFormatter.format(eval.grossFare.roundToInt())}"
            tvTotalKm.text = String.format(Locale.US, "%.1f km", eval.totalKm)

            val breakdownPrefix = if (eval.pickupKm > 0.0 && eval.tripKm > 0.0) {
                String.format(
                    Locale.US,
                    "Pickup %.1f km + Trip %.1f km • Comm %.0f%%",
                    eval.pickupKm,
                    eval.tripKm,
                    eval.commissionPct
                )
            } else {
                String.format(
                    Locale.US,
                    "Trip %.1f km • Comm %.0f%%",
                    eval.totalKm,
                    eval.commissionPct
                )
            }
            tvBreakdown.text = "$breakdownPrefix • Drag to move"

            // Trigger dual-beep chime ONLY for GREEN rides when enabled
            val signature = "${eval.appName}|${eval.grossFare.roundToInt()}|${String.format(Locale.US, "%.1f", eval.totalKm)}"
            if (eval.tier == ProfitTier.GREEN) {
                val now = System.currentTimeMillis()
                if (signature != lastBeepSignature || (now - lastBeepTimestamp) > BEEP_COOLDOWN_MS) {
                    lastBeepSignature = signature
                    lastBeepTimestamp = now
                    playGreenRideChime()
                }
            }

            mainHandler.removeCallbacks(autoHideRunnable)
            mainHandler.postDelayed(autoHideRunnable, AUTO_HIDE_MS)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureBannerAttached() {
        if (bannerView != null) return

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.overlay_fare_banner, null)

        val displayMetrics = context.resources.displayMetrics
        val defaultY = (displayMetrics.heightPixels * 0.14f).toInt()
        val savedY = prefs.getInt(PREF_OVERLAY_Y, defaultY)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = savedY.coerceIn(0, (displayMetrics.heightPixels * 0.78f).toInt())
        }

        view.findViewById<View>(R.id.btnCloseBanner).setOnClickListener {
            hideBanner()
        }

        val dragTarget = view.findViewById<View>(R.id.overlayDragHandle)
        var initialY = 0
        var initialTouchY = 0f

        dragTarget.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = params.y
                    initialTouchY = event.rawY
                    mainHandler.removeCallbacks(autoHideRunnable)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    val maxY = (displayMetrics.heightPixels - view.height).coerceAtLeast(100)
                    params.y = (initialY + deltaY).coerceIn(0, maxY)
                    try {
                        windowManager.updateViewLayout(view, params)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    prefs.edit().putInt(PREF_OVERLAY_Y, params.y).apply()
                    mainHandler.postDelayed(autoHideRunnable, AUTO_HIDE_MS)
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(view, params)
            bannerView = view
            layoutParams = params
        } catch (_: Exception) {
            bannerView = null
            layoutParams = null
        }
    }

    private fun playGreenRideChime() {
        val beepEnabled = prefs.getBoolean(PREF_GREEN_BEEP_ENABLED, true)
        if (!beepEnabled) return

        releaseToneGenerator()
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            activeToneGenerator = tg
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
            mainHandler.postDelayed({
                if (activeToneGenerator === tg) {
                    releaseToneGenerator()
                } else {
                    try {
                        tg.release()
                    } catch (_: Exception) {
                    }
                }
            }, 350L)
        } catch (_: Exception) {
            releaseToneGenerator()
        }
    }

    private fun releaseToneGenerator() {
        val tg = activeToneGenerator ?: return
        activeToneGenerator = null
        try {
            tg.stopTone()
        } catch (_: Exception) {
        }
        try {
            tg.release()
        } catch (_: Exception) {
        }
    }

    fun hideBanner() {
        mainHandler.removeCallbacks(autoHideRunnable)
        val view = bannerView ?: return
        bannerView = null
        layoutParams = null
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
    }

    fun release() {
        mainHandler.post {
            hideBanner()
            releaseToneGenerator()
        }
    }

    companion object {
        const val PREFS_NAME = "xfare_prefs"
        const val PREF_OVERLAY_Y = "pref_overlay_y"
        const val PREF_GREEN_BEEP_ENABLED = "pref_green_beep_enabled"
        const val PREF_COMMISSION_PCT = "pref_commission_pct"
        const val PREF_GREEN_THRESHOLD = "pref_green_threshold"
        const val PREF_AMBER_THRESHOLD = "pref_amber_threshold"

        private const val AUTO_HIDE_MS = 15_000L
        private const val BEEP_COOLDOWN_MS = 8_000L
    }
}

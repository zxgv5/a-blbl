package blbl.cat3399.core.ui

import android.content.Context
import kotlin.math.min
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.prefs.AppPrefs

object UiScale {
    // Baseline chosen from a known-good TV layout:
    // 1920x1080 with screen scale shown as 1.5x in "设备信息 -> 屏幕".
    const val BASELINE_DENSITY = 1.5f
    const val BASELINE_SHORT_SIDE_PX = 1080f

    // Keep only a very wide sanity clamp to avoid broken metrics causing NaN/0 or absurd values.
    private const val MIN_FACTOR = 0.20f
    private const val MAX_FACTOR = 8.00f

    fun presetFactor(prefValue: String): Float {
        return when (prefValue) {
            AppPrefs.SIDEBAR_SIZE_SMALL -> 0.90f
            AppPrefs.SIDEBAR_SIZE_LARGE -> 1.10f
            else -> 1.00f
        }
    }

    /**
     * Device scale relative to the baseline (1080p @ density 1.5).
     *
     * The formula uses both density and resolution so that:
     * - Same resolution, different "屏幕显示比例" (density) -> scale compensates.
     * - Different resolution -> scale follows screen size, with clamping.
     */
    fun deviceFactor(context: Context): Float {
        val dm = context.resources.displayMetrics
        val density = dm.density.takeIf { it.isFinite() && it > 0f } ?: 1.0f
        val minPx = min(dm.widthPixels, dm.heightPixels).toFloat().takeIf { it.isFinite() && it > 0f } ?: BASELINE_SHORT_SIDE_PX
        val raw = (BASELINE_DENSITY / density) * (minPx / BASELINE_SHORT_SIDE_PX)
        return raw.coerceIn(MIN_FACTOR, MAX_FACTOR)
    }

    fun factor(context: Context): Float {
        return factor(context = context, prefValue = BiliClient.prefs.sidebarSize)
    }

    fun factor(context: Context, prefValue: String): Float {
        val preset = presetFactor(prefValue)
        return (deviceFactor(context) * preset).coerceIn(MIN_FACTOR, MAX_FACTOR)
    }
}

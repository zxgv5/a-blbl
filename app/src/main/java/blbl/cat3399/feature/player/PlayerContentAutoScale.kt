package blbl.cat3399.feature.player

import android.view.View
import kotlin.math.min
import kotlin.math.pow

/**
 * Auto-scale UI based on the effective 16:9 video content size inside [playerView].
 *
 * Baseline is the "known good" tuning target:
 * - 1920x1080 with density x2.00 -> content height ~= 540dp.
 */
object PlayerContentAutoScale {
    private const val BASELINE_CONTENT_HEIGHT_DP = 540f

    // Conservative scaling curve: reduces sensitivity for large screens.
    private const val EXPONENT = 0.99f

    private const val MIN_AUTO_SCALE = 0.85f
    private const val MAX_AUTO_SCALE = 1.35f

    fun factor(playerView: View, density: Float): Float {
        val d = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val widthDp = playerView.width / d
        val heightDp = playerView.height / d
        if (!widthDp.isFinite() || !heightDp.isFinite() || widthDp <= 0f || heightDp <= 0f) return 1f

        // 16:9 content is limited by the smaller of view height and (view width * 9/16).
        val contentHeightDp = min(heightDp, widthDp * 9f / 16f)
        if (contentHeightDp <= 0f) return 1f

        val raw = contentHeightDp / BASELINE_CONTENT_HEIGHT_DP
        val scaled = raw.pow(EXPONENT)
        return scaled.coerceIn(MIN_AUTO_SCALE, MAX_AUTO_SCALE)
    }
}


package blbl.cat3399.feature.following

import android.content.res.Resources
import blbl.cat3399.R

fun followingSpanCountForWidth(resources: Resources, tvMode: Boolean): Int {
    val dm = resources.displayMetrics
    val widthPx = dm.widthPixels
    val recyclerPaddingPx = (8f * dm.density).toInt() * 2 // activity_following_list.xml recycler padding=8dp
    val availablePx = (widthPx - recyclerPaddingPx).coerceAtLeast(1)

    fun px(id: Int): Int = resources.getDimensionPixelSize(id)
    val itemMargin = px(if (tvMode) R.dimen.following_grid_item_margin_tv else R.dimen.following_grid_item_margin)
    val itemPadding = px(if (tvMode) R.dimen.following_grid_item_padding_tv else R.dimen.following_grid_item_padding)
    val avatarSize = px(if (tvMode) R.dimen.following_grid_avatar_size_tv else R.dimen.following_grid_avatar_size)

    // Match item_following_grid + FollowingGridAdapter sizing:
    // columnWidth >= left/right margins + left/right padding + avatarSize
    val minItemWidthPx = (itemMargin * 2) + (itemPadding * 2) + avatarSize
    val raw = (availablePx / minItemWidthPx).coerceAtLeast(1)

    // Avoid becoming overly dense on very wide screens.
    val maxSpan = if (tvMode) 14 else 12
    return raw.coerceAtMost(maxSpan)
}

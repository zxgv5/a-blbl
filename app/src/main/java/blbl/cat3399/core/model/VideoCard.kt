package blbl.cat3399.core.model

data class VideoCard(
    val bvid: String,
    val cid: Long?,
    val title: String,
    val coverUrl: String,
    val durationSec: Int,
    val ownerName: String,
    val ownerFace: String?,
    val view: Long?,
    val danmaku: Long?,
    val pubDateText: String?,
)


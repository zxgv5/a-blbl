package blbl.cat3399.core.api

import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.model.Danmaku
import blbl.cat3399.core.model.Following
import blbl.cat3399.core.model.VideoCard
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.proto.dm.DmSegMobileReply
import blbl.cat3399.proto.dmview.DmWebViewReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

object BiliApi {
    private const val TAG = "BiliApi"

    data class RelationStat(
        val following: Long,
        val follower: Long,
    )

    data class DanmakuWebSetting(
        val dmSwitch: Boolean,
        val allowScroll: Boolean,
        val allowTop: Boolean,
        val allowBottom: Boolean,
        val allowColor: Boolean,
        val allowSpecial: Boolean,
        val aiEnabled: Boolean,
        val aiLevel: Int,
    )

    data class DanmakuWebView(
        val segmentTotal: Int,
        val segmentPageSizeMs: Long,
        val count: Long,
        val setting: DanmakuWebSetting?,
    )

    suspend fun nav(): JSONObject {
        return BiliClient.getJson("https://api.bilibili.com/x/web-interface/nav")
    }

    suspend fun relationStat(vmid: Long): RelationStat {
        val url = BiliClient.withQuery(
            "https://api.bilibili.com/x/relation/stat",
            mapOf("vmid" to vmid.toString()),
        )
        val json = BiliClient.getJson(url)
        val data = json.optJSONObject("data") ?: JSONObject()
        return RelationStat(
            following = data.optLong("following"),
            follower = data.optLong("follower"),
        )
    }

    suspend fun recommend(
        freshIdx: Int = 1,
        ps: Int = 20,
        fetchRow: Int = 1,
    ): List<VideoCard> {
        val keys = BiliClient.ensureWbiKeys()
        val url = BiliClient.signedWbiUrl(
            path = "/x/web-interface/wbi/index/top/feed/rcmd",
            params = mapOf(
                "ps" to ps.toString(),
                "fresh_idx" to freshIdx.toString(),
                "fresh_idx_1h" to freshIdx.toString(),
                "fetch_row" to fetchRow.toString(),
                "feed_version" to "V8",
            ),
            keys = keys,
        )
        val json = BiliClient.getJson(url)
        val items = json.optJSONObject("data")?.optJSONArray("item") ?: JSONArray()
        AppLog.d(TAG, "recommend items=${items.length()}")
        return withContext(Dispatchers.Default) { parseVideoCards(items) }
    }

    suspend fun popular(pn: Int = 1, ps: Int = 20): List<VideoCard> {
        val url = BiliClient.withQuery(
            "https://api.bilibili.com/x/web-interface/popular",
            mapOf("pn" to pn.toString(), "ps" to ps.toString()),
        )
        val json = BiliClient.getJson(url)
        val list = json.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
        AppLog.d(TAG, "popular list=${list.length()}")
        return withContext(Dispatchers.Default) { parseVideoCards(list) }
    }

    suspend fun regionLatest(rid: Int, pn: Int = 1, ps: Int = 20): List<VideoCard> {
        val url = BiliClient.withQuery(
            "https://api.bilibili.com/x/web-interface/dynamic/region",
            mapOf("rid" to rid.toString(), "pn" to pn.toString(), "ps" to ps.toString()),
        )
        val json = BiliClient.getJson(url)
        val archives = json.optJSONObject("data")?.optJSONArray("archives") ?: JSONArray()
        AppLog.d(TAG, "region rid=$rid archives=${archives.length()}")
        return withContext(Dispatchers.Default) { parseVideoCards(archives) }
    }

    suspend fun view(bvid: String): JSONObject {
        val url = BiliClient.withQuery(
            "https://api.bilibili.com/x/web-interface/view",
            mapOf("bvid" to bvid),
        )
        return BiliClient.getJson(url)
    }

    suspend fun playUrlDash(bvid: String, cid: Long, qn: Int = 80, fnval: Int = 16): JSONObject {
        val keys = BiliClient.ensureWbiKeys()
        val hasSessData = BiliClient.cookies.hasSessData()
        val params =
            mutableMapOf(
                "bvid" to bvid,
                "cid" to cid.toString(),
                "qn" to qn.toString(),
                "fnver" to "0",
                "fnval" to fnval.toString(),
                "fourk" to "1",
                "platform" to "pc",
                "otype" to "json",
                "from_client" to "BROWSER",
                "web_location" to "1315873",
                "isGaiaAvoided" to "false",
            )
        if (!hasSessData) {
            params["gaia_source"] = "view-card"
            params["try_look"] = "1"
        }
        genPlayUrlSession()?.let { params["session"] = it }
        return try {
            requestPlayUrl(path = "/x/player/wbi/playurl", params = params, keys = keys)
        } catch (e: BiliApiException) {
            if (hasSessData && isRiskControl(e)) {
                val fallback = params.toMutableMap()
                fallback["try_look"] = "1"
                fallback.remove("gaia_source")
                fallback.remove("session")
                val json = requestPlayUrl(path = "/x/player/wbi/playurl", params = fallback, keys = keys, noCookies = true)
                json.put("__blbl_risk_control_bypassed", true)
                json.put("__blbl_risk_control_code", e.apiCode)
                json.put("__blbl_risk_control_message", e.apiMessage)
                json
            } else {
                throw e
            }
        }
    }

    suspend fun playerWbiV2(bvid: String, cid: Long): JSONObject {
        val keys = BiliClient.ensureWbiKeys()
        val url = BiliClient.signedWbiUrl(
            path = "/x/player/wbi/v2",
            params = mapOf(
                "bvid" to bvid,
                "cid" to cid.toString(),
            ),
            keys = keys,
        )
        return BiliClient.getJson(url)
    }

    suspend fun dmSeg(cid: Long, segmentIndex: Int): List<Danmaku> {
        val url = BiliClient.withQuery(
            "https://api.bilibili.com/x/v2/dm/web/seg.so",
            mapOf(
                "type" to "1",
                "oid" to cid.toString(),
                "segment_index" to segmentIndex.toString(),
            ),
        )
        val bytes = BiliClient.getBytes(url)
        val reply = DmSegMobileReply.parseFrom(bytes)
        val list = reply.elemsList.mapNotNull { e ->
            val text = e.content ?: return@mapNotNull null
            Danmaku(
                timeMs = e.progress,
                mode = e.mode,
                text = text,
                color = e.color.toInt(),
                fontSize = e.fontsize,
                weight = e.weight,
            )
        }
        AppLog.d(TAG, "dmSeg cid=$cid seg=$segmentIndex size=${list.size} state=${reply.state}")
        return list
    }

    suspend fun dmWebView(cid: Long, aid: Long? = null): DanmakuWebView {
        val params = mutableMapOf(
            "type" to "1",
            "oid" to cid.toString(),
        )
        if (aid != null && aid > 0) params["pid"] = aid.toString()
        val url = BiliClient.withQuery("https://api.bilibili.com/x/v2/dm/web/view", params)
        val bytes = BiliClient.getBytes(url)
        val reply = DmWebViewReply.parseFrom(bytes)

        val seg = reply.dmSge
        val segTotal = seg.total.coerceAtLeast(0).toInt()
        val pageSizeMs = seg.pageSize.coerceAtLeast(0)

        val setting = if (reply.hasDmSetting()) {
            val s = reply.dmSetting
            val aiLevel = when (s.aiLevel) {
                0 -> 3 // 0 表示默认等级（通常为 3）
                else -> s.aiLevel.coerceIn(0, 10)
            }
            DanmakuWebSetting(
                dmSwitch = s.dmSwitch,
                allowScroll = s.blockscroll,
                allowTop = s.blocktop,
                allowBottom = s.blockbottom,
                allowColor = s.blockcolor,
                allowSpecial = s.blockspecial,
                aiEnabled = s.aiSwitch,
                aiLevel = aiLevel,
            )
        } else {
            null
        }
        AppLog.d(TAG, "dmWebView cid=$cid segTotal=$segTotal pageSizeMs=$pageSizeMs hasSetting=${setting != null}")
        return DanmakuWebView(
            segmentTotal = segTotal,
            segmentPageSizeMs = pageSizeMs,
            count = reply.count,
            setting = setting,
        )
    }

    private fun parseVideoCards(arr: JSONArray): List<VideoCard> {
        val out = ArrayList<VideoCard>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val bvid = obj.optString("bvid", "")
            if (bvid.isBlank()) continue
            val owner = obj.optJSONObject("owner") ?: JSONObject()
            val stat = obj.optJSONObject("stat") ?: JSONObject()
            out.add(
                VideoCard(
                    bvid = bvid,
                    cid = obj.optLong("cid").takeIf { it > 0 },
                    title = obj.optString("title", ""),
                    coverUrl = obj.optString("pic", obj.optString("cover", "")),
                    durationSec = obj.optInt("duration", parseDuration(obj.optString("duration_text", "0:00"))),
                    ownerName = owner.optString("name", ""),
                    ownerFace = owner.optString("face").takeIf { it.isNotBlank() },
                    view = stat.optLong("view").takeIf { it > 0 } ?: stat.optLong("play").takeIf { it > 0 },
                    danmaku = stat.optLong("danmaku").takeIf { it > 0 } ?: stat.optLong("dm").takeIf { it > 0 },
                    pubDateText = null,
                ),
            )
        }
        return out
    }

    private fun parseDuration(durationText: String): Int {
        val parts = durationText.split(":")
        if (parts.isEmpty()) return 0
        return try {
            when (parts.size) {
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                else -> parts[0].toInt()
            }
        } catch (_: Throwable) {
            0
        }
    }

    suspend fun followings(vmid: Long, pn: Int = 1, ps: Int = 20): List<Following> {
        val url = BiliClient.withQuery(
            "https://api.bilibili.com/x/relation/followings",
            mapOf("vmid" to vmid.toString(), "pn" to pn.toString(), "ps" to ps.toString()),
        )
        val json = BiliClient.getJson(
            url,
            headers = mapOf(
                "Referer" to "https://www.bilibili.com/",
            ),
        )
        val list = json.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
        return withContext(Dispatchers.Default) {
            val out = ArrayList<Following>(list.length())
            for (i in 0 until list.length()) {
                val obj = list.optJSONObject(i) ?: continue
                out.add(
                    Following(
                        mid = obj.optLong("mid"),
                        name = obj.optString("uname", ""),
                        avatarUrl = obj.optString("face").takeIf { it.isNotBlank() },
                    ),
                )
            }
            AppLog.d(TAG, "followings vmid=$vmid size=${out.size}")
            out
        }
    }

    data class DynamicPage(
        val items: List<VideoCard>,
        val nextOffset: String?,
    )

    suspend fun dynamicAllVideo(offset: String? = null): DynamicPage {
        val params = mutableMapOf(
            "type" to "video",
            "platform" to "web",
            "features" to "itemOpusStyle,listOnlyfans,opusBigCover",
        )
        if (!offset.isNullOrBlank()) params["offset"] = offset
        val url = BiliClient.withQuery("https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all", params)
        val json = BiliClient.getJson(url)
        val data = json.optJSONObject("data") ?: JSONObject()
        val items = data.optJSONArray("items") ?: JSONArray()
        return withContext(Dispatchers.Default) {
            val cards = ArrayList<VideoCard>()
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i) ?: continue
                val modules = it.optJSONObject("modules") ?: continue
                val moduleDynamic = modules.optJSONObject("module_dynamic") ?: continue
                val major = moduleDynamic.optJSONObject("major") ?: continue
                val archive = major.optJSONObject("archive") ?: continue
                val bvid = archive.optString("bvid", "")
                if (bvid.isBlank()) continue

                val ownerName = modules.optJSONObject("module_author")?.optString("name", "") ?: ""
                val ownerFace = modules.optJSONObject("module_author")?.optString("face")?.takeIf { it.isNotBlank() }
                val stat = archive.optJSONObject("stat") ?: JSONObject()
                cards.add(
                    VideoCard(
                        bvid = bvid,
                        cid = null,
                        title = archive.optString("title", ""),
                        coverUrl = archive.optString("cover", ""),
                        durationSec = parseDuration(archive.optString("duration_text", "0:00")),
                        ownerName = ownerName,
                        ownerFace = ownerFace,
                        view = stat.optString("play").toLongOrNull(),
                        danmaku = stat.optString("danmaku").toLongOrNull(),
                        pubDateText = null,
                    ),
                )
            }
            val next = data.optString("offset", "").takeIf { it.isNotBlank() }
            AppLog.d(TAG, "dynamicAllVideo size=${cards.size} nextOffset=${next?.take(8)}")
            DynamicPage(cards, next)
        }
    }

    suspend fun dynamicSpaceVideo(hostMid: Long, offset: String? = null): DynamicPage {
        val params = mutableMapOf(
            "host_mid" to hostMid.toString(),
            "platform" to "web",
            "features" to "itemOpusStyle,listOnlyfans,opusBigCover",
        )
        if (!offset.isNullOrBlank()) params["offset"] = offset
        val url = BiliClient.withQuery("https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space", params)
        val json = BiliClient.getJson(url)
        val data = json.optJSONObject("data") ?: JSONObject()
        val items = data.optJSONArray("items") ?: JSONArray()
        return withContext(Dispatchers.Default) {
            val cards = ArrayList<VideoCard>()
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i) ?: continue
                val modules = it.optJSONObject("modules") ?: continue
                val moduleDynamic = modules.optJSONObject("module_dynamic") ?: continue
                val major = moduleDynamic.optJSONObject("major") ?: continue
                val archive = major.optJSONObject("archive") ?: continue
                val bvid = archive.optString("bvid", "")
                if (bvid.isBlank()) continue

                val ownerName = modules.optJSONObject("module_author")?.optString("name", "") ?: ""
                val ownerFace = modules.optJSONObject("module_author")?.optString("face")?.takeIf { it.isNotBlank() }
                val stat = archive.optJSONObject("stat") ?: JSONObject()
                cards.add(
                    VideoCard(
                        bvid = bvid,
                        cid = null,
                        title = archive.optString("title", ""),
                        coverUrl = archive.optString("cover", ""),
                        durationSec = parseDuration(archive.optString("duration_text", "0:00")),
                        ownerName = ownerName,
                        ownerFace = ownerFace,
                        view = stat.optString("play").toLongOrNull(),
                        danmaku = stat.optString("danmaku").toLongOrNull(),
                        pubDateText = null,
                    ),
                )
            }
            val next = data.optString("offset", "").takeIf { it.isNotBlank() }
            AppLog.d(TAG, "dynamicSpaceVideo hostMid=$hostMid size=${cards.size} nextOffset=${next?.take(8)}")
            DynamicPage(cards, next)
        }
    }

    private fun genPlayUrlSession(nowMs: Long = System.currentTimeMillis()): String? {
        val buvid3 = BiliClient.cookies.getCookieValue("buvid3")?.takeIf { it.isNotBlank() } ?: return null
        return md5Hex(buvid3 + nowMs.toString())
    }

    private suspend fun requestPlayUrl(
        path: String,
        params: Map<String, String>,
        keys: blbl.cat3399.core.net.WbiSigner.Keys,
        noCookies: Boolean = false,
    ): JSONObject {
        val url = BiliClient.signedWbiUrl(path = path, params = params, keys = keys)
        val json = BiliClient.getJson(url, noCookies = noCookies)
        val code = json.optInt("code", 0)
        if (code != 0) {
            val msg = json.optString("message", json.optString("msg", ""))
            throw BiliApiException(apiCode = code, apiMessage = msg)
        }
        return json
    }

    private fun isRiskControl(e: BiliApiException): Boolean {
        if (e.apiCode == -412) return true
        val m = e.apiMessage
        return m.contains("风控") || m.contains("拦截") || m.contains("风险")
    }

    private fun md5Hex(s: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) sb.append(String.format(Locale.US, "%02x", b))
        return sb.toString()
    }
}

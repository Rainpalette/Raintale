package io.github.rainpalette.raintale.wiki

import io.github.rainpalette.raintale.debug.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 维基百科查询（知识链接/导游讲解用）。
 *
 * - 无坐标：直接用 REST **summary 接口**按名字查（跟随重定向）。
 * - 有坐标：用 **search 接口**取候选 → 查候选的坐标+简介 → 挑离用户**最近**的那个
 *   （坐标消歧义，解决同名多地）；候选都无坐标（品牌/物体）则取搜索第一条。
 *
 * 简介内容**来自权威源（维基），非 AI 生成**。本地缓存、消歧义页特殊处理留待后续。
 */
object WikiRepository {

    data class Result(val title: String, val extract: String)

    private data class Page(val title: String, val extract: String, val lat: Double?, val lon: Double?)

    /** 按实体名查维基摘要；可带用户坐标做消歧义。查不到/出错返回 null。 */
    suspend fun lookup(entity: String, lat: Double? = null, lon: Double? = null): Result? =
        withContext(Dispatchers.IO) {
            val lang = Locale.getDefault().language.ifBlank { "en" }
            try {
                if (lat == null || lon == null) return@withContext summaryLookup(lang, entity)

                val titles = searchTitles(lang, entity)
                if (titles.isEmpty()) return@withContext summaryLookup(lang, entity)

                val pages = fetchPages(lang, titles)
                if (pages.isEmpty()) return@withContext summaryLookup(lang, entity)

                val withCoords = pages.filter { it.lat != null && it.lon != null }
                val chosen = if (withCoords.isNotEmpty()) {
                    withCoords.minByOrNull { haversine(lat, lon, it.lat!!, it.lon!!) }!!
                } else {
                    pages.first()
                }
                if (chosen.extract.isBlank()) {
                    summaryLookup(lang, chosen.title)
                } else {
                    Result(chosen.title, chosen.extract)
                }
            } catch (e: Exception) {
                DebugLog.e("WikiRepository", "维基查询失败", e)
                null
            }
        }

    /** REST summary：按名字直查（跟随重定向）。 */
    private fun summaryLookup(lang: String, entity: String): Result? {
        val title = URLEncoder.encode(entity, "UTF-8").replace("+", "%20")
        val body = httpGet("https://$lang.wikipedia.org/api/rest_v1/page/summary/$title") ?: return null
        val obj = JSONObject(body)
        val extract = obj.optString("extract")
        if (extract.isBlank()) return null
        return Result(obj.optString("title", entity), extract)
    }

    /** search 接口：取前几条候选标题。 */
    private fun searchTitles(lang: String, entity: String): List<String> {
        val q = URLEncoder.encode(entity, "UTF-8")
        val url = "https://$lang.wikipedia.org/w/api.php?action=query&format=json&list=search&srsearch=$q&srlimit=5"
        val body = httpGet(url) ?: return emptyList()
        val search = JSONObject(body).optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        return (0 until search.length()).mapNotNull { search.optJSONObject(it)?.optString("title") }
            .filter { it.isNotBlank() }
    }

    /** 查候选页的坐标 + 简介。 */
    private fun fetchPages(lang: String, titles: List<String>): List<Page> {
        val titleParam = URLEncoder.encode(titles.joinToString("|"), "UTF-8")
        val url = "https://$lang.wikipedia.org/w/api.php?action=query&format=json" +
            "&prop=coordinates|extracts&exintro=1&explaintext=1&exlimit=max&titles=$titleParam"
        val body = httpGet(url) ?: return emptyList()
        val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        val result = mutableListOf<Page>()
        for (key in pages.keys()) {
            val p = pages.optJSONObject(key) ?: continue
            val coord = p.optJSONArray("coordinates")?.optJSONObject(0)
            result += Page(
                title = p.optString("title"),
                extract = p.optString("extract"),
                lat = coord?.optDouble("lat"),
                lon = coord?.optDouble("lon"),
            )
        }
        return result
    }

    private fun httpGet(urlString: String): String? {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            // Wikimedia UA 政策：可识别项目名 + 联系方式（release-checklist P2 #16）。
            setRequestProperty("User-Agent", "RainTales/0.1 (https://github.com/Rainpalette/raintale)")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                DebugLog.i("WikiRepository", "维基请求 HTTP $code: $urlString")
                null
            } else {
                conn.inputStream.bufferedReader().use { it.readText() }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** 两点球面距离（米）。 */
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}

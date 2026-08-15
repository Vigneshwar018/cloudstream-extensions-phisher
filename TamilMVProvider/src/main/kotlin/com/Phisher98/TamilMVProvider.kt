@file:Suppress("DEPRECATION_ERROR")
package com.Phisher98

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.nodes.Element

@Suppress("DEPRECATION_ERROR")
class TamilMVProvider : MainAPI() {
    override var mainUrl = "https://www.1tamilmv.ing"
    override var name = "1TamilMV"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Torrent
    )

    companion object {
        private const val IMDB_SUGGEST = "https://v2.sg.media-imdb.com/suggestion"
        private val posterCache = HashMap<String, String?>()
    }

    override val mainPage = mainPageOf(
        "$mainUrl/index.php?/forums/forum/11-web-hd-itunes-hd-bluray/" to "Tamil HD",
        "$mainUrl/index.php?/forums/forum/10-predvd-dvdscr-cam-tc/" to "Tamil PreDVD",
        "$mainUrl/index.php?/forums/forum/17-hollywood-movies-in-multi-audios/" to "Tamil Dubbed",
        "$mainUrl/index.php?/forums/forum/23-web-hd-itunes-hd-bluray/" to "Telugu HD",
        "$mainUrl/index.php?/forums/forum/57-web-hd-itunes-hd-bluray/" to "Hindi HD",
        "$mainUrl/index.php?/forums/forum/35-web-hd-itunes-hd-bluray/" to "Malayalam HD",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if (page == 1) {
            request.data
        } else {
            "${request.data}&page=$page"
        }
        val document = app.get(url).document

        val home = document.select("li.ipsDataItem[data-rowID]").mapNotNull {
            it.toSearchResult()
        }

        // Fetch TMDB posters for items that don't have one yet
        home.amap { result ->
            if (result.posterUrl == null) {
                val year = when (result) {
                    is MovieSearchResponse -> result.year
                    is TvSeriesSearchResponse -> result.year
                    else -> null
                }
                val isTV = result.type == TvType.TvSeries
                val poster = fetchTmdbPoster(result.name, year, isTV)
                if (poster != null) {
                    result.posterUrl = poster
                }
            }
        }

        val hasNext = document.select("li.ipsPagination_next a").isNotEmpty()

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = hasNext
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h4 span.ipsContained a, h4.ipsDataItem_title a[href*='/forums/topic/']")
            ?: return null
        val rawTitle = titleElement.attr("title").ifBlank { titleElement.text() }.trim()
        if (rawTitle.isBlank() || rawTitle.length < 5) return null

        val href = fixUrl(titleElement.attr("href"))
        if (!href.contains("/forums/topic/")) return null

        val title = parseMovieName(rawTitle)
        if (title.isBlank()) return null

        val yearRegex = Regex("\\((\\d{4})")
        val year = yearRegex.find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()

        val checkTvSeriesRegex = Regex("(?i)S(\\d+)\\s*EP")
        val tvType = if (rawTitle.contains(checkTvSeriesRegex)) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.year = year
                this.posterUrl = getPosterFromCache(title, year)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.year = year
                this.posterUrl = getPosterFromCache(title, year)
            }
        }
    }

    private fun getPosterFromCache(title: String, year: Int?): String? {
        val key = "$title|$year"
        return posterCache[key]
    }

    private suspend fun fetchTmdbPoster(title: String, year: Int?, isTV: Boolean = false): String? {
        val key = "$title|$year"
        if (posterCache.containsKey(key)) return posterCache[key]

        try {
            val query = if (year != null) "$title $year" else title
            val encoded = query.replace(" ", "%20").lowercase()
            val firstChar = encoded.first()
            val searchUrl = "$IMDB_SUGGEST/$firstChar/$encoded.json"
            val response = app.get(searchUrl).text

            // Find the best match - look for matching title and year
            val imageUrlRegex = Regex("\"imageUrl\":\"([^\"]+)\"")
            val titleRegex = Regex("\"l\":\"([^\"]+)\"")
            val yearRegex = Regex("\"y\":(\\d{4})")

            // Parse all results
            val imageUrls = imageUrlRegex.findAll(response).map { it.groupValues[1] }.toList()
            val titles = titleRegex.findAll(response).map { it.groupValues[1] }.toList()
            val years = yearRegex.findAll(response).map { it.groupValues[1].toInt() }.toList()

            // Find best match by title similarity and year
            var bestPoster: String? = null
            for (i in titles.indices) {
                if (i < imageUrls.size) {
                    val matchTitle = titles[i].lowercase()
                    val searchTitle = title.lowercase()
                    if (matchTitle == searchTitle || matchTitle.contains(searchTitle) || searchTitle.contains(matchTitle)) {
                        if (year == null || (i < years.size && years[i] == year)) {
                            bestPoster = imageUrls[i]
                            break
                        }
                        if (bestPoster == null) bestPoster = imageUrls[i]
                    }
                }
            }

            // If no title match, just use first result with an image
            if (bestPoster == null && imageUrls.isNotEmpty()) {
                bestPoster = imageUrls[0]
            }

            // Resize IMDB image for faster loading
            bestPoster = bestPoster?.replace("._V1_.jpg", "._V1_SX300.jpg")

            posterCache[key] = bestPoster
            return bestPoster
        } catch (_: Exception) {
            posterCache[key] = null
            return null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/index.php?/search/?&q=${query.replace(" ", "+")}&type=forums_topic&search_and_or=or"
        val document = app.get(searchUrl).document

        return document.select("li.ipsStreamItem, ol.ipsStream li").mapNotNull { item ->
            val linkElement = item.selectFirst("a[href*='/forums/topic/']") ?: return@mapNotNull null
            val rawTitle = linkElement.attr("title").ifBlank { linkElement.text() }.trim()
            if (rawTitle.isBlank()) return@mapNotNull null
            val href = fixUrl(linkElement.attr("href"))
            val title = parseMovieName(rawTitle)
            if (title.isBlank()) return@mapNotNull null

            val yearRegex = Regex("\\((\\d{4})")
            val year = yearRegex.find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()
            val poster = fetchTmdbPoster(title, year)

            val checkTvSeriesRegex = Regex("(?i)S(\\d+)\\s*EP")
            if (rawTitle.contains(checkTvSeriesRegex)) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.year = year
                    this.posterUrl = poster
                }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.year = year
                    this.posterUrl = poster
                }
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val rawTitle = document.selectFirst("h1.ipsType_pageTitle span, h1 span.ipsType_break")?.text()?.trim()
            ?: document.selectFirst("title")?.text()?.substringBefore(" - ")?.trim()
            ?: "Unknown"

        val title = parseMovieName(rawTitle)
        val yearRegex = Regex("\\((\\d{4})")
        val year = yearRegex.find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()

        val checkTvSeriesRegex = Regex("(?i)S(\\d+)\\s*EP")
        val isTV = rawTitle.contains(checkTvSeriesRegex)

        // Try TMDB poster first
        val tmdbPoster = fetchTmdbPoster(title, year, isTV)

        // Fallback: find poster image from the post content
        val poster = tmdbPoster ?: run {
            val postContent = document.selectFirst("div[data-role=commentContent], div.cPost_contentWrap")
            postContent?.select("img")?.firstOrNull { img ->
                val src = img.attr("src")
                src.isNotBlank() && !src.contains("torrborder") && !src.contains("uTorrent") &&
                    !src.contains("avatar") && !src.contains("emoji") && !src.contains("svg")
            }?.attr("src")?.let { fixUrlNull(it) }
        }

        val tvType = if (isTV) TvType.TvSeries else TvType.Movie

        return newMovieLoadResponse(title, url, tvType, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = rawTitle
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val magnetLinks = document.select("a[href^=magnet:]")
        if (magnetLinks.isEmpty()) return false

        magnetLinks.forEach { magnetElement ->
            val magnetUrl = magnetElement.attr("href")
            if (magnetUrl.isBlank()) return@forEach

            val qualityText = getQualityDescription(magnetElement)
            val quality = getQualityFromName(qualityText)

            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = qualityText.ifBlank {
                        Regex("dn=([^&]+)").find(magnetUrl)?.groupValues?.get(1)
                            ?.replace("+", " ")?.replace("%20", " ")
                            ?.replace("www.1TamilMV.ing - ", "")
                            ?: "Torrent"
                    },
                    url = magnetUrl,
                    referer = "",
                    quality = quality,
                    type = ExtractorLinkType.MAGNET
                )
            )
        }

        return true
    }

    private fun getQualityDescription(element: Element): String {
        val parent = element.parent() ?: return ""

        // Walk backwards from magnet link to find bold text with quality info
        var sibling = element.previousElementSibling()
        while (sibling != null) {
            val text = sibling.text().trim()
            if (text.contains(Regex("(?i)(\\d+p|4K|HEVC|AVC|x264|x265|\\d+\\.?\\d*\\s*[GM]B)"))) {
                return text
                    .replace(Regex("^www\\.1TamilMV\\.\\w+ - "), "")
                    .replace(Regex("\\.mkv\\.torrent$"), "")
                    .trim()
            }
            sibling = sibling.previousElementSibling()
        }

        // Try parent HTML before this element
        val parentHtml = parent.html()
        val magnetIndex = parentHtml.indexOf(element.outerHtml())
        if (magnetIndex > 0) {
            val before = parentHtml.substring(maxOf(0, magnetIndex - 300), magnetIndex)
            val lines = before.split(Regex("<br\\s*/?>|</?p>|</?strong>|</?b>"))
            val lastRelevant = lines.lastOrNull { it.contains(Regex("(?i)(\\d+p|4K|HEVC|AVC|x264|x265|\\d+\\.?\\d*\\s*[GM]B)")) }
            if (lastRelevant != null) {
                return lastRelevant.replace(Regex("<[^>]+>"), "")
                    .replace(Regex("^www\\.1TamilMV\\.\\w+ - "), "")
                    .replace(Regex("\\.mkv\\.torrent$"), "")
                    .replace("&amp;", "&")
                    .trim()
            }
        }

        return ""
    }

    /**
     * Parse movie name from raw forum title using pattern analysis.
     * Extracts the clean title before (Year).
     */
    private fun parseMovieName(rawTitle: String): String {
        // Remove alternate title in square brackets before year
        val withoutAlt = rawTitle.replace(Regex("\\s*\\[[^\\]]*\\]\\s*(?=\\(\\d{4})"), " ").trim()

        // Extract everything before (Year)
        val beforeYear = Regex("^(.+?)\\s*\\(\\d{4}").find(withoutAlt)?.groupValues?.get(1)?.trim()
        if (!beforeYear.isNullOrBlank()) {
            // Remove trailing language/source words
            return beforeYear
                .replace(Regex("\\s+(Tamil|Telugu|Hindi|Malayalam|Kannada|English|Multi)$", RegexOption.IGNORE_CASE), "")
                .trim()
        }

        // Fallback: take text before first dash or bracket
        return rawTitle.substringBefore(" - ").substringBefore("[").substringBefore("(").trim()
    }
}

@file:Suppress("DEPRECATION_ERROR")
package com.Phisher98

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class TamilYogiProvider : MainAPI() {
    override var mainUrl = "https://1tamilyogi.ro"
    override var name = "TamilYogi"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "$mainUrl/category/tamil-new-movies/" to "New Movies",
        "$mainUrl/category/tamil-hd-movies/" to "HD Movies",
        "$mainUrl/category/tamil-dubbed-movies/" to "Dubbed Movies",
        "$mainUrl/category/tamil-web-series/" to "TV Series"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = if (page == 1) {
            app.get(request.data).document
        } else {
            app.get(request.data + "page/$page/").document
        }

        val home = document.select("div.movie-grid-card").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(
            arrayListOf(HomePageList(request.name, home, isHorizontalImages = true)),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // Category pages: h3.movie-link-text > a
        val titleElement = this.selectFirst("h3.movie-link-text a")
            ?: this.selectFirst("h2 a")
            ?: return null
        val titleS = titleElement.text().trim()
        if (titleS.isBlank()) return null
        val title = titleS

        val href = fixUrl(titleElement.attr("href"))

        // Category pages: div.image-aspect-wrapper > a > img
        val posterUrl = this.selectFirst("div.image-aspect-wrapper a img")?.let { img ->
            img.attr("src").takeIf { it.isNotBlank() }
                ?: img.attr("data-src").takeIf { it.isNotBlank() }
        }?.let { fixUrlNull(it) }
            ?: this.selectFirst("img")?.let { img ->
                img.attr("src").takeIf { it.isNotBlank() }
            }?.let { fixUrlNull(it) }

        val qualityRegex = Regex("(?i)(CAM|PRE|DVDRip|HD|HQ|HDRip|720p|1080p|480p)")
        val qualityN = qualityRegex.find(titleS)?.value ?: ""
        val quality = getQualityFromString(qualityN)

        val checkTvSeriesRegex = Regex("(?i)(Season\\s?\\d+|Epi\\s?\\d+|E\\s?\\d+|S\\d+)")
        val isTV = title.contains(checkTvSeriesRegex)

        return if (isTV) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document

        return document.select("div.movie-grid-card").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document

        val titleL = doc.selectFirst("h1.entry-title")?.text()?.trim()
            ?: doc.selectFirst("h1")?.text()?.trim()
            ?: return null
        val title = titleL

        val poster = doc.selectFirst("div.entry-content img, article img, .post-thumb img")?.let { img ->
            img.attr("src").takeIf { it.isNotBlank() }
                ?: img.attr("data-src").takeIf { it.isNotBlank() }
        }?.let { fixUrlNull(it) }

        val yearRegex = Regex("\\((\\d{4})\\)")
        val year = yearRegex.find(title)?.groupValues?.get(1)?.toIntOrNull()

        val checkTvSeriesRegex = Regex("(?i)(Season\\s?\\d+|Epi\\s?\\d+|E\\s?\\d+|S\\d+)")
        val tvType = if (title.contains(checkTvSeriesRegex))
            TvType.TvSeries else TvType.Movie

        val recommendations = doc.select("div.movie-grid-card").mapNotNull {
            it.toSearchResult()
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.recommendations = recommendations
        }
    }

    @Suppress("DEPRECATION_ERROR")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linkRegex = Regex("(https?://[^\\s<>\"]+?(?:\\.m3u8|\\.mp4))")
        val doc = app.get(data).document
        val source = doc.select("iframe").attr("src").takeIf { it.isNotBlank() }
            ?: doc.select("iframe").attr("data-src").takeIf { it.isNotBlank() }
            ?: return false

        val iframePage = app.get(source, referer = "$mainUrl/").document
        val script = iframePage.selectFirst("body > script")?.toString()
            ?: iframePage.select("script").firstOrNull { it.data().contains(".m3u8") || it.data().contains(".mp4") }?.data()
            ?: return false

        val links = linkRegex.findAll(script).map { it.value.trim() }.toList()

        if (links.isEmpty()) return false

        safeApiCall {
            links.forEachIndexed { index, link ->
                val qualityName = when (index) {
                    0 -> "HD"
                    1 -> "SD"
                    2 -> "Low"
                    else -> "Source ${index + 1}"
                }
                val qualityValue = when (index) {
                    0 -> Qualities.Unknown.value
                    1 -> Qualities.P480.value
                    2 -> Qualities.P360.value
                    else -> Qualities.Unknown.value
                }
                callback.invoke(
                    ExtractorLink(
                        source = "TamilYogi",
                        name = qualityName,
                        url = link,
                        referer = "$mainUrl/",
                        quality = qualityValue,
                        type = INFER_TYPE
                    )
                )
            }
        }

        return true
    }
}

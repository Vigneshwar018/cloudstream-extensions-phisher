package com.Topstreamfilm

import android.annotation.SuppressLint
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.utils.loadExtractor

class TopStreamFilm : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.topstreamfilm.live"
    override var name = "TopStreamFilm"
    override val hasMainPage = true
    override var lang = "de"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "" to "Home",
        "serien" to "Series",
        "filme-online-sehen" to "Movies/Series",
        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url ="$mainUrl/${request.data}/page/$page"
        val document = app.get(url).document
        val home = document.select("article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    @SuppressLint("SuspiciousIndentation")
    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a img")?.attr("data-src"))
        val quality =getQualityFromString(this.select("span.Qlty").text())

            return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?story=$query&do=search&subaction=search").document
        return document.select("article").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.Title")?.text() ?:return null
        val plot = document.select("div.Description p:nth-child(2)").text()
        val poster = fixUrlNull(document.select("article div.TPostBg img.TPostBg").attr("data-src"))
        val year=document.selectFirst("span.Date.AAIco-date_range")?.text()?.toIntOrNull()
        val tags=document.select("ul li.AAIco-adjust:contains(Genre) a").map { it.text() }
        val rating=document.select("ul li.AAIco-adjust:contains(Rating) span").text().toRatingInt()
        val checkSeason=document.selectFirst("div.tt_season")?.text()
        val type=if (checkSeason!=null) TvType.TvSeries else TvType.Movie
        return if (type==TvType.TvSeries)
        {
            val episodes = mutableListOf<Episode>()
            document.select("div.su-accordion div.cu-ss").amap { it ->
                    val name = it.text().substringAfter(" ").substringBefore(" – ").trim()
                    val href = it.select("a").map {
                        it.attr("href")
                    }.toString()
                    val ep =it.text().substringAfter("x").toIntOrNull()
                    val season = it.text().substringBefore("x").trim().toIntOrNull()
                    episodes.add(
                        Episode(
                            data = href,
                            episode = ep,
                            name = name,
                            season = season
                        )
                    )
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.tags = tags
                this.rating = rating
            }
        }
        else
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.tags=tags
            this.year = year
            this.rating = rating
            this.posterUrl = poster
            this.plot=plot

        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val urls = data
            .removePrefix("[")
            .removeSuffix("]")
            .split(", ")
            .map { it.trim() }
        urls.forEach {
            loadExtractor(it,subtitleCallback, callback)
        }
        return true
    }
}
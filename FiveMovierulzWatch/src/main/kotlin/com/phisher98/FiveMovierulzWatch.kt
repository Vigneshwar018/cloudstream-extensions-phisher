@file:Suppress("DEPRECATION_ERROR")
package com.phisher98

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class FiveMovierulzWatch : MainAPI() {

    override var mainUrl = "https://www.5movierulz.watch"
    override var name = "5MovieRulz Watch"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "category/tamil-movies-2026" to "Tamil 2026",
        "category/tamil-movies-2025" to "Tamil 2025",
        "category/tamil-movies-2024" to "Tamil 2024",
        "category/tamil-movies-2023" to "Tamil 2023",
        "category/hollywood-movies-2026" to "Hollywood 2026",
        "category/hollywood-movies-2025" to "Hollywood 2025",
        "category/hollywood-movies-2024" to "Hollywood 2024",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl/${request.data}/page/$page"
        val document = app.get(url, timeout = 20L).document
        val home = document.select("#main .cont_display").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("a[title]")?.attr("title") ?: return null
        val href = this.selectFirst("a[href]")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("img[src]")?.attr("src")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query", timeout = 20L).document
        return document.select("#main .cont_display").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, timeout = 20L).document
        val title = document.selectFirst("h2.entry-title")?.text()?.trim() ?: ""
        val posterUrl = document.selectFirst(".entry-content img[src]")?.attr("src")
        val year = Regex("(\\d{4})").find(title)?.groupValues?.get(1)?.toIntOrNull()
        val description = document.select(".entry-content p").firstOrNull {
            it.text().contains("synopsis", ignoreCase = true) ||
            it.text().contains("story", ignoreCase = true) ||
            it.text().length > 50
        }?.text()?.trim()

        val entryContent = document.select(".entry-content p")
        val genres = entryContent.firstOrNull {
            it.text().contains("genre", ignoreCase = true)
        }?.text()?.substringAfter(":")?.split(",")?.map { it.trim() }

        val actors = entryContent.firstOrNull {
            it.text().contains("star", ignoreCase = true) ||
            it.text().contains("cast", ignoreCase = true) ||
            it.text().contains("actor", ignoreCase = true)
        }?.text()?.substringAfter(":")?.split(",")?.map {
            ActorData(Actor(it.trim()))
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = posterUrl
            this.year = year
            this.plot = description
            this.tags = genres
            this.actors = actors
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, timeout = 20L).document
        val knownHosts = listOf(
            "filelions", "streamwish", "hglink", "minochinos",
            "easysyncr", "vcdnlare", "uperbox", "vidhide"
        )
        document.select("a[href]").forEach { element ->
            val href = element.attr("href")
            if (knownHosts.any { href.contains(it, ignoreCase = true) }) {
                loadExtractor(href, mainUrl, subtitleCallback, callback)
            }
        }
        return true
    }
}

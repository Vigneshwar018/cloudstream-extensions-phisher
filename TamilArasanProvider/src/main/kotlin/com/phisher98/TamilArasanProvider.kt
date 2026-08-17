@file:Suppress("DEPRECATION_ERROR")
package com.phisher98

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import com.lagradost.nicehttp.NiceResponse
import org.jsoup.nodes.Element

class TamilArasanProvider : MainAPI() {

    override var mainUrl = "https://tamilarasan.online"
    override var name = "TamilArasan"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val mainPage = mainPageOf(
        "genre/tamil-hd-movies" to "Tamil HD Movies",
        "genre/new-tamil-movies" to "New Tamil Movies",
        "genre/hindi-movies" to "Hindi Movies",
        "genre/telugu-hd-movies" to "Telugu HD Movies",
        "genre/tamil-dubbed-movies" to "Tamil Dubbed Movies",
        "tvshows" to "TV Shows",
        "genre/tamil-web-series" to "Tamil Web Series",
    )

    private val cloudflareKiller by lazy { CloudflareKiller() }

    private suspend fun cfGet(url: String): NiceResponse {
        // Try with CloudflareKiller which uses WebView to solve challenges
        // Retry up to 3 times on connection failure
        var lastException: Exception? = null
        for (i in 1..3) {
            try {
                val response = app.get(
                    url,
                    interceptor = cloudflareKiller,
                    timeout = 30L
                )
                // If we got Cloudflare challenge page, the interceptor should handle it
                // But check if we actually got real content
                val title = response.document.select("title").text()
                if (title != "Just a moment..." && response.text.length > 1000) {
                    return response
                }
            } catch (e: Exception) {
                lastException = e
                kotlinx.coroutines.delay(1000L * i)
            }
        }
        // Final attempt without interceptor as last resort
        return app.get(url, timeout = 30L)
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if (page == 1) "$mainUrl/${request.data}/" else "$mainUrl/${request.data}/page/$page/"
        val document = cfGet(url).document
        val home = document.select("div.items.normal article, div#archive-content article, div.items.full article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3 > a")?.text() ?: return null
        val href = getProperLink(fixUrl(this.selectFirst("h3 > a")!!.attr("href")))
        val posterUrl = this.select("div.poster img").last()?.getImageAttr()
        val quality = getQualityFromString(this.select("span.quality").text())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = quality
        }
    }

    private fun getProperLink(uri: String): String {
        return when {
            uri.contains("/episodes/") -> {
                var title = uri.substringAfter("$mainUrl/episodes/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvshows/$title"
            }
            uri.contains("/seasons/") -> {
                var title = uri.substringAfter("$mainUrl/seasons/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvshows/$title"
            }
            else -> uri
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = cfGet("$mainUrl/?s=$query").document
        return document.select("div.result-item").mapNotNull {
            val title = it.selectFirst("div.title > a")?.text()?.replace(Regex("\\(\\d{4}\\)"), "")?.trim() ?: return@mapNotNull null
            val href = getProperLink(fixUrl(it.selectFirst("div.title > a")!!.attr("href")))
            val posterUrl = it.selectFirst("img")?.attr("src")
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val request = cfGet(url)
        val document = request.document

        val title = document.selectFirst("div.data > h1")?.text()?.trim().toString()
        val posterUrl = fixUrlNull(document.select("div.poster img").attr("src"))
        val backgroundUrl = fixUrlNull(document.selectFirst(".playbox img.cover")?.attr("src"))
        val tags = document.select("div.sgeneros > a").map { it.text() }
        val year = Regex(",\\s?(\\d+)").find(
            document.select("span.date").text().trim()
        )?.groupValues?.get(1)?.toIntOrNull()
        val tvType = if (document.select("ul#section > li:nth-child(1)").text()
                .contains("Episodes") || document.select("ul#playeroptionsul li span.title")
                .text().contains(Regex("Episode\\s+\\d+|EP\\d+|PE\\d+"))
        ) TvType.TvSeries else TvType.Movie
        val description = document.select("div.wp-content > p").text().trim()
        val rating = document.selectFirst("span.dt_rating_vgs")?.text()?.toRatingInt()

        val recommendations = document.select("div.owl-item").mapNotNull {
            val recName = it.selectFirst("a")?.attr("href")?.removeSuffix("/")?.split("/")?.last() ?: return@mapNotNull null
            val recHref = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val recPosterUrl = it.selectFirst("img")?.getImageAttr()
            newMovieSearchResponse(recName, recHref, TvType.Movie) {
                this.posterUrl = recPosterUrl
            }
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = if (document.select("ul.episodios > li").isNotEmpty()) {
                document.select("ul.episodios > li").map {
                    val href = it.select("a").attr("href")
                    val name = fixTitle(it.select("div.episodiotitle > a").text().trim())
                    val image = it.selectFirst("div.imagen > img")?.getImageAttr()
                    val episode = it.select("div.numerando").text().replace(" ", "").split("-").last().toIntOrNull()
                    val season = it.select("div.numerando").text().replace(" ", "").split("-").first().toIntOrNull()
                    newEpisode(href) {
                        this.name = name
                        this.episode = episode
                        this.season = season
                        this.posterUrl = image
                    }
                }
            } else {
                document.select("ul#playeroptionsul > li").mapNotNull {
                    val name = it.selectFirst("span.title")?.text() ?: return@mapNotNull null
                    val href = it.attr("data-post")
                    newEpisode(url) {
                        this.name = name
                    }
                }.ifEmpty {
                    listOf(newEpisode(url) { this.name = title })
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = backgroundUrl
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = backgroundUrl
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.recommendations = recommendations
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = cfGet(data)
        val document = response.document
        val rawHtml = response.text

        // Method 1: Extract iframes using regex on raw HTML (handles uppercase/malformed IFRAME tags)
        val iframeSrcRegex = Regex("""(?i)<iframe[^>]*\ssrc\s*=\s*["']?([^"'\s>]+)["']?""")
        val iframeMatches = iframeSrcRegex.findAll(rawHtml).map { it.groupValues[1] }
            .filter { it.startsWith("http") && !it.contains("youtube") && !it.contains("google.com/recaptcha") }
            .toList()
        
        if (iframeMatches.isNotEmpty()) {
            iframeMatches.amap { src ->
                loadExtractor(src, "$mainUrl/", subtitleCallback, callback)
            }
            return true
        }

        // Method 2: Try JSoup selector as fallback
        val iframes = document.select("iframe[src]")
        if (iframes.isNotEmpty()) {
            iframes.amap { iframe ->
                val src = iframe.attr("src").takeIf { it.isNotBlank() } ?: return@amap
                if (src.contains("youtube") || src.contains("google.com/recaptcha")) return@amap
                val fixedUrl = if (src.startsWith("//")) "https:$src" else src
                loadExtractor(fixedUrl, "$mainUrl/", subtitleCallback, callback)
            }
            return true
        }

        // Method 3: Try doo_player_ajax if playeroptionsul exists
        val playerOptions = document.select("ul#playeroptionsul > li")
        if (playerOptions.isNotEmpty()) {
            playerOptions.map {
                Triple(
                    it.attr("data-post"),
                    it.attr("data-nume"),
                    it.attr("data-type")
                )
            }.amap { (id, nume, type) ->
                try {
                    val source = app.post(
                        url = "$mainUrl/wp-admin/admin-ajax.php",
                        data = mapOf(
                            "action" to "doo_player_ajax",
                            "post" to id,
                            "nume" to nume,
                            "type" to type
                        ),
                        referer = data,
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                    ).parsed<ResponseHash>().embed_url
                    if (!source.contains("youtube")) {
                        loadExtractor(source, "$mainUrl/", subtitleCallback, callback)
                    }
                } catch (_: Exception) { }
            }
            return true
        }

        // Method 4: Look for any URLs to known video hosts in the raw HTML
        val knownHosts = listOf("voe.sx", "ok.ru", "morencius.com", "minochinos.com", "filelions", "streamwish", "embedwish", "hgcloud.to")
        val urlRegex = Regex("""https?://[^\s"'<>]+""")
        val allUrls = urlRegex.findAll(rawHtml).map { it.value }.toList()
        allUrls.filter { url ->
            knownHosts.any { host -> url.contains(host) } &&
            (url.contains("/e/") || url.contains("/embed/") || url.contains("/v/"))
        }.distinct().forEach { videoUrl ->
            loadExtractor(videoUrl, "$mainUrl/", subtitleCallback, callback)
        }

        return true
    }

    data class ResponseHash(
        @com.fasterxml.jackson.annotation.JsonProperty("embed_url") val embed_url: String,
        @com.fasterxml.jackson.annotation.JsonProperty("type") val type: String?,
    )

    private fun Element.getImageAttr(): String? {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }
}

# Cloudstream Extensions - Developer Reference Guide

## Table of Contents
1. [Project Architecture](#project-architecture)
2. [Plugin Structure](#plugin-structure)
3. [Provider Implementation Patterns](#provider-implementation-patterns)
4. [Link Extraction Methods](#link-extraction-methods)
5. [Extractor Implementation](#extractor-implementation)
6. [Handling Anti-Bot Protection](#handling-anti-bot-protection)
7. [Common Video Hosts & Extractors](#common-video-hosts--extractors)
8. [Plugin Registry](#plugin-registry)

---

## Project Architecture

### Directory Layout
```
cloudstream-extensions-phisher/
├── build.gradle.kts          # Root build config
├── settings.gradle.kts       # Auto-includes all dirs with build.gradle.kts
├── PluginName/
│   ├── build.gradle.kts      # Plugin metadata (version, language, tvTypes)
│   └── src/main/
│       ├── AndroidManifest.xml   # Empty: <?xml version="1.0"?><manifest />
│       └── kotlin/com/phisher98/
│           ├── ProviderName.kt       # MainAPI implementation
│           ├── ProviderNamePlugin.kt # Plugin registration
│           └── Extractors.kt        # Custom video extractors (optional)
```

### Auto-Registration
`settings.gradle.kts` auto-discovers plugins:
```kotlin
File(rootDir, ".").eachDir { dir ->
    if (File(dir, "build.gradle.kts").exists()) include(dir.name)
}
```
No manual registration needed — just create the directory with `build.gradle.kts`.

### build.gradle.kts Template
```kotlin
version = 1  // Integer, increment on updates

cloudstream {
    language = "ta"  // ISO 639-1 code
    description = "Description here"
    authors = listOf("AuthorName")
    status = 1  // 0=Down, 1=Ok, 2=Slow, 3=Beta
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=example.com&sz=%size%"
}
```

---

## Plugin Structure

### Plugin Registration (Plugin.kt)
```kotlin
package com.phisher98

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class MyPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(MyProvider())
        registerExtractorAPI(MyExtractor())  // Optional custom extractors
    }
}
```

### Provider Base Class (MainAPI)
Every provider extends `MainAPI()` and must implement:
- `mainUrl` — base site URL
- `name` — display name
- `supportedTypes` — Set of TvType (Movie, TvSeries, Anime, etc.)
- `getMainPage()` — home page content
- `search()` — search results
- `load()` — movie/show detail page
- `loadLinks()` — extract video streaming links

---

## Provider Implementation Patterns

### Pattern 1: WordPress Dflavor Theme (doo_player_ajax)
**Used by:** Movierulzhd, MultiMovies, Telugumv, Anisaga, Pinoymoviepedia (Bluray7)

Sites using WordPress with the Dooplay/Flavor theme load players via AJAX:
```kotlin
// In loadLinks():
document.select("ul#playeroptionsul > li").map {
    Triple(it.attr("data-post"), it.attr("data-nume"), it.attr("data-type"))
}.amap { (id, nume, type) ->
    val source = app.post(
        url = "$mainUrl/wp-admin/admin-ajax.php",
        data = mapOf(
            "action" to "doo_player_ajax",
            "post" to id, "nume" to nume, "type" to type
        ),
        headers = mapOf("X-Requested-With" to "XMLHttpRequest")
    ).parsed<ResponseHash>().embed_url
    loadExtractor(source, "$mainUrl/", subtitleCallback, callback)
}
```

### Pattern 2: Direct Iframe Extraction
**Used by:** Pinoymoviepedia, Bolly2Tolly, PRMovies, TamilYogi, TamilArasan

Iframes embedded directly in page content:
```kotlin
// JSoup selector
document.select("div.wp-content iframe, div#info iframe").amap { iframe ->
    val src = iframe.attr("src").takeIf { it.isNotBlank() } ?: return@amap
    loadExtractor(src, "$mainUrl/", subtitleCallback, callback)
}

// Regex fallback for uppercase/malformed HTML
val regex = Regex("""(?i)<iframe[^>]*\ssrc\s*=\s*["']?([^"'\s>]+)["']?""")
regex.findAll(rawHtml).map { it.groupValues[1] }.filter { it.startsWith("http") }
```

### Pattern 3: Direct Link Extraction (a[href])
**Used by:** Fivemovierulz, FiveMovierulzWatch, HDhub4u

Links to video hosts as `<a>` tags:
```kotlin
val knownHosts = listOf("filelions", "streamwish", "hgcloud", "voe.sx", "ok.ru")
document.select("a[href]").forEach { element ->
    val href = element.attr("href")
    if (knownHosts.any { href.contains(it) }) {
        loadExtractor(href, "$mainUrl/", subtitleCallback, callback)
    }
}
```

### Pattern 4: TMDB Meta Provider
**Used by:** Tamilian, StreamPlay, SuperStream

Uses TMDB metadata for search/browse, resolves links via external APIs:
```kotlin
class Tamilian : TmdbProvider() {
    override val useMetaLoadResponse = true
    override val instantLinkLoading = true
    // loadLinks uses TMDB ID to fetch from custom API
}
```

### Pattern 5: JSON API
**Used by:** AllMovieLand, IndianTV, ShowFlix, MPlayer

Fetches data from JSON APIs instead of scraping HTML:
```kotlin
val json = app.get("$mainUrl/api/movies").text
val movies = AppUtils.parseJson<List<Movie>>(json)
```

---

## Link Extraction Methods

### loadExtractor() — Built-in
Cloudstream's `loadExtractor()` automatically matches URLs to registered extractors:
```kotlin
import com.lagradost.cloudstream3.utils.loadExtractor
loadExtractor(url, referer, subtitleCallback, callback)
```

### Manual ExtractorLink
For direct video URLs (m3u8/mp4):
```kotlin
callback.invoke(ExtractorLink(
    source = "SourceName",
    name = "HD",
    url = "https://example.com/video.m3u8",
    referer = "$mainUrl/",
    quality = Qualities.P1080.value,
    type = INFER_TYPE  // or ExtractorLinkType.M3U8
))
```

### Parallel Extraction (amap)
Use `amap` for concurrent link extraction:
```kotlin
listOfUrls.amap { url -> loadExtractor(url, referer, subtitleCallback, callback) }
```

---

## Extractor Implementation

### VidhideExtractor (Most Common)
For sites using the VidHide/FileLions player pattern:
```kotlin
import com.lagradost.cloudstream3.extractors.VidhideExtractor

class MySite : VidhideExtractor() {
    override var name = "MySite"
    override var mainUrl = "https://mysite.com"
    override val requiresReferer = false
}
```
**Known VidhideExtractor clones:** FileLions, VidHidePro, Filelions, HGCloud, Morencius, Minochinos, Animezia, Dhtpre, smoothpre, vidhidevip

### StreamWishExtractor
```kotlin
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
class MyWish : StreamWishExtractor() {
    override var mainUrl = "https://mywish.com"
}
```

### Voe (Built-in)
```kotlin
import com.lagradost.cloudstream3.extractors.Voe
registerExtractorAPI(Voe())
```

### OkRu (Built-in)
```kotlin
import com.lagradost.cloudstream3.extractors.OkRuSSL
import com.lagradost.cloudstream3.extractors.OkRuHTTP
registerExtractorAPI(OkRuSSL())
registerExtractorAPI(OkRuHTTP())
```

### Custom Extractor (Full)
```kotlin
class MyExtractor : ExtractorApi() {
    override var name = "MyExtractor"
    override var mainUrl = "https://myextractor.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val doc = app.get(url, referer = referer).document
        // Extract m3u8/mp4 URL from page
        val videoUrl = doc.selectFirst("script")?.data()
            ?.let { Regex("file:\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
        return listOf(ExtractorLink(name, name, videoUrl!!, mainUrl, Qualities.Unknown.value, isM3u8 = true))
    }
}
```

---

## Handling Anti-Bot Protection

### CloudflareKiller
For sites with Cloudflare "Just a moment..." challenge:
```kotlin
import com.lagradost.cloudstream3.network.CloudflareKiller

private val cloudflareKiller by lazy { CloudflareKiller() }

// Conditional usage (UHDmovies pattern):
var doc = app.get(url)
if (doc.document.select("title").text() == "Just a moment...") {
    doc = app.get(url, interceptor = CloudflareKiller())
}

// Always-on usage (if site always challenges):
app.get(url, interceptor = cloudflareKiller, timeout = 30L)
```

### DdosGuardKiller
For DDoS-Guard protected sites:
```kotlin
import com.lagradost.cloudstream3.network.DdosGuardKiller
private val ddosGuard = DdosGuardKiller(true)
app.get(url, interceptor = ddosGuard)
```

### Retry Pattern (TamilArasan approach)
For sites with intermittent TLS blocking:
```kotlin
private suspend fun cfGet(url: String): NiceResponse {
    for (i in 1..3) {
        try {
            val response = app.get(url, interceptor = cloudflareKiller, timeout = 30L)
            if (response.text.length > 1000) return response
        } catch (e: Exception) {
            kotlinx.coroutines.delay(1000L * i)
        }
    }
    return app.get(url, timeout = 30L)  // Final fallback
}
```

### WebViewResolver
For extractors that need full JS execution:
```kotlin
import com.lagradost.cloudstream3.network.WebViewResolver
val response = app.get(url, interceptor = WebViewResolver(Regex("master\\.m3u8")))
```

---

## Common Video Hosts & Extractors

| Video Host | Extractor Base Class | URL Pattern |
|---|---|---|
| filelions.to | VidhideExtractor | `/v/ID.html` or `/f/ID` |
| voe.sx | Voe (built-in) | `/e/ID` |
| ok.ru | OkRuSSL (built-in) | `/videoembed/ID` |
| streamwish.to | StreamWishExtractor | `/e/ID` |
| vidhide.com | VidhideExtractor (built-in) | `/v/ID` or `/e/ID` |
| hgcloud.to | VidhideExtractor | `/e/ID` |
| morencius.com | VidhideExtractor | `/v/ID` or `/embed/ID` |
| minochinos.com | VidhideExtractor | `/embed/ID` or `/f/ID` |
| hglink.to | VidhideExtractor | `/ID` |
| dood.wf | Dood (built-in) | `/e/ID` or `/d/ID` |
| filemoon.sx | Filesim | `/e/ID` |
| mixdrop.ps | MixDrop (built-in) | `/e/ID` |
| streamtape.com | StreamTape (built-in) | `/e/ID` |

---

## Plugin Registry

### Tamil Providers
| Plugin | Site | Link Method | Anti-Bot |
|---|---|---|---|
| TamilArasan | tamilarasan.online | Inline iframes (regex) | CloudflareKiller + retry |
| TamilYogi | 1tamilyogi.ro | Single iframe → m3u8 regex | None |
| Tamilian | embedojo.net via TMDB | TMDB API | None |
| TamilMV | tamilmv.* | Magnet/torrent | None |
| MassTamilan | masstamilan.* | Direct audio links | None |
| EinthusanProvider | einthusan.tv | Custom player | None |

### Multi-Language Providers
| Plugin | Site | Link Method | Anti-Bot |
|---|---|---|---|
| Movierulzhd | movierulzhd.* | doo_player_ajax | None |
| FiveMovierulzWatch | 5movierulz.watch | Direct a[href] links | None |
| Fivemovierulz | 5movierulz.mom | a[href] filelions | None |
| MultiMovies | multimovies.* | doo_player_ajax | None |
| HDhub4u | hdhub4u.* | Encoded links | None |
| UHDmovies | uhdmovies.* | WordPress + bypass | CloudflareKiller |
| StreamPlay | TMDB-based | 80+ external sources | CloudflareKiller |
| SuperStream | TMDB-based | External APIs | None |

### Anime Providers
| Plugin | Site | Link Method | Anti-Bot |
|---|---|---|---|
| HiAnime | hianime.to | API + encrypted | None |
| AnimeKai | animekai.* | Encrypted iframe | None |
| AnimePahe | animepahe.* | Kwik extractor | None |
| Kickassanime | kickassanime.* | Encrypted + GoGo | None |
| AnimeDekho | animedekho.* | Multiple extractors | None |

### Key Design Patterns by Plugin

#### TamilArasanProvider
- **Challenge:** Site uses uppercase `<IFRAME SRC=...>` tags + Cloudflare
- **Solution:** Regex on raw `response.text` + CloudflareKiller with 3x retry
- **Extractors:** Voe, OkRuSSL, OkRuHTTP, Hgcloud, Morencius, Minochinos

#### FiveMovierulzWatch
- **Challenge:** Simple site, links as `<a>` tags to video hosts
- **Solution:** Select all `<a>` href matching known host domains
- **Extractors:** FilelionsWatch, HglinkTo, MinochinosWatch

#### Movierulzhd
- **Challenge:** WordPress Dooplay theme with AJAX player loading
- **Solution:** POST to wp-admin/admin-ajax.php with doo_player_ajax action
- **Extractors:** FMHD, Akamaicdn, Mocdn, Luluvdo, FMX, Lulust, Playonion, FilemoonV2

#### StreamPlay
- **Challenge:** TMDB-based with 80+ external video sources
- **Solution:** Massive extractor file (4700+ lines) invoking dozens of APIs
- **Key:** Uses link data (TMDB ID, IMDB ID) to query external providers

---

## Common Selectors Reference

### WordPress Dooplay Theme
```
Movie listing:  div.items.normal article, div#archive-content article
Title:          h3 > a
Poster:         div.poster img
Quality badge:  span.quality
Search:         $mainUrl/search/$query  or  $mainUrl/?s=$query
Search results: div.result-item
Movie title:    div.data > h1
Movie year:     span.date
Genres:         div.sgeneros > a
Description:    div.wp-content > p
Player list:    ul#playeroptionsul > li[data-post][data-nume][data-type]
Episodes:       ul.episodios > li
```

### Generic Movie Sites
```
Listing:        #main .cont_display
Title:          a[title] or h2.entry-title
Poster:         .entry-content img[src]
Pagination:     /page/N/
Search:         /?s=query
```

---

## Build & Test Commands

```bash
# Compile single plugin
.\gradlew.bat PluginName:compileDebugKotlin

# Clean + compile
.\gradlew.bat PluginName:clean PluginName:compileDebugKotlin

# Build APK/CS3 file
.\gradlew.bat PluginName:make

# Compile all plugins
.\gradlew.bat compileDebugKotlin
```

---

## Tips & Best Practices

1. **Always use `response.text` for regex** — JSoup's `document.html()` re-serializes and may drop malformed tags
2. **Register extractors** — `loadExtractor()` only works if the host has a matching registered extractor
3. **Use `amap` for parallel requests** — speeds up link extraction significantly
4. **Handle exceptions in loadLinks** — wrap individual extractor calls in try-catch
5. **Test with multiple movies** — different pages on the same site may use different embedding methods
6. **Check both `src` and `data-src`** — lazy-loaded iframes use `data-src`
7. **URL fixing** — always handle `//` prefix URLs (add `https:`) and use `fixUrl()`
8. **Timeout** — set appropriate timeouts (20-30s) for slow sites
9. **Version increment** — always bump `version` in build.gradle.kts when updating a plugin

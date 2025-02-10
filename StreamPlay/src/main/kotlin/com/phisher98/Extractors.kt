package com.Phisher98

import com.Phisher98.StreamPlay.Companion.animepaheAPI
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.JsonParser
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.getCaptchaToken
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.GMPlayer
import com.lagradost.cloudstream3.extractors.Jeniusplay
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import okhttp3.FormBody
import org.json.JSONObject
import java.math.BigInteger
import java.net.URI
import java.security.MessageDigest

open class Playm4u : ExtractorApi() {
    override val name = "Playm4u"
    override val mainUrl = "https://play9str.playm4u.xyz"
    override val requiresReferer = true
    private val password = "plhq@@@22"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).document
        val script = document.selectFirst("script:containsData(idfile =)")?.data() ?: return
        val passScript = document.selectFirst("script:containsData(domain_ref =)")?.data() ?: return

        val pass = passScript.substringAfter("CryptoJS.MD5('").substringBefore("')")
        val amount = passScript.substringAfter(".toString()), ").substringBefore("));").toInt()

        val idFile = "idfile".findIn(script)
        val idUser = "idUser".findIn(script)
        val domainApi = "DOMAIN_API".findIn(script)
        val nameKeyV3 = "NameKeyV3".findIn(script)
        val dataEnc = caesarShift(
            mahoa(
                "Win32|$idUser|$idFile|$referer",
                md5(pass)
            ), amount
        ).toHex()

        val captchaKey =
            document.select("script[src*=https://www.google.com/recaptcha/api.js?render=]")
                .attr("src").substringAfter("render=")
        val token = getCaptchaToken(
            url,
            captchaKey,
            referer = referer
        )

        val source = app.post(
            domainApi, data = mapOf(
                "namekey" to nameKeyV3,
                "token" to "$token",
                "referrer" to "$referer",
                "data" to "$dataEnc|${md5(dataEnc + password)}",
            ), referer = "$mainUrl/"
        ).parsedSafe<Source>()

        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                source?.data ?: return,
                "$mainUrl/",
                Qualities.P1080.value,
                INFER_TYPE
            )
        )

        subtitleCallback.invoke(
            SubtitleFile(
                source.sub?.substringBefore("|")?.toLanguage() ?: return,
                source.sub.substringAfter("|"),
            )
        )

    }

    private fun caesarShift(str: String, amount: Int): String {
        var output = ""
        val adjustedAmount = if (amount < 0) amount + 26 else amount
        for (element in str) {
            var c = element
            if (c.isLetter()) {
                val code = c.code
                c = when (code) {
                    in 65..90 -> ((code - 65 + adjustedAmount) % 26 + 65).toChar()
                    in 97..122 -> ((code - 97 + adjustedAmount) % 26 + 97).toChar()
                    else -> c
                }
            }
            output += c
        }
        return output
    }

    private fun mahoa(input: String, key: String): String {
        val a = CryptoJS.encrypt(key, input)
        return a.replace("U2FsdGVkX1", "")
            .replace("/", "|a")
            .replace("+", "|b")
            .replace("=", "|c")
            .replace("|", "-z")
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
    }

    private fun String.toHex(): String {
        return this.toByteArray().joinToString("") { "%02x".format(it) }
    }

    private fun String.findIn(data: String): String {
        return "$this\\s*=\\s*[\"'](\\S+)[\"'];".toRegex().find(data)?.groupValues?.get(1) ?: ""
    }

    private fun String.toLanguage(): String {
        return if (this == "EN") "English" else this
    }

    data class Source(
        @JsonProperty("data") val data: String? = null,
        @JsonProperty("sub") val sub: String? = null,
    )

}

open class M4ufree : ExtractorApi() {
    override val name = "M4ufree"
    override val mainUrl = "https://play.playm4u.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = session.get(url, referer = referer).document
        val script = document.selectFirst("script:containsData(idfile =)")?.data() ?: return

        val idFile = "idfile".findIn(script)
        val idUser = "idUser".findIn(script)

        val video = session.post(
            "https://api-plhq.playm4u.xyz/apidatard/$idUser/$idFile",
            data = mapOf("referrer" to "$referer"),
            headers = mapOf(
                "Accept" to "*/*",
                "X-Requested-With" to "XMLHttpRequest",
            )
        ).text.let { AppUtils.tryParseJson<Source>(it) }?.data

        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                video ?: return,
                referer ?: "",
                Qualities.P720.value,
                INFER_TYPE
            )
        )

    }

    private fun String.findIn(data: String): String? {
        return "$this\\s*=\\s*[\"'](\\S+)[\"'];".toRegex().find(data)?.groupValues?.get(1)
    }

    data class Source(
        @JsonProperty("data") val data: String? = null,
    )

}

class VCloudGDirect : ExtractorApi() {
    override val name: String = "V-Cloud GD"
    override val mainUrl: String = "https://fastdl.icu"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val source = app.get(url).document.selectFirst("#vd")?.attr("href") ?: ""
        if (source.isNotEmpty()) {
            Log.d("Phisher GD",source)
            callback.invoke(
                ExtractorLink(
                    "V-Cloud GD 10 Gbps",
                    "V-Cloud GD 10 Gbps",
                    source,
                    "",
                    getQualityFromName(""),
                )
            )
        } else {
            loadExtractor(source, subtitleCallback, callback)
        }
    }
}

    
class VCloud : ExtractorApi() {
    override val name: String = "V-Cloud"
    override val mainUrl: String = "https://vcloud.lol"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("Phisher Vega",url)
        var href=url
        if (href.contains("api/index.php"))
        {
            href=app.get(url).document.selectFirst("div.main h4 a")?.attr("href") ?:""
        }
        val doc = app.get(href).document
        val scriptTag = doc.selectFirst("script:containsData(url)")?.toString() ?:""
        val urlValue = Regex("var url = '([^']*)'").find(scriptTag) ?. groupValues ?. get(1) ?: ""
        if (urlValue.isNotEmpty()) {
            val document = app.get(urlValue).document
            val size = document.selectFirst("i#size")?.text() ?: ""
            val div = document.selectFirst("div.card-body")
            val header = document.selectFirst("div.card-header")?.text() ?: ""
            val headerdetails =
                """\.\d{3,4}p\.(.*)-[^-]*${'$'}""".toRegex().find(header)?.groupValues?.get(1)
                    ?.trim() ?: ""
            div?.select("h2 a.btn")?.filterNot {it.text().contains("Telegram", ignoreCase = true)}
                ?.amap {
                    val link = it.attr("href")
                    Log.d("Phisher Vega",link)
                    if (link.contains("technorozen.workers.dev")) {
                        @Suppress("NAME_SHADOWING") val href = app.get(link).document.selectFirst("#vd")?.attr("href") ?: ""
                        callback.invoke(
                            ExtractorLink(
                                "V-Cloud 10 Gbps $headerdetails",
                                "V-Cloud 10 Gbps $size",
                                href,
                                "",
                                getIndexQuality(header),
                            )
                        )
                    } else
                        if (link.contains("pixeldra")) {
                            callback.invoke(
                                ExtractorLink(
                                    "Pixeldrain $headerdetails",
                                    "Pixeldrain $size",
                                    link,
                                    "",
                                    getIndexQuality(header),
                                )
                            )
                        } else if (link.contains("dl.php")) {
                            val response = app.get(link, allowRedirects = false)
                            val downloadLink =
                                response.headers["location"].toString().split("link=").getOrNull(1)
                                    ?: link
                            callback.invoke(
                                ExtractorLink(
                                    "V-Cloud[Download] $headerdetails",
                                    "V-Cloud[Download] $size",
                                    downloadLink,
                                    "",
                                    getIndexQuality(header),
                                )
                            )
                        } else if (link.contains(".dev")) {
                            callback.invoke(
                                ExtractorLink(
                                    "V-Cloud $headerdetails",
                                    "V-Cloud $size",
                                    link,
                                    "",
                                    getIndexQuality(header),
                                )
                            )
                        } else if (link.contains(".hubcdn.xyz")) {
                            callback.invoke(
                                ExtractorLink(
                                    "V-Cloud $headerdetails",
                                    "V-Cloud $size",
                                    link,
                                    "",
                                    getIndexQuality(header),
                                )
                            )
                        } else if (link.contains(".lol")) {
                            callback.invoke(
                                ExtractorLink(
                                    "V-Cloud [FSL] $headerdetails",
                                    "V-Cloud $size",
                                    link,
                                    "",
                                    getIndexQuality(header),
                                )
                            )
                        } else {
                            loadExtractor(link, subtitleCallback, callback)
                        }
                }
        }
    }


    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

}

open class Streamruby : ExtractorApi() {
    override val name = "Streamruby"
    override val mainUrl = "https://streamruby.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = "/e/(\\w+)".toRegex().find(url)?.groupValues?.get(1) ?: return
        val response = app.post(
            "$mainUrl/dl", data = mapOf(
                "op" to "embed",
                "file_code" to id,
                "auto" to "1",
                "referer" to "",
            ), referer = referer
        )
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        }
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
        M3u8Helper.generateM3u8(
            name,
            m3u8 ?: return,
            mainUrl
        ).forEach(callback)
    }

}

open class Uploadever : ExtractorApi() {
    override val name = "Uploadever"
    override val mainUrl = "https://uploadever.in"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var res = app.get(url, referer = referer).document
        val formUrl = res.select("form").attr("action")
        var formData = res.select("form input").associate { it.attr("name") to it.attr("value") }
            .filterKeys { it != "go" }
            .toMutableMap()
        val formReq = app.post(formUrl, data = formData)

        res = formReq.document
        val captchaKey =
            res.select("script[src*=https://www.google.com/recaptcha/api.js?render=]").attr("src")
                .substringAfter("render=")
        val token = getCaptchaToken(url, captchaKey, referer = "$mainUrl/")
        formData = res.select("form#down input").associate { it.attr("name") to it.attr("value") }
            .toMutableMap()
        formData["adblock_detected"] = "0"
        formData["referer"] = url
        res = app.post(
            formReq.url,
            data = formData + mapOf("g-recaptcha-response" to "$token"),
            cookies = formReq.cookies
        ).document
        val video = res.select("div.download-button a.btn.btn-dow.recaptchav2").attr("href")

        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                video,
                "",
                Qualities.Unknown.value,
                INFER_TYPE
            )
        )

    }

}

open class Netembed : ExtractorApi() {
    override var name: String = "Netembed"
    override var mainUrl: String = "https://play.netembed.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val script = getAndUnpack(response.text)
        val m3u8 = Regex("((https:|http:)//.*\\.m3u8)").find(script)?.groupValues?.getOrNull(1) ?: return

        M3u8Helper.generateM3u8(this.name, m3u8, "$mainUrl/").forEach(callback)
    }
}

open class Ridoo : ExtractorApi() {
    override val name = "Ridoo"
    override var mainUrl = "https://ridoo.net"
    override val requiresReferer = true
    open val defaulQuality = Qualities.P1080.value

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        }
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
        val quality = "qualityLabels.*\"(\\d{3,4})[pP]\"".toRegex().find(script)?.groupValues?.get(1)
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                m3u8 ?: return,
                mainUrl,
                quality?.toIntOrNull() ?: defaulQuality,
                INFER_TYPE
            )
        )
    }

}

open class Streamvid : ExtractorApi() {
    override val name = "Streamvid"
    override val mainUrl = "https://streamvid.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        }
        val m3u8 =
            Regex("src:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
        M3u8Helper.generateM3u8(
            name,
            m3u8 ?: return,
            mainUrl
        ).forEach(callback)
    }

}

open class Embedrise : ExtractorApi() {
    override val name = "Embedrise"
    override val mainUrl = "https://embedrise.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, referer = referer).document
        val title = res.select("title").text()
        val video = res.select("video#player source").attr("src")

        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                video,
                "$mainUrl/",
                getIndexQuality(title),
                INFER_TYPE
            )
        )

    }

}

class FilemoonNl : Ridoo() {
    override val name = "FilemoonNl"
    override var mainUrl = "https://filemoon.nl"
    override val defaulQuality = Qualities.Unknown.value
}

class AllinoneDownloader : Filesim() {
    override var name = "MultiMovies API"
    override var mainUrl = "https://allinonedownloader.fun"
}

class Alions : Ridoo() {
    override val name = "Alions"
    override var mainUrl = "https://alions.pro"
    override val defaulQuality = Qualities.Unknown.value
}

class UqloadsXyz : Filesim() {
    override val name = "Uqloads"
    override var mainUrl = "https://uqloads.xyz"
}

class Pixeldra : PixelDrain() {
    override val mainUrl = "https://pixeldra.in"
}

class Snolaxstream : Filesim() {
    override val mainUrl = "https://snolaxstream.online"
    override val name = "Snolaxstream"
}

class bulbasaur : Filesim() {
    override val mainUrl = "https://file-mi11ljwj-embed.com"
    override val name = "Filemoon"
}
class do0od : DoodLaExtractor() {
    override var mainUrl = "https://do0od.com"
}

class doodre : DoodLaExtractor() {
    override var mainUrl = "https://dood.re"
}

class TravelR : GMPlayer() {
    override val name = "TravelR"
    override val mainUrl = "https://travel-russia.xyz"
}

class Mwish : Filesim() {
    override val name = "Mwish"
    override var mainUrl = "https://mwish.pro"
}

class Animefever : Filesim() {
    override val name = "Animefever"
    override var mainUrl = "https://animefever.fun"
}

class Multimovies : Ridoo() {
    override val name = "Multimovies"
    override var mainUrl = "https://multimovies.cloud"
}

class MultimoviesSB : StreamSB() {
    override var name = "Multimovies"
    override var mainUrl = "https://multimovies.website"
}

class Yipsu : Voe() {
    override val name = "Yipsu"
    override var mainUrl = "https://yip.su"
}

class Filelions : VidhideExtractor() {
    override var name = "Filelions"
    override var mainUrl = "https://alions.pro"
    override val requiresReferer = false
}


class Embedwish : Filesim() {
    override val name = "Embedwish"
    override var mainUrl = "https://embedwish.com"
}

class dwish : StreamWishExtractor() {
    override var mainUrl = "https://dwish.pro"
}

class dlions : VidhideExtractor() {
    override var name = "Dlions"
    override var mainUrl = "https://dlions.pro"
}

class Animezia : VidhideExtractor() {
    override var name = "MultiMovies API"
    override var mainUrl = "https://animezia.cloud"
}

class MixDropSi : MixDrop(){
    override var mainUrl = "https://mixdrop.si"
}

class MixDropPs : MixDrop(){
    override var mainUrl = "https://mixdrop.ps"
}

class Servertwo : VidhideExtractor() {
    override var name = "MultiMovies Vidhide"
    override var mainUrl = "https://server2.shop"
}

class Filelion : Filesim() {
    override val name = "Filelion"
    override var mainUrl = "https://filelions.to"
}

class MultimoviesAIO: StreamWishExtractor() {
    override var name = "Multimovies Cloud AIO"
    override var mainUrl = "https://allinonedownloader.fun"
}

class Flaswish : Ridoo() {
    override val name = "Flaswish"
    override var mainUrl = "https://flaswish.com"
    override val defaulQuality = Qualities.Unknown.value
}

class Comedyshow : Jeniusplay() {
    override val mainUrl = "https://comedyshow.to"
    override val name = "Comedyshow"
}


open class Chillx : ExtractorApi() {
    override val name = "Chillx"
    override val mainUrl = "https://chillx.top"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            // Fetch the raw response from the URL
            val res = app.get(url).toString()

            // Extract the encoded string using regex
            val encodedString = Regex("const\\s+\\w+\\s*=\\s*'(.*?)'").find(res)?.groupValues?.get(1) ?: ""
            if (encodedString.isEmpty()) {
                throw Exception("Encoded string not found")
            }
            // Decrypt the encoded string
            val password = "HG1I}V!u\$IR6Rxdf"
            val decryptedData = decryptXOR(encodedString, password)
            Log.d("Phisher",decryptedData)
            // Extract the m3u8 URL from decrypted data
            val m3u8 = Regex("\"?file\"?:\\s*\"([^\"]+)").find(decryptedData)?.groupValues?.get(1)?.trim() ?: ""
            if (m3u8.isEmpty()) {
                throw Exception("m3u8 URL not found")
            }

            // Prepare headers
            val header = mapOf(
                "accept" to "*/*",
                "accept-language" to "en-US,en;q=0.5",
                "Origin" to mainUrl,
                "Accept-Encoding" to "gzip, deflate, br",
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "cross-site",
                "user-agent" to USER_AGENT
            )

            // Return the extractor link
            callback.invoke(
                ExtractorLink(
                    name,
                    name,
                    m3u8,
                    mainUrl,
                    Qualities.P1080.value,
                    INFER_TYPE,
                    headers = header
                )
            )

            // Extract and return subtitles
            val subtitles = extractSrtSubtitles(decryptedData)
            subtitles.forEachIndexed { _, (language, url) ->
                subtitleCallback.invoke(SubtitleFile(language, url))
            }

        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }

    private fun extractSrtSubtitles(subtitle: String): List<Pair<String, String>> {
        val regex = """\[([^]]+)](https?://[^\s,]+\.srt)""".toRegex()
        return regex.findAll(subtitle).map { match ->
            val (language, url) = match.destructured
            language.trim() to url.trim()
        }.toList()
    }

    private fun decryptXOR(encryptedData: String, password: String): String {
        return try {
            val passwordBytes = password.toByteArray(Charsets.UTF_8)
            val decryptedBytes = (encryptedData.indices step 2)
                .map { i ->
                    val byteValue = encryptedData.substring(i, i + 2).toInt(16) // Convert hex to int
                    byteValue xor passwordBytes[(i / 2) % passwordBytes.size].toInt() // XOR with repeating password
                }
                .map { it.toByte() } // Convert to Byte
                .toByteArray() // Convert to ByteArray

            String(decryptedBytes, Charsets.UTF_8) // Convert ByteArray to String
        } catch (e: Exception) {
            e.printStackTrace()
            "Decryption Failed"
        }
    }
}


class Bestx : Chillx() {
    override val name = "Bestx"
    override val mainUrl = "https://bestx.stream"
    override val requiresReferer = true
}

class Vectorx : Chillx() {
    override val name = "Vectorx"
    override val mainUrl = "https://vectorx.top"
    override val requiresReferer = true
}

class Boosterx : Chillx() {
    override val name = "Vectorx"
    override val mainUrl = "https://boosterx.stream"
    override val requiresReferer = true
}

class Graceaddresscommunity : Voe() {
    override var mainUrl = "https://graceaddresscommunity.com"
}

class Sethniceletter : Voe() {
    override var mainUrl = "https://sethniceletter.com"
}


class Tellygossips : ExtractorApi() {
    override val mainUrl = "https://flow.tellygossips.net"
    override val name = "Tellygossips"
    override val requiresReferer = false
    private val referer = "http://tellygossips.net/"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = this.referer).text
        val link = doc.substringAfter("src\":\"").substringBefore("\",")
        callback(
            ExtractorLink(
                name,
                name,
                link,
                url,
                Qualities.Unknown.value,
                type = INFER_TYPE
            )
        )

    }
}

class Tvlogy : ExtractorApi() {
    override val mainUrl = "https://hls.tvlogy.to"
    override val name = "Tvlogy"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfter("data=")
        val data = mapOf(
            "hash" to id,
            "r" to "http%3A%2F%2Ftellygossips.net%2F"
        )
        val headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        val meta = app.post("$url&do=getVideo", headers = headers, referer = url, data = data)
            .parsedSafe<MetaData>() ?: return
        callback(
            ExtractorLink(
                name,
                name,
                meta.videoSource,
                url,
                Qualities.Unknown.value,
                meta.hls
            )
        )
    }

    data class MetaData(
        val hls: Boolean,
        val videoSource: String
    )

}
/*
open class Mdrive : ExtractorApi() {
    override val name: String = "Mdrive"
    override val mainUrl: String = "https://gamerxyt.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val host=url.substringAfter("?").substringBefore("&")
        val id=url.substringAfter("id=").substringBefore("&")
        val token=url.substringAfter("token=").substringBefore("&")
        val Cookie="$host; hostid=$id; hosttoken=$token"
        val doc = app.get("$mainUrl/games/",headers = mapOf("Cookie" to Cookie)).document
        val links = doc.select("div.card-body > h2 > a").attr("href")
        val header = doc.selectFirst("div.card-header")?.text()
        if (links.contains("pixeldrain"))
        {
            callback.invoke(
                ExtractorLink(
                    "MovieDrive",
                    "PixelDrain",
                    links,
                    referer = links,
                    quality = getIndexQuality(header),
                    type = INFER_TYPE
                )
            )
        }else
        if (links.contains("gofile")) {
            loadExtractor(links, subtitleCallback, callback)
        }
        else {
            callback.invoke(
                ExtractorLink(
                    "MovieDrive",
                    "MovieDrive",
                    links,
                    referer = "",
                    quality = getIndexQuality(header),
                    type = INFER_TYPE
                )
            )
        }
    }
}
 */

suspend fun Unblockedlinks(url: String): String {
    val driveLink = bypassHrefli(url) ?:""
    return driveLink
}

open class Modflix : ExtractorApi() {
    override val name: String = "Modflix"
    override val mainUrl: String = "https://video-seed.xyz"
    override val requiresReferer = true

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override suspend fun getUrl(
        finallink: String,
        quality: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val token = finallink.substringAfter("https://video-seed.xyz/?url=")
        val downloadlink = app.post(
            url = "https://video-seed.xyz/api",
            data = mapOf(
                "keys" to token
            ),
            referer = finallink,
            headers = mapOf(
                "x-token" to "video-seed.xyz",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
            )
        )
        val finaldownloadlink =
            downloadlink.toString().substringAfter("url\":\"")
                .substringBefore("\",\"name")
                .replace("\\/", "/")
        val link = finaldownloadlink
        callback.invoke(
            ExtractorLink(
                name,
                name,
                url = link,
                "",
                getQualityFromName(quality)
            )
        )
    }
}


/*
open class Asianbxkiun : ExtractorApi() {
    override val name: String = "Asianbxkiun"
    override val mainUrl: String = "https://asianbxkiun.pro"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val iframe = app.get(httpsify(url))
        val iframeDoc = iframe.document
        argamap({
            iframeDoc.select("#list-server-more ul li")
                .forEach { element ->
                    val extractorData = element.attr("data-video").substringBefore("=http")
                    val dataprovider = element.attr("data-provider") ?: return@forEach
                    if (dataprovider != "serverwithtoken") return@forEach
                    Log.d("Phisher",iframe.url)
                    loadExtractor(extractorData, iframe.url, subtitleCallback, callback)
                }
        }, {
            val iv = "9262859232435825"
            val secretKey = "93422192433952489752342908585752"
            val secretDecryptKey = secretKey
            GogoHelper.extractVidstream(
                iframe.url,
                this.name,
                callback,
                iv,
                secretKey,
                secretDecryptKey,
                isUsingAdaptiveKeys = false,
                isUsingAdaptiveData = true,
                iframeDocument = iframeDoc
            )
        })
    }
}
 */

class furher : Filesim() {
    override val name: String = "AZSeries"
    override var mainUrl = "https://furher.in"
}
class fastdlserver : GDFlix() {
    override var mainUrl = "https://fastdlserver.online"
}

class GDFlix1 : GDFlix() {
    override val mainUrl: String = "https://new3.gdflix.cfd"
}

class GDFlix2 : GDFlix() {
    override val mainUrl: String = "https://new2.gdflix.cfd"
}


open class GDFlix : ExtractorApi() {
    override val name: String = "GDFlix"
    override val mainUrl: String = "https://new1.gdflix.dad"
    override val requiresReferer = false

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "") ?. groupValues ?. getOrNull(1) ?. toIntOrNull()
            ?: Qualities.Unknown.value
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).document
        val fileName = document.selectFirst("ul > li.list-group-item")?.text()?.substringAfter("Name : ") ?: ""
        document.select("div.text-center a").amap {
            val text = it.select("a").text()
            if (
                text.contains("FAST CLOUD") &&
                !text.contains("ZIP")
            )
            {
                val link=it.attr("href")
                if(link.contains("mkv") || link.contains("mp4")) {
                    callback.invoke(
                        ExtractorLink(
                            "GDFlix[Fast Cloud]",
                            "GDFLix[Fast Cloud] - $fileName",
                            link,
                            "",
                            getIndexQuality(fileName),
                        )
                    )
                }
                else {
                    val trueurl=app.get("https://new1.gdflix.dad$link", timeout = 100L).document.selectFirst("a.btn-success")?.attr("href") ?:""
                    callback.invoke(
                        ExtractorLink(
                            "GDFlix[Fast Cloud]",
                            "GDFLix[Fast Cloud] - $fileName",
                            trueurl,
                            "",
                            getIndexQuality(fileName)
                        )
                    )
                }
            }
            else if(text.contains("DIRECT DL")) {
                val link = it.attr("href")
                callback.invoke(
                    ExtractorLink(
                        "GDFlix[Direct]",
                        "GDFLix[Direct] - $fileName",
                        link,
                        "",
                        getIndexQuality(fileName),
                    )
                )
            }
            else if(text.contains("Index Links")) {
                val link = it.attr("href")
                val doc = app.get("https://new1.gdflix.dad$link").document
                doc.select("a.btn.btn-outline-info").amap {
                    val serverUrl = mainUrl + it.attr("href")
                    app.get(serverUrl).document.select("div.mb-4 > a").amap {
                        val source = it.attr("href")
                        callback.invoke(
                            ExtractorLink(
                                "GDFlix[Index]",
                                "GDFLix[Index] - $fileName",
                                source,
                                "",
                                getIndexQuality(fileName),
                            )
                        )
                    }
                }
            }
            else if (text.contains("DRIVEBOT LINK"))
            {
                val driveLink = it.attr("href")
                val id = driveLink.substringAfter("id=").substringBefore("&")
                val doId = driveLink.substringAfter("do=").substringBefore("==")
                val baseUrls = listOf("https://drivebot.sbs", "https://drivebot.cfd")
                baseUrls.amap { baseUrl ->
                    val indexbotlink = "$baseUrl/download?id=$id&do=$doId"
                    val indexbotresponse = app.get(indexbotlink, timeout = 100L)
                    if(indexbotresponse.isSuccessful) {
                        val cookiesSSID = indexbotresponse.cookies["PHPSESSID"]
                        val indexbotDoc = indexbotresponse.document
                        val token = Regex("""formData\.append\('token', '([a-f0-9]+)'\)""").find(indexbotDoc.toString()) ?. groupValues ?. get(1) ?: ""
                        val postId = Regex("""fetch\('/download\?id=([a-zA-Z0-9/+]+)'""").find(indexbotDoc.toString()) ?. groupValues ?. get(1) ?: ""

                        val requestBody = FormBody.Builder()
                            .add("token", token)
                            .build()

                        val headers = mapOf(
                            "Referer" to indexbotlink
                        )

                        val cookies = mapOf(
                            "PHPSESSID" to "$cookiesSSID",
                        )

                        val response = app.post(
                            "$baseUrl/download?id=${postId}",
                            requestBody = requestBody,
                            headers = headers,
                            cookies = cookies,
                            timeout = 100L
                        ).toString()

                        var downloadlink = Regex("url\":\"(.*?)\"").find(response) ?. groupValues ?. get(1) ?: ""

                        downloadlink = downloadlink.replace("\\", "")

                        callback.invoke(
                            ExtractorLink(
                                "GDFlix[DriveBot]",
                                "GDFlix[DriveBot] - $fileName",
                                downloadlink,
                                baseUrl,
                                getIndexQuality(fileName)
                            )
                        )
                    }
                }
            }
            else if (text.contains("Instant DL"))
            {
                val instantLink = it.attr("href")
                val link = app.get(instantLink, timeout = 30L, allowRedirects = false).headers["Location"]?.split("url=") ?. getOrNull(1) ?: ""
                callback.invoke(
                    ExtractorLink(
                        "GDFlix[Instant Download]",
                        "GDFlix[Instant Download] - $fileName",
                        link,
                        "",
                        getIndexQuality(fileName)
                    )
                )
            }
            else if(text.contains("CLOUD DOWNLOAD [FSL]")) {
                val link = it.attr("href").substringAfter("url=")
                callback.invoke(
                    ExtractorLink(
                        "GDFlix[FSL]",
                        "GDFlix[FSL] - $fileName",
                        link,
                        "",
                        getIndexQuality(fileName)
                    )
                )
            }
            else {
                Log.d("Error", "No Server matched")
            }
        }
    }
}

class HubCloudClub : HubCloud() {
    override var mainUrl = "https://hubcloud.club"
}

class HubCloudink : HubCloud() {
    override var mainUrl = "https://hubcloud.ink"
}

class HubCloudtel : HubCloud() {
    override var mainUrl = "https://hubcloud.tel"
}

class HubCloudlol : HubCloud() {
    override var mainUrl = "https://hubcloud.lol"
}

open class PixelDrain : ExtractorApi() {
    override val name            = "PixelDrain"
    override val mainUrl         = "https://pixeldrain.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val mId = Regex("/u/(.*)").find(url)?.groupValues?.get(1)
        if (mId.isNullOrEmpty())
        {
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    url,
                    url,
                    Qualities.Unknown.value,
                )
            )
        }
        else {
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    "$mainUrl/api/file/${mId}?download",
                    url,
                    Qualities.Unknown.value,
                )
            )
        }
    }
}

class Moviesapi : Chillx() {
    override val name = "Moviesapi"
    override val mainUrl = "https://w1.moviesapi.club"
    override val requiresReferer = true
}

open class HubCloud : ExtractorApi() {
    override val name: String = "Hub-Cloud"
    override val mainUrl: String = "https://hubcloud.art"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        source: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url=replaceHubclouddomain(url)
        Log.d("Phisher real url", url)
        val href:String
        if (url.contains("hubcloud.php"))
        {
            href=url
        }
        else {
            val doc = app.get(url).text
            //val gamerlink=Regex("""hubcloud\.php\?([^'"]+)""").find(doc)?.groupValues?.get(1)
            href=doc.substringAfter("var url = '").substringBefore("\'")
            //href = "https://gamerxyt.com/hubcloud.php?$bypasslink"
            Log.d("Phisher Hub", href)
            if (href.isEmpty()) {
                Log.d("Error", "Not Found")
            }
        }
        if (href.isNotEmpty()) {
            val document = app.get(href).document
            val size = document.selectFirst("i#size")?.text()
            val div = document.selectFirst("div.card-body")
            val header = document.selectFirst("div.card-header")?.text()
            div?.select("div.card-body a.btn")?.amap {
                val link = it.attr("href")
                it.text()
                if (link.contains("www-google-com"))
                {
                    Log.d("Error:","Not Found")
                }
                else
                if (link.contains("technorozen.workers.dev"))
                {
                    val iframe=getGBurl(link)
                    callback.invoke(
                        ExtractorLink(
                            "$source 10GB Server",
                            "$source 10GB Server $size",
                            iframe,
                            "",
                            getIndexQuality(header),
                        )
                    )
                } else if (link.contains("pixeldra.in")) {
                    callback.invoke(
                        ExtractorLink(
                            "$source Pixeldrain",
                            "$source Pixeldrain $size",
                            link,
                            "",
                            getIndexQuality(header),
                        )
                    )
                } else if (link.contains("buzzheavier")) {
                callback.invoke(
                    ExtractorLink(
                        "$source Buzzheavier",
                        "$source Buzzheavier $size",
                        "$link/download",
                        "",
                        getIndexQuality(header),
                    )
                )
                } else if (link.contains(".dev")) {
                    callback.invoke(
                        ExtractorLink(
                            "$source Hub-Cloud",
                            "$source Hub-Cloud $size",
                            link,
                            "",
                            getIndexQuality(header),
                        )
                    )
                } else if (link.contains("fastdl.lol"))
                {
                    callback.invoke(
                        ExtractorLink(
                            "$source [FSL] Hub-Cloud",
                            "$source [FSL] Hub-Cloud $size",
                            link,
                            "",
                            getIndexQuality(header),
                        )
                    )
                } else if (link.contains("hubcdn.xyz"))
                {
                callback.invoke(
                    ExtractorLink(
                        "$source [File] Hub-Cloud",
                        "$source [File] Hub-Cloud $size",
                        link,
                        "",
                        getIndexQuality(header),
                    )
                )
                } else if (link.contains("gofile.io"))
                {
                    loadCustomExtractor("$source",link,"",subtitleCallback, callback)
                } else if (link.contains("pixeldrain"))
                {
                    loadCustomExtractor("$source",link,"",subtitleCallback, callback)
                }
                else
                {
                    Log.d("Error:","Not Server Match Found")
                }
            }
        }
    }


    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "") ?. groupValues ?. getOrNull(1) ?. toIntOrNull()
            ?: Qualities.P2160.value
    }

    private suspend fun getGBurl(url: String): String {
        return app.get(url).document.selectFirst("#vd")?.attr("href") ?:""
    }

}

class Driveleech : Driveseed() {
    override val name: String = "Driveleech"
    override val mainUrl: String = "https://driveleech.org"
}

open class Driveseed : ExtractorApi() {
    override val name: String = "Driveseed"
    override val mainUrl: String = "https://driveseed.org"
    override val requiresReferer = false

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "") ?. groupValues ?. getOrNull(1) ?. toIntOrNull()
            ?: Qualities.Unknown.value
    }

    private suspend fun CFType1(url: String): List<String> {
        val document = app.get(url+"?type=1").document
        val links = document.select("a.btn-success").mapNotNull { it.attr("href") }
        return links
    }

    private suspend fun resumeCloudLink(url: String): String {
        val resumeCloudUrl = mainUrl + url
        val document = app.get(resumeCloudUrl).document
        val link = document.selectFirst("a.btn-success")?.attr("href").toString()
        return link
    }

    private suspend fun resumeBot(url : String): String {
        val resumeBotResponse = app.get(url)
        val resumeBotDoc = resumeBotResponse.document.toString()
        val ssid = resumeBotResponse.cookies["PHPSESSID"]
        val resumeBotToken = Regex("formData\\.append\\('token', '([a-f0-9]+)'\\)").find(resumeBotDoc)?.groups?.get(1)?.value
        val resumeBotPath = Regex("fetch\\('/download\\?id=([a-zA-Z0-9/+]+)'").find(resumeBotDoc)?.groups?.get(1)?.value
        val resumeBotBaseUrl = url.split("/download")[0]
        val requestBody = FormBody.Builder()
            .addEncoded("token", "$resumeBotToken")
            .build()

        val jsonResponse = app.post(resumeBotBaseUrl + "/download?id=" + resumeBotPath,
            requestBody = requestBody,
            headers = mapOf(
                "Accept" to "*/*",
                "Origin" to resumeBotBaseUrl,
                "Sec-Fetch-Site" to "same-origin"
            ),
            cookies = mapOf("PHPSESSID" to "$ssid"),
            referer = url
        ).text
        val jsonObject = JSONObject(jsonResponse)
        val link = jsonObject.getString("url")
        return link
    }

    private suspend fun instantLink(finallink: String): String {
        val url = if(finallink.contains("video-leech")) "video-leech.xyz" else "video-seed.xyz"
        val token = finallink.substringAfter("url=")
        val downloadlink = app.post(
            url = "https://$url/api",
            data = mapOf(
                "keys" to token
            ),
            referer = finallink,
            headers = mapOf(
                "x-token" to url            )
        )
        val finaldownloadlink =
            downloadlink.toString().substringAfter("url\":\"")
                .substringBefore("\",\"name")
                .replace("\\/", "/")
        val link = finaldownloadlink
        return link
    }


    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = if(url.contains("r?key=")) {
            val temp = app.get(url).document.selectFirst("script")?.data()?.substringAfter("replace(\"")?.substringBefore("\")") ?: ""
            app.get(mainUrl + temp).document
        }
        else {
            app.get(url).document
        }
        val quality = document.selectFirst("li.list-group-item")?.text() ?: ""
        val fileName = quality.replace("Name : ", "")
        document.select("div.text-center > a").amap { element ->
            val text = element.text()
            val href = element.attr("href")
            when {
                text.contains("Instant Download") -> {
                    try {
                        val instant = instantLink(href)
                        callback.invoke(
                            ExtractorLink(
                                "$name Instant(Download)",
                                "$name Instant(Download) - $fileName",
                                instant,
                                "",
                                getIndexQuality(quality)
                            )
                        )
                    } catch (e: Exception) {
                        Log.d("Error:", e.toString())
                    }
                }
                text.contains("Resume Worker Bot") -> {
                    val resumeLink = resumeBot(href)
                    callback.invoke(
                        ExtractorLink(
                            "$name ResumeBot(VLC)",
                            "$name ResumeBot(VLC) - $fileName",
                            resumeLink,
                            "",
                            getIndexQuality(quality)
                        )
                    )
                }
                text.contains("Direct Links") -> {
                    val link = mainUrl + href
                    CFType1(link).forEach {
                        callback.invoke(
                            ExtractorLink(
                                "$name CF Type1",
                                "$name CF Type1 - $fileName",
                                it,
                                "",
                                getIndexQuality(quality)
                            )
                        )
                    }
                }
                text.contains("Resume Cloud") -> {
                    val resumeCloud = resumeCloudLink(href)
                    callback.invoke(
                        ExtractorLink(
                            "$name ResumeCloud",
                            "$name ResumeCloud - $fileName",
                            resumeCloud,
                            "",
                            getIndexQuality(quality)
                        )
                    )
                }
                else -> {
                }
            }
        }
    }
}

class Kwik : ExtractorApi() {
    override val name            = "Kwik"
    override val mainUrl         = "https://kwik.si"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val res = app.get(url,referer=animepaheAPI)
        val script =
            res.document.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data()
        val unpacked = getAndUnpack(script ?: return)
        val m3u8 =Regex("source=\\s*'(.*?m3u8.*?)'").find(unpacked)?.groupValues?.getOrNull(1) ?:""
        callback.invoke(
            ExtractorLink(
                name,
                name,
                m3u8,
                "",
                getQualityFromName(referer),
                INFER_TYPE
            )
        )
    }
}


open class Embtaku : ExtractorApi() {
    override var name = "Embtaku"
    override var mainUrl = "https://embtaku.pro"
    override val requiresReferer = false

    override suspend fun
            getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val responsecode= app.get(url)
        val serverRes = responsecode.document
        serverRes.select("ul.list-server-items").amap {
            val href=it.attr("data-video")
            loadCustomExtractor("Anichi [Embtaku]",href,"",subtitleCallback,callback)
        }
    }
}

class GDMirrorbot : ExtractorApi() {
    override var name = "GDMirrorbot"
    override var mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = true
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val host = getBaseUrl(app.get(url).url)
        val embed = url.substringAfter("embed/")
        val data = mapOf("sid" to embed)
        val jsonString = app.post("$host/embedhelper.php", data = data).toString()
        val jsonObject = JsonParser.parseString(jsonString).asJsonObject
        val siteUrls = jsonObject.getAsJsonObject("siteUrls").asJsonObject
        val mresult = jsonObject.getAsJsonObject("mresult").toString()
        val regex = """"(\w+)":"([^"]+)"""".toRegex()
        val mresultMap = regex.findAll(mresult).associate {
            it.groupValues[1] to it.groupValues[2]
        }

        val matchingResults = mutableListOf<Pair<String, String>>()
        siteUrls.keySet().forEach { key ->
            if (mresultMap.containsKey(key)) { // Use regex-matched keys and values
                val value1 = siteUrls.get(key).asString
                val value2 = mresultMap[key].orEmpty()
                matchingResults.add(Pair(value1, value2))
            }
        }

        matchingResults.amap { (siteUrl, result) ->
            val href = "$siteUrl$result"
            Log.d("Phisher", "Generated Href: $href")
            loadExtractor(href, subtitleCallback, callback)
        }

    }

    fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }
}


class OwlExtractor : ExtractorApi() {
    override var name = "OwlExtractor"
    override var mainUrl = "https://whguides.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val response = app.get(url).document
        val datasrc=response.select("button#hot-anime-tab").attr("data-source")
        val id=datasrc.substringAfterLast("/")
        val epJS= app.get("$referer/players/$id.v2.js").text.let {
            Deobfuscator.deobfuscateScript(it)
        }
        val jwt=findFirstJwt(epJS?: throw Exception("Unable to get jwt")) ?:return

        val servers=app.get("$referer$datasrc").parsedSafe<Response>()
        val sources= mutableListOf<String>()
        servers?.kaido?.let {
            sources+="$it$jwt"
        }

        servers?.luffy?.let {
            val m3u8= app.get("$it$jwt", allowRedirects = false).headers["location"] ?:return
            sources+=m3u8
        }
        servers?.zoro?.let {
            val m3u8= app.get("$it$jwt").parsedSafe<Zoro>()?.url ?:return
            val vtt= app.get("$it$jwt").parsedSafe<Zoro>()?.subtitle ?:return
            sources+=m3u8
            sources+=vtt
        }

        sources.amap { m3u8->
            if (m3u8.contains("vvt"))
            {
                subtitleCallback.invoke(
                    SubtitleFile(
                        "English",
                        m3u8
                    )
                )
            }
            else
            {
                callback.invoke(
                    ExtractorLink(
                        name,
                        name,
                        m3u8,
                        mainUrl,
                        Qualities.P1080.value,
                        INFER_TYPE,
                    )
                )
            }
        }

        return
    }
}

private fun findFirstJwt(text: String): String? {
    val jwtPattern = Regex("['\"]([A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+)['\"]")
    return jwtPattern.find(text)?.groupValues?.get(1)
}

data class Response(
    val kaido: String? = null,
    val luffy: String? = null,
    val zoro: String? = null,
)

data class Zoro(
    val url: String,
    val subtitle: String,
)



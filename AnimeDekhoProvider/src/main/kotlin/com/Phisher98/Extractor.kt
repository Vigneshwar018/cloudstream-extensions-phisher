package com.Phisher98

import com.lagradost.cloudstream3.USER_AGENT
import com.google.gson.JsonParser
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.extractors.Vidmoly
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URI

class FilemoonV2 : ExtractorApi() {
    override var name = "Filemoon"
    override var mainUrl = " https://filemoon.nl"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val href=app.get(url).document.selectFirst("iframe")?.attr("src") ?:""
        val res= app.get(href, headers = mapOf("Accept-Language" to "en-US,en;q=0.5","sec-fetch-dest" to "iframe")).document.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()
        val m3u8= JsUnpacker(res).unpack()?.let { unPacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)
        }
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                m3u8 ?:"",
                url,
                Qualities.P1080.value,
                type = ExtractorLinkType.M3U8,
            )
        )
    }
}

open class Streamruby : ExtractorApi() {
    override var name = "Streamruby"
    override var mainUrl = "streamruby.com"
    override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        if (url.contains("/e/"))
        {
            val newurl=url.replace("/e","")
            val txt = app.get(newurl).text
            val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(txt)?.groupValues?.getOrNull(1).toString()
            return listOf(
                ExtractorLink(
                    this.name,
                    this.name,
                    m3u8,
                    mainUrl,
                    Qualities.Unknown.value,
                    type = INFER_TYPE
                )
            )
        }
        else
        {
            val txt = app.get(url).text
            val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(txt)?.groupValues?.getOrNull(1).toString()
            return listOf(
                ExtractorLink(
                    this.name,
                    this.name,
                    m3u8,
                    mainUrl,
                    Qualities.Unknown.value,
                    type = INFER_TYPE
                )
            )
        }
    }
}


open class VidStream : ExtractorApi() {
    override val name = "VidStream"
    override val mainUrl = "https://vidstreaming.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).toString()
        val encodedString =
            Regex("const\\s+\\w+\\s*=\\s*'(.*?)'").find(res)?.groupValues?.get(1) ?:""
        val password = "TGRKeQCC8yrxC;5)"
        val decryptedData = decryptXOR(encodedString, password)
        val m3u8 = Regex("\"?file\"?:\\s*\"([^\"]+)").find(decryptedData)?.groupValues?.get(1)
            ?.trim()
            ?:""
        val header =mapOf(
            "accept" to "*/*",
            "accept-language" to "en-US,en;q=0.5",
            "Origin" to mainUrl,
            "Accept-Encoding" to "gzip, deflate, br",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "user-agent" to USER_AGENT,)
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

        val subtitles = extractSrtSubtitles(decryptedData)
        subtitles.forEachIndexed { _, (language, url) ->
            subtitleCallback.invoke(
                SubtitleFile(
                    language,
                    url
                )
            )
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
            val decryptedBytes = encryptedData.chunked(3) // Split into chunks of 3 characters
                .map { it.toIntOrNull() ?: 0 } // Convert to integer, default to 0 if invalid
                .mapIndexed { index, num -> (num xor password[index % password.length].code).toByte() } // XOR with repeating password
                .toByteArray() // Convert to byte array

            String(decryptedBytes, Charsets.UTF_8) // Convert bytes to string
        } catch (e: Exception) {
            e.printStackTrace()
            "Decryption Failed"
        }
    }
}

class Multimovies: StreamWishExtractor() {
    override var name = "Multimovies Cloud"
    override var mainUrl = "https://multimovies.cloud"
    override var requiresReferer = true
}

class FileMoonNL : Filesim() {
    override val mainUrl = "https://filemoon.nl"
    override val name = "FileMoon"
}

class Vidmolynet : Vidmoly() {
    override val mainUrl = "https://vidmoly.net"
}

class Cdnwish : StreamWishExtractor() {
    override var name = "Streamwish"
    override var mainUrl = "https://cdnwish.com"
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
            loadExtractor(href, subtitleCallback, callback)
        }

    }

    fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }
}

class Animezia : VidhideExtractor() {
    override var name = "Animezia"
    override var mainUrl = "https://animezia.cloud"
    override var requiresReferer = true
}

data class Media(val url: String, val poster: String? = null, val mediaType: Int? = null)

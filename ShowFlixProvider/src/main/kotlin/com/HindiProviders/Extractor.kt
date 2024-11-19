package com.Phisher98
import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import java.net.URI

fun deobfuscateCode(obfuscatedCode: String): String {
    // Helper function for deobfuscation
    fun deobfuscate(p: String, a: Int, c: Int, k: List<String>, e: Map<String, String>, d: Map<String, String>): String {
        var result = p
        for (i in (c - 1) downTo 0) {
            if (k[i].isNotEmpty()) {
                val regex = "\\b${i.toString(a)}\\b".toRegex()
                result = result.replace(regex, k[i])
            }
        }
        return result
    }

    // Extract the parameters from the obfuscated code
    val paramRegex = "eval\\(function\\(p,a,c,k,e,d\\)\\{(.+?)\\}\\('(.+?)',([0-9]+),([0-9]+),'(.+?)'".toRegex()
    val matchResult = paramRegex.find(obfuscatedCode) ?: return ""

    // Access the groups directly
    val p = matchResult.groupValues[2]
    val a = matchResult.groupValues[3]
    val c = matchResult.groupValues[4]
    val k = matchResult.groupValues[5]

    val kList = k.split("|")

    // Return the deobfuscated code
    return deobfuscate(p, a.toInt(), c.toInt(), kList, emptyMap(), emptyMap())
}

// Helper function to convert a number to a different base (similar to toString(radix) in JavaScript)
fun Int.toString(radix: Int): String {
    require(radix in 2..36) { "radix must be in range 2..36" }
    if (this == 0) return "0"

    val digits = "0123456789abcdefghijklmnopqrstuvwxyz"
    var num = this
    val result = StringBuilder()

    while (num != 0) {
        result.insert(0, digits[Math.abs(num % radix)])
        num /= radix
    }

    return if (this < 0) "-$result" else result.toString()
}

open class Streamwish : ExtractorApi() {
    override val name = "Streamwish"
    override val mainUrl = "https://embedwish.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit

    ) {
        val response=app.get(url,referer=url, headers = mapOf("X-Requested-With" to "XMLHttpRequest")).document

        Log.d("VicTest22", url)

        val scriptBody = response.selectFirst("body > script")?.data().toString()

        Log.d("VicTest2233", scriptBody)

        val script = deobfuscateCode(scriptBody)
        Log.d("VicTest222", script)
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to url,
        )

        Regex("file:\"(.*)\"").find(script)?.groupValues?.get(1)?.let { link ->
            Log.d("VicTest22444", link.toString())
            callback.invoke(
                ExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = link,
                    referer = "https://showflix.xyz/",
                    quality = Qualities.P1080.value,
                    type = INFER_TYPE,
                    headers = headers
                )
            )
        }
    }
}


open class Filelion : ExtractorApi() {
    override val name = "Filelion"
    override val mainUrl = "https://filelions.to"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<ExtractorLink>? {
        val responsecode=app.get(url)
        val response = responsecode.document
        val script = response.selectFirst("script:containsData(sources)")?.data().toString()
        Regex("file:\"(.*)\"").find(script)?.groupValues?.get(1)?.let { link ->
            return listOf(
                ExtractorLink(
                    this.name,
                    this.name,
                    link,
                    referer ?: "",
                    getQualityFromName(""),
                    URI(link).path.endsWith(".m3u8")
                )
            )
        }
        return null
    }
}


open class StreamRuby : ExtractorApi() {
    override val name = "StreamRuby"
    override val mainUrl = "https://streamruby.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response=app.get(url,referer=url, headers = mapOf("X-Requested-With" to "XMLHttpRequest")).document

        Log.d("VicTest2", url)

        val script = response.selectFirst("script:containsData(vplayer)")?.data().toString()
        Log.d("VicTest2", script)
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to url,

            )

        Regex("file:\"(.*)\"").find(script)?.groupValues?.get(1)?.let { link ->
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    link,
                    "https://rubystm.com",
                    Qualities.P1080.value,
                    type = INFER_TYPE,
                    headers
                )
            )
        }
    }
}
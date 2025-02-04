package com.Phisher98

import com.Phisher98.StreamPlayExtractor.invokeAnimenexus
import com.Phisher98.StreamPlayExtractor.invokeFlixAPIHQ
import com.Phisher98.StreamPlayExtractor.invokeVidbinge
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.argamap
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink

class StreamPlayTest : StreamPlay() {
    override var name = "StreamPlay-Test"
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val res = AppUtils.parseJson<LinkData>(data)
        argamap(
            {
                if (!res.isAnime) invokeFlixAPIHQ(
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            }
        )
        return true
    }

}
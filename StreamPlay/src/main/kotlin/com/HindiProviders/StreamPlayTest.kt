package com.Phisher98

import android.util.Log
import com.Phisher98.StreamPlayExtractor.invokeExtramovies
import com.Phisher98.StreamPlayExtractor.invokeFlixAPI
import com.Phisher98.StreamPlayExtractor.invokeVidbinge
import com.Phisher98.StreamPlayExtractor.invokenyaa
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
        Log.d("Test1", "$res")
        argamap(
            {
                invokeFlixAPI(
                    res.id,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeVidbinge(
                    res.imdbId,
                    res.id,
                    res.title,
                    res.year,
                    res.season,
                    res.lastSeason,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            }

        )
        return true
    }

}
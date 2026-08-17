package com.phisher98

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.extractors.StreamWishExtractor

@CloudstreamPlugin
class FiveMovierulzWatchPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(FiveMovierulzWatch())
        registerExtractorAPI(FilelionsWatch())
        registerExtractorAPI(HglinkTo())
        registerExtractorAPI(MinochinosWatch())
    }
}

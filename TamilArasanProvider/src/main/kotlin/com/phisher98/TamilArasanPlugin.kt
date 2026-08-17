package com.phisher98

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.extractors.OkRuSSL
import com.lagradost.cloudstream3.extractors.OkRuHTTP

@CloudstreamPlugin
class TamilArasanPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(TamilArasanProvider())
        registerExtractorAPI(Voe())
        registerExtractorAPI(OkRuSSL())
        registerExtractorAPI(OkRuHTTP())
        registerExtractorAPI(Hgcloud())
        registerExtractorAPI(Morencius())
        registerExtractorAPI(Minochinos())
    }
}

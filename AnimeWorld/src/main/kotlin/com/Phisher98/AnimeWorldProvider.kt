package com.phisher98

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnimeWorldPlugin : BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list
        // directly.
        registerMainAPI(AnimeWorld())
        registerExtractorAPI(Pixdrive())
        registerExtractorAPI(Ghbrisk())
        registerExtractorAPI(AWSStream())
    }
}

package com.phisher98

import com.lagradost.cloudstream3.extractors.VidhideExtractor

class Hgcloud : VidhideExtractor() {
    override var name = "HGCloud"
    override var mainUrl = "https://hgcloud.to"
    override val requiresReferer = false
}

class Morencius : VidhideExtractor() {
    override var name = "Morencius"
    override var mainUrl = "https://morencius.com"
    override val requiresReferer = false
}

class Minochinos : VidhideExtractor() {
    override var name = "Minochinos"
    override var mainUrl = "https://minochinos.com"
    override val requiresReferer = false
}

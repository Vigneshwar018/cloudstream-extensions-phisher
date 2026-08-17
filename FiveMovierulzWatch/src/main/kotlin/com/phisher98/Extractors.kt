package com.phisher98

import com.lagradost.cloudstream3.extractors.VidhideExtractor

class FilelionsWatch : VidhideExtractor() {
    override var name = "Filelions"
    override var mainUrl = "https://filelions.to"
    override val requiresReferer = false
}

class HglinkTo : VidhideExtractor() {
    override var name = "HGLink"
    override var mainUrl = "https://hglink.to"
    override val requiresReferer = false
}

class MinochinosWatch : VidhideExtractor() {
    override var name = "Minochinos"
    override var mainUrl = "https://minochinos.com"
    override val requiresReferer = false
}

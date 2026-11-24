package com.ibrahim.av1mix.data

object SiteCatalog {
    val sites = listOf(
        SourceSite("xfree", "XFree", "https://www.xfree.com/", FeedMode.SHORT, "AV1 not verified"),
        SourceSite("eporner", "Eporner", "https://www.eporner.com/", FeedMode.LONG, "AV1 reported on selected videos"),
        SourceSite("xhamster", "XHamster", "https://xhamster.com/", FeedMode.LONG, "AV1 variants reported; availability varies")
    )
}

package com.rameshta.splitframe.ads

internal enum class EmbeddedAdSurface {
    Home,
    TemplateDiscovery,
    RecentProjects,
    Editor,
    Resize,
    VideoEditor,
    Privacy,
}

internal object EmbeddedAdPolicy {
    private val bannerSurfaces = setOf(
        EmbeddedAdSurface.Home,
        EmbeddedAdSurface.TemplateDiscovery,
        EmbeddedAdSurface.RecentProjects,
    )

    fun isEligible(
        adsEnabled: Boolean,
        fullScreenAdState: FullScreenAdState,
        appOpenWindowActive: Boolean,
        imeVisible: Boolean,
    ): Boolean =
        adsEnabled &&
            fullScreenAdState == FullScreenAdState.Idle &&
            !appOpenWindowActive &&
            !imeVisible

    fun shouldShowBanner(
        surface: EmbeddedAdSurface,
        embeddedAdsEligible: Boolean,
    ): Boolean = embeddedAdsEligible && surface in bannerSurfaces

    /**
     * Returns organic item counts after which a native slot may be inserted.
     * A trailing organic item is required, so an ad is never the first or last feed item.
     */
    fun nativeInsertionPositions(
        organicItemCount: Int,
        afterEvery: Int,
        maximumAds: Int,
    ): List<Int> {
        if (organicItemCount <= 1 || afterEvery <= 0 || maximumAds <= 0) return emptyList()
        return generateSequence(afterEvery) { previous -> previous + afterEvery }
            .takeWhile { organicBeforeAd -> organicBeforeAd < organicItemCount }
            .take(maximumAds)
            .toList()
    }
}

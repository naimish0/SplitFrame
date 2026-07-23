package com.rameshta.splitframe.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedAdPolicyTest {
    @Test
    fun `embedded eligibility requires consent idle fullscreen and no transient UI`() {
        assertTrue(
            EmbeddedAdPolicy.isEligible(
                adsEnabled = true,
                fullScreenAdState = FullScreenAdState.Idle,
                appOpenWindowActive = false,
                imeVisible = false,
            ),
        )
        assertFalse(
            EmbeddedAdPolicy.isEligible(
                adsEnabled = false,
                fullScreenAdState = FullScreenAdState.Idle,
                appOpenWindowActive = false,
                imeVisible = false,
            ),
        )
        for (fullScreenState in listOf(FullScreenAdState.AppOpen, FullScreenAdState.Interstitial)) {
            assertFalse(
                EmbeddedAdPolicy.isEligible(
                    adsEnabled = true,
                    fullScreenAdState = fullScreenState,
                    appOpenWindowActive = false,
                    imeVisible = false,
                ),
            )
        }
        assertFalse(
            EmbeddedAdPolicy.isEligible(
                adsEnabled = true,
                fullScreenAdState = FullScreenAdState.Idle,
                appOpenWindowActive = true,
                imeVisible = false,
            ),
        )
        assertFalse(
            EmbeddedAdPolicy.isEligible(
                adsEnabled = true,
                fullScreenAdState = FullScreenAdState.Idle,
                appOpenWindowActive = false,
                imeVisible = true,
            ),
        )
    }

    @Test
    fun `banners are limited to safe browsing surfaces`() {
        val allowed = setOf(
            EmbeddedAdSurface.Home,
            EmbeddedAdSurface.TemplateDiscovery,
            EmbeddedAdSurface.RecentProjects,
        )
        for (surface in EmbeddedAdSurface.entries) {
            assertEquals(
                surface in allowed,
                EmbeddedAdPolicy.shouldShowBanner(surface, embeddedAdsEligible = true),
            )
            assertFalse(EmbeddedAdPolicy.shouldShowBanner(surface, embeddedAdsEligible = false))
        }
    }

    @Test
    fun `native positions are stable bounded and have organic content on both sides`() {
        assertEquals(emptyList<Int>(), EmbeddedAdPolicy.nativeInsertionPositions(0, 7, 2))
        assertEquals(emptyList<Int>(), EmbeddedAdPolicy.nativeInsertionPositions(7, 7, 2))
        assertEquals(listOf(7), EmbeddedAdPolicy.nativeInsertionPositions(8, 7, 2))
        assertEquals(listOf(7, 14), EmbeddedAdPolicy.nativeInsertionPositions(110, 7, 2))
        assertEquals(listOf(6), EmbeddedAdPolicy.nativeInsertionPositions(7, 6, 1))
        assertEquals(listOf(6), EmbeddedAdPolicy.nativeInsertionPositions(100, 6, 1))
    }

    @Test
    fun `invalid native configuration never inserts an ad`() {
        assertEquals(emptyList<Int>(), EmbeddedAdPolicy.nativeInsertionPositions(20, 0, 2))
        assertEquals(emptyList<Int>(), EmbeddedAdPolicy.nativeInsertionPositions(20, 7, 0))
        assertEquals(emptyList<Int>(), EmbeddedAdPolicy.nativeInsertionPositions(-1, 7, 2))
    }
}

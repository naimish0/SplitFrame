package com.rameshta.splitframe.ads

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstSessionAppOpenGateDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var preferences: SharedPreferences
    private var markerExisted = false
    private var markerValue = false

    @Before
    fun preserveMarkerAndStartAsFreshInstall() {
        preferences =
            context.getSharedPreferences(SplitFrameAdPreferencesName, Context.MODE_PRIVATE)
        markerExisted = preferences.contains(FirstSessionCompletedPreferenceKey)
        markerValue = preferences.getBoolean(FirstSessionCompletedPreferenceKey, false)
        preferences.edit()
            .remove(FirstSessionCompletedPreferenceKey)
            .commit()
    }

    @After
    fun restoreMarker() {
        val editor = preferences.edit()
        if (markerExisted) {
            editor.putBoolean(FirstSessionCompletedPreferenceKey, markerValue)
        } else {
            editor.remove(FirstSessionCompletedPreferenceKey)
        }
        editor.commit()
    }

    @Test
    fun firstSessionMarkerSurvivesGateRecreationWithoutRotationCompletingIt() {
        val firstSession = createFirstSessionAppOpenGate(preferences)

        assertFalse(firstSession.appOpenAdsAllowed)
        firstSession.onActivityStopped(changingConfigurations = true)
        assertFalse(preferences.contains(FirstSessionCompletedPreferenceKey))

        firstSession.onActivityStopped(changingConfigurations = false)
        assertTrue(preferences.getBoolean(FirstSessionCompletedPreferenceKey, false))
        assertFalse(firstSession.appOpenAdsAllowed)

        val laterSession = createFirstSessionAppOpenGate(preferences)
        assertTrue(laterSession.appOpenAdsAllowed)
    }
}

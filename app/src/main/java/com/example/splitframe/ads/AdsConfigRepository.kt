package com.example.splitframe.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdsConfigRepository(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(applicationContext)
    private val initializationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val consentInfoUpdateRequested = AtomicBoolean(false)
    private val consentFlowInProgress = AtomicBoolean(false)
    private val mobileAdsInitializationStarted = AtomicBoolean(false)
    private val mobileAdsInitialized = AtomicBoolean(false)

    private val _isAdsEnabled = MutableStateFlow(false)
    val isAdsEnabled: StateFlow<Boolean> = _isAdsEnabled.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    val hasRequestedConsentInfoThisProcess: Boolean
        get() = consentInfoUpdateRequested.get()

    fun gatherConsent(
        activity: Activity,
        onComplete: (FormError?) -> Unit = {},
    ) {
        if (!consentFlowInProgress.compareAndSet(false, true)) return

        consentInfoUpdateRequested.set(true)
        _isAdsEnabled.value = false
        val parameters = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                updatePrivacyOptionsRequirement()
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    finishConsentFlow()
                    onComplete(formError)
                }
            },
            { requestConsentError ->
                finishConsentFlow()
                onComplete(requestConsentError)
            },
        )
    }

    fun showPrivacyOptions(
        activity: Activity,
        onDismissed: (FormError?) -> Unit = {},
    ) {
        if (!_privacyOptionsRequired.value) {
            onDismissed(null)
            return
        }

        if (!consentFlowInProgress.compareAndSet(false, true)) return
        _isAdsEnabled.value = false
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            finishConsentFlow()
            onDismissed(formError)
        }
    }

    private fun finishConsentFlow() {
        consentFlowInProgress.set(false)
        updatePrivacyOptionsRequirement()
        updateAdsAvailability()
    }

    private fun updatePrivacyOptionsRequirement() {
        _privacyOptionsRequired.value =
            consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun updateAdsAvailability() {
        if (consentFlowInProgress.get() || !consentInformation.canRequestAds()) {
            _isAdsEnabled.value = false
            return
        }
        if (mobileAdsInitialized.get()) {
            _isAdsEnabled.value = true
            return
        }
        initializeMobileAds()
    }

    private fun initializeMobileAds() {
        if (!mobileAdsInitializationStarted.compareAndSet(false, true)) return

        initializationScope.launch {
            runCatching {
                MobileAds.initialize(applicationContext) {
                    mobileAdsInitialized.set(true)
                    updateAdsAvailability()
                }
            }.onFailure {
                mobileAdsInitializationStarted.set(false)
                _isAdsEnabled.value = false
            }
        }
    }
}

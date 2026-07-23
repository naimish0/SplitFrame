package com.rameshta.splitframe.ads

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

    private val _canPreloadAds = MutableStateFlow(false)
    internal val canPreloadAds: StateFlow<Boolean> = _canPreloadAds.asStateFlow()

    private val _isConsentFlowInProgress = MutableStateFlow(false)
    internal val isConsentFlowInProgress: StateFlow<Boolean> =
        _isConsentFlowInProgress.asStateFlow()

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
        _isConsentFlowInProgress.value = true
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
        // UMP synchronously restores the previous session after this request starts. Google
        // permits preloading from that state while the current-session form remains unresolved;
        // display stays disabled until finishConsentFlow().
        updateAdReadiness()
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
        _isConsentFlowInProgress.value = true
        _isAdsEnabled.value = false
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            finishConsentFlow()
            onDismissed(formError)
        }
    }

    private fun finishConsentFlow() {
        consentFlowInProgress.set(false)
        _isConsentFlowInProgress.value = false
        updatePrivacyOptionsRequirement()
        updateAdReadiness()
    }

    private fun updatePrivacyOptionsRequirement() {
        _privacyOptionsRequired.value =
            consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun updateAdReadiness() {
        if (!consentInformation.canRequestAds()) {
            _canPreloadAds.value = false
            _isAdsEnabled.value = false
            return
        }
        if (mobileAdsInitialized.get()) {
            _canPreloadAds.value = true
            _isAdsEnabled.value = !consentFlowInProgress.get()
            return
        }
        _canPreloadAds.value = false
        _isAdsEnabled.value = false
        initializeMobileAds()
    }

    private fun initializeMobileAds() {
        if (!mobileAdsInitializationStarted.compareAndSet(false, true)) return

        initializationScope.launch {
            runCatching {
                MobileAds.initialize(applicationContext) {
                    mobileAdsInitialized.set(true)
                    updateAdReadiness()
                }
            }.onFailure {
                mobileAdsInitializationStarted.set(false)
                _canPreloadAds.value = false
                _isAdsEnabled.value = false
            }
        }
    }
}

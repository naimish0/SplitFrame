package com.rameshta.splitframe.ads

import android.content.Context

internal const val SplitFrameAdPreferencesName = "splitframe_ads"

internal class SharedPreferencesWorkflowInterstitialStateStore(
    context: Context,
) : WorkflowInterstitialStateStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(SplitFrameAdPreferencesName, Context.MODE_PRIVATE)

    override fun read(): WorkflowInterstitialState =
        WorkflowInterstitialState(
            successfulWorkflowsSinceDisplay = preferences.getInt(SuccessfulWorkflowCountKey, 0),
            processedWorkflowKeys = preferences.getString(ProcessedWorkflowKeysKey, null)
                ?.split(WorkflowKeySeparator)
                .orEmpty(),
            handledPresentationKeys = preferences.getString(HandledPresentationKeysKey, null)
                ?.split(WorkflowKeySeparator)
                .orEmpty(),
        )

    override fun write(state: WorkflowInterstitialState) {
        preferences.edit()
            .putInt(SuccessfulWorkflowCountKey, state.successfulWorkflowsSinceDisplay)
            .putString(ProcessedWorkflowKeysKey, state.processedWorkflowKeys.joinToString(WorkflowKeySeparator))
            .putString(HandledPresentationKeysKey, state.handledPresentationKeys.joinToString(WorkflowKeySeparator))
            .apply()
    }

    private companion object {
        const val SuccessfulWorkflowCountKey = "successful_workflows_since_interstitial"
        const val ProcessedWorkflowKeysKey = "processed_workflow_keys"
        const val HandledPresentationKeysKey = "handled_presentation_keys"
        const val WorkflowKeySeparator = ","
    }
}

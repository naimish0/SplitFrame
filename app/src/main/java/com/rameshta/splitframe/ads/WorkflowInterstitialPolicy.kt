package com.rameshta.splitframe.ads

import java.security.MessageDigest
import java.util.UUID

internal enum class WorkflowKind {
    PhotoCollage,
    ImageResize,
    VideoComposition,
}

internal enum class WorkflowOutcome {
    Succeeded,
    Failed,
    Cancelled,
}

internal data class WorkflowCompletionEvent(
    val kind: WorkflowKind,
    val stableWorkflowId: String,
    val outcome: WorkflowOutcome,
)

internal object WorkflowCompletionEvents {
    fun photoCollage(savedUri: String): WorkflowCompletionEvent? =
        succeeded(WorkflowKind.PhotoCollage, savedUri)

    fun imageResize(savedUri: String): WorkflowCompletionEvent? =
        succeeded(WorkflowKind.ImageResize, savedUri)

    fun videoComposition(workId: String?): WorkflowCompletionEvent? {
        val candidate = workId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val canonicalWorkId = runCatching { UUID.fromString(candidate).toString() }
            .getOrNull()
            ?.takeIf { candidate.length == it.length && candidate.equals(it, ignoreCase = true) }
            ?: return null
        return succeeded(WorkflowKind.VideoComposition, canonicalWorkId)
    }

    private fun succeeded(
        kind: WorkflowKind,
        stableWorkflowId: String,
    ): WorkflowCompletionEvent? {
        val normalizedId = stableWorkflowId.trim().takeIf(String::isNotEmpty) ?: return null
        return WorkflowCompletionEvent(
            kind = kind,
            stableWorkflowId = normalizedId,
            outcome = WorkflowOutcome.Succeeded,
        )
    }
}

internal data class WorkflowInterstitialState(
    val successfulWorkflowsSinceDisplay: Int = 0,
    val processedWorkflowKeys: List<String> = emptyList(),
    val handledPresentationKeys: List<String> = emptyList(),
)

internal interface WorkflowInterstitialStateStore {
    fun read(): WorkflowInterstitialState

    fun write(state: WorkflowInterstitialState)
}

internal enum class WorkflowInterstitialDecision {
    Ignored,
    Duplicate,
    Counted,
    Eligible,
}

internal class WorkflowInterstitialTracker(
    private val store: WorkflowInterstitialStateStore,
    private val successfulWorkflowsPerInterstitial: Int = 2,
    private val historyLimit: Int = 64,
) {
    private var state = sanitize(runCatching(store::read).getOrDefault(WorkflowInterstitialState()))

    init {
        require(successfulWorkflowsPerInterstitial > 0)
        require(historyLimit > 0)
    }

    @Synchronized
    fun record(event: WorkflowCompletionEvent): WorkflowInterstitialDecision {
        if (event.outcome != WorkflowOutcome.Succeeded || event.stableWorkflowId.isBlank()) {
            return WorkflowInterstitialDecision.Ignored
        }

        val workflowKey = workflowKey(event.kind, event.stableWorkflowId)
        if (workflowKey in state.processedWorkflowKeys) {
            return WorkflowInterstitialDecision.Duplicate
        }

        val successfulCount =
            (state.successfulWorkflowsSinceDisplay + 1).coerceAtMost(successfulWorkflowsPerInterstitial)
        state = state.copy(
            successfulWorkflowsSinceDisplay = successfulCount,
            processedWorkflowKeys =
                (listOf(workflowKey) + state.processedWorkflowKeys)
                    .distinct()
                    .take(historyLimit),
        )
        persist()
        return if (successfulCount >= successfulWorkflowsPerInterstitial) {
            WorkflowInterstitialDecision.Eligible
        } else {
            WorkflowInterstitialDecision.Counted
        }
    }

    @Synchronized
    fun onDisplayStarted() {
        if (state.successfulWorkflowsSinceDisplay == 0) return
        state = state.copy(successfulWorkflowsSinceDisplay = 0)
        persist()
    }

    @Synchronized
    fun claimPresentation(event: WorkflowCompletionEvent): Boolean {
        if (event.outcome != WorkflowOutcome.Succeeded || event.stableWorkflowId.isBlank()) return false
        val workflowKey = workflowKey(event.kind, event.stableWorkflowId)
        if (workflowKey !in state.processedWorkflowKeys || workflowKey in state.handledPresentationKeys) {
            return false
        }

        state = state.copy(
            handledPresentationKeys =
                (listOf(workflowKey) + state.handledPresentationKeys)
                    .distinct()
                    .take(historyLimit),
        )
        persist()
        return state.successfulWorkflowsSinceDisplay >= successfulWorkflowsPerInterstitial
    }

    @Synchronized
    fun pendingSuccessfulWorkflowCount(): Int = state.successfulWorkflowsSinceDisplay

    private fun sanitize(restored: WorkflowInterstitialState): WorkflowInterstitialState =
        WorkflowInterstitialState(
            successfulWorkflowsSinceDisplay =
                restored.successfulWorkflowsSinceDisplay.coerceIn(0, successfulWorkflowsPerInterstitial),
            processedWorkflowKeys = restored.processedWorkflowKeys
                .asSequence()
                .filter(String::isNotBlank)
                .distinct()
                .take(historyLimit)
                .toList(),
            handledPresentationKeys = restored.handledPresentationKeys
                .asSequence()
                .filter(String::isNotBlank)
                .distinct()
                .take(historyLimit)
                .toList(),
        )

    private fun persist() {
        runCatching { store.write(state) }
    }

    private fun workflowKey(kind: WorkflowKind, stableWorkflowId: String): String {
        val value = "${kind.name}\u0000${stableWorkflowId.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val encoded = CharArray(digest.size * 2)
        digest.forEachIndexed { index, byte ->
            val valueAsInt = byte.toInt() and 0xff
            encoded[index * 2] = HexDigits[valueAsInt ushr 4]
            encoded[index * 2 + 1] = HexDigits[valueAsInt and 0x0f]
        }
        return String(encoded)
    }

    private companion object {
        const val HexDigits = "0123456789abcdef"
    }
}

internal enum class FullScreenAdState {
    Idle,
    Interstitial,
    AppOpen,
}

internal data class InterstitialEligibility(
    val adsEligible: Boolean,
    val adLoaded: Boolean,
    val fullScreenAdState: FullScreenAdState,
    val activityResumed: Boolean,
    val windowFocused: Boolean,
    val workflowInProgress: Boolean,
    val naturalBreak: Boolean,
    val interstitialIntervalElapsed: Boolean,
    val appOpenSeparationElapsed: Boolean,
    val appOpenWindowActive: Boolean = false,
)

internal object FullScreenAdEligibility {
    fun canShowInterstitial(eligibility: InterstitialEligibility): Boolean =
        eligibility.adsEligible &&
            eligibility.adLoaded &&
            eligibility.fullScreenAdState == FullScreenAdState.Idle &&
            eligibility.activityResumed &&
            eligibility.windowFocused &&
            !eligibility.workflowInProgress &&
            eligibility.naturalBreak &&
            eligibility.interstitialIntervalElapsed &&
            eligibility.appOpenSeparationElapsed &&
            !eligibility.appOpenWindowActive

    fun hasMinimumSeparation(
        nowMillis: Long,
        lastShownAtMillis: Long,
        minimumSeparationMillis: Long,
    ): Boolean {
        if (lastShownAtMillis <= 0L) return true
        val elapsed = nowMillis - lastShownAtMillis
        return elapsed >= minimumSeparationMillis
    }
}

internal enum class WorkflowInterstitialAction {
    Continue,
    Show,
}

internal class WorkflowInterstitialCoordinator(
    private val tracker: WorkflowInterstitialTracker,
) {
    fun onCompletion(
        event: WorkflowCompletionEvent,
        eligibility: InterstitialEligibility,
    ): WorkflowInterstitialAction {
        tracker.record(event)
        val hasPresentationOpportunity = tracker.claimPresentation(event)
        return if (
            hasPresentationOpportunity &&
            FullScreenAdEligibility.canShowInterstitial(eligibility)
        ) {
            WorkflowInterstitialAction.Show
        } else {
            WorkflowInterstitialAction.Continue
        }
    }

    fun onDisplayStarted() {
        tracker.onDisplayStarted()
    }
}

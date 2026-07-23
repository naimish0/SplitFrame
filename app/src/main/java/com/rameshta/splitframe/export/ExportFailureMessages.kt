package com.rameshta.splitframe.export

internal fun actionableExportFailure(
    failure: Throwable,
    fallback: String,
): String {
    val causes = generateSequence(failure as Throwable?) { it.cause }.take(12).toList()
    if (causes.any { it is SecurityException }) {
        return "Media access is no longer available. Choose the affected media again and retry."
    }
    if (causes.any { cause ->
            val message = cause.message?.lowercase().orEmpty()
            "enospc" in message || "no space left" in message || "disk full" in message
        }
    ) {
        return "Not enough storage is available. Free some space and try again."
    }
    return failure.message?.takeIf(String::isNotBlank) ?: fallback
}

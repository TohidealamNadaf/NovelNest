package com.novelnest.app.feature.reader

import com.novelnest.app.data.ai.AiAction
import com.novelnest.app.data.ai.AiErrorCode
import com.novelnest.app.data.ai.AiFailure

/** Failures reaching the reader are sanitized by the AI boundary. Preserve their actionable
 * details instead of replacing different size/quota failures with one generic rewrite warning. */
internal fun aiErrorHelpText(failure: AiFailure, action: AiAction?): String {
    val detail = failure.message.ifBlank {
        when (failure.code) {
            AiErrorCode.NOT_CONFIGURED -> "Configure a provider and model in Settings > AI Reading Tools."
            AiErrorCode.CONSENT_REQUIRED -> "Enable chapter-sharing consent for this provider in AI Reading Tools."
            AiErrorCode.CREDENTIALS -> "Update this provider's API key in AI Reading Tools."
            AiErrorCode.BILLING_POLICY -> "This route is not allowed by the current cost policy. Review AI Reading Tools."
            AiErrorCode.MODEL_UNAVAILABLE -> "The selected model is unavailable. Select another configured route."
            AiErrorCode.RATE_LIMIT -> "The provider's usage allowance was reached."
            AiErrorCode.TOO_LARGE -> {
                val task = when (action) {
                    AiAction.SUMMARY -> "summary"
                    AiAction.REWRITE -> "rewrite"
                    AiAction.EXTRACT -> "term-extraction"
                    null -> "AI"
                }
                "This chapter exceeds the selected route's $task request budget. Choose a larger-capacity route in AI Reading Tools."
            }
            AiErrorCode.BUSY -> "Another AI request is already in progress."
            AiErrorCode.CANCELLED -> "The request was cancelled."
            else -> "Could not complete the AI request."
        }
    }
    val retry = failure.retryAfterSeconds?.takeIf { it > 0 }
    return if (failure.code == AiErrorCode.RATE_LIMIT && retry != null && !detail.contains("retry", ignoreCase = true))
        "$detail Retry in about $retry seconds." else detail
}

/** A generic failure card must retry the action that failed, including extraction. */
internal fun retryAiFailure(action: AiAction?, summary: () -> Unit, rewrite: () -> Unit, extract: () -> Unit) {
    when (action) {
        AiAction.SUMMARY -> summary()
        AiAction.REWRITE -> rewrite()
        AiAction.EXTRACT -> extract()
        null -> Unit
    }
}

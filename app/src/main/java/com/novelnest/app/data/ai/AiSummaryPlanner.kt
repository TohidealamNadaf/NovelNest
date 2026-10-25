package com.novelnest.app.data.ai

/** Summarization has small output even for a large input. Rewrite limits must not multiply its calls.
 * Never sample or discard the end of a chapter to make it fit a free provider. */
internal object AiSummaryPlanner {
    const val OUTPUT_TOKENS = 2_048

    fun chunks(title: String, paragraphs: List<AiParagraph>, spec: AiModelSpec): List<List<AiParagraph>> {
        if (spec.route.provider == AiProviderId.GROQ) {
            // The free TPM allowance, rather than the much larger model context, is the useful bound.
            // Reject before spending on partial results that would require minute-long quota waits.
            val prompt = AiPromptFactory.create(AiAction.SUMMARY, title, paragraphs)
            if (AiRequestPolicy.estimatedTokens(prompt, minOf(OUTPUT_TOKENS, spec.outputTokens)) > AiRequestPolicy.GROQ_TOKEN_BUDGET)
                throw AiException(AiErrorCode.TOO_LARGE,
                    "This whole chapter exceeds Groq's quick-summary token budget. Enable another provider with a larger allowance in AI Reading Tools; no text has been omitted or sent.")
            return listOf(paragraphs)
        }
        return ChapterAiRepository.chunk(paragraphs, spec.summaryInputBytes)
    }
}

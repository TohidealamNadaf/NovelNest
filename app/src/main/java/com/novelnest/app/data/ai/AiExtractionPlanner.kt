package com.novelnest.app.data.ai

/** Plan extraction independently of rewrite length. The topic, JSON schema and full possible output
 * all count toward admission. Never shrink the output reservation or sample chapter text to fit. */
internal object AiExtractionPlanner {
    fun chunks(title: String, paragraphs: List<AiParagraph>, target: String, spec: AiModelSpec): List<List<AiParagraph>> {
        if (spec.route.provider == AiProviderId.GROQ) {
            val prompt = AiPromptFactory.create(AiAction.EXTRACT, title, paragraphs, target)
            if (AiRequestPolicy.estimatedTokens(prompt, spec.outputTokens) > AiRequestPolicy.GROQ_TOKEN_BUDGET)
                throw AiException(AiErrorCode.TOO_LARGE,
                    "This whole chapter exceeds Groq's quick-extraction token budget. Enable another extraction provider with a larger allowance in AI Reading Tools; no text has been omitted or sent.")
            return listOf(paragraphs)
        }
        return ChapterAiRepository.chunk(paragraphs, spec.extractionInputBytes)
    }
}

package com.katixo.ai.llm;

import java.util.List;

/**
 * A model-agnostic generation request.
 *
 * @param role       selects which configured model to use (TEXT vs VISION)
 * @param prompt     the full prompt text
 * @param base64Images images for the VISION role (base64-encoded PNG); empty for TEXT
 * @param jsonFormat request strict JSON-mode output
 */
public record LlmRequest(LlmRole role, String prompt, List<String> base64Images, boolean jsonFormat) {

    public LlmRequest {
        base64Images = base64Images == null ? List.of() : List.copyOf(base64Images);
    }

    public static LlmRequest text(String prompt) {
        return new LlmRequest(LlmRole.TEXT, prompt, List.of(), true);
    }

    public static LlmRequest vision(String prompt, List<String> base64Images) {
        return new LlmRequest(LlmRole.VISION, prompt, base64Images, true);
    }
}

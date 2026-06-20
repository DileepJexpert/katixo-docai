package com.katixo.ai.llm;

/**
 * Result of one generation.
 *
 * @param text      the raw model output (expected to be a JSON string in M1)
 * @param model     the concrete model that served the request (for logging/evals)
 * @param latencyMs wall-clock latency of the call
 */
public record LlmResponse(String text, String model, long latencyMs) {
}

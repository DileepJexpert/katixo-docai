package com.katixo.ai.llm;

/**
 * Abstraction over the local inference engine. The single Milestone 1 implementation is
 * {@link OllamaLlmClient}; swapping engines or model names is a config change, not a code change.
 */
public interface LlmClient {

    /**
     * @throws com.katixo.ai.support.UpstreamUnavailableException if the engine is unreachable
     */
    LlmResponse generate(LlmRequest request);

    /** @return health snapshot; never throws. */
    LlmHealth health();
}

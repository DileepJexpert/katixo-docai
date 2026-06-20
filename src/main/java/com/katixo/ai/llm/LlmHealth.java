package com.katixo.ai.llm;

import java.util.List;

/**
 * Health snapshot of the inference engine.
 *
 * @param reachable         whether the engine answered
 * @param configuredText    the configured text model name
 * @param configuredVision  the configured vision model name
 * @param loadedModels      models currently resident in the engine (may be empty)
 */
public record LlmHealth(boolean reachable, String configuredText, String configuredVision,
                        List<String> loadedModels) {

    public LlmHealth {
        loadedModels = loadedModels == null ? List.of() : List.copyOf(loadedModels);
    }
}

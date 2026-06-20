package com.katixo.ai.web;

import java.util.List;

/**
 * Health of the service and its local dependencies, including the currently loaded model
 * (spec section 5.1).
 */
public record HealthResponse(
        String status,                 // UP | DEGRADED
        String service,
        boolean ollamaReachable,
        String configuredTextModel,
        String configuredVisionModel,
        List<String> ollamaLoadedModels,
        boolean ocrReachable
) {
}

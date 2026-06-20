package com.katixo.ai.web;

import com.katixo.ai.llm.LlmClient;
import com.katixo.ai.llm.LlmHealth;
import com.katixo.ai.ocr.OcrClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports service + Ollama + OCR sidecar reachability and the currently loaded model (spec 5.1).
 * Always returns 200 with a status field so callers can poll it cheaply.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final LlmClient llm;
    private final OcrClient ocr;

    public HealthController(LlmClient llm, OcrClient ocr) {
        this.llm = llm;
        this.ocr = ocr;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        LlmHealth llmHealth = llm.health();
        boolean ocrReachable = ocr.isReachable();
        String status = (llmHealth.reachable() && ocrReachable) ? "UP" : "DEGRADED";
        return new HealthResponse(
                status,
                "katixo-ai-service",
                llmHealth.reachable(),
                llmHealth.configuredText(),
                llmHealth.configuredVision(),
                llmHealth.loadedModels(),
                ocrReachable);
    }
}

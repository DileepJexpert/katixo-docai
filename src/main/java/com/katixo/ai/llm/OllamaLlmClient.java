package com.katixo.ai.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.katixo.ai.commons.gpu.GpuGuardException;
import com.katixo.ai.commons.gpu.GpuResourceGuard;
import com.katixo.ai.commons.sidecar.SidecarClient;
import com.katixo.ai.commons.sidecar.SidecarConfig;
import com.katixo.ai.commons.sidecar.SidecarHealth;
import com.katixo.ai.config.AiProperties;
import com.katixo.ai.support.UpstreamUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama implementation of {@link LlmClient} - the only inference engine (local HTTP, localhost).
 *
 * <p>Determinism is enforced here: {@code temperature} and {@code seed} come from config and the
 * request uses Ollama's {@code format: json} JSON-mode. Model names are resolved from config by
 * {@link LlmRole}, so nothing upstream hard-codes a model.
 *
 * <p>Every generation is a GPU call, so it runs through the shared {@link GpuResourceGuard}: on this
 * single-GPU box only one model may be resident at a time, and the guard ensures image-generator and
 * this service never hit the GPU together. It extends the platform {@link SidecarClient} base for the
 * shared localhost-sidecar plumbing (idempotency key, retry helper, {@code probe()} health contract).
 */
@Component
public class OllamaLlmClient extends SidecarClient implements LlmClient {

    private final RestClient ollama;
    private final AiProperties props;
    private final GpuResourceGuard gpuGuard;

    public OllamaLlmClient(RestClient ollamaRestClient, AiProperties props, GpuResourceGuard gpuGuard) {
        super(props.getOllama().getBaseUrl(),
                SidecarConfig.noRetry("ollama", Duration.ofSeconds(5),
                        Duration.ofSeconds(props.getOllama().getTimeoutSeconds())));
        this.ollama = ollamaRestClient;
        this.props = props;
        this.gpuGuard = gpuGuard;
    }

    private String modelFor(LlmRole role) {
        return role == LlmRole.VISION ? props.getOllama().getVisionModel() : props.getOllama().getTextModel();
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        // Serialize GPU access across both Katixo apps; the guard always releases, even on error.
        try {
            return gpuGuard.runExclusively("docai-llm-" + request.role(), () -> doGenerate(request));
        } catch (RuntimeException e) {
            // UpstreamUnavailableException and GpuBusyException propagate unchanged to the web layer.
            throw e;
        } catch (Exception e) {
            throw new GpuGuardException("Guarded LLM call failed unexpectedly", e);
        }
    }

    /** The actual Ollama call (the GPU work), run under the guard by {@link #generate}. */
    private LlmResponse doGenerate(LlmRequest request) {
        AiProperties.Ollama cfg = props.getOllama();
        String model = modelFor(request.role());

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", cfg.getTemperature());
        options.put("seed", cfg.getSeed());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("prompt", request.prompt());
        body.put("stream", false);
        body.put("keep_alive", cfg.getKeepAlive());
        body.put("options", options);
        if (request.jsonFormat()) {
            body.put("format", cfg.getFormat());
        }
        if (!request.base64Images().isEmpty()) {
            body.put("images", request.base64Images());
        }

        long start = System.currentTimeMillis();
        try {
            OllamaGenerateResponse resp = ollama.post()
                    .uri("/api/generate")
                    .body(body)
                    .retrieve()
                    .body(OllamaGenerateResponse.class);
            long latency = System.currentTimeMillis() - start;
            String text = resp == null || resp.response() == null ? "" : resp.response();
            return new LlmResponse(text, model, latency);
        } catch (RestClientException e) {
            throw new UpstreamUnavailableException("ollama",
                    "Local LLM (Ollama) is unavailable. Is `ollama serve` running and model '"
                            + model + "' pulled?", e);
        }
    }

    @Override
    public LlmHealth health() {
        AiProperties.Ollama cfg = props.getOllama();
        List<String> loaded = new ArrayList<>();
        boolean reachable;
        try {
            OllamaPsResponse ps = ollama.get().uri("/api/ps").retrieve().body(OllamaPsResponse.class);
            reachable = true;
            if (ps != null && ps.models() != null) {
                ps.models().forEach(m -> loaded.add(m.name()));
            }
        } catch (RestClientException e) {
            log.debug("Ollama health check failed: {}", e.getMessage());
            reachable = false;
        }
        return new LlmHealth(reachable, cfg.getTextModel(), cfg.getVisionModel(), loaded);
    }

    @Override
    public SidecarHealth probe() {
        return health().reachable()
                ? SidecarHealth.up(config.name())
                : SidecarHealth.down(config.name(), "Ollama not reachable");
    }

    // --- Ollama wire types (only the fields we read) ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaGenerateResponse(String response, boolean done) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaPsResponse(List<LoadedModel> models) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LoadedModel(String name, String model) {
    }
}

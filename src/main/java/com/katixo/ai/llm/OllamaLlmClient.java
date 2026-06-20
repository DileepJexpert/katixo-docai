package com.katixo.ai.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.katixo.ai.config.AiProperties;
import com.katixo.ai.support.UpstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
 */
@Component
public class OllamaLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmClient.class);

    private final RestClient ollama;
    private final AiProperties props;

    public OllamaLlmClient(RestClient ollamaRestClient, AiProperties props) {
        this.ollama = ollamaRestClient;
        this.props = props;
    }

    private String modelFor(LlmRole role) {
        return role == LlmRole.VISION ? props.getOllama().getVisionModel() : props.getOllama().getTextModel();
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
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

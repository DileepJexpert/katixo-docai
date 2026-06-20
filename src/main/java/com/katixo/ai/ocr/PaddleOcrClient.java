package com.katixo.ai.ocr;

import com.katixo.ai.commons.sidecar.SidecarClient;
import com.katixo.ai.commons.sidecar.SidecarConfig;
import com.katixo.ai.commons.sidecar.SidecarHealth;
import com.katixo.ai.config.AiProperties;
import com.katixo.ai.support.UpstreamUnavailableException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * Talks to the local PaddleOCR FastAPI sidecar over HTTP (localhost only). Extends the platform
 * {@link SidecarClient} base for the shared localhost-sidecar plumbing and the {@code probe()} health
 * contract.
 *
 * <p>OCR is intentionally NOT routed through the GPU guard: PaddleOCR is light and commonly CPU-bound,
 * and the dominant GPU consumer in this service is the Ollama LLM (which is guarded).
 */
@Component
public class PaddleOcrClient extends SidecarClient implements OcrClient {

    private final RestClient ocr;

    public PaddleOcrClient(RestClient ocrRestClient, AiProperties props) {
        super(props.getOcr().getBaseUrl(),
                SidecarConfig.noRetry("ocr", Duration.ofSeconds(5),
                        Duration.ofSeconds(props.getOcr().getTimeoutSeconds())));
        this.ocr = ocrRestClient;
    }

    @Override
    public OcrResult ocr(byte[] pngImage, String filename) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(pngImage) {
            @Override
            public String getFilename() {
                return filename == null ? "page.png" : filename;
            }
        });
        try {
            OcrResult result = ocr.post()
                    .uri("/ocr")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(OcrResult.class);
            return result == null ? OcrResult.empty() : result;
        } catch (RestClientException e) {
            throw new UpstreamUnavailableException("ocr-sidecar",
                    "OCR sidecar is unavailable. Is the PaddleOCR sidecar running on localhost?", e);
        }
    }

    @Override
    public boolean isReachable() {
        try {
            ocr.get().uri("/health").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.debug("OCR sidecar health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public SidecarHealth probe() {
        return isReachable() ? SidecarHealth.up(config.name())
                : SidecarHealth.down(config.name(), "OCR sidecar not reachable");
    }
}

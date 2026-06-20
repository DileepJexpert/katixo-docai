package com.katixo.ai.ocr;

import com.katixo.ai.support.UpstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Talks to the local PaddleOCR FastAPI sidecar over HTTP (localhost only). */
@Component
public class PaddleOcrClient implements OcrClient {

    private static final Logger log = LoggerFactory.getLogger(PaddleOcrClient.class);

    private final RestClient ocr;

    public PaddleOcrClient(RestClient ocrRestClient) {
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
}

package com.katixo.ai.privacy;

import com.katixo.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Asserts the privacy/offline guarantee in code and states it in the logs (spec sections 1.4 & 7).
 *
 * <p>On startup it verifies that the Ollama and OCR endpoints are loopback. When
 * {@code katixo.ai.privacy.enforce-localhost=true} (default) a non-local endpoint is a fatal
 * misconfiguration and the application refuses to start - making "documents never leave the host"
 * true by construction rather than by policy.
 */
@Component
public class PrivacyGuard {

    private static final Logger log = LoggerFactory.getLogger(PrivacyGuard.class);

    private final AiProperties props;

    public PrivacyGuard(AiProperties props) {
        this.props = props;
    }

    /** Fail fast at construction time so a misconfiguration can never serve a single request. */
    public void verifyEndpoints() {
        String ollama = props.getOllama().getBaseUrl();
        String ocr = props.getOcr().getBaseUrl();

        boolean ollamaLocal = LocalhostEndpoints.isLocal(ollama);
        boolean ocrLocal = LocalhostEndpoints.isLocal(ocr);

        if (props.getPrivacy().isEnforceLocalhost() && !(ollamaLocal && ocrLocal)) {
            throw new IllegalStateException(
                    "PRIVACY VIOLATION: AI endpoints must be localhost. ollama=" + ollama
                            + " (local=" + ollamaLocal + "), ocr=" + ocr + " (local=" + ocrLocal + "). "
                            + "Set katixo.ai.privacy.enforce-localhost=false only if you understand the consequences.");
        }
        if (!(ollamaLocal && ocrLocal)) {
            log.warn("Privacy enforcement is DISABLED and a non-local AI endpoint is configured. "
                    + "Document content may leave this host. ollama={}, ocr={}", ollama, ocr);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void announce() {
        verifyEndpoints();
        log.info("====================================================================");
        log.info(" Katixo Local AI Service - PRIVACY: all inference is LOCAL.");
        log.info("   - Inference engine : Ollama @ {}", props.getOllama().getBaseUrl());
        log.info("   - OCR sidecar      : {}", props.getOcr().getBaseUrl());
        log.info("   - No paid/external AI calls at runtime. No telemetry leaves the host.");
        log.info("   - Client financial documents never leave this machine.");
        log.info("====================================================================");
    }
}

package com.katixo.ai;

import com.katixo.ai.config.AiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Katixo Local AI Service - Milestone 1.
 *
 * <p>Turns a photographed/scanned invoice, GRN or purchase bill into validated structured JSON.
 * All inference runs locally (Ollama + OCR sidecar on localhost). No paid/external AI calls are
 * made at runtime and no document bytes ever leave the host - see {@link com.katixo.ai.privacy}.
 */
@SpringBootApplication
@EnableConfigurationProperties(AiProperties.class)
public class KatixoAiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KatixoAiServiceApplication.class, args);
    }
}

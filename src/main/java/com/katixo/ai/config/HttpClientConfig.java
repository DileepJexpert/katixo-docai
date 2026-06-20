package com.katixo.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Builds the two (and only two) HTTP clients this service uses, both pointed at localhost:
 * Ollama and the OCR sidecar. Timeouts come from {@link AiProperties}.
 */
@Configuration
public class HttpClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClient ollamaRestClient(AiProperties props) {
        return build(props.getOllama().getBaseUrl(), props.getOllama().getTimeoutSeconds());
    }

    @Bean
    public RestClient ocrRestClient(AiProperties props) {
        return build(props.getOcr().getBaseUrl(), props.getOcr().getTimeoutSeconds());
    }

    private RestClient build(String baseUrl, int readTimeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(readTimeoutSeconds).toMillis());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}

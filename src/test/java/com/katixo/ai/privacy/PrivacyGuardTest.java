package com.katixo.ai.privacy;

import com.katixo.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrivacyGuardTest {

    @Test
    void detectsLoopbackHosts() {
        assertThat(LocalhostEndpoints.isLocal("http://localhost:11434")).isTrue();
        assertThat(LocalhostEndpoints.isLocal("http://127.0.0.1:8000")).isTrue();
        assertThat(LocalhostEndpoints.isLocal("http://[::1]:8000")).isTrue();
        assertThat(LocalhostEndpoints.isLocal("https://api.openai.com")).isFalse();
        assertThat(LocalhostEndpoints.isLocal("http://192.168.1.50:11434")).isFalse();
        assertThat(LocalhostEndpoints.isLocal(null)).isFalse();
    }

    @Test
    void refusesNonLocalEndpointWhenEnforced() {
        AiProperties props = new AiProperties();
        props.getPrivacy().setEnforceLocalhost(true);
        props.getOllama().setBaseUrl("https://api.openai.com");
        props.getOcr().setBaseUrl("http://localhost:8000");

        assertThatThrownBy(() -> new PrivacyGuard(props).verifyEndpoints())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PRIVACY VIOLATION");
    }

    @Test
    void allowsLocalhostEndpoints() {
        AiProperties props = new AiProperties();
        props.getPrivacy().setEnforceLocalhost(true);
        props.getOllama().setBaseUrl("http://localhost:11434");
        props.getOcr().setBaseUrl("http://127.0.0.1:8000");

        assertThatCode(() -> new PrivacyGuard(props).verifyEndpoints()).doesNotThrowAnyException();
    }
}

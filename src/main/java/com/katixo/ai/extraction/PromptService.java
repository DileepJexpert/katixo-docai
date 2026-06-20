package com.katixo.ai.extraction;

import com.katixo.ai.config.AiProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads versioned prompt templates from {@code /prompts/*.txt} (never inline strings - spec 5.3)
 * and fills in the per-document placeholders.
 */
@Service
public class PromptService {

    private final AiProperties props;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public PromptService(AiProperties props) {
        this.props = props;
    }

    public String version() {
        return props.getPrompts().getVersion();
    }

    public String buildTextPrompt(String ocrText, String docTypeHint) {
        return template("extraction")
                .replace("{{DOC_TYPE_HINT}}", nullSafe(docTypeHint))
                .replace("{{OCR_TEXT}}", nullSafe(ocrText));
    }

    public String buildVlmPrompt(String docTypeHint) {
        return template("extraction_vlm")
                .replace("{{DOC_TYPE_HINT}}", nullSafe(docTypeHint));
    }

    public String buildRepairPrompt(String previousOutput, String error) {
        return template("repair")
                .replace("{{ERROR}}", nullSafe(error))
                .replace("{{PREVIOUS_OUTPUT}}", nullSafe(previousOutput));
    }

    private String template(String name) {
        String key = name + "_" + version();
        return cache.computeIfAbsent(key, this::load);
    }

    private String load(String key) {
        String path = "prompts/" + key + ".txt";
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing prompt resource: " + path, e);
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}

package com.katixo.ai.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Validates a parsed JSON node against {@code schema/extraction.schema.json}. */
@Component
public class ExtractionSchemaValidator {

    private JsonSchema schema;

    @PostConstruct
    void init() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream is = new ClassPathResource("schema/extraction.schema.json").getInputStream()) {
            this.schema = factory.getSchema(is);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load extraction schema", e);
        }
    }

    /** @return list of human-readable schema violations; empty == valid. */
    public List<String> validate(JsonNode node) {
        Set<ValidationMessage> messages = schema.validate(node);
        List<String> out = new ArrayList<>(messages.size());
        for (ValidationMessage m : messages) {
            out.add(m.getMessage());
        }
        return out;
    }
}

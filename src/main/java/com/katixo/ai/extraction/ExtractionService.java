package com.katixo.ai.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.katixo.ai.config.AiProperties;
import com.katixo.ai.llm.LlmClient;
import com.katixo.ai.llm.LlmRequest;
import com.katixo.ai.llm.LlmResponse;
import com.katixo.ai.model.ExceptionType;
import com.katixo.ai.model.ExtractedDocument;
import com.katixo.ai.model.ExtractionException;
import com.katixo.ai.model.ModelPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 4-5 of the pipeline (spec 5.2): route to the text-LLM or VLM path, send the strict prompt,
 * then parse the JSON. On a parse/schema failure it runs exactly ONE repair retry (feeding the
 * error back); if that still fails it returns a structured failure - it never crashes.
 */
@Service
public class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

    private final LlmClient llm;
    private final PromptService prompts;
    private final ExtractionSchemaValidator schemaValidator;
    private final ObjectMapper mapper;
    private final AiProperties props;

    public ExtractionService(LlmClient llm, PromptService prompts, ExtractionSchemaValidator schemaValidator,
                             ObjectMapper mapper, AiProperties props) {
        this.llm = llm;
        this.prompts = prompts;
        this.schemaValidator = schemaValidator;
        this.mapper = mapper;
        this.props = props;
    }

    /**
     * @param ocrText       combined OCR text across pages (empty for a pure VLM run)
     * @param ocrConfidence aggregate OCR confidence used for routing
     * @param base64Images  page images for the VLM path
     * @param docTypeHint   caller-supplied hint (may be null)
     */
    public ExtractionStepResult extract(String ocrText, double ocrConfidence,
                                        List<String> base64Images, String docTypeHint) {
        ModelPath path = ocrConfidence >= props.getOcr().getThreshold() ? ModelPath.TEXT_LLM : ModelPath.VLM;
        log.debug("Routing to {} (ocrConfidence={}, threshold={})", path, ocrConfidence,
                props.getOcr().getThreshold());

        LlmRequest request = path == ModelPath.TEXT_LLM
                ? LlmRequest.text(prompts.buildTextPrompt(ocrText, docTypeHint))
                : LlmRequest.vision(prompts.buildVlmPrompt(docTypeHint), base64Images);

        LlmResponse first = llm.generate(request);
        long latency = first.latencyMs();
        ParseAttempt a1 = parse(first.text());

        if (a1.isValid()) {
            return success(a1.doc(), path, first.text(), first.model(), latency, false);
        }

        // One repair retry, feeding the error back (spec 5.2.5).
        String error = a1.errorSummary();
        log.debug("First parse failed ({}); attempting one repair retry", error);
        LlmResponse second = llm.generate(LlmRequest.text(prompts.buildRepairPrompt(first.text(), error)));
        latency += second.latencyMs();
        ParseAttempt a2 = parse(second.text());

        if (a2.isValid()) {
            return success(a2.doc(), path, second.text(), first.model(), latency, true);
        }

        // Still invalid -> structured failure, never an exception to the caller.
        return failure(a1, a2, path, second.text(), first.model(), latency);
    }

    private ExtractionStepResult success(ExtractedDocument doc, ModelPath path, String raw,
                                         String model, long latency, boolean repaired) {
        return new ExtractionStepResult(doc, path, raw, prompts.version(), model, latency, true, repaired, List.of());
    }

    private ExtractionStepResult failure(ParseAttempt a1, ParseAttempt a2, ModelPath path,
                                         String raw, String model, long latency) {
        ExtractedDocument best = a2.doc() != null ? a2.doc() : a1.doc();
        List<ExtractionException> structural = new ArrayList<>();
        if (best == null) {
            structural.add(ExtractionException.of("$", ExceptionType.JSON_INVALID,
                    "Model output was not valid JSON even after one repair attempt."));
        } else {
            structural.add(ExtractionException.of("$", ExceptionType.SCHEMA_INVALID,
                    "Model output did not match schema after repair: " + truncate(a2.errorSummary(), 300)));
        }
        log.warn("Extraction produced invalid output after repair (path={})", path);
        return new ExtractionStepResult(best, path, raw, prompts.version(), model, latency, false, true, structural);
    }

    private ParseAttempt parse(String raw) {
        String cleaned = stripToJson(raw);
        List<String> errors = new ArrayList<>();
        JsonNode node;
        try {
            node = mapper.readTree(cleaned);
        } catch (JsonProcessingException e) {
            errors.add("Invalid JSON: " + e.getOriginalMessage());
            return new ParseAttempt(null, errors);
        }
        errors.addAll(schemaValidator.validate(node));
        ExtractedDocument doc = null;
        try {
            doc = mapper.treeToValue(node, ExtractedDocument.class);
        } catch (JsonProcessingException e) {
            errors.add("Could not map JSON to model: " + e.getOriginalMessage());
        }
        return new ParseAttempt(doc, errors);
    }

    /** Models occasionally wrap JSON in prose or code fences; keep only the outermost object. */
    static String stripToJson(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceAll("(?s)```[a-zA-Z]*", "").trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** Internal parse result. */
    private record ParseAttempt(ExtractedDocument doc, List<String> errors) {
        boolean isValid() {
            return doc != null && errors.isEmpty();
        }

        String errorSummary() {
            return errors.isEmpty() ? "Output did not match the required schema." : String.join("; ", errors);
        }
    }
}

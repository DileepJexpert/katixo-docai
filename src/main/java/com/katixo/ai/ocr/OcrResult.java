package com.katixo.ai.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * OCR output for a single image/page: the flattened text, the layout blocks (text + bounding box),
 * and an aggregate per-document confidence used to route between the text-LLM and VLM paths.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OcrResult(String text, List<OcrBlock> blocks, double confidence) {

    public OcrResult {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public static OcrResult empty() {
        return new OcrResult("", List.of(), 0.0);
    }
}

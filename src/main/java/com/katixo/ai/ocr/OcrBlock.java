package com.katixo.ai.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One recognised text block with its layout box.
 *
 * @param bbox axis-aligned box as [x1, y1, x2, y2] in pixels of the OCR'd image
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OcrBlock(String text, List<Double> bbox, double confidence) {
}

package com.katixo.ai.ocr;

/** Abstraction over the OCR engine. Milestone 1 ships a PaddleOCR sidecar implementation. */
public interface OcrClient {

    /**
     * Run OCR over a single rendered page image (PNG bytes).
     *
     * @throws com.katixo.ai.support.UpstreamUnavailableException if the engine is unreachable
     */
    OcrResult ocr(byte[] pngImage, String filename);

    /** @return true if the OCR engine is reachable. Never throws. */
    boolean isReachable();
}

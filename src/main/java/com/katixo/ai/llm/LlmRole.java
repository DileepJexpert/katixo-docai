package com.katixo.ai.llm;

/**
 * The role a request plays, NOT a concrete model. The {@link LlmClient} implementation maps each
 * role to a model name from config, so business code never hard-codes a model (spec section 3 & 6).
 */
public enum LlmRole {
    /** Primary path: structure OCR text into JSON. */
    TEXT,
    /** Fallback path: read the image directly when OCR confidence is low. */
    VISION
}

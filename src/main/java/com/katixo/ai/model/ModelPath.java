package com.katixo.ai.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Which inference path actually produced the result (always recorded - spec section 5.2.3). */
public enum ModelPath {
    TEXT_LLM("text-llm"),
    VLM("vlm");

    private final String wire;

    ModelPath(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    /** Parse from the wire form (and tolerate the enum name) so persisted results round-trip. */
    @JsonCreator
    public static ModelPath fromWire(String value) {
        if (value == null) {
            return null;
        }
        for (ModelPath p : values()) {
            if (p.wire.equalsIgnoreCase(value) || p.name().equalsIgnoreCase(value)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown ModelPath: " + value);
    }
}

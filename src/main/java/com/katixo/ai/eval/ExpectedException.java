package com.katixo.ai.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.katixo.ai.model.ExceptionType;

/** A (field, type) pair the golden label says SHOULD be raised. Used for exception precision/recall. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpectedException(String field, ExceptionType type) {

    public String key() {
        return field + "|" + type;
    }
}

package com.katixo.ai.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.katixo.ai.model.DocType;
import com.katixo.ai.model.InvoiceHeader;
import com.katixo.ai.model.LineItem;

import java.util.List;

/**
 * Hand-labelled ground truth for one golden document ({@code <name>.expected.json}).
 *
 * @param expectedExceptions exceptions a correct run SHOULD raise (usually empty for clean invoices)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvalExpected(
        DocType docType,
        InvoiceHeader header,
        List<LineItem> lineItems,
        List<ExpectedException> expectedExceptions
) {
    public EvalExpected {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
        expectedExceptions = expectedExceptions == null ? List.of() : List.copyOf(expectedExceptions);
    }
}

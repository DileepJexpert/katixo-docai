package com.katixo.ai.preprocess;

import java.util.List;

/**
 * Output of pre-processing: one cleaned-up PNG per page, ready for OCR/VLM.
 *
 * @param pagesPng   PNG bytes per page (deskewed, downscaled, grayscale)
 * @param wasPdf     whether the input was a multi-page PDF
 */
public record PreprocessedDocument(List<byte[]> pagesPng, boolean wasPdf) {

    public PreprocessedDocument {
        pagesPng = List.copyOf(pagesPng);
    }

    public int pageCount() {
        return pagesPng.size();
    }
}

package com.katixo.ai.extraction;

import com.katixo.ai.exception.ExceptionService;
import com.katixo.ai.exception.ReviewAssessment;
import com.katixo.ai.model.DocType;
import com.katixo.ai.model.ExtractedDocument;
import com.katixo.ai.model.ExtractionResult;
import com.katixo.ai.model.InvoiceHeader;
import com.katixo.ai.model.LineItem;
import com.katixo.ai.ocr.OcrClient;
import com.katixo.ai.ocr.OcrResult;
import com.katixo.ai.persistence.CallLogger;
import com.katixo.ai.persistence.RecordService;
import com.katixo.ai.preprocess.PreprocessService;
import com.katixo.ai.preprocess.PreprocessedDocument;
import com.katixo.ai.support.Hashing;
import com.katixo.ai.validation.ValidationOutcome;
import com.katixo.ai.validation.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the full extraction pipeline (spec 5.2). The controller holds NO logic so this can
 * move behind a queue later without touching the web layer.
 *
 * <p>preprocess -> OCR -> route(text|vlm) -> extract+parse(+repair) -> validate ->
 * confidence/exceptions -> persist + call-log.
 */
@Service
public class ExtractionPipeline {

    private static final Logger log = LoggerFactory.getLogger(ExtractionPipeline.class);

    private final PreprocessService preprocessService;
    private final OcrClient ocrClient;
    private final ExtractionService extractionService;
    private final ValidationService validationService;
    private final ExceptionService exceptionService;
    private final RecordService recordService;
    private final CallLogger callLogger;

    public ExtractionPipeline(PreprocessService preprocessService, OcrClient ocrClient,
                              ExtractionService extractionService, ValidationService validationService,
                              ExceptionService exceptionService, RecordService recordService,
                              CallLogger callLogger) {
        this.preprocessService = preprocessService;
        this.ocrClient = ocrClient;
        this.extractionService = extractionService;
        this.validationService = validationService;
        this.exceptionService = exceptionService;
        this.recordService = recordService;
        this.callLogger = callLogger;
    }

    public ExtractionResult extract(byte[] fileBytes, String fileName, String contentType, DocType docTypeHint) {
        long start = System.currentTimeMillis();
        String fileHash = Hashing.sha256Hex(fileBytes);

        // 1. Preprocess: PDF/image -> clean PNG page(s)
        PreprocessedDocument pre = preprocessService.preprocess(fileBytes, fileName, contentType);

        // 2. OCR each page, aggregate text + confidence; keep page images for a possible VLM fallback
        StringBuilder text = new StringBuilder();
        double confidenceSum = 0;
        int pageNo = 0;
        List<String> base64Images = new ArrayList<>(pre.pageCount());
        for (byte[] page : pre.pagesPng()) {
            pageNo++;
            OcrResult ocr = ocrClient.ocr(page, "page-" + pageNo + ".png");
            if (text.length() > 0) {
                text.append("\n");
            }
            text.append(ocr.text() == null ? "" : ocr.text());
            confidenceSum += ocr.confidence();
            base64Images.add(Base64.getEncoder().encodeToString(page));
        }
        double ocrConfidence = pre.pageCount() == 0 ? 0.0 : confidenceSum / pre.pageCount();
        String ocrText = text.toString();

        // 3-5. Route + extract + parse (+ one repair retry)
        String hint = docTypeHint == null ? null : docTypeHint.name();
        ExtractionStepResult step = extractionService.extract(ocrText, ocrConfidence, base64Images, hint);
        ExtractedDocument doc = step.document();

        // 6. Deterministic validation + confidence/exceptions
        ValidationOutcome validation = validationService.validate(doc);
        ReviewAssessment assessment = exceptionService.assess(ocrConfidence, step, validation);

        // Assemble the public contract
        DocType docType = resolveDocType(doc, docTypeHint);
        InvoiceHeader header = doc == null ? null : doc.header();
        List<LineItem> lineItems = doc == null ? List.of() : doc.lineItems();
        String id = UUID.randomUUID().toString();
        ExtractionResult result = new ExtractionResult(id, docType, step.modelPath(), header, lineItems,
                assessment.confidence(), assessment.exceptions(), assessment.needsHumanReview());

        long total = System.currentTimeMillis() - start;

        // 7. Persist + log (mandatory)
        recordService.save(result);
        callLogger.log(result, fileHash, fileName, ocrText, ocrConfidence, step, validation, total);

        log.info("Extracted id={} path={} confidence={} review={} exceptions={} latencyMs={}",
                id, step.modelPath().wire(), assessment.confidence(), assessment.needsHumanReview(),
                assessment.exceptions().size(), total);
        return result;
    }

    private DocType resolveDocType(ExtractedDocument doc, DocType hint) {
        if (doc != null && doc.docType() != null) {
            return doc.docType();
        }
        return hint != null ? hint : DocType.INVOICE;
    }
}

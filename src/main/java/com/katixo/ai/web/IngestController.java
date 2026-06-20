package com.katixo.ai.web;

import com.katixo.ai.extraction.ExtractionPipeline;
import com.katixo.ai.model.DocType;
import com.katixo.ai.model.ExtractionResult;
import com.katixo.ai.persistence.RecordService;
import com.katixo.ai.support.BadInputException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * REST entry point (spec 5.1). Deliberately thin: ALL logic lives in {@link ExtractionPipeline} so
 * the service can move to a queue later without touching the controller.
 */
@RestController
@RequestMapping("/api/v1")
public class IngestController {

    private final ExtractionPipeline pipeline;
    private final RecordService recordService;

    public IngestController(ExtractionPipeline pipeline, RecordService recordService) {
        this.pipeline = pipeline;
        this.recordService = recordService;
    }

    @PostMapping(value = "/extract", consumes = "multipart/form-data")
    public ExtractionResult extract(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "docType", required = false) String docType) {
        if (file == null || file.isEmpty()) {
            throw new BadInputException("No file uploaded (multipart field 'file' is required).");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadInputException("Could not read uploaded file: " + e.getMessage());
        }
        return pipeline.extract(bytes, file.getOriginalFilename(), file.getContentType(), parseDocType(docType));
    }

    @GetMapping("/extract/{id}")
    public ResponseEntity<ExtractionResult> get(@PathVariable String id) {
        return recordService.find(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    private DocType parseDocType(String docType) {
        if (docType == null || docType.isBlank()) {
            return null;
        }
        try {
            return DocType.valueOf(docType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadInputException("Invalid docType '" + docType + "'. Use one of INVOICE, GRN, BILL.");
        }
    }
}

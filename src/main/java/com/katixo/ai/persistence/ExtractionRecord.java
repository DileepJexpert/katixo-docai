package com.katixo.ai.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persisted extraction result (spec 5.2.7). The full {@link com.katixo.ai.model.ExtractionResult}
 * is stored as JSON in {@code resultJson}; key fields are denormalised into columns so the review
 * queue ({@code needsHumanReview=true}) is queryable without parsing JSON.
 */
@Entity
@Table(name = "extraction_record", indexes = {
        @Index(name = "idx_record_review", columnList = "needsHumanReview"),
        @Index(name = "idx_record_created", columnList = "createdAt")
})
public class ExtractionRecord {

    @Id
    private String id;

    @Column(length = 16)
    private String docType;

    @Column(length = 16)
    private String modelPath;

    private double confidence;

    private boolean needsHumanReview;

    @Column(columnDefinition = "text")
    private String resultJson;

    private Instant createdAt;

    protected ExtractionRecord() {
    }

    public ExtractionRecord(String id, String docType, String modelPath, double confidence,
                            boolean needsHumanReview, String resultJson, Instant createdAt) {
        this.id = id;
        this.docType = docType;
        this.modelPath = modelPath;
        this.confidence = confidence;
        this.needsHumanReview = needsHumanReview;
        this.resultJson = resultJson;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getDocType() { return docType; }
    public String getModelPath() { return modelPath; }
    public double getConfidence() { return confidence; }
    public boolean isNeedsHumanReview() { return needsHumanReview; }
    public String getResultJson() { return resultJson; }
    public Instant getCreatedAt() { return createdAt; }
}

package com.katixo.ai.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Full input/output capture for EVERY call (spec 5.2.7) - the dataset that later powers evals and
 * (much later, only if justified) fine-tuning. Nothing here ever leaves the host.
 */
@Entity
@Table(name = "call_log", indexes = {
        @Index(name = "idx_calllog_record", columnList = "recordId"),
        @Index(name = "idx_calllog_hash", columnList = "fileHash")
})
public class CallLog {

    @Id
    private String id;

    private String recordId;

    @Column(length = 64)
    private String fileHash;

    private String fileName;

    @Column(length = 16)
    private String modelPath;

    private String modelName;

    @Column(length = 32)
    private String promptVersion;

    private double ocrConfidence;

    private long llmLatencyMs;

    private long totalLatencyMs;

    @Column(columnDefinition = "text")
    private String ocrText;

    @Column(columnDefinition = "text")
    private String rawModelOutput;

    @Column(columnDefinition = "text")
    private String parsedResultJson;

    @Column(columnDefinition = "text")
    private String validationOutcome;

    private Instant createdAt;

    protected CallLog() {
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public CallLog(String id, String recordId, String fileHash, String fileName, String modelPath,
                   String modelName, String promptVersion, double ocrConfidence, long llmLatencyMs,
                   long totalLatencyMs, String ocrText, String rawModelOutput, String parsedResultJson,
                   String validationOutcome, Instant createdAt) {
        this.id = id;
        this.recordId = recordId;
        this.fileHash = fileHash;
        this.fileName = fileName;
        this.modelPath = modelPath;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.ocrConfidence = ocrConfidence;
        this.llmLatencyMs = llmLatencyMs;
        this.totalLatencyMs = totalLatencyMs;
        this.ocrText = ocrText;
        this.rawModelOutput = rawModelOutput;
        this.parsedResultJson = parsedResultJson;
        this.validationOutcome = validationOutcome;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getRecordId() { return recordId; }
    public String getFileHash() { return fileHash; }
    public String getFileName() { return fileName; }
    public String getModelPath() { return modelPath; }
    public String getModelName() { return modelName; }
    public String getPromptVersion() { return promptVersion; }
    public double getOcrConfidence() { return ocrConfidence; }
    public long getLlmLatencyMs() { return llmLatencyMs; }
    public long getTotalLatencyMs() { return totalLatencyMs; }
    public String getOcrText() { return ocrText; }
    public String getRawModelOutput() { return rawModelOutput; }
    public String getParsedResultJson() { return parsedResultJson; }
    public String getValidationOutcome() { return validationOutcome; }
    public Instant getCreatedAt() { return createdAt; }
}

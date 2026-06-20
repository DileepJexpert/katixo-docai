package com.katixo.ai.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.katixo.ai.model.ExtractionResult;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persists and retrieves {@link ExtractionResult}s (stored as JSON + denormalised columns). */
@Service
public class RecordService {

    private final ExtractionRecordRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;

    public RecordService(ExtractionRecordRepository repository, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    public void save(ExtractionResult result) {
        ExtractionRecord record = new ExtractionRecord(
                result.id(),
                result.docType() == null ? null : result.docType().name(),
                result.modelPath() == null ? null : result.modelPath().wire(),
                result.confidence(),
                result.needsHumanReview(),
                toJson(result),
                Instant.now(clock));
        repository.save(record);
    }

    public Optional<ExtractionResult> find(String id) {
        return repository.findById(id).map(r -> fromJson(r.getResultJson()));
    }

    public List<ExtractionRecord> reviewQueue() {
        return repository.findByNeedsHumanReviewTrueOrderByCreatedAtDesc();
    }

    private String toJson(ExtractionResult result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize ExtractionResult", e);
        }
    }

    private ExtractionResult fromJson(String json) {
        try {
            return mapper.readValue(json, ExtractionResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not deserialize ExtractionResult", e);
        }
    }
}

package com.katixo.ai.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExtractionRecordRepository extends JpaRepository<ExtractionRecord, String> {

    /** The human-review queue (spec 5.6 - "ExceptionService: confidence + queue"). */
    List<ExtractionRecord> findByNeedsHumanReviewTrueOrderByCreatedAtDesc();
}

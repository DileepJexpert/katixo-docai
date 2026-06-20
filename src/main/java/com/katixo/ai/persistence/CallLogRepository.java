package com.katixo.ai.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CallLogRepository extends JpaRepository<CallLog, String> {
}

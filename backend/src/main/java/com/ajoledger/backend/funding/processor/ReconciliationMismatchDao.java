package com.ajoledger.backend.funding.processor;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReconciliationMismatchDao {

    private final JdbcTemplate jdbcTemplate;

    public void insert(String processor, String eventId, ReconciliationMismatchType mismatchType,
                        BigDecimal expectedAmount, BigDecimal actualAmount) {
        String sql = """
            INSERT INTO reconciliation_mismatch (id, processor, event_id, mismatch_type, expected_amount, actual_amount, detected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql, UUID.randomUUID(), processor, eventId, mismatchType.name(),
                expectedAmount, actualAmount, Timestamp.from(Instant.now()));
    }
}

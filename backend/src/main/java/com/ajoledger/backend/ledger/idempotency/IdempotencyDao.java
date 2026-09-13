package com.ajoledger.backend.ledger.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class IdempotencyDao {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<IdempotencyRecord> ROW_MAPPER = (rs, rowNum) -> new IdempotencyRecord(
            UUID.fromString(rs.getString("id")),
            rs.getString("idempotency_key"),
            rs.getString("request_type"),
            rs.getString("request_hash"),
            IdempotencyStatus.valueOf(rs.getString("status")),
            rs.getString("transaction_id") == null ? null : UUID.fromString(rs.getString("transaction_id")),
            rs.getString("failure_reason"),
            rs.getTimestamp("created_at").toInstant(),
            toInstant(rs.getTimestamp("completed_at"))
    );

    // pgjdbc's typed getObject(col, Instant.class) doesn't support timestamptz, only OffsetDateTime.
    // Reading via getTimestamp().toInstant() sidesteps that, but needs a null guard since
    // completed_at is nullable while created_at never is.
    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    /** Returns empty if this caller now owns the key, safe to proceed. Returns the existing
     * record if someone already claimed it first. */
    public Optional<IdempotencyRecord> claim(UUID id, String idempotencyKey, String requestType, String requestHash) {
        String insertSql = """
            INSERT INTO idempotency_key (id, idempotency_key, request_type, request_hash, status, created_at)
            VALUES (?, ?, ?, ?, 'IN_PROGRESS', ?)
            ON CONFLICT (idempotency_key) DO NOTHING
            """;
        int inserted = jdbcTemplate.update(insertSql, id, idempotencyKey, requestType, requestHash, Timestamp.from(Instant.now()));
        if (inserted == 1) {
            return Optional.empty();
        }
        return findByKey(idempotencyKey);
    }

    public Optional<IdempotencyRecord> findByKey(String idempotencyKey) {
        String sql = "SELECT * FROM idempotency_key WHERE idempotency_key = ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, idempotencyKey).stream().findFirst();
    }

    public void markSucceeded(UUID id, UUID transactionId) {
        String sql = "UPDATE idempotency_key SET status = 'SUCCEEDED', transaction_id = ?, completed_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, transactionId, Timestamp.from(Instant.now()), id);
    }

    public void markFailed(UUID id, String failureReason) {
        String sql = "UPDATE idempotency_key SET status = 'FAILED', failure_reason = ?, completed_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, failureReason, Timestamp.from(Instant.now()), id);
    }
}

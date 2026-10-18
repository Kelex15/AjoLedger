package com.ajoledger.backend.email;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class EmailOutboxDao {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<OutboxEmail> ROW_MAPPER = (rs, rowNum) -> new OutboxEmail(
            UUID.fromString(rs.getString("id")),
            rs.getString("recipient"),
            rs.getString("subject"),
            rs.getString("body"),
            rs.getInt("attempts"),
            toInstant(rs.getTimestamp("expires_at"))
    );

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    public void enqueue(String recipient, String subject, String body, Instant expiresAt) {
        String sql = """
            INSERT INTO email_outbox (id, recipient, subject, body, status, next_attempt_at, expires_at)
            VALUES (?, ?, ?, ?, 'PENDING', ?, ?)
            """;
        jdbcTemplate.update(sql, UUID.randomUUID(), recipient, subject, body, Timestamp.from(Instant.now()),
                toTimestamp(expiresAt));
    }

    public List<OutboxEmail> findDue(Instant now, int limit) {
        String sql = """
            SELECT * FROM email_outbox
            WHERE status = 'PENDING' AND next_attempt_at <= ?
            ORDER BY next_attempt_at
            LIMIT ?
            """;
        return jdbcTemplate.query(sql, ROW_MAPPER, Timestamp.from(now), limit);
    }

    public void markSent(UUID id, int attempts) {
        String sql = "UPDATE email_outbox SET status = 'SENT', attempts = ?, sent_at = ?, last_error = NULL WHERE id = ?";
        jdbcTemplate.update(sql, attempts, Timestamp.from(Instant.now()), id);
    }

    public void scheduleRetry(UUID id, int attempts, Instant nextAttemptAt, String error) {
        String sql = "UPDATE email_outbox SET attempts = ?, next_attempt_at = ?, last_error = ? WHERE id = ?";
        jdbcTemplate.update(sql, attempts, Timestamp.from(nextAttemptAt), error, id);
    }

    public void markFailed(UUID id, int attempts, String error) {
        String sql = "UPDATE email_outbox SET status = 'FAILED', attempts = ?, last_error = ? WHERE id = ?";
        jdbcTemplate.update(sql, attempts, error, id);
    }

    public int deleteFinishedBefore(Instant cutoff) {
        String sql = "DELETE FROM email_outbox WHERE status <> 'PENDING' AND created_at < ?";
        return jdbcTemplate.update(sql, Timestamp.from(cutoff));
    }
}

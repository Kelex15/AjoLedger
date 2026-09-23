package com.ajoledger.backend.funding.stripe;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProcessedWebhookEventDao {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<ProcessedWebhookEvent> ROW_MAPPER = (rs, rowNum) -> new ProcessedWebhookEvent(
            UUID.fromString(rs.getString("id")),
            rs.getString("processor"),
            rs.getString("event_id"),
            rs.getString("raw_payload"),
            rs.getTimestamp("received_at").toInstant(),
            toInstant(rs.getTimestamp("processed_at")),
            rs.getString("ledger_transaction_id") == null ? null : UUID.fromString(rs.getString("ledger_transaction_id"))
    );

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public Optional<ProcessedWebhookEvent> findByProcessorAndEventId(String processor, String eventId) {
        String sql = "SELECT * FROM processed_webhook_event WHERE processor = ? AND event_id = ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, processor, eventId).stream().findFirst();
    }

    /** Returns empty if this caller now owns the (processor, event_id) pair, safe to process.
     * Returns the existing record if a concurrent delivery already claimed it first. */
    public Optional<ProcessedWebhookEvent> recordReceived(UUID id, String processor, String eventId, String rawPayload) {
        String insertSql = """
            INSERT INTO processed_webhook_event (id, processor, event_id, raw_payload, received_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (processor, event_id) DO NOTHING
            """;
        int inserted = jdbcTemplate.update(insertSql, id, processor, eventId, rawPayload, Timestamp.from(Instant.now()));
        if (inserted == 1) {
            return Optional.empty();
        }
        return findByProcessorAndEventId(processor, eventId);
    }

    public List<ProcessedWebhookEvent> findProcessedSince(String processor, Instant since) {
        String sql = "SELECT * FROM processed_webhook_event WHERE processor = ? AND processed_at IS NOT NULL AND received_at >= ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, processor, Timestamp.from(since));
    }

    public void markProcessed(UUID id, UUID ledgerTransactionId) {
        String sql = "UPDATE processed_webhook_event SET processed_at = ?, ledger_transaction_id = ? WHERE id = ?";
        jdbcTemplate.update(sql, Timestamp.from(Instant.now()), ledgerTransactionId, id);
    }
}

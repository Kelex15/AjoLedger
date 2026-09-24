package com.ajoledger.backend.ledger.hashchain;

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
public class LedgerHashChainDao {

    public record Tip(long seq, String hash) {
        static final Tip GENESIS = new Tip(0, LedgerHashChainHasher.GENESIS_HASH);
    }

    public record PendingRow(String sourceTable, UUID sourceId, long sourceSeq, String rowJson) {
    }

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<LedgerHashChainEntry> ROW_MAPPER = (rs, rowNum) -> new LedgerHashChainEntry(
            UUID.fromString(rs.getString("id")),
            rs.getLong("seq"),
            rs.getString("source_table"),
            UUID.fromString(rs.getString("source_id")),
            rs.getLong("source_seq"),
            rs.getString("prev_hash"),
            rs.getString("row_hash"),
            rs.getTimestamp("sealed_at").toInstant());

    public Tip findCurrentTip() {
        String sql = "SELECT seq, row_hash FROM ledger_hash_chain_entry ORDER BY seq DESC LIMIT 1";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Tip(rs.getLong("seq"), rs.getString("row_hash")))
                .stream().findFirst().orElse(Tip.GENESIS);
    }

    /** Every row across the three ledger tables that has no matching entry yet, ordered so the
     * seal is deterministic. A row not yet committed when this runs is invisible to this query
     * (ordinary transaction isolation) and simply shows up on the next call instead. */
    public List<PendingRow> findPendingRows() {
        String sql = """
            SELECT 'ledger_operation' AS source_table, id AS source_id, insert_seq AS source_seq, to_jsonb(o)::text AS row_json
            FROM ledger_operation o
            WHERE NOT EXISTS (SELECT 1 FROM ledger_hash_chain_entry e
                WHERE e.source_table = 'ledger_operation' AND e.source_id = o.id)
            UNION ALL
            SELECT 'transaction', id, insert_seq, to_jsonb(t)::text
            FROM transaction t
            WHERE NOT EXISTS (SELECT 1 FROM ledger_hash_chain_entry e
                WHERE e.source_table = 'transaction' AND e.source_id = t.id)
            UNION ALL
            SELECT 'ledger_entry', id, insert_seq, to_jsonb(le)::text
            FROM ledger_entry le
            WHERE NOT EXISTS (SELECT 1 FROM ledger_hash_chain_entry e
                WHERE e.source_table = 'ledger_entry' AND e.source_id = le.id)
            ORDER BY source_table, source_seq
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new PendingRow(
                rs.getString("source_table"),
                UUID.fromString(rs.getString("source_id")),
                rs.getLong("source_seq"),
                rs.getString("row_json")));
    }

    public void insertEntries(List<LedgerHashChainEntry> entries) {
        String sql = """
            INSERT INTO ledger_hash_chain_entry (id, seq, source_table, source_id, source_seq, prev_hash, row_hash, sealed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.batchUpdate(sql, entries, entries.size(), (ps, entry) -> {
            ps.setObject(1, entry.id());
            ps.setLong(2, entry.seq());
            ps.setString(3, entry.sourceTable());
            ps.setObject(4, entry.sourceId());
            ps.setLong(5, entry.sourceSeq());
            ps.setString(6, entry.prevHash());
            ps.setString(7, entry.rowHash());
            ps.setTimestamp(8, Timestamp.from(entry.sealedAt()));
        });
    }

    public List<LedgerHashChainEntry> findAllOrderedBySeq() {
        String sql = "SELECT * FROM ledger_hash_chain_entry ORDER BY seq";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    /** The source row's live content, hashed the same way it was at seal time. Empty if the row
     * itself is gone, which append-only triggers should never allow through normal SQL. */
    public Optional<String> currentRowJson(String sourceTable, UUID sourceId) {
        String table = validatedTableName(sourceTable);
        String sql = "SELECT to_jsonb(t)::text FROM " + table + " t WHERE id = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), sourceId).stream().findFirst();
    }

    private static String validatedTableName(String sourceTable) {
        return switch (sourceTable) {
            case "ledger_operation", "transaction", "ledger_entry" -> sourceTable;
            default -> throw new IllegalArgumentException("Unknown source table: " + sourceTable);
        };
    }
}

package com.ajoledger.backend.ledger;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LedgerTransactionDao {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<LedgerTransaction> ROW_MAPPER = (rs, rowNum) -> new LedgerTransaction(
            UUID.fromString(rs.getString("id")),
            rs.getBigDecimal("amount"),
            rs.getString("description"),
            LedgerTransactionType.valueOf(rs.getString("transaction_type")),
            rs.getString("operation_id") == null ? null : UUID.fromString(rs.getString("operation_id")),
            rs.getTimestamp("created_at").toInstant()
    );

    public void insertTransaction(LedgerTransaction transaction) {
        String sql = """
            INSERT INTO transaction (id, amount, description, transaction_type, operation_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                transaction.id(),
                transaction.amount(),
                transaction.description(),
                transaction.transactionType().name(),
                transaction.operationId(),
                Timestamp.from(transaction.createdAt()));
    }

    public Optional<LedgerTransaction> findById(UUID id) {
        String sql = "SELECT * FROM transaction WHERE id = ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, id).stream().findFirst();
    }
}

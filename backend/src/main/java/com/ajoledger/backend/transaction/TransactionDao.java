package com.ajoledger.backend.transaction;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TransactionDao {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<Transaction> ROW_MAPPER = (rs, rowNum) -> new Transaction(
            UUID.fromString(rs.getString("id")),
            rs.getBigDecimal("amount"),
            rs.getString("description"),
            TransactionType.valueOf(rs.getString("transaction_type")),
            rs.getObject("created_at", LocalDateTime.class)
    );

    public void insertTransaction(Transaction transaction) {
        String sql = """
            INSERT INTO transaction (id, amount, description, transaction_type, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                transaction.id(),
                transaction.amount(),
                transaction.description(),
                transaction.transactionType().name(),
                transaction.createdAt());
    }

    public Optional<Transaction> findById(UUID id) {
        String sql = "SELECT * FROM transaction WHERE id = ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, id).stream().findFirst();
    }
}

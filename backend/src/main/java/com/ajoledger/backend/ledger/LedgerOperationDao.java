package com.ajoledger.backend.ledger;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
@RequiredArgsConstructor
public class LedgerOperationDao {

    private final JdbcTemplate jdbcTemplate;

    public void insertOperation(LedgerOperation operation) {
        String sql = """
            INSERT INTO ledger_operation (id, operation_type, reference_id, created_at)
            VALUES (?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                operation.id(),
                operation.operationType(),
                operation.referenceId(),
                Timestamp.from(operation.createdAt()));
    }
}

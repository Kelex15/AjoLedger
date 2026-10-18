package com.ajoledger.backend.ledger;

import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.account.UserAccountType;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class LedgerTransactionDao {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<LedgerTransaction> ROW_MAPPER = (rs, rowNum) -> new LedgerTransaction(
            UUID.fromString(rs.getString("id")),
            rs.getBigDecimal("amount"),
            Currency.valueOf(rs.getString("currency")),
            rs.getString("description"),
            LedgerTransactionType.valueOf(rs.getString("transaction_type")),
            rs.getString("operation_id") == null ? null : UUID.fromString(rs.getString("operation_id")),
            rs.getTimestamp("created_at").toInstant()
    );

    public void insertTransaction(LedgerTransaction transaction) {
        String sql = """
            INSERT INTO transaction (id, amount, currency, description, transaction_type, operation_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                transaction.id(),
                transaction.amount(),
                transaction.currency().name(),
                transaction.description(),
                transaction.transactionType().name(),
                transaction.operationId(),
                Timestamp.from(transaction.createdAt()));
    }

    public Optional<LedgerTransaction> findById(UUID id) {
        String sql = "SELECT * FROM transaction WHERE id = ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, id).stream().findFirst();
    }

    /** The lien skim is part of what the recipient got, so it's counted alongside the PAYOUT leg.
     * The buffer fee is the only part that left them. */
    public Map<UUID, PayoutSplit> findPayoutSplits(List<UUID> operationIds) {
        List<UUID> distinctIds = operationIds.stream().distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = distinctIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = """
            SELECT operation_id,
                   coalesce(sum(amount) FILTER (WHERE transaction_type = 'PAYOUT'), 0) AS savings,
                   coalesce(sum(amount) FILTER (WHERE transaction_type = 'PAYOUT_LIEN_SKIM'), 0) AS reserve
            FROM transaction
            WHERE transaction_type IN ('PAYOUT', 'PAYOUT_LIEN_SKIM') AND operation_id IN (%s)
            GROUP BY operation_id
            """.formatted(placeholders);

        Map<UUID, PayoutSplit> splitsByOperationId = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            splitsByOperationId.put(UUID.fromString(rs.getString("operation_id")),
                    new PayoutSplit(rs.getBigDecimal("savings"), rs.getBigDecimal("reserve")));
        }, distinctIds.toArray());
        return splitsByOperationId;
    }

    private static final RowMapper<LedgerActivity> ACTIVITY_ROW_MAPPER = (rs, rowNum) -> new LedgerActivity(
            UUID.fromString(rs.getString("entry_id")),
            UUID.fromString(rs.getString("transaction_id")),
            UserAccountType.valueOf(rs.getString("user_account_type")),
            rs.getString("account_number"),
            rs.getBigDecimal("amount"),
            Currency.valueOf(rs.getString("currency")),
            rs.getString("description"),
            LedgerTransactionType.valueOf(rs.getString("transaction_type")),
            rs.getString("circle_name"),
            (Integer) rs.getObject("round_number"),
            rs.getTimestamp("created_at").toInstant()
    );

    private static final String ACTIVITY_SELECT = """
        SELECT le.id AS entry_id,
               le.transaction_id,
               ua.user_account_type,
               a.account_number,
               le.credit_amount - le.debit_amount AS amount,
               t.currency,
               t.description,
               t.transaction_type,
               circle_source.name AS circle_name,
               cp.round_number,
               le.created_at
        FROM ledger_entry le
        JOIN user_account ua ON ua.id = le.account_id
        JOIN account a ON a.id = le.account_id
        JOIN transaction t ON t.id = le.transaction_id
        LEFT JOIN LATERAL (
            SELECT c.name
            FROM ledger_entry other
            JOIN circle_account ca ON ca.id = other.account_id
            JOIN circle c ON c.id = ca.circle_id
            WHERE other.transaction_id = t.id
            LIMIT 1
        ) circle_source ON true
        LEFT JOIN circle_payout cp ON cp.operation_id = t.operation_id
        WHERE ua.user_id = ?
          AND ua.user_account_type IN ('CHECKING', 'SAVINGS', 'LIEN')
        """;

    /** Most recent first, optionally narrowed to one of the user's accounts. Pass the last row of the
     * previous page as the cursor to get the next one; the entry id breaks ties so entries sharing a
     * timestamp are never skipped or repeated. */
    public List<LedgerActivity> findActivityForUser(UUID userId, String accountNumber,
                                                      Instant beforeCreatedAt, UUID beforeEntryId, int limit) {
        StringBuilder sql = new StringBuilder(ACTIVITY_SELECT);
        List<Object> args = new ArrayList<>();
        args.add(userId);

        if (accountNumber != null) {
            sql.append("  AND a.account_number = ?\n");
            args.add(accountNumber);
        }
        if (beforeCreatedAt != null) {
            sql.append("  AND (le.created_at, le.id) < (?, ?)\n");
            args.add(Timestamp.from(beforeCreatedAt));
            args.add(beforeEntryId);
        }
        sql.append("ORDER BY le.created_at DESC, le.id DESC LIMIT ?");
        args.add(limit);

        return jdbcTemplate.query(sql.toString(), ACTIVITY_ROW_MAPPER, args.toArray());
    }
}

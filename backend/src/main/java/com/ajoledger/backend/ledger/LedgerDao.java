package com.ajoledger.backend.ledger;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class LedgerDao {

    private final JdbcTemplate jdbcTemplate;

    /** An account's locked balance, plus whether it's exempt from balance-sufficiency checks.
     * "Unlimited" accounts are clearing accounts, matched by naming convention (CLEARING_%) so a
     * future funding source needs no code change here. They're the platform's external boundary
     * and are expected to run negative, unlike every user-facing account type. */
    public record AccountBalance(BigDecimal balance, boolean unlimited) {
    }

    /**
     * Locks every given account row (SELECT ... FOR UPDATE) in a deterministic order
     * (sorted by id) to avoid deadlocks when concurrent writes touch overlapping account sets.
     * Must be called from inside the caller's existing transaction (TransactionTemplate callback).
     */
    public Map<UUID, AccountBalance> lockAccounts(List<UUID> accountIds) {
        List<UUID> sortedIds = accountIds.stream().distinct().sorted().toList();
        if (sortedIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = sortedIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = """
            SELECT a.id, a.balance, COALESCE(ua.user_account_type LIKE 'CLEARING_%', false) AS unlimited
            FROM account a
            LEFT JOIN user_account ua ON ua.id = a.id
            WHERE a.id IN (""" + placeholders + """
            )
            ORDER BY a.id FOR UPDATE OF a
            """;

        Map<UUID, AccountBalance> balancesById = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            balancesById.put(UUID.fromString(rs.getString("id")),
                    new AccountBalance(rs.getBigDecimal("balance"), rs.getBoolean("unlimited")));
        }, sortedIds.toArray());
        return balancesById;
    }

    public void insertEntries(List<LedgerEntry> entries) {
        String sql = """
            INSERT INTO ledger_entry (id, account_id, debit_amount, credit_amount, transaction_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.batchUpdate(sql, entries, entries.size(), (ps, entry) -> {
            ps.setObject(1, entry.id());
            ps.setObject(2, entry.accountId());
            ps.setBigDecimal(3, entry.debitAmount());
            ps.setBigDecimal(4, entry.creditAmount());
            ps.setObject(5, entry.transactionId());
            ps.setObject(6, entry.createdAt());
        });
    }

    /** The account's true balance, aggregated from ledger_entry directly. Independent of, and
     * useful for verifying against, the cached Account.balance column. */
    public BigDecimal getBalance(UUID accountId) {
        String sql = "SELECT COALESCE(SUM(credit_amount - debit_amount), 0) FROM ledger_entry WHERE account_id = ?";
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, accountId);
    }

    public boolean transactionInvolvesUser(UUID transactionId, UUID userId) {
        String sql = """
            SELECT EXISTS (
                SELECT 1 FROM ledger_entry le
                JOIN user_account ua ON ua.id = le.account_id
                WHERE le.transaction_id = ? AND ua.user_id = ?
            )
            """;
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, transactionId, userId));
    }

    /**
     * Applies net balance changes (positive = credit, negative = debit) to each account.
     * Accounts must already be locked via {@link #lockAccounts}.
     */
    public void adjustBalances(Map<UUID, BigDecimal> netChangesByAccountId) {
        String sql = "UPDATE account SET balance = balance + ?, updated_at = ? WHERE id = ?";
        LocalDateTime now = LocalDateTime.now();
        List<Map.Entry<UUID, BigDecimal>> entries = List.copyOf(netChangesByAccountId.entrySet());
        jdbcTemplate.batchUpdate(sql, entries, entries.size(), (ps, entry) -> {
            ps.setBigDecimal(1, entry.getValue());
            ps.setObject(2, now);
            ps.setObject(3, entry.getKey());
        });
    }
}

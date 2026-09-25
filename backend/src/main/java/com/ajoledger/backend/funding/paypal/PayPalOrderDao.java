package com.ajoledger.backend.funding.paypal;

import com.ajoledger.backend.account.Currency;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PayPalOrderDao {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<PayPalOrder> ROW_MAPPER = (rs, rowNum) -> new PayPalOrder(
            UUID.fromString(rs.getString("id")),
            rs.getString("order_id"),
            UUID.fromString(rs.getString("user_id")),
            rs.getString("idempotency_key"),
            rs.getBigDecimal("amount"),
            Currency.valueOf(rs.getString("currency")),
            rs.getTimestamp("created_at").toInstant()
    );

    public void insert(UUID id, String orderId, UUID userId, String idempotencyKey, BigDecimal amount, Currency currency) {
        String sql = """
            INSERT INTO paypal_order (id, order_id, user_id, idempotency_key, amount, currency, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql, id, orderId, userId, idempotencyKey, amount, currency.name(), Timestamp.from(Instant.now()));
    }

    public Optional<PayPalOrder> findByOrderId(String orderId) {
        String sql = "SELECT * FROM paypal_order WHERE order_id = ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, orderId).stream().findFirst();
    }

    public List<PayPalOrder> findCreatedSince(Instant since) {
        String sql = "SELECT * FROM paypal_order WHERE created_at >= ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, Timestamp.from(since));
    }
}

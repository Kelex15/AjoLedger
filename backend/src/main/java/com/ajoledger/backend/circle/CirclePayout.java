package com.ajoledger.backend.circle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

import com.ajoledger.backend.user.User;

@Entity
@Table(name = "circle_payout", uniqueConstraints = @UniqueConstraint(columnNames = {"circle_id", "round_number"}))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CirclePayout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "circle_id", nullable = false, updatable = false)
    private Circle circle;

    @ManyToOne
    @JoinColumn(name = "recipient_user_id", nullable = false, updatable = false)
    private User recipient;

    @Column(name = "round_number", nullable = false, updatable = false)
    private int roundNumber;

    @Column(name = "operation_id")
    private UUID operationId; // null until paid. Not a JPA relation, LedgerOperation isn't a JPA entity.

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    @PrePersist
    protected void onCreate(){
        this.paidAt = Instant.now();
    }
}

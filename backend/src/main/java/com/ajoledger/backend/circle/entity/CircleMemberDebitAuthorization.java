package com.ajoledger.backend.circle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Max amount still seizable from this member's own accounts, tracked separately from the live
 * group-loss formulas so any drift between the two can only under-seize, never over-seize.
 *
 * No @Version: a member holds only one ACTIVE membership at a time, so nothing writes this
 * concurrently today. Add one first if that ever changes. */
@Entity
@Table(name = "circle_member_debit_authorization")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CircleMemberDebitAuthorization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "circle_membership_id", nullable = false, updatable = false, unique = true)
    private CircleMembership circleMembership;

    @Column(name = "remaining_ceiling", nullable = false)
    private BigDecimal remainingCeiling;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

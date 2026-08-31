package com.ajoledger.backend.circle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.ajoledger.backend.circle.policy.CircleSizingPolicyType;
import com.ajoledger.backend.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "circle")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Circle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank(message = "Name is required")
    @Column(nullable = false, length = 25)
    private String name;

    @NotBlank(message = "Description is required")
    @Column(nullable = false)
    private String description;

    @NotNull(message = "Max members is required")
    @Column(name = "max_members", nullable = false, updatable = false)
    private Integer maxMembers;

    @NotNull(message = "Term is required")
    @Column(nullable = false)
    private Integer term; // in weeks

    @ManyToOne
    @JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "contribution_amount", nullable = false, updatable = false)
    private BigDecimal contributionAmount;

    @Column(name = "current_round", nullable = false)
    private int currentRound;

    /** When the last round completed (payout resolved); anchors the next round's due date (lastRoundAt + term). */
    @Column(name = "last_round_at")
    private LocalDateTime lastRoundAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CircleStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "sizing_policy_type", nullable = false, updatable = false)
    private CircleSizingPolicyType sizingPolicyType;

    /** Membership requires a redeemed invite (see CircleInvite) instead of being open to anyone. */
    @Column(nullable = false, updatable = false)
    private boolean gated;

    @JsonIgnore
    @OneToMany(mappedBy = "circle")
    private List<CircleMembership> memberships;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate(){
        this.updatedAt = LocalDateTime.now();
    }
}

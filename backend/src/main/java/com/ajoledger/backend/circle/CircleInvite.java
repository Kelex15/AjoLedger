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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ajoledger.backend.user.User;

@Entity
@Table(name = "circle_invite")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CircleInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "circle_id", nullable = false, updatable = false)
    private Circle circle;

    @ManyToOne
    @JoinColumn(name = "invited_by_user_id", nullable = false, updatable = false)
    private User invitedBy;

    @Column(nullable = false, unique = true, updatable = false, length = 64)
    private String code;

    /** Null until an email invite is redeemed by whoever signs up with that email. Set immediately for a username invite. */
    @ManyToOne
    @JoinColumn(name = "invited_user_id")
    private User invitedUser;

    @Column(name = "invited_email", length = 100)
    private String invitedEmail;

    /** Null until redeemed. A code is redeemable exactly once. */
    @Column(name = "redeemed_at")
    private LocalDateTime redeemedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
    }
}

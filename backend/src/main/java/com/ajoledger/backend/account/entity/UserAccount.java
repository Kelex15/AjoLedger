package com.ajoledger.backend.account.entity;

import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "user_account", uniqueConstraints = {
    @UniqueConstraint(name = "uq_user_account_type", columnNames = {"user_id", "user_account_type"})
})
@DiscriminatorValue("USER")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class UserAccount extends Account {
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", updatable = false, nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_account_type", nullable = false, updatable = false)
    private UserAccountType userAccountType;
}

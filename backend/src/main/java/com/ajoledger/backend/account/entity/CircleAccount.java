package com.ajoledger.backend.account.entity;

import com.ajoledger.backend.account.CircleAccountType;
import com.ajoledger.backend.circle.Circle;

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
@Table(name = "circle_account", uniqueConstraints = {
    @UniqueConstraint(name = "uq_circle_account", columnNames = {"circle_id", "circle_account_type"})
})
@DiscriminatorValue("CIRCLE")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class CircleAccount extends Account {

    @ManyToOne(optional = false)
    @JoinColumn(name = "circle_id", updatable = false, nullable = false)
    private Circle circle;

    @Enumerated(EnumType.STRING)
    @Column(name = "circle_account_type", nullable = false, updatable = false)
    private CircleAccountType circleAccountType;
}

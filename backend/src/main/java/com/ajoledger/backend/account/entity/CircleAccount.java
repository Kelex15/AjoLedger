package com.ajoledger.backend.account.entity;

import com.ajoledger.backend.circle.Circle;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
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
    @UniqueConstraint(name = "uq_circle_account", columnNames = {"circle_id"})
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
}

package com.ajoledger.backend.account.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ajoledger.backend.account.entity.Account;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    // Additional query methods can be defined here if needed
    
}

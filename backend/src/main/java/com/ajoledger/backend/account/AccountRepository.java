package com.ajoledger.backend.account;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    // Additional query methods can be defined here if needed
    
}

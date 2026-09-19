package com.ajoledger.backend.account;

import com.ajoledger.backend.account.dto.AccountResponseDTO;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping(path = "/me", version = "1")
    public ResponseEntity<List<AccountResponseDTO>> getCurrentUserAccounts(@AuthenticationPrincipal UUID currentUserId) {
        List<AccountResponseDTO> accounts = accountService.getUserAccounts(currentUserId).stream()
                .map(AccountResponseDTO::fromAccount)
                .toList();
        return ResponseEntity.ok(accounts);
    }
}

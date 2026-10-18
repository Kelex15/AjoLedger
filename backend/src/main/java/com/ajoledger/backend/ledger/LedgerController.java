package com.ajoledger.backend.ledger;

import com.ajoledger.backend.account.AccountService;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.account.entity.UserAccount;
import com.ajoledger.backend.account.exception.AccountNotFoundException;
import com.ajoledger.backend.circle.CircleTransactionsService;
import com.ajoledger.backend.circle.dto.LienAmountRequestDTO;
import com.ajoledger.backend.circle.dto.LienStatusResponseDTO;
import com.ajoledger.backend.ledger.dto.LedgerActivityPageResponseDTO;
import com.ajoledger.backend.ledger.dto.LedgerActivityResponseDTO;
import com.ajoledger.backend.ledger.dto.LedgerTransactionResponseDTO;
import com.ajoledger.backend.ledger.dto.TransferRequestDTO;
import com.ajoledger.backend.ledger.exception.MalformedCursorException;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;
    private final CircleTransactionsService circleTransactionsService;
    private final AccountService accountService;

    private static final int DEFAULT_ACTIVITY_LIMIT = 25;
    private static final int MAX_ACTIVITY_LIMIT = 100;

    @GetMapping(path = "/transactions", version = "1")
    public ResponseEntity<LedgerActivityPageResponseDTO> listActivity(@AuthenticationPrincipal UUID currentUserId,
                                                                        @RequestParam(required = false) String accountNumber,
                                                                        @RequestParam(required = false) String cursor,
                                                                        @RequestParam(required = false) Integer limit) {
        int pageSize = Math.clamp(limit == null ? DEFAULT_ACTIVITY_LIMIT : limit, 1, MAX_ACTIVITY_LIMIT);
        Cursor from = Cursor.parse(cursor);

        // One extra row tells us whether a further page exists without a second count query.
        List<LedgerActivity> rows = ledgerService.findActivityForUser(
                currentUserId, accountNumber, from.createdAt(), from.entryId(), pageSize + 1);

        boolean hasMore = rows.size() > pageSize;
        List<LedgerActivity> page = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore ? Cursor.of(page.getLast()) : null;

        return ResponseEntity.ok(new LedgerActivityPageResponseDTO(
                page.stream().map(LedgerActivityResponseDTO::fromActivity).toList(), nextCursor));
    }

    private record Cursor(Instant createdAt, UUID entryId) {

        private static final Cursor NONE = new Cursor(null, null);

        static Cursor parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return NONE;
            }
            int separator = raw.indexOf('_');
            try {
                return new Cursor(Instant.parse(raw.substring(0, separator)),
                        UUID.fromString(raw.substring(separator + 1)));
            } catch (IndexOutOfBoundsException | DateTimeParseException | IllegalArgumentException e) {
                throw new MalformedCursorException("Malformed cursor");
            }
        }

        static String of(LedgerActivity activity) {
            return activity.createdAt() + "_" + activity.entryId();
        }
    }

    @GetMapping(path = "/transactions/{id}", version = "1")
    public ResponseEntity<LedgerTransactionResponseDTO> getTransaction(@AuthenticationPrincipal UUID currentUserId,
                                                                         @PathVariable UUID id) {
        return ledgerService.findTransactionForUser(id, currentUserId)
                .map(transaction -> ResponseEntity.ok(
                        LedgerTransactionResponseDTO.fromTransaction(transaction, ledgerService.getLegs(transaction.id()))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(path = "/transfer", version = "1")
    public ResponseEntity<LedgerTransactionResponseDTO> transfer(@AuthenticationPrincipal UUID currentUserId,
                                                                   @Valid @RequestBody TransferRequestDTO dto) {
        LedgerTransaction transaction = ledgerService.postTransferToUser(
                currentUserId, dto.fromType(), dto.toUsername(), dto.toType(),
                dto.amount(), dto.idempotencyKey());
        List<LedgerEntry> legs = ledgerService.getLegs(transaction.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(LedgerTransactionResponseDTO.fromTransaction(transaction, legs));
    }

    @GetMapping(path = "/lien", version = "1")
    public ResponseEntity<LienStatusResponseDTO> lienStatus(@AuthenticationPrincipal UUID currentUserId) {
        UserAccount lien = accountService.getUserAccount(currentUserId, UserAccountType.LIEN)
                .orElseThrow(() -> new AccountNotFoundException("No LIEN account for user: " + currentUserId));
        BigDecimal floor = circleTransactionsService.computeLienWithdrawalFloor(currentUserId);
        return ResponseEntity.ok(LienStatusResponseDTO.of(lien, floor));
    }

    @PostMapping(path = "/lien/hold", version = "1")
    public ResponseEntity<LedgerTransactionResponseDTO> lienHold(@AuthenticationPrincipal UUID currentUserId,
                                                                   @Valid @RequestBody LienAmountRequestDTO dto) {
        LedgerTransaction transaction = circleTransactionsService.postLienHold(currentUserId, dto.amount(), dto.idempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(LedgerTransactionResponseDTO.fromTransaction(transaction, ledgerService.getLegs(transaction.id())));
    }

    @PostMapping(path = "/lien/withdraw", version = "1")
    public ResponseEntity<LedgerTransactionResponseDTO> lienWithdraw(@AuthenticationPrincipal UUID currentUserId,
                                                                       @Valid @RequestBody LienAmountRequestDTO dto) {
        LedgerTransaction transaction = circleTransactionsService.postLienWithdrawal(currentUserId, dto.amount(), dto.idempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(LedgerTransactionResponseDTO.fromTransaction(transaction, ledgerService.getLegs(transaction.id())));
    }
}

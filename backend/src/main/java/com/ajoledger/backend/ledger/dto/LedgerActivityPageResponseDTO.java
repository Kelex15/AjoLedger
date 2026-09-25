package com.ajoledger.backend.ledger.dto;

import java.util.List;

/** nextCursor is null on the last page. It's opaque to clients: pass it back verbatim. */
public record LedgerActivityPageResponseDTO(
    List<LedgerActivityResponseDTO> items,
    String nextCursor
) {

}

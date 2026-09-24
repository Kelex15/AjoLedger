package com.ajoledger.backend.ledger.hashchain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** The one place this hash is computed, so sealing and verification can never drift apart. */
final class LedgerHashChainHasher {

    static final String GENESIS_HASH = "0".repeat(64);

    private LedgerHashChainHasher() {
    }

    static String compute(String sourceTable, long seq, String prevHash, String rowJson) {
        String canonical = sourceTable + "|" + seq + "|" + prevHash + "|" + rowJson;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

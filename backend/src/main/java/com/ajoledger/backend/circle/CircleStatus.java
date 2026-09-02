package com.ajoledger.backend.circle;

public enum CircleStatus {
    FORMING,
    ACTIVE,
    COMPLETED,
    DISSOLVED // never activated. Failed to reach minimum members, or the creator cancelled it.
}

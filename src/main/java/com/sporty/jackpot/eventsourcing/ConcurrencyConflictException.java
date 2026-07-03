package com.sporty.jackpot.eventsourcing;

import java.util.UUID;

public class ConcurrencyConflictException extends RuntimeException {

    public ConcurrencyConflictException(UUID aggregateId, int version) {
        super("Concurrency conflict for aggregate " + aggregateId + " at version " + version);
    }
}

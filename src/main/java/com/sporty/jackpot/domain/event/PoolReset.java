package com.sporty.jackpot.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PoolReset(
        UUID aggregateId,
        BigDecimal resetAmount,
        Instant occurredAt
) implements DomainEvent {
}

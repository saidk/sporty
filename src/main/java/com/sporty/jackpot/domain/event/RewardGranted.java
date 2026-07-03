package com.sporty.jackpot.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RewardGranted(
        UUID aggregateId,
        UUID betId,
        UUID userId,
        BigDecimal rewardAmount,
        Instant occurredAt
) implements DomainEvent {
}

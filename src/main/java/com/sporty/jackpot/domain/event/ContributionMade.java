package com.sporty.jackpot.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ContributionMade(
        UUID aggregateId,
        UUID betId,
        UUID userId,
        BigDecimal stakeAmount,
        BigDecimal contributionAmount,
        BigDecimal newPoolAmount,
        Instant occurredAt
) implements DomainEvent {
}

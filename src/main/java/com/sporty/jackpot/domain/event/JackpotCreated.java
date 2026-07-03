package com.sporty.jackpot.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record JackpotCreated(
        UUID aggregateId,
        BigDecimal initialPool,
        String contributionPolicyType,
        String contributionPolicyConfig,
        String rewardPolicyType,
        String rewardPolicyConfig,
        Instant occurredAt
) implements DomainEvent {
}

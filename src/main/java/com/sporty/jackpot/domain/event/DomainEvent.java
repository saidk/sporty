package com.sporty.jackpot.domain.event;

import java.time.Instant;
import java.util.UUID;

public sealed interface DomainEvent permits JackpotCreated, ContributionMade, RewardGranted, PoolReset {

    UUID aggregateId();

    Instant occurredAt();
}

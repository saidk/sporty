package com.sporty.betting;

import java.math.BigDecimal;
import java.util.UUID;

public record BetMessage(UUID betId, UUID userId, UUID jackpotId, BigDecimal amount) {}

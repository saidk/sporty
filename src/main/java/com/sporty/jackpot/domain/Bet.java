package com.sporty.jackpot.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record Bet(UUID betId, UUID userId, UUID jackpotId, BigDecimal amount) {}

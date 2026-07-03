package com.sporty.betting;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record BetRequest(
        @NotNull
        UUID betId,

        @NotNull
        UUID userId,

        @NotNull
        UUID jackpotId,

        @NotNull
        @Positive
        BigDecimal amount
) {
}

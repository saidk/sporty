package com.sporty.jackpot;

import java.math.BigDecimal;
import java.util.UUID;

public record RewardResponse(UUID betId, UUID userId, UUID jackpotId, BigDecimal rewardAmount, boolean won) {

    public static RewardResponse noWin(UUID betId, UUID jackpotId) {
        return new RewardResponse(betId, null, jackpotId, BigDecimal.ZERO, false);
    }

    public static RewardResponse winner(UUID betId, UUID userId, UUID jackpotId, BigDecimal amount) {
        return new RewardResponse(betId, userId, jackpotId, amount, true);
    }
}

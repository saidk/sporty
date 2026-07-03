package com.sporty.jackpot.domain;

import com.sporty.jackpot.domain.reward.VariableRewardPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableRewardPolicyTest {

    @Test
    void isWinner_poolAtOrAboveLimit_alwaysWins() {
        var policy = new VariableRewardPolicy(new BigDecimal("5"), new BigDecimal("1000"));

        for (int i = 0; i < 100; i++) {
            assertTrue(policy.isWinner(new BigDecimal("1000")));
            assertTrue(policy.isWinner(new BigDecimal("2000")));
        }
    }

    @Test
    void isWinner_poolAtZero_usesBaseChance() {
        // With 100% base chance, should always win even at zero pool
        var policy = new VariableRewardPolicy(new BigDecimal("100"), new BigDecimal("5000"));

        for (int i = 0; i < 100; i++) {
            assertTrue(policy.isWinner(BigDecimal.ZERO));
        }
    }
}

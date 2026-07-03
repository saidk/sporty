package com.sporty.jackpot.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FixedContributionPolicyTest {

    @Test
    void calculate_returnsFixedPercentageOfBet() {
        var policy = new FixedContributionPolicy(new BigDecimal("10"));

        BigDecimal result = policy.calculate(new BigDecimal("200"), new BigDecimal("5000"));

        assertEquals(new BigDecimal("20.00"), result);
    }

    @Test
    void calculate_ignoresCurrentPool() {
        var policy = new FixedContributionPolicy(new BigDecimal("15"));

        BigDecimal low = policy.calculate(new BigDecimal("100"), new BigDecimal("10"));
        BigDecimal high = policy.calculate(new BigDecimal("100"), new BigDecimal("99999"));

        assertEquals(low, high);
    }
}

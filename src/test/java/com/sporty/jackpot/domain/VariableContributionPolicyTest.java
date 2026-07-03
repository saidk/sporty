package com.sporty.jackpot.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VariableContributionPolicyTest {

    @Test
    void calculate_atZeroPool_usesFullInitialPercentage() {
        var policy = new VariableContributionPolicy(
                new BigDecimal("20"), new BigDecimal("0.5"), new BigDecimal("1000"));

        BigDecimal result = policy.calculate(new BigDecimal("100"), BigDecimal.ZERO);

        assertEquals(new BigDecimal("20.00"), result);
    }

    @Test
    void calculate_atHalfThreshold_reducesContribution() {
        var policy = new VariableContributionPolicy(
                new BigDecimal("20"), new BigDecimal("0.5"), new BigDecimal("1000"));

        BigDecimal result = policy.calculate(new BigDecimal("100"), new BigDecimal("500"));

        // ratio=0.5, effective=20-(20*0.5*0.5)=15%
        assertEquals(new BigDecimal("15.00"), result);
    }

    @Test
    void calculate_atOrAboveThreshold_returnsMinimum() {
        var policy = new VariableContributionPolicy(
                new BigDecimal("20"), new BigDecimal("1.0"), new BigDecimal("1000"));

        BigDecimal result = policy.calculate(new BigDecimal("100"), new BigDecimal("1000"));

        // Decay would bring it to 0%, but floor is 1% → contribution = 1.00
        assertEquals(new BigDecimal("1.00"), result);
    }

    @Test
    void calculate_contributionNeverGoesBelow1Percent() {
        var policy = new VariableContributionPolicy(
                new BigDecimal("10"), new BigDecimal("2.0"), new BigDecimal("100"));

        BigDecimal result = policy.calculate(new BigDecimal("100"), new BigDecimal("100"));

        // Floor is 1%, so minimum contribution on 100 bet = 1.00
        assertEquals(new BigDecimal("1.00"), result);
    }
}

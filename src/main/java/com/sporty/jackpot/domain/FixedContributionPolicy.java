package com.sporty.jackpot.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record FixedContributionPolicy(BigDecimal percentage) implements ContributionPolicy {

    @Override
    public BigDecimal calculate(BigDecimal betAmount, BigDecimal currentPool) {
        return betAmount.multiply(percentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Override
    public String type() {
        return "FIXED";
    }

    @Override
    public String config() {
        return percentage.toPlainString();
    }

}

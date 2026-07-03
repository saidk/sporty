package com.sporty.jackpot.domain;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

public record FixedRewardPolicy(BigDecimal chancePercentage) implements RewardPolicy {

    @Override
    public boolean isWinner(BigDecimal currentPool) {
        // Roll 0-100; wins if roll falls below the configured chance percentage
        double roll = ThreadLocalRandom.current().nextDouble(100);
        return roll < chancePercentage.doubleValue();
    }

    @Override
    public String type() {
        return "FIXED";
    }

    @Override
    public String config() {
        return chancePercentage.toPlainString();
    }

}

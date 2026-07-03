package com.sporty.jackpot.domain.reward;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

public record VariableRewardPolicy(BigDecimal baseChance, BigDecimal poolLimit) implements RewardPolicy {

    @Override
    public boolean isWinner(BigDecimal currentPool) {
        // Win chance scales linearly from baseChance to 100% as pool approaches poolLimit
        if (currentPool.compareTo(poolLimit) >= 0) {
            return true;
        }

        BigDecimal poolRatio = currentPool.divide(poolLimit, 4, RoundingMode.HALF_UP);
        BigDecimal effectiveChance = baseChance.add(
                BigDecimal.valueOf(100).subtract(baseChance).multiply(poolRatio)
        );

        double roll = ThreadLocalRandom.current().nextDouble(100);
        return roll < effectiveChance.doubleValue();
    }

    @Override
    public String type() {
        return "VARIABLE";
    }

    @Override
    public String config() {
        return baseChance.toPlainString() + "," + poolLimit.toPlainString();
    }

}

package com.sporty.jackpot.domain.contribution;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record VariableContributionPolicy(BigDecimal initialPercentage, BigDecimal decayRate,
                                         BigDecimal poolThreshold) implements ContributionPolicy {

    @Override
    public BigDecimal calculate(BigDecimal betAmount, BigDecimal currentPool) {
        // Contribution starts at initialPercentage and decays as pool grows; floored at 1% minimum
        BigDecimal poolRatio = currentPool.divide(poolThreshold, 4, RoundingMode.HALF_UP);
        if (poolRatio.compareTo(BigDecimal.ONE) >= 0) {
            poolRatio = BigDecimal.ONE;
        }

        BigDecimal effectivePercentage = initialPercentage.subtract(
                initialPercentage.multiply(poolRatio).multiply(decayRate)
        );

        if (effectivePercentage.compareTo(BigDecimal.ONE) < 0) {
            effectivePercentage = BigDecimal.ONE;
        }

        return betAmount.multiply(effectivePercentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Override
    public String type() {
        return "VARIABLE";
    }

    @Override
    public String config() {
        return initialPercentage.toPlainString() + "," +
                decayRate.toPlainString() + "," +
                poolThreshold.toPlainString();
    }

}

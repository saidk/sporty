package com.sporty.jackpot.domain.contribution;

import java.math.BigDecimal;

public interface ContributionPolicy {

    BigDecimal calculate(BigDecimal betAmount, BigDecimal currentPool);

    String type();

    String config();
}

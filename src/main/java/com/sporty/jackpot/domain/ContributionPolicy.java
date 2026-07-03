package com.sporty.jackpot.domain;

import java.math.BigDecimal;

public interface ContributionPolicy {

    BigDecimal calculate(BigDecimal betAmount, BigDecimal currentPool);

    String type();

    String config();
}

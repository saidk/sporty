package com.sporty.jackpot.domain.reward;

import java.math.BigDecimal;

public interface RewardPolicy {

    boolean isWinner(BigDecimal currentPool);

    String type();

    String config();
}

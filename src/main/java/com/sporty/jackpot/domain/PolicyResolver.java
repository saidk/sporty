package com.sporty.jackpot.domain;

import com.sporty.jackpot.domain.contribution.ContributionPolicy;
import com.sporty.jackpot.domain.reward.RewardPolicy;

public interface PolicyResolver {

    ContributionPolicy resolveContribution(String type, String config);

    RewardPolicy resolveReward(String type, String config);
}

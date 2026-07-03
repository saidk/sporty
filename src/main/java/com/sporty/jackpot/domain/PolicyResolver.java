package com.sporty.jackpot.domain;

public interface PolicyResolver {

    ContributionPolicy resolveContribution(String type, String config);

    RewardPolicy resolveReward(String type, String config);
}

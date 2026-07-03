package com.sporty.jackpot.eventsourcing.deserializer;

import com.sporty.jackpot.domain.reward.RewardPolicy;

public interface RewardPolicyDeserializer {

    String supportedType();

    RewardPolicy fromConfig(String config);
}

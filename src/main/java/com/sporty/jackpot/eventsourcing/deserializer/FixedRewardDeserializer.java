package com.sporty.jackpot.eventsourcing.deserializer;

import com.sporty.jackpot.domain.FixedRewardPolicy;
import com.sporty.jackpot.domain.RewardPolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FixedRewardDeserializer implements RewardPolicyDeserializer {

    @Override
    public String supportedType() {
        return "FIXED";
    }

    @Override
    public RewardPolicy fromConfig(String config) {
        return new FixedRewardPolicy(new BigDecimal(config));
    }
}

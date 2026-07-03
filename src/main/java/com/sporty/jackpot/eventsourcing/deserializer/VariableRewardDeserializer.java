package com.sporty.jackpot.eventsourcing.deserializer;

import com.sporty.jackpot.domain.RewardPolicy;
import com.sporty.jackpot.domain.VariableRewardPolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class VariableRewardDeserializer implements RewardPolicyDeserializer {

    @Override
    public String supportedType() {
        return "VARIABLE";
    }

    @Override
    public RewardPolicy fromConfig(String config) {
        String[] parts = config.split(",");
        return new VariableRewardPolicy(
                new BigDecimal(parts[0]),
                new BigDecimal(parts[1])
        );
    }
}

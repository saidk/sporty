package com.sporty.jackpot.eventsourcing.deserializer;

import com.sporty.jackpot.domain.contribution.ContributionPolicy;
import com.sporty.jackpot.domain.contribution.VariableContributionPolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class VariableContributionDeserializer implements ContributionPolicyDeserializer {

    @Override
    public String supportedType() {
        return "VARIABLE";
    }

    @Override
    public ContributionPolicy fromConfig(String config) {
        String[] parts = config.split(",");
        return new VariableContributionPolicy(
                new BigDecimal(parts[0]),
                new BigDecimal(parts[1]),
                new BigDecimal(parts[2])
        );
    }
}

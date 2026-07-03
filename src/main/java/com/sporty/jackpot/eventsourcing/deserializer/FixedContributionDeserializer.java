package com.sporty.jackpot.eventsourcing.deserializer;

import com.sporty.jackpot.domain.ContributionPolicy;
import com.sporty.jackpot.domain.FixedContributionPolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FixedContributionDeserializer implements ContributionPolicyDeserializer {

    @Override
    public String supportedType() {
        return "FIXED";
    }

    @Override
    public ContributionPolicy fromConfig(String config) {
        return new FixedContributionPolicy(new BigDecimal(config));
    }
}

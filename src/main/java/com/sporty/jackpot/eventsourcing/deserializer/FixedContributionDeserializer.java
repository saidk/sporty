package com.sporty.jackpot.eventsourcing.deserializer;

import com.sporty.jackpot.domain.contribution.ContributionPolicy;
import com.sporty.jackpot.domain.contribution.FixedContributionPolicy;
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

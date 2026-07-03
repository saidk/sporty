package com.sporty.jackpot.eventsourcing.deserializer;

import com.sporty.jackpot.domain.ContributionPolicy;

public interface ContributionPolicyDeserializer {

    String supportedType();

    ContributionPolicy fromConfig(String config);
}

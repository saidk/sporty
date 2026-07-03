package com.sporty.jackpot.eventsourcing;

import com.sporty.jackpot.domain.ContributionPolicy;
import com.sporty.jackpot.domain.RewardPolicy;
import com.sporty.jackpot.eventsourcing.deserializer.ContributionPolicyDeserializer;
import com.sporty.jackpot.eventsourcing.deserializer.RewardPolicyDeserializer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PolicyRegistry {

    private final Map<String, ContributionPolicyDeserializer> contributionDeserializers;
    private final Map<String, RewardPolicyDeserializer> rewardDeserializers;

    public PolicyRegistry(List<ContributionPolicyDeserializer> contributionList,
                          List<RewardPolicyDeserializer> rewardList) {
        this.contributionDeserializers = contributionList.stream()
                .collect(Collectors.toMap(ContributionPolicyDeserializer::supportedType, Function.identity()));
        this.rewardDeserializers = rewardList.stream()
                .collect(Collectors.toMap(RewardPolicyDeserializer::supportedType, Function.identity()));
    }

    public ContributionPolicy contributionFrom(String type, String config) {
        ContributionPolicyDeserializer deserializer = contributionDeserializers.get(type);
        if (deserializer == null) {
            throw new IllegalArgumentException("Unknown contribution policy type: " + type);
        }
        return deserializer.fromConfig(config);
    }

    public RewardPolicy rewardFrom(String type, String config) {
        RewardPolicyDeserializer deserializer = rewardDeserializers.get(type);
        if (deserializer == null) {
            throw new IllegalArgumentException("Unknown reward policy type: " + type);
        }
        return deserializer.fromConfig(config);
    }
}

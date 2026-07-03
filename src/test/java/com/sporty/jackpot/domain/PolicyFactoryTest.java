package com.sporty.jackpot.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PolicyFactoryTest {

    @Test
    void fixedContributionPolicy_serializesTypeAndConfig() {
        var policy = new FixedContributionPolicy(new BigDecimal("12.5"));

        assertEquals("FIXED", policy.type());
        assertEquals("12.5", policy.config());
    }

    @Test
    void variableContributionPolicy_serializesTypeAndConfig() {
        var policy = new VariableContributionPolicy(
                new BigDecimal("20"), new BigDecimal("0.5"), new BigDecimal("10000"));

        assertEquals("VARIABLE", policy.type());
        assertEquals("20,0.5,10000", policy.config());
    }

    @Test
    void fixedRewardPolicy_serializesTypeAndConfig() {
        var policy = new FixedRewardPolicy(new BigDecimal("7.5"));

        assertEquals("FIXED", policy.type());
        assertEquals("7.5", policy.config());
    }

    @Test
    void variableRewardPolicy_serializesTypeAndConfig() {
        var policy = new VariableRewardPolicy(new BigDecimal("3"), new BigDecimal("5000"));

        assertEquals("VARIABLE", policy.type());
        assertEquals("3,5000", policy.config());
    }

    @Test
    void roundTrip_contributionPolicyThroughResolver() {
        var original = new FixedContributionPolicy(new BigDecimal("15"));
        PolicyResolver resolver = createTestResolver();

        ContributionPolicy restored = resolver.resolveContribution(original.type(), original.config());

        assertNotNull(restored);
        assertEquals(original.calculate(new BigDecimal("100"), BigDecimal.ZERO),
                restored.calculate(new BigDecimal("100"), BigDecimal.ZERO));
    }

    @Test
    void roundTrip_rewardPolicyThroughResolver() {
        var original = new VariableRewardPolicy(new BigDecimal("5"), new BigDecimal("1000"));
        PolicyResolver resolver = createTestResolver();

        RewardPolicy restored = resolver.resolveReward(original.type(), original.config());

        assertNotNull(restored);
    }

    private PolicyResolver createTestResolver() {
        return new PolicyResolver() {
            @Override
            public ContributionPolicy resolveContribution(String type, String config) {
                String[] parts = config.split(",");
                return switch (type) {
                    case "FIXED" -> new FixedContributionPolicy(new BigDecimal(parts[0]));
                    case "VARIABLE" -> new VariableContributionPolicy(
                            new BigDecimal(parts[0]), new BigDecimal(parts[1]), new BigDecimal(parts[2]));
                    default -> throw new IllegalArgumentException("Unknown type: " + type);
                };
            }

            @Override
            public RewardPolicy resolveReward(String type, String config) {
                String[] parts = config.split(",");
                return switch (type) {
                    case "FIXED" -> new FixedRewardPolicy(new BigDecimal(parts[0]));
                    case "VARIABLE" -> new VariableRewardPolicy(new BigDecimal(parts[0]), new BigDecimal(parts[1]));
                    default -> throw new IllegalArgumentException("Unknown type: " + type);
                };
            }
        };
    }
}

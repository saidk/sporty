package com.sporty.jackpot.domain;

import com.sporty.jackpot.domain.contribution.ContributionPolicy;
import com.sporty.jackpot.domain.contribution.FixedContributionPolicy;
import com.sporty.jackpot.domain.contribution.VariableContributionPolicy;
import com.sporty.jackpot.domain.event.ContributionMade;
import com.sporty.jackpot.domain.event.DomainEvent;
import com.sporty.jackpot.domain.event.JackpotCreated;
import com.sporty.jackpot.domain.event.RewardGranted;
import com.sporty.jackpot.domain.reward.FixedRewardPolicy;
import com.sporty.jackpot.domain.reward.RewardPolicy;
import com.sporty.jackpot.domain.reward.VariableRewardPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JackpotTest {

    private static final UUID JACKPOT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private static final PolicyResolver TEST_RESOLVER = new PolicyResolver() {
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

    @Test
    void createJackpot_raisesJackpotCreatedEvent() {
        Jackpot jackpot = Jackpot.create(
                JACKPOT_ID,
                new BigDecimal("100"),
                new FixedContributionPolicy(new BigDecimal("10")),
                new FixedRewardPolicy(new BigDecimal("5"))
        );

        assertEquals(1, jackpot.getUncommittedEvents().size());
        assertInstanceOf(JackpotCreated.class, jackpot.getUncommittedEvents().getFirst());
        assertEquals(new BigDecimal("100"), jackpot.getCurrentPool());
    }

    @Test
    void contribute_fixedPolicy_calculatesCorrectAmount() {
        Jackpot jackpot = createFixedJackpot(new BigDecimal("100"), new BigDecimal("10"));
        jackpot.clearUncommittedEvents();

        Bet bet = new Bet(UUID.randomUUID(), USER_ID, JACKPOT_ID, new BigDecimal("50"));
        Optional<ContributionMade> result = jackpot.contribute(bet);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("5.00"), result.get().contributionAmount());
        assertEquals(new BigDecimal("105.00"), result.get().newPoolAmount());
        assertEquals(new BigDecimal("105.00"), jackpot.getCurrentPool());
    }

    @Test
    void contribute_variablePolicy_decaysAsPoolGrows() {
        Jackpot jackpot = createVariableContributionJackpot(
                new BigDecimal("0"),
                new BigDecimal("20"), new BigDecimal("0.5"), new BigDecimal("1000")
        );
        jackpot.clearUncommittedEvents();

        Bet bet = new Bet(UUID.randomUUID(), USER_ID, JACKPOT_ID, new BigDecimal("100"));
        Optional<ContributionMade> result = jackpot.contribute(bet);

        assertTrue(result.isPresent());
        // pool=0, ratio=0, effective=20%, contribution=20
        assertEquals(new BigDecimal("20.00"), result.get().contributionAmount());
    }

    @Test
    void contribute_variablePolicy_lowerContributionWhenPoolIsHigh() {
        Jackpot jackpot = createVariableContributionJackpot(
                new BigDecimal("500"),
                new BigDecimal("20"), new BigDecimal("0.5"), new BigDecimal("1000")
        );
        jackpot.clearUncommittedEvents();

        Bet bet = new Bet(UUID.randomUUID(), USER_ID, JACKPOT_ID, new BigDecimal("100"));
        Optional<ContributionMade> result = jackpot.contribute(bet);

        assertTrue(result.isPresent());
        // pool=500, ratio=0.5, decay=0.5, effective=20-(20*0.5*0.5)=20-5=15%, contribution=15
        assertEquals(new BigDecimal("15.00"), result.get().contributionAmount());
    }

    @Test
    void contribute_duplicateBet_returnsEmpty() {
        Jackpot jackpot = createFixedJackpot(new BigDecimal("100"), new BigDecimal("10"));
        jackpot.clearUncommittedEvents();

        UUID betId = UUID.randomUUID();
        Bet bet = new Bet(betId, USER_ID, JACKPOT_ID, new BigDecimal("50"));

        jackpot.contribute(bet);
        Optional<ContributionMade> duplicate = jackpot.contribute(bet);

        assertTrue(duplicate.isEmpty());
    }

    @Test
    void evaluateReward_betNotContributed_throws() {
        Jackpot jackpot = createFixedJackpot(new BigDecimal("100"), new BigDecimal("10"));

        Bet bet = new Bet(UUID.randomUUID(), USER_ID, JACKPOT_ID, new BigDecimal("50"));

        assertThrows(IllegalStateException.class, () -> jackpot.evaluateReward(bet));
    }

    @Test
    void evaluateReward_alreadyRewarded_returnsEmpty() {
        Jackpot jackpot = createWithGuaranteedWin(new BigDecimal("100"));
        jackpot.clearUncommittedEvents();

        Bet bet = new Bet(UUID.randomUUID(), USER_ID, JACKPOT_ID, new BigDecimal("50"));
        jackpot.contribute(bet);

        Optional<RewardGranted> first = jackpot.evaluateReward(bet);
        assertTrue(first.isPresent());

        Optional<RewardGranted> second = jackpot.evaluateReward(bet);
        assertTrue(second.isEmpty());
    }

    @Test
    void evaluateReward_win_resetsPoolToInitial() {
        Jackpot jackpot = createWithGuaranteedWin(new BigDecimal("100"));
        jackpot.clearUncommittedEvents();

        Bet bet = new Bet(UUID.randomUUID(), USER_ID, JACKPOT_ID, new BigDecimal("50"));
        jackpot.contribute(bet);

        Optional<RewardGranted> result = jackpot.evaluateReward(bet);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("100"), jackpot.getCurrentPool());
    }

    @Test
    void evaluateReward_win_rewardAmountIsCurrentPool() {
        Jackpot jackpot = createWithGuaranteedWin(new BigDecimal("100"));
        jackpot.clearUncommittedEvents();

        Bet bet = new Bet(UUID.randomUUID(), USER_ID, JACKPOT_ID, new BigDecimal("50"));
        jackpot.contribute(bet);
        // pool is now 105 (100 + 10% of 50)

        Optional<RewardGranted> result = jackpot.evaluateReward(bet);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("105.00"), result.get().rewardAmount());
    }

    @Test
    void rehydrate_rebuildsStateFromEvents() {
        Jackpot original = createFixedJackpot(new BigDecimal("100"), new BigDecimal("10"));
        Bet bet1 = new Bet(UUID.randomUUID(), USER_ID, JACKPOT_ID, new BigDecimal("50"));
        Bet bet2 = new Bet(UUID.randomUUID(), USER_ID, JACKPOT_ID, new BigDecimal("30"));
        original.contribute(bet1);
        original.contribute(bet2);

        List<DomainEvent> history = original.getUncommittedEvents();
        Jackpot rehydrated = Jackpot.rehydrate(history, TEST_RESOLVER);

        assertEquals(original.getCurrentPool(), rehydrated.getCurrentPool());
        assertEquals(original.getVersion(), rehydrated.getVersion());
    }

    @Test
    void rehydrate_duplicateBetIsStillRejected() {
        Jackpot original = createFixedJackpot(new BigDecimal("100"), new BigDecimal("10"));
        UUID betId = UUID.randomUUID();
        Bet bet = new Bet(betId, USER_ID, JACKPOT_ID, new BigDecimal("50"));
        original.contribute(bet);

        Jackpot rehydrated = Jackpot.rehydrate(original.getUncommittedEvents(), TEST_RESOLVER);
        Optional<ContributionMade> duplicate = rehydrated.contribute(bet);

        assertTrue(duplicate.isEmpty());
    }

    private Jackpot createFixedJackpot(BigDecimal initialPool, BigDecimal contributionPct) {
        return Jackpot.create(JACKPOT_ID, initialPool,
                new FixedContributionPolicy(contributionPct),
                new FixedRewardPolicy(new BigDecimal("0"))); // 0% chance — never wins
    }

    private Jackpot createWithGuaranteedWin(BigDecimal initialPool) {
        return Jackpot.create(JACKPOT_ID, initialPool,
                new FixedContributionPolicy(new BigDecimal("10")),
                new FixedRewardPolicy(new BigDecimal("100"))); // 100% chance — always wins
    }

    private Jackpot createVariableContributionJackpot(BigDecimal initialPool,
                                                      BigDecimal initialPct, BigDecimal decayRate, BigDecimal threshold) {
        return Jackpot.create(JACKPOT_ID, initialPool,
                new VariableContributionPolicy(initialPct, decayRate, threshold),
                new FixedRewardPolicy(new BigDecimal("0")));
    }
}

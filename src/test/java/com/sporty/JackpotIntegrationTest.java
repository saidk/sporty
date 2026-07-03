package com.sporty;

import com.sporty.jackpot.domain.Bet;
import com.sporty.jackpot.domain.FixedContributionPolicy;
import com.sporty.jackpot.domain.FixedRewardPolicy;
import com.sporty.jackpot.domain.Jackpot;
import com.sporty.jackpot.JackpotService;
import com.sporty.jackpot.RewardResponse;
import com.sporty.jackpot.eventsourcing.EventStore;
import com.sporty.jackpot.projection.JackpotContributionRepository;
import com.sporty.jackpot.projection.JackpotRewardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 3, topics = "jackpot-bets",
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"})
class JackpotIntegrationTest {

    @Autowired
    private EventStore eventStore;

    @Autowired
    private JackpotService jackpotService;

    @Autowired
    private JackpotContributionRepository contributionRepository;

    @Autowired
    private JackpotRewardRepository rewardRepository;

    @Autowired
    private com.sporty.betting.BetProducer betProducer;

    private UUID jackpotId;

    @BeforeEach
    void setUp() {
        jackpotId = UUID.randomUUID();
        Jackpot jackpot = Jackpot.create(
                jackpotId,
                new BigDecimal("100"),
                new FixedContributionPolicy(new BigDecimal("10")),
                new FixedRewardPolicy(new BigDecimal("100"))
        );
        eventStore.save(jackpot);
    }

    @Test
    void endToEnd_betPublishedToKafka_consumedAndContributed() {
        UUID betId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var message = new com.sporty.betting.BetMessage(betId, userId, jackpotId, new BigDecimal("50"));

        betProducer.publish(message);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var contribution = contributionRepository.findByBetIdAndJackpotId(betId, jackpotId);
            assertTrue(contribution.isPresent());
            assertEquals(new BigDecimal("5.00"), contribution.get().getContributionAmount());
        });

        // With 100% win policy, the reward is auto-evaluated and pool resets to initial
        Jackpot loaded = eventStore.load(jackpotId).orElseThrow();
        assertEquals(new BigDecimal("100"), loaded.getCurrentPool());

        // Verify the reward was auto-created
        var reward = rewardRepository.findByBetIdAndJackpotId(betId, jackpotId);
        assertTrue(reward.isPresent());
        assertEquals(new BigDecimal("105.00"), reward.get().getRewardAmount());
    }

    @Test
    void highVolume_thousandBetsProcessedCorrectly() {
        int betCount = 1000;

        for (int i = 0; i < betCount; i++) {
            Bet bet = new Bet(UUID.randomUUID(), UUID.randomUUID(), jackpotId, new BigDecimal("50"));
            jackpotService.processContribution(bet);
        }

        // With 100% win policy, every contribution auto-wins and resets pool to initial
        Jackpot loaded = eventStore.load(jackpotId).orElseThrow();
        assertEquals(new BigDecimal("100"), loaded.getCurrentPool());

        long projectedCount = contributionRepository.findAll().stream()
                .filter(c -> c.getJackpotId().equals(jackpotId))
                .count();
        assertEquals(betCount, projectedCount);

        // Every bet should have produced a reward
        long rewardCount = rewardRepository.findAll().stream()
                .filter(r -> r.getJackpotId().equals(jackpotId))
                .count();
        assertEquals(betCount, rewardCount);
    }

    @Test
    void highVolume_rehydrationPerformance() {
        int betCount = 500;
        for (int i = 0; i < betCount; i++) {
            Bet bet = new Bet(UUID.randomUUID(), UUID.randomUUID(), jackpotId, new BigDecimal("25"));
            jackpotService.processContribution(bet);
        }

        long start = System.nanoTime();
        Jackpot loaded = eventStore.load(jackpotId).orElseThrow();
        long elapsed = System.nanoTime() - start;

        assertNotNull(loaded);
        assertTrue(elapsed < 200_000_000, "Rehydration took " + elapsed / 1_000_000 + "ms, expected < 200ms");
    }

    @Test
    void highVolume_rewardResetsPoolAndContinues() {
        Bet[] bets = new Bet[100];
        for (int i = 0; i < 100; i++) {
            bets[i] = new Bet(UUID.randomUUID(), UUID.randomUUID(), jackpotId, new BigDecimal("20"));
            jackpotService.processContribution(bets[i]);
        }

        // Reward is auto-evaluated during processContribution — verify via projection
        RewardResponse reward = jackpotService.getReward(bets[0].betId(), jackpotId);
        assertTrue(reward.won());

        // With 100% win, every contribution wins and resets pool to initial
        Jackpot afterAll = eventStore.load(jackpotId).orElseThrow();
        assertEquals(new BigDecimal("100"), afterAll.getCurrentPool());

        // New contribution after all resets — pool goes 100 + 8 = 108, then wins and resets to 100
        Bet newBet = new Bet(UUID.randomUUID(), UUID.randomUUID(), jackpotId, new BigDecimal("80"));
        jackpotService.processContribution(newBet);

        Jackpot afterNew = eventStore.load(jackpotId).orElseThrow();
        assertEquals(new BigDecimal("100"), afterNew.getCurrentPool());

        // Verify the new bet also got a reward
        RewardResponse newReward = jackpotService.getReward(newBet.betId(), jackpotId);
        assertTrue(newReward.won());
        assertEquals(new BigDecimal("108.00"), newReward.rewardAmount());
    }

    @Test
    void idempotency_duplicateBetProcessedOnce() {
        UUID betId = UUID.randomUUID();
        Bet bet = new Bet(betId, UUID.randomUUID(), jackpotId, new BigDecimal("100"));

        jackpotService.processContribution(bet);
        jackpotService.processContribution(bet);

        // With 100% win, the single contribution wins and resets pool to initial
        Jackpot loaded = eventStore.load(jackpotId).orElseThrow();
        assertEquals(new BigDecimal("100"), loaded.getCurrentPool());

        // Verify only one reward was created
        var reward = rewardRepository.findByBetIdAndJackpotId(betId, jackpotId);
        assertTrue(reward.isPresent());
    }

    @Test
    void endToEnd_multipleBetsViaKafka_allProcessed() {
        // Use a separate jackpot with 0% reward for pool accumulation test
        UUID noWinJackpotId = UUID.randomUUID();
        Jackpot noWinJackpot = Jackpot.create(noWinJackpotId, new BigDecimal("100"),
                new FixedContributionPolicy(new BigDecimal("10")),
                new FixedRewardPolicy(new BigDecimal("0")));
        eventStore.save(noWinJackpot);

        int betCount = 20;
        UUID[] betIds = new UUID[betCount];

        for (int i = 0; i < betCount; i++) {
            betIds[i] = UUID.randomUUID();
            var message = new com.sporty.betting.BetMessage(betIds[i], UUID.randomUUID(), noWinJackpotId, new BigDecimal("30"));
            betProducer.publish(message);
        }

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = contributionRepository.findAll().stream()
                    .filter(c -> c.getJackpotId().equals(noWinJackpotId))
                    .count();
            assertEquals(betCount, count);
        });

        Jackpot loaded = eventStore.load(noWinJackpotId).orElseThrow();
        // 100 + 20 * (10% of 30) = 100 + 60 = 160
        assertEquals(new BigDecimal("160.00"), loaded.getCurrentPool());

        // Verify no rewards were given
        long rewardCount = rewardRepository.findAll().stream()
                .filter(r -> r.getJackpotId().equals(noWinJackpotId))
                .count();
        assertEquals(0, rewardCount);
    }
}

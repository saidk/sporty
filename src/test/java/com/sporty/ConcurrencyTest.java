package com.sporty;

import com.sporty.jackpot.domain.Bet;
import com.sporty.jackpot.domain.contribution.FixedContributionPolicy;
import com.sporty.jackpot.domain.reward.FixedRewardPolicy;
import com.sporty.jackpot.domain.Jackpot;
import com.sporty.jackpot.JackpotService;
import com.sporty.jackpot.eventsourcing.EventStore;
import com.sporty.jackpot.projection.JackpotContributionRepository;
import com.sporty.jackpot.projection.JackpotRewardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@EmbeddedKafka(partitions = 3, topics = "jackpot-bets",
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"})
class ConcurrencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private JackpotService jackpotService;

    @Autowired
    private JackpotContributionRepository contributionRepository;

    @Autowired
    private JackpotRewardRepository rewardRepository;

    private UUID jackpotId;

    @BeforeEach
    void setUp() {
        jackpotId = UUID.randomUUID();
    }

    @Test
    void concurrentBetPlacements_allProcessedCorrectly() throws Exception {
        Jackpot jackpot = Jackpot.create(jackpotId, new BigDecimal("100"),
                new FixedContributionPolicy(new BigDecimal("10")),
                new FixedRewardPolicy(new BigDecimal("0")));
        eventStore.save(jackpot);

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            UUID betId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            futures.add(executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post("/api/bets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "betId": "%s",
                                            "userId": "%s",
                                            "jackpotId": "%s",
                                            "amount": 50.00
                                        }
                                        """.formatted(betId, userId, jackpotId)))
                        .andExpect(status().isAccepted())
                        .andReturn();
            }));
        }

        startLatch.countDown();
        for (Future<MvcResult> future : futures) {
            future.get();
        }
        executor.shutdown();

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = contributionRepository.findAll().stream()
                    .filter(c -> c.getJackpotId().equals(jackpotId))
                    .count();
            assertEquals(threadCount, count);
        });

        Jackpot loaded = eventStore.load(jackpotId).orElseThrow();
        assertEquals(new BigDecimal("200.00"), loaded.getCurrentPool());
    }

    @Test
    void concurrentContributions_onlyOneRewardPerPoolCycle() throws Exception {
        // 100% win chance — every bet that evaluates should win, but pool resets after each win
        Jackpot jackpot = Jackpot.create(jackpotId, new BigDecimal("0"),
                new FixedContributionPolicy(new BigDecimal("10")),
                new FixedRewardPolicy(new BigDecimal("100")));
        eventStore.save(jackpot);

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            Bet bet = new Bet(UUID.randomUUID(), UUID.randomUUID(), jackpotId, new BigDecimal("50"));
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    jackpotService.processContribution(bet);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        startLatch.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        // Each bet wins because 100% chance — all 20 should have rewards
        // Lock striping ensures sequential processing, so each sees the pool after previous reset
        long rewardCount = rewardRepository.findAll().stream()
                .filter(r -> r.getJackpotId().equals(jackpotId))
                .count();
        assertEquals(threadCount, rewardCount);

        // All contributions processed
        long contributionCount = contributionRepository.findAll().stream()
                .filter(c -> c.getJackpotId().equals(jackpotId))
                .count();
        assertEquals(threadCount, contributionCount);
    }

    @Test
    void concurrentDuplicateBets_onlyOneContributed() throws Exception {
        Jackpot jackpot = Jackpot.create(jackpotId, new BigDecimal("100"),
                new FixedContributionPolicy(new BigDecimal("10")),
                new FixedRewardPolicy(new BigDecimal("0")));
        eventStore.save(jackpot);

        UUID betId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post("/api/bets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "betId": "%s",
                                            "userId": "%s",
                                            "jackpotId": "%s",
                                            "amount": 100.00
                                        }
                                        """.formatted(betId, userId, jackpotId)))
                        .andExpect(status().isAccepted())
                        .andReturn();
            }));
        }

        startLatch.countDown();
        for (Future<MvcResult> future : futures) {
            future.get();
        }
        executor.shutdown();

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = contributionRepository.findAll().stream()
                    .filter(c -> c.getBetId().equals(betId))
                    .count();
            assertEquals(1L, count);
        });

        Jackpot loaded = eventStore.load(jackpotId).orElseThrow();
        assertEquals(new BigDecimal("110.00"), loaded.getCurrentPool());
    }
}

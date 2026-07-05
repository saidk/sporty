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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 3, topics = "jackpot-bets",
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"})
class EndToEndTest {

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
        Jackpot jackpot = Jackpot.create(jackpotId, new BigDecimal("100"),
                new FixedContributionPolicy(new BigDecimal("10")),
                new FixedRewardPolicy(new BigDecimal("100")));
        eventStore.save(jackpot);
    }

    @Nested
    class Validation {

        @Test
        void placeBet_validRequest_returns202() throws Exception {
            UUID betId = UUID.randomUUID();
            mockMvc.perform(post("/api/bets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"betId":"%s","userId":"%s","jackpotId":"%s","amount":50.00}
                                    """.formatted(betId, UUID.randomUUID(), jackpotId)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.betId").value(betId.toString()))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));
        }

        @Test
        void placeBet_invalidInput_returns400() throws Exception {
            mockMvc.perform(post("/api/bets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"jackpotId":"%s","amount":-10.00}
                                    """.formatted(jackpotId)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.betId").exists())
                    .andExpect(jsonPath("$.fields.userId").exists())
                    .andExpect(jsonPath("$.fields.amount").exists());
        }

        @Test
        void getReward_noContribution_returnsNoWin() throws Exception {
            mockMvc.perform(get("/api/bets/{betId}/reward", UUID.randomUUID())
                            .param("jackpotId", jackpotId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.won").value(false));
        }
    }

    @Nested
    class FullFlow {

        @Test
        void placeBet_autoEvaluatesReward_queryIsIdempotent() throws Exception {
            UUID betId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            mockMvc.perform(post("/api/bets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"betId":"%s","userId":"%s","jackpotId":"%s","amount":50.00}
                                    """.formatted(betId, userId, jackpotId)))
                    .andExpect(status().isAccepted());

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    mockMvc.perform(get("/api/bets/{betId}/reward", betId)
                                    .param("jackpotId", jackpotId.toString()))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.won").value(true))
                            .andExpect(jsonPath("$.rewardAmount").value(105.00))
                            .andExpect(jsonPath("$.userId").value(userId.toString()))
            );

            // Second query — same result (read-only projection)
            mockMvc.perform(get("/api/bets/{betId}/reward", betId)
                            .param("jackpotId", jackpotId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.won").value(true))
                    .andExpect(jsonPath("$.rewardAmount").value(105.00));
        }
    }

    @Nested
    class HighVolume {

        // Calls service directly to avoid Kafka async delay on 1000 messages
        @Test
        void thousandBets_allContributedAndRewarded() {
            int betCount = 1000;
            for (int i = 0; i < betCount; i++) {
                Bet bet = new Bet(UUID.randomUUID(), UUID.randomUUID(), jackpotId, new BigDecimal("50"));
                jackpotService.processContribution(bet);
            }

            Jackpot loaded = eventStore.load(jackpotId).orElseThrow();
            assertEquals(new BigDecimal("100"), loaded.getCurrentPool());

            long contributions = contributionRepository.findAll().stream()
                    .filter(c -> c.getJackpotId().equals(jackpotId))
                    .count();
            assertEquals(betCount, contributions);

            long rewards = rewardRepository.findAll().stream()
                    .filter(r -> r.getJackpotId().equals(jackpotId))
                    .count();
            assertEquals(betCount, rewards);
        }

        // Measures event replay speed, not HTTP throughput
        @Test
        void rehydration_fiveHundredEventsUnder200ms() {
            UUID perfJackpotId = UUID.randomUUID();
            Jackpot perfJackpot = Jackpot.create(perfJackpotId, new BigDecimal("100"),
                    new FixedContributionPolicy(new BigDecimal("10")),
                    new FixedRewardPolicy(new BigDecimal("0")));
            eventStore.save(perfJackpot);

            for (int i = 0; i < 500; i++) {
                Bet bet = new Bet(UUID.randomUUID(), UUID.randomUUID(), perfJackpotId, new BigDecimal("25"));
                jackpotService.processContribution(bet);
            }

            long start = System.nanoTime();
            Jackpot loaded = eventStore.load(perfJackpotId).orElseThrow();
            long elapsed = System.nanoTime() - start;

            assertNotNull(loaded);
            assertTrue(elapsed < 200_000_000, "Rehydration took " + elapsed / 1_000_000 + "ms");
        }
    }

    @Nested
    class Concurrency {

        @Test
        void twentyThreadsSameJackpot_allProcessed() throws Exception {
            UUID concJackpotId = UUID.randomUUID();
            Jackpot concJackpot = Jackpot.create(concJackpotId, new BigDecimal("100"),
                    new FixedContributionPolicy(new BigDecimal("10")),
                    new FixedRewardPolicy(new BigDecimal("0")));
            eventStore.save(concJackpot);

            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Future<MvcResult>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    startLatch.await();
                    return mockMvc.perform(post("/api/bets")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"betId":"%s","userId":"%s","jackpotId":"%s","amount":50.00}
                                            """.formatted(UUID.randomUUID(), UUID.randomUUID(), concJackpotId)))
                            .andExpect(status().isAccepted())
                            .andReturn();
                }));
            }

            startLatch.countDown();
            for (Future<MvcResult> f : futures) {
                f.get();
            }
            executor.shutdown();

            await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
                long count = contributionRepository.findAll().stream()
                        .filter(c -> c.getJackpotId().equals(concJackpotId))
                        .count();
                assertEquals(threadCount, count);
            });

            Jackpot loaded = eventStore.load(concJackpotId).orElseThrow();
            assertEquals(new BigDecimal("200.00"), loaded.getCurrentPool());
        }

        @Test
        void duplicateBets_onlyOneProcessed() throws Exception {
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
                                            {"betId":"%s","userId":"%s","jackpotId":"%s","amount":100.00}
                                            """.formatted(betId, userId, jackpotId)))
                            .andExpect(status().isAccepted())
                            .andReturn();
                }));
            }

            startLatch.countDown();
            for (Future<MvcResult> f : futures) {
                f.get();
            }
            executor.shutdown();

            await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
                long count = contributionRepository.findAll().stream()
                        .filter(c -> c.getBetId().equals(betId))
                        .count();
                assertEquals(1L, count);
            });
        }
    }
}

package com.sporty;

import com.sporty.jackpot.domain.FixedContributionPolicy;
import com.sporty.jackpot.domain.FixedRewardPolicy;
import com.sporty.jackpot.domain.Jackpot;
import com.sporty.jackpot.eventsourcing.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@EmbeddedKafka(partitions = 3, topics = "jackpot-bets",
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"})
class EndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventStore eventStore;

    private UUID jackpotId;

    @BeforeEach
    void setUp() {
        jackpotId = UUID.randomUUID();
        Jackpot jackpot = Jackpot.create(
                jackpotId,
                new BigDecimal("100"),
                new FixedContributionPolicy(new BigDecimal("10")),
                new FixedRewardPolicy(new BigDecimal("100")) // guaranteed win
        );
        eventStore.save(jackpot);
    }

    @Test
    void placeBet_validRequest_returns202WithBetId() throws Exception {
        UUID betId = UUID.randomUUID();

        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "betId": "%s",
                                    "userId": "%s",
                                    "jackpotId": "%s",
                                    "amount": 50.00
                                }
                                """.formatted(betId, UUID.randomUUID(), jackpotId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.betId").value(betId.toString()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void placeBet_missingUserId_returns400() throws Exception {
        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "betId": "%s",
                                    "jackpotId": "%s",
                                    "amount": 50.00
                                }
                                """.formatted(UUID.randomUUID(), jackpotId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.userId").exists());
    }

    @Test
    void placeBet_negativeAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "betId": "%s",
                                    "userId": "%s",
                                    "jackpotId": "%s",
                                    "amount": -10.00
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), jackpotId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amount").exists());
    }

    @Test
    void placeBet_zeroAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "betId": "%s",
                                    "userId": "%s",
                                    "jackpotId": "%s",
                                    "amount": 0
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), jackpotId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amount").exists());
    }

    @Test
    void getReward_noContribution_returnsNoWin() throws Exception {
        UUID betId = UUID.randomUUID();

        mockMvc.perform(get("/api/bets/{betId}/reward", betId)
                        .param("jackpotId", jackpotId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.won").value(false))
                .andExpect(jsonPath("$.rewardAmount").value(0));
    }

    @Test
    void fullFlow_placeBet_waitForProcessing_getReward_wins() throws Exception {
        UUID betId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // Place bet via API
        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "betId": "%s",
                                    "userId": "%s",
                                    "jackpotId": "%s",
                                    "amount": 50.00
                                }
                                """.formatted(betId, userId, jackpotId)))
                .andExpect(status().isAccepted());

        // Wait for Kafka consumer to process and auto-evaluate reward
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/bets/{betId}/reward", betId)
                                .param("jackpotId", jackpotId.toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.won").value(true))
                        .andExpect(jsonPath("$.rewardAmount").value(105.00))
                        .andExpect(jsonPath("$.userId").value(userId.toString()))
        );
    }

    @Test
    void getReward_calledMultipleTimes_returnsConsistentResult() throws Exception {
        UUID betId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // Place and wait for processing
        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "betId": "%s",
                                    "userId": "%s",
                                    "jackpotId": "%s",
                                    "amount": 50.00
                                }
                                """.formatted(betId, userId, jackpotId)))
                .andExpect(status().isAccepted());

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/bets/{betId}/reward", betId)
                                .param("jackpotId", jackpotId.toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.won").value(true))
        );

        // Second query — same result since it's just reading the projection
        mockMvc.perform(get("/api/bets/{betId}/reward", betId)
                        .param("jackpotId", jackpotId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.won").value(true));
    }
}

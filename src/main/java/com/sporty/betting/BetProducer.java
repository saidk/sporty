package com.sporty.betting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class BetProducer {

    private static final Logger log = LoggerFactory.getLogger(BetProducer.class);
    private static final String TOPIC = "jackpot-bets";

    private final KafkaTemplate<String, BetMessage> kafkaTemplate;

    public BetProducer(KafkaTemplate<String, BetMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(BetMessage bet) {
        CompletableFuture<SendResult<String, BetMessage>> future =
                kafkaTemplate.send(TOPIC, bet.jackpotId().toString(), bet);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish bet {} to Kafka, will be retried by producer", bet.betId(), ex);
            } else {
                log.info("Bet {} published to partition {} offset {}",
                        bet.betId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}

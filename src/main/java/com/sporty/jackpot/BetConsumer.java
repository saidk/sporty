package com.sporty.jackpot;

import com.sporty.jackpot.domain.Bet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BetConsumer {

    private static final Logger log = LoggerFactory.getLogger(BetConsumer.class);

    private final JackpotService jackpotService;

    public BetConsumer(JackpotService jackpotService) {
        this.jackpotService = jackpotService;
    }

    @KafkaListener(topics = "jackpot-bets", groupId = "jackpot-service")
    public void onBet(BetMessage message) {
        log.info("Consumed bet {} for jackpot {}", message.betId(), message.jackpotId());
        Bet bet = new Bet(message.betId(), message.userId(), message.jackpotId(), message.amount());
        jackpotService.processContribution(bet);
    }
}

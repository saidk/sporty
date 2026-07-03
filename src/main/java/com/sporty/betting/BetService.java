package com.sporty.betting;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class BetService {

    private final BetProducer betProducer;

    public BetService(BetProducer betProducer) {
        this.betProducer = betProducer;
    }

    public UUID placeBet(BetRequest request) {
        BetMessage message = new BetMessage(request.betId(), request.userId(), request.jackpotId(), request.amount());
        betProducer.publish(message);
        return request.betId();
    }
}

package com.sporty.jackpot;

import com.sporty.jackpot.domain.Bet;
import com.sporty.jackpot.domain.Jackpot;
import com.sporty.jackpot.domain.event.ContributionMade;
import com.sporty.jackpot.domain.event.DomainEvent;
import com.sporty.jackpot.eventsourcing.ConcurrencyConflictException;
import com.sporty.jackpot.eventsourcing.EventStore;
import com.sporty.jackpot.projection.EventProjector;
import com.sporty.jackpot.projection.JackpotRewardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class JackpotService {

    private static final Logger log = LoggerFactory.getLogger(JackpotService.class);

    private final EventStore eventStore;
    private final EventProjector projector;
    private final JackpotRewardRepository rewardRepository;

    // Per-jackpot lock striping; @Retryable is a fallback for multi-instance deployments
    private final ConcurrentHashMap<UUID, ReentrantLock> jackpotLocks = new ConcurrentHashMap<>();

    public JackpotService(EventStore eventStore, EventProjector projector,
                          JackpotRewardRepository rewardRepository) {
        this.eventStore = eventStore;
        this.projector = projector;
        this.rewardRepository = rewardRepository;
    }

    @Retryable(retryFor = ConcurrencyConflictException.class,
            backoff = @Backoff(delay = 50, multiplier = 2))
    @Transactional
    public void processContribution(Bet bet) {
        ReentrantLock lock = jackpotLocks.computeIfAbsent(bet.jackpotId(), k -> new ReentrantLock());
        lock.lock();
        try {
            Jackpot jackpot = eventStore.load(bet.jackpotId())
                    .orElseThrow(() -> new IllegalArgumentException("Jackpot not found: " + bet.jackpotId()));

            Optional<ContributionMade> result = jackpot.contribute(bet);
            if (result.isEmpty()) {
                log.debug("Bet {} already processed, skipping", bet.betId());
                return;
            }

            // Evaluate reward immediately after contribution
            jackpot.evaluateReward(bet);

            List<DomainEvent> newEvents = List.copyOf(jackpot.getUncommittedEvents());
            eventStore.save(jackpot);
            // TODO: in production, decouple projection from event persistence to avoid shared rollback
            projector.project(newEvents);
        } finally {
            lock.unlock();
        }
    }

    public RewardResponse getReward(UUID betId, UUID jackpotId) {
        return rewardRepository.findByBetIdAndJackpotId(betId, jackpotId)
                .map(r -> RewardResponse.winner(r.getBetId(), r.getUserId(), r.getJackpotId(), r.getRewardAmount()))
                .orElse(RewardResponse.noWin(betId, jackpotId));
    }
}

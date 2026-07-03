package com.sporty.jackpot.projection;

import com.sporty.jackpot.domain.event.ContributionMade;
import com.sporty.jackpot.domain.event.DomainEvent;
import com.sporty.jackpot.domain.event.JackpotCreated;
import com.sporty.jackpot.domain.event.PoolReset;
import com.sporty.jackpot.domain.event.RewardGranted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EventProjector {

    private static final Logger log = LoggerFactory.getLogger(EventProjector.class);

    private final JackpotContributionRepository contributionRepository;
    private final JackpotRewardRepository rewardRepository;

    public EventProjector(JackpotContributionRepository contributionRepository,
                          JackpotRewardRepository rewardRepository) {
        this.contributionRepository = contributionRepository;
        this.rewardRepository = rewardRepository;
    }

    public void project(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            project(event);
        }
    }

    private void project(DomainEvent event) {
        switch (event) {
            case ContributionMade e -> projectContribution(e);
            case RewardGranted e -> projectReward(e);
            case JackpotCreated ignored -> { }
            case PoolReset ignored -> { }
        }
    }

    private void projectContribution(ContributionMade e) {
        try {
            contributionRepository.saveAndFlush(new JackpotContribution(
                    e.betId(), e.userId(), e.aggregateId(),
                    e.stakeAmount(), e.contributionAmount(),
                    e.newPoolAmount(), e.occurredAt()
            ));
        } catch (DataIntegrityViolationException ex) {
            log.debug("Contribution already projected for bet {}", e.betId());
        }
    }

    private void projectReward(RewardGranted e) {
        try {
            rewardRepository.saveAndFlush(new JackpotReward(
                    e.betId(), e.userId(), e.aggregateId(),
                    e.rewardAmount(), e.occurredAt()
            ));
        } catch (DataIntegrityViolationException ex) {
            log.debug("Reward already projected for bet {}", e.betId());
        }
    }
}

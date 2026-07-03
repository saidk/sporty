package com.sporty.jackpot.domain;

import com.sporty.jackpot.domain.contribution.ContributionPolicy;
import com.sporty.jackpot.domain.event.ContributionMade;
import com.sporty.jackpot.domain.event.DomainEvent;
import com.sporty.jackpot.domain.event.JackpotCreated;
import com.sporty.jackpot.domain.event.PoolReset;
import com.sporty.jackpot.domain.event.RewardGranted;
import com.sporty.jackpot.domain.reward.RewardPolicy;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class Jackpot {

    @Getter
    private UUID id;
    @Getter
    private BigDecimal currentPool;
    private BigDecimal initialPool;
    private ContributionPolicy contributionPolicy;
    private RewardPolicy rewardPolicy;
    @Getter
    private int version;
    private PolicyResolver policyResolver;

    private final Set<UUID> processedBetIds = new HashSet<>();
    private final Set<UUID> rewardedBetIds = new HashSet<>();
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();

    private Jackpot() {
    }

    public static Jackpot rehydrate(List<DomainEvent> history, PolicyResolver resolver) {
        Jackpot jackpot = new Jackpot();
        jackpot.policyResolver = resolver;
        for (DomainEvent event : history) {
            jackpot.apply(event);
            jackpot.version++;
        }
        return jackpot;
    }

    public static Jackpot create(UUID id, BigDecimal initialPool,
                                 ContributionPolicy contributionPolicy,
                                 RewardPolicy rewardPolicy) {
        Jackpot jackpot = new Jackpot();
        jackpot.contributionPolicy = contributionPolicy;
        jackpot.rewardPolicy = rewardPolicy;
        jackpot.raise(new JackpotCreated(
                id, initialPool,
                contributionPolicy.type(),
                contributionPolicy.config(),
                rewardPolicy.type(),
                rewardPolicy.config(),
                Instant.now()
        ));
        return jackpot;
    }

    public Optional<ContributionMade> contribute(Bet bet) {
        if (processedBetIds.contains(bet.betId())) {
            return Optional.empty();
        }

        BigDecimal contribution = contributionPolicy.calculate(bet.amount(), currentPool);
        BigDecimal newPool = currentPool.add(contribution);

        ContributionMade event = new ContributionMade(
                id, bet.betId(), bet.userId(),
                bet.amount(), contribution, newPool, Instant.now()
        );
        raise(event);
        return Optional.of(event);
    }

    public Optional<RewardGranted> evaluateReward(Bet bet) {
        if (!processedBetIds.contains(bet.betId())) {
            throw new IllegalStateException("Bet has not contributed to this jackpot yet");
        }
        if (rewardedBetIds.contains(bet.betId())) {
            return Optional.empty();
        }

        boolean won = rewardPolicy.isWinner(currentPool);
        if (!won) {
            return Optional.empty();
        }

        RewardGranted reward = new RewardGranted(id, bet.betId(), bet.userId(), currentPool, Instant.now());
        raise(reward);
        raise(new PoolReset(id, initialPool, Instant.now()));
        return Optional.of(reward);
    }

    private void apply(DomainEvent event) {
        switch (event) {
            case JackpotCreated e -> {
                this.id = e.aggregateId();
                this.initialPool = e.initialPool();
                this.currentPool = e.initialPool();
                if (policyResolver != null) {
                    this.contributionPolicy = policyResolver.resolveContribution(
                            e.contributionPolicyType(), e.contributionPolicyConfig());
                    this.rewardPolicy = policyResolver.resolveReward(
                            e.rewardPolicyType(), e.rewardPolicyConfig());
                }
            }
            case ContributionMade e -> {
                this.currentPool = e.newPoolAmount();
                this.processedBetIds.add(e.betId());
            }
            case RewardGranted e -> this.rewardedBetIds.add(e.betId());
            case PoolReset e -> this.currentPool = e.resetAmount();
        }
    }

    private void raise(DomainEvent event) {
        apply(event);
        version++;
        uncommittedEvents.add(event);
    }

    public List<DomainEvent> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }

    public void clearUncommittedEvents() {
        uncommittedEvents.clear();
    }
}

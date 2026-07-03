package com.sporty.jackpot.eventsourcing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporty.jackpot.domain.Jackpot;
import com.sporty.jackpot.domain.PolicyResolver;
import com.sporty.jackpot.domain.contribution.ContributionPolicy;
import com.sporty.jackpot.domain.event.ContributionMade;
import com.sporty.jackpot.domain.event.DomainEvent;
import com.sporty.jackpot.domain.event.JackpotCreated;
import com.sporty.jackpot.domain.event.PoolReset;
import com.sporty.jackpot.domain.event.RewardGranted;
import com.sporty.jackpot.domain.reward.RewardPolicy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class EventStore {

    private final StoredEventRepository repository;
    private final ObjectMapper objectMapper;
    private final PolicyResolver policyResolver;

    public EventStore(StoredEventRepository repository, ObjectMapper objectMapper, PolicyRegistry policyRegistry) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.policyResolver = new PolicyResolver() {
            @Override
            public ContributionPolicy resolveContribution(String type, String config) {
                return policyRegistry.contributionFrom(type, config);
            }

            @Override
            public RewardPolicy resolveReward(String type, String config) {
                return policyRegistry.rewardFrom(type, config);
            }
        };
    }

    public Optional<Jackpot> load(UUID aggregateId) {
        List<StoredEvent> storedEvents = repository.findByAggregateIdOrderByVersionAsc(aggregateId);
        if (storedEvents.isEmpty()) {
            return Optional.empty();
        }

        List<DomainEvent> events = storedEvents.stream()
                .map(this::deserialize)
                .toList();

        return Optional.of(Jackpot.rehydrate(events, policyResolver));
    }

    @Transactional
    public void save(Jackpot jackpot) {
        List<DomainEvent> newEvents = jackpot.getUncommittedEvents();
        int baseVersion = jackpot.getVersion() - newEvents.size();

        for (int i = 0; i < newEvents.size(); i++) {
            DomainEvent event = newEvents.get(i);
            int eventVersion = baseVersion + i + 1;

            StoredEvent stored = new StoredEvent(
                    jackpot.getId(),
                    eventVersion,
                    event.getClass().getSimpleName(),
                    serialize(event),
                    event.occurredAt()
            );

            try {
                repository.saveAndFlush(stored);
            } catch (DataIntegrityViolationException e) {
                throw new ConcurrencyConflictException(jackpot.getId(), eventVersion);
            }
        }

        jackpot.clearUncommittedEvents();
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    private DomainEvent deserialize(StoredEvent stored) {
        try {
            Class<? extends DomainEvent> clazz = resolveEventClass(stored.getEventType());
            return objectMapper.readValue(stored.getPayload(), clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize event: " + stored.getEventType(), e);
        }
    }

    private Class<? extends DomainEvent> resolveEventClass(String eventType) {
        return switch (eventType) {
            case "JackpotCreated" -> JackpotCreated.class;
            case "ContributionMade" -> ContributionMade.class;
            case "RewardGranted" -> RewardGranted.class;
            case "PoolReset" -> PoolReset.class;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}

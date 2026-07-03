package com.sporty.jackpot.eventsourcing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Table(name = "stored_events", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"aggregateId", "version"})
})
public class StoredEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID aggregateId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, length = 4096)
    private String payload;

    @Column(nullable = false)
    private Instant occurredAt;

    public StoredEvent(UUID aggregateId, int version, String eventType, String payload, Instant occurredAt) {
        this.aggregateId = aggregateId;
        this.version = version;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }
}

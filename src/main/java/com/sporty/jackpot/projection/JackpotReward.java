package com.sporty.jackpot.projection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Table(name = "jackpot_rewards",
        uniqueConstraints = @UniqueConstraint(columnNames = {"betId", "jackpotId"}),
        indexes = @Index(name = "idx_reward_user_id", columnList = "userId")
)
public class JackpotReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID betId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID jackpotId;

    @Column(nullable = false)
    private BigDecimal rewardAmount;

    @Column(nullable = false)
    private Instant createdAt;

    public JackpotReward(UUID betId, UUID userId, UUID jackpotId,
                         BigDecimal rewardAmount, Instant createdAt) {
        this.betId = betId;
        this.userId = userId;
        this.jackpotId = jackpotId;
        this.rewardAmount = rewardAmount;
        this.createdAt = createdAt;
    }
}

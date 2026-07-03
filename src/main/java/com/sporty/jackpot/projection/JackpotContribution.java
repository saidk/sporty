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
@Table(name = "jackpot_contributions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"betId", "jackpotId"}),
        indexes = @Index(name = "idx_contribution_user_id", columnList = "userId")
)
public class JackpotContribution {

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
    private BigDecimal stakeAmount;

    @Column(nullable = false)
    private BigDecimal contributionAmount;

    @Column(nullable = false)
    private BigDecimal currentJackpotAmount;

    @Column(nullable = false)
    private Instant createdAt;

    public JackpotContribution(UUID betId, UUID userId, UUID jackpotId,
                               BigDecimal stakeAmount, BigDecimal contributionAmount,
                               BigDecimal currentJackpotAmount, Instant createdAt) {
        this.betId = betId;
        this.userId = userId;
        this.jackpotId = jackpotId;
        this.stakeAmount = stakeAmount;
        this.contributionAmount = contributionAmount;
        this.currentJackpotAmount = currentJackpotAmount;
        this.createdAt = createdAt;
    }
}

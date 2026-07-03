package com.sporty.jackpot.projection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JackpotRewardRepository extends JpaRepository<JackpotReward, Long> {

    Optional<JackpotReward> findByBetIdAndJackpotId(UUID betId, UUID jackpotId);
}

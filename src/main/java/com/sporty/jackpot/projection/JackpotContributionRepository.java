package com.sporty.jackpot.projection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JackpotContributionRepository extends JpaRepository<JackpotContribution, Long> {

    Optional<JackpotContribution> findByBetIdAndJackpotId(UUID betId, UUID jackpotId);
}

package com.sporty.config;

import com.sporty.jackpot.domain.contribution.FixedContributionPolicy;
import com.sporty.jackpot.domain.reward.FixedRewardPolicy;
import com.sporty.jackpot.domain.Jackpot;
import com.sporty.jackpot.domain.contribution.VariableContributionPolicy;
import com.sporty.jackpot.domain.reward.VariableRewardPolicy;
import com.sporty.jackpot.eventsourcing.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final EventStore eventStore;

    public DataInitializer(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public void run(String... args) {
        UUID fixedJackpotId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID variableJackpotId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        if (eventStore.load(fixedJackpotId).isEmpty()) {
            Jackpot fixedJackpot = Jackpot.create(
                    fixedJackpotId,
                    new BigDecimal("100.00"),
                    new FixedContributionPolicy(new BigDecimal("10")),
                    new FixedRewardPolicy(new BigDecimal("5"))
            );
            eventStore.save(fixedJackpot);
            log.info("Seeded jackpot {} with fixed 10% contribution, 5% reward chance", fixedJackpotId);
        }

        if (eventStore.load(variableJackpotId).isEmpty()) {
            Jackpot variableJackpot = Jackpot.create(
                    variableJackpotId,
                    new BigDecimal("200.00"),
                    new VariableContributionPolicy(new BigDecimal("20"), new BigDecimal("0.8"), new BigDecimal("2000")),
                    new VariableRewardPolicy(new BigDecimal("2"), new BigDecimal("5000"))
            );
            eventStore.save(variableJackpot);
            log.info("Seeded jackpot {} with variable contribution and reward policies", variableJackpotId);
        }
    }
}

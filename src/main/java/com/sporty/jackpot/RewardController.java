package com.sporty.jackpot;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/bets")
public class RewardController {

    private final JackpotService jackpotService;

    public RewardController(JackpotService jackpotService) {
        this.jackpotService = jackpotService;
    }

    @GetMapping("/{betId}/reward")
    public ResponseEntity<RewardResponse> getReward(
            @PathVariable UUID betId,
            @RequestParam UUID jackpotId) {

        RewardResponse response = jackpotService.getReward(betId, jackpotId);
        return ResponseEntity.ok(response);
    }
}

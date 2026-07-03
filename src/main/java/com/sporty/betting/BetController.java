package com.sporty.betting;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/bets")
public class BetController {

    private final BetService betService;

    public BetController(BetService betService) {
        this.betService = betService;
    }

    @PostMapping
    public ResponseEntity<BetResponse> placeBet(@Valid @RequestBody BetRequest request) {
        UUID betId = betService.placeBet(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new BetResponse(betId, "ACCEPTED"));
    }
}

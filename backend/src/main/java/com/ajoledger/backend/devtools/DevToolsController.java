package com.ajoledger.backend.devtools;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Profile("dev")
@RestController
@RequestMapping("/api/dev/circles/{circleId}")
@RequiredArgsConstructor
public class DevToolsController {

    private final DevToolsService devToolsService;

    @PostMapping(path = "/force-round", version = "1")
    public ResponseEntity<Void> forceRound(@PathVariable UUID circleId) {
        devToolsService.forceRound(circleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/force-enrollment-close", version = "1")
    public ResponseEntity<Void> forceEnrollmentClose(@PathVariable UUID circleId) {
        devToolsService.forceEnrollmentClose(circleId);
        return ResponseEntity.noContent().build();
    }
}

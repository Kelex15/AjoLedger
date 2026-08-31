package com.ajoledger.backend.circle;

import com.ajoledger.backend.circle.dto.CircleCreateRequestDTO;
import com.ajoledger.backend.circle.dto.CircleInviteCreateRequestDTO;
import com.ajoledger.backend.circle.dto.CircleInviteResponseDTO;
import com.ajoledger.backend.circle.dto.CircleMembershipResponseDTO;
import com.ajoledger.backend.circle.dto.CircleResponseDTO;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/circles")
@RequiredArgsConstructor
public class CircleController {

    private final CircleService circleService;

    @PostMapping(version = "1")
    public ResponseEntity<CircleResponseDTO> create(@AuthenticationPrincipal UUID currentUserId,
                                                      @Valid @RequestBody CircleCreateRequestDTO dto) {
        Circle circle = circleService.createCircle(currentUserId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(CircleResponseDTO.fromCircle(circle));
    }

    @PostMapping(path = "/{circleId}/invites", version = "1")
    public ResponseEntity<CircleInviteResponseDTO> invite(@AuthenticationPrincipal UUID currentUserId,
                                                            @PathVariable UUID circleId,
                                                            @Valid @RequestBody CircleInviteCreateRequestDTO dto) {
        CircleInvite invite = circleService.inviteToCircle(circleId, currentUserId, dto.getUsername(), dto.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(CircleInviteResponseDTO.fromInvite(invite));
    }

    @PostMapping(path = "/invites/{code}/redeem", version = "1")
    public ResponseEntity<CircleResponseDTO> redeem(@AuthenticationPrincipal UUID currentUserId,
                                                      @PathVariable String code) {
        Circle circle = circleService.redeemInvite(code, currentUserId);
        return ResponseEntity.ok(CircleResponseDTO.fromCircle(circle));
    }

    @PostMapping(path = "/{circleId}/join", version = "1")
    public ResponseEntity<CircleMembershipResponseDTO> join(@AuthenticationPrincipal UUID currentUserId,
                                                              @PathVariable UUID circleId) {
        CircleMembership membership = circleService.joinCircle(currentUserId, circleId);
        return ResponseEntity.status(HttpStatus.CREATED).body(CircleMembershipResponseDTO.fromMembership(membership));
    }
}

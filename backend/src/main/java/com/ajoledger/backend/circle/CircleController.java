package com.ajoledger.backend.circle;

import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.circle.dto.CircleCreateRequestDTO;
import com.ajoledger.backend.circle.dto.CircleHistoryEntryDTO;
import com.ajoledger.backend.circle.dto.CircleInviteCreateRequestDTO;
import com.ajoledger.backend.circle.dto.CircleInviteResponseDTO;
import com.ajoledger.backend.circle.dto.CircleMemberResponseDTO;
import com.ajoledger.backend.circle.dto.CircleMembershipResponseDTO;
import com.ajoledger.backend.circle.dto.CircleResponseDTO;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/circles")
@RequiredArgsConstructor
public class CircleController {

    private final CircleService circleService;

    private CircleResponseDTO toResponseDTO(Circle circle) {
        BigDecimal collateral = circleService.calculateJoinCollateral(circle);
        Currency currency = circleService.getCircleCurrency(circle.getId());
        return CircleResponseDTO.fromCircle(circle, collateral, currency);
    }

    @PostMapping(version = "1")
    public ResponseEntity<CircleResponseDTO> create(@AuthenticationPrincipal UUID currentUserId,
                                                      @Valid @RequestBody CircleCreateRequestDTO dto) {
        Circle circle = circleService.createCircle(currentUserId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponseDTO(circle));
    }

    @GetMapping(path = "/me", version = "1")
    public ResponseEntity<CircleResponseDTO> myCircle(@AuthenticationPrincipal UUID currentUserId) {
        Circle circle = circleService.getMyCircle(currentUserId);
        return ResponseEntity.ok(toResponseDTO(circle));
    }

    @GetMapping(path = "/mine", version = "1")
    public ResponseEntity<List<CircleHistoryEntryDTO>> myMemberships(@AuthenticationPrincipal UUID currentUserId) {
        List<CircleHistoryEntryDTO> memberships = circleService.getMyMemberships(currentUserId).stream()
                .map(CircleHistoryEntryDTO::fromMembership)
                .toList();
        return ResponseEntity.ok(memberships);
    }

    @GetMapping(path = "/open", version = "1")
    public ResponseEntity<List<CircleResponseDTO>> openCircles(@AuthenticationPrincipal UUID currentUserId) {
        List<CircleResponseDTO> circles = circleService.getBrowsableCircles(currentUserId).stream()
                .map(this::toResponseDTO)
                .toList();
        return ResponseEntity.ok(circles);
    }

    @PostMapping(path = "/{circleId}/invites", version = "1")
    public ResponseEntity<CircleInviteResponseDTO> invite(@AuthenticationPrincipal UUID currentUserId,
                                                            @PathVariable UUID circleId,
                                                            @Valid @RequestBody CircleInviteCreateRequestDTO dto) {
        CircleInvite invite = circleService.inviteToCircle(circleId, currentUserId, dto.username(), dto.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(CircleInviteResponseDTO.fromInvite(invite));
    }

    @PostMapping(path = "/invites/{code}/redeem", version = "1")
    public ResponseEntity<CircleResponseDTO> redeem(@AuthenticationPrincipal UUID currentUserId,
                                                      @PathVariable String code) {
        Circle circle = circleService.redeemInvite(code, currentUserId);
        return ResponseEntity.ok(toResponseDTO(circle));
    }

    @PostMapping(path = "/{circleId}/join", version = "1")
    public ResponseEntity<CircleMembershipResponseDTO> join(@AuthenticationPrincipal UUID currentUserId,
                                                              @PathVariable UUID circleId) {
        CircleMembership membership = circleService.joinCircle(currentUserId, circleId);
        return ResponseEntity.status(HttpStatus.CREATED).body(CircleMembershipResponseDTO.fromMembership(membership));
    }

    @PostMapping(path = "/{circleId}/cancel", version = "1")
    public ResponseEntity<CircleResponseDTO> cancel(@AuthenticationPrincipal UUID currentUserId,
                                                      @PathVariable UUID circleId) {
        Circle circle = circleService.cancelCircle(circleId, currentUserId);
        return ResponseEntity.ok(toResponseDTO(circle));
    }

    @GetMapping(path = "/{circleId}/members", version = "1")
    public ResponseEntity<List<CircleMemberResponseDTO>> members(@PathVariable UUID circleId) {
        List<CircleMemberResponseDTO> members = circleService.getMemberStandings(circleId).stream()
                .map(CircleMemberResponseDTO::of)
                .toList();
        return ResponseEntity.ok(members);
    }

    @PostMapping(path = "/{circleId}/members/{memberUserId}/remove", version = "1")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal UUID currentUserId,
                                               @PathVariable UUID circleId,
                                               @PathVariable UUID memberUserId) {
        circleService.removeMember(circleId, currentUserId, memberUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{circleId}/members/{memberUserId}/ban", version = "1")
    public ResponseEntity<Void> banMember(@AuthenticationPrincipal UUID currentUserId,
                                            @PathVariable UUID circleId,
                                            @PathVariable UUID memberUserId) {
        circleService.banMember(circleId, currentUserId, memberUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{circleId}/leave", version = "1")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal UUID currentUserId,
                                        @PathVariable UUID circleId) {
        circleService.leaveCircle(circleId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}

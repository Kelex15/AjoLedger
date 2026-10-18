package com.ajoledger.backend.demo;

import com.ajoledger.backend.circle.CircleStatus;
import com.ajoledger.backend.circle.MembershipStatus;
import com.ajoledger.backend.circle.entity.CircleMembership;
import com.ajoledger.backend.circle.repository.CircleMembershipRepository;
import com.ajoledger.backend.circle.repository.CircleRepository;
import com.ajoledger.backend.system.Constants;
import com.ajoledger.backend.user.User;
import com.ajoledger.backend.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// Seeding runs once, at startup, the way it does in production.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "ajoledger.demo.seed-enabled=true")
@Testcontainers
class DemoSeederIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private DemoSeeder demoSeeder;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CircleRepository circleRepository;
    @Autowired
    private CircleMembershipRepository circleMembershipRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User demo() {
        return userRepository.findByUsername(Constants.Demo.USERNAME).orElseThrow();
    }

    @Test
    void demoBecomesReachableOnlyOnceSeedingFinished() {
        assertThat(userRepository.existsByUsername(Constants.Demo.USERNAME)).isTrue();
        assertThat(userRepository.existsByUsername(Constants.Demo.SEEDING_USERNAME)).isFalse();
    }

    @Test
    void demoHistoryHasFinishedCirclesADefaultAWithdrawalAndOneActiveCircle() {
        List<CircleMembership> memberships = circleMembershipRepository.findByUser_IdOrderByJoinedAtDesc(demo().getId());
        assertThat(memberships).hasSize(8);
        assertThat(memberships).filteredOn(m -> m.getStatus() == MembershipStatus.COMPLETED).hasSize(7);
        assertThat(memberships).filteredOn(m -> m.getStatus() == MembershipStatus.ACTIVE).hasSize(1);

        Set<UUID> demoCircleIds = memberships.stream().map(m -> m.getCircle().getId()).collect(Collectors.toSet());
        assertThat(demoCircleIds).anyMatch(id -> hasMemberWithStatus(id, MembershipStatus.DEFAULTED));
        assertThat(demoCircleIds).anyMatch(id -> hasMemberWithStatus(id, MembershipStatus.WITHDRAWN));
    }

    private boolean hasMemberWithStatus(UUID circleId, MembershipStatus status) {
        return !circleMembershipRepository.findByCircle_IdAndStatus(circleId, status).isEmpty();
    }

    @Test
    void openCirclesAreThereToBrowse() {
        assertThat(circleRepository.findByStatus(CircleStatus.FORMING)).hasSize(10);
    }

    @Test
    void ledgerStillNetsToZeroAcrossEveryAccount() {
        BigDecimal total = jdbcTemplate.queryForObject("SELECT sum(balance) FROM account", BigDecimal.class);
        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void seededAccountsNeverQueueEmail() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM email_outbox", Integer.class)).isZero();
    }

    @Test
    void runningAgainChangesNothing() {
        long usersBefore = userRepository.count();
        long circlesBefore = circleRepository.count();

        demoSeeder.run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(circleRepository.count()).isEqualTo(circlesBefore);
    }
}

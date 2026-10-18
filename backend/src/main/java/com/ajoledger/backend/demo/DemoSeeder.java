package com.ajoledger.backend.demo;

import com.ajoledger.backend.account.AccountService;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.account.repository.UserAccountRepository;
import com.ajoledger.backend.circle.CircleRoundService;
import com.ajoledger.backend.circle.CircleService;
import com.ajoledger.backend.circle.CircleStatus;
import com.ajoledger.backend.circle.CircleTransactionsService;
import com.ajoledger.backend.circle.dto.CircleCreateRequestDTO;
import com.ajoledger.backend.circle.entity.Circle;
import com.ajoledger.backend.circle.policy.CircleSizingPolicyType;
import com.ajoledger.backend.circle.repository.CircleRepository;
import com.ajoledger.backend.funding.FundingService;
import com.ajoledger.backend.ledger.LedgerService;
import com.ajoledger.backend.system.Constants;
import com.ajoledger.backend.user.User;
import com.ajoledger.backend.user.UserRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Builds the demo account's history by running the real circle and ledger flows, so every balance,
 * fee and seizure a visitor sees came from the same code real members go through. */
@Component
@ConditionalOnProperty(prefix = "ajoledger.demo", name = "seed-enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private static final int MEMBER_COUNT = 70;

    private static final String[] FIRST_NAMES = {
            "Adaeze", "Tunde", "Ngozi", "Chidi", "Funmi", "Emeka", "Bisi", "Kemi", "Segun", "Amaka",
            "Yemi", "Ifeoma", "Femi", "Chioma", "Dayo", "Uche", "Bola", "Nkechi", "Wale", "Zainab",
            "Kunle", "Ebere", "Tobi", "Halima", "Gbenga", "Obiageli", "Lanre", "Aisha", "Seun", "Chinedu",
            "Titi", "Ikenna", "Folake", "Musa", "Ronke"};
    private static final String[] LAST_NAMES = {
            "Okafor", "Bakare", "Eze", "Nwosu", "Adeyemi", "Obi", "Alade", "Balogun", "Okonkwo", "Adebayo",
            "Ibrahim", "Uzor", "Ogunleye", "Nnamdi", "Afolabi", "Mohammed", "Chukwu", "Oyelaran", "Ekwueme",
            "Lawal", "Onyeka", "Salami", "Agu", "Danjuma", "Oladipo"};

    private final UserRepository userRepository;
    private final AccountService accountService;
    private final UserAccountRepository userAccountRepository;
    private final FundingService fundingService;
    private final LedgerService ledgerService;
    private final CircleService circleService;
    private final CircleRoundService circleRoundService;
    private final CircleTransactionsService circleTransactionsService;
    private final CircleRepository circleRepository;

    private enum Ending { COMPLETED, DEFAULT_AFTER_PAYOUT, WITHDRAWAL_BEFORE_PAYOUT }

    private record Plan(String name, String description, int size, String contribution, Ending ending) {
    }

    private static final List<Plan> COMMUNITY_CIRCLES = List.of(
            new Plan("Ikeja Traders Union", "Shop owners on the same street.", 5, "200.00", Ending.COMPLETED),
            new Plan("Okada Riders Thrift", "Saving toward new bikes.", 6, "50.00", Ending.DEFAULT_AFTER_PAYOUT),
            new Plan("Nurses' Night Shift Ajo", "Colleagues on the night rota.", 4, "300.00", Ending.COMPLETED),
            new Plan("Tailors' Cooperative", "Pooling for new machines.", 7, "100.00", Ending.COMPLETED),
            new Plan("Campus Alumni Circle", "Old classmates keeping in touch.", 6, "150.00",
                    Ending.WITHDRAWAL_BEFORE_PAYOUT),
            new Plan("Bakers' Weekly Pot", "Saving for a second oven.", 8, "75.00", Ending.COMPLETED));

    private static final List<Plan> DEMO_HISTORY = List.of(
            new Plan("Friends and Family Ajo", "Four friends saving toward the new year.", 4, "100.00", Ending.COMPLETED),
            new Plan("Market Day Esusu", "Traders pooling weekly takings.", 6, "250.00", Ending.DEFAULT_AFTER_PAYOUT),
            new Plan("Office Colleagues Thrift", "Saving between paydays.", 8, "150.00", Ending.COMPLETED),
            new Plan("Wedding Fund Circle", "Helping each other with wedding costs.", 5, "500.00", Ending.COMPLETED),
            new Plan("Church Women's Ajo", "The Sunday group's savings.", 10, "100.00", Ending.COMPLETED),
            new Plan("Rent Advance Circle", "Getting ahead of the yearly rent.", 6, "300.00",
                    Ending.WITHDRAWAL_BEFORE_PAYOUT),
            new Plan("Business Expansion Circle", "Small business owners growing together.", 8, "200.00",
                    Ending.COMPLETED));

    private static final Plan DEMO_ACTIVE = new Plan("December Savings",
            "Ten members putting aside for the holidays.", 10, "200.00", Ending.COMPLETED);
    private static final int DEMO_ACTIVE_ROUNDS_PROCESSED = 5;

    private record Community(String name, String description, int size, String contribution, int roundsProcessed) {
    }

    private static final List<Community> COMMUNITY_ACTIVE = List.of(
            new Community("Lekki Landlords Circle", "Saving for property repairs.", 5, "400.00", 2),
            new Community("Hairdressers' Hustle", "Salon owners saving together.", 6, "120.00", 3));

    private record Forming(String name, String description, int size, String contribution, int joined,
                           Double minReputation) {
    }

    private static final List<Forming> OPEN_CIRCLES = List.of(
            new Forming("School Fees Circle", "Saving for the September term.", 5, "250.00", 2, null),
            new Forming("New Year Savings Club", "Starting the year with something put away.", 8, "100.00", 3, null),
            new Forming("Generator Fund", "Saving for a backup generator.", 4, "150.00", 1, null),
            new Forming("Young Professionals Ajo", "First jobs, first savings.", 10, "75.00", 4, null),
            new Forming("Market Women Esusu", "Market traders' weekly contribution.", 6, "200.00", 0, null),
            new Forming("Trusted Members Circle", "For members with a track record.", 6, "500.00", 0, 0.6),
            new Forming("Weekend Savers", "Small, steady weekly savings.", 7, "50.00", 2, null),
            new Forming("Travel Fund Circle", "Saving toward a trip home.", 5, "300.00", 1, null),
            new Forming("Car Down Payment Ajo", "Pooling for a first car.", 8, "400.00", 0, 0.5),
            new Forming("Students' Book Fund", "Textbooks for next semester.", 4, "40.00", 1, null));

    /** Members who can join a new circle. A circle takes some out and returns them when it finishes. */
    private final List<User> available = new ArrayList<>();

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(Constants.Demo.USERNAME)) {
            return;
        }
        if (userRepository.existsByUsername(Constants.Demo.SEEDING_USERNAME)) {
            // The ledger is append-only, so a partial history can't be cleared automatically.
            log.error("A previous demo seed didn't finish, so the demo stays unavailable until it's cleaned up by hand");
            return;
        }
        seed();
        log.info("Seeded the demo account");
    }

    private void seed() {
        User demo = createUser(Constants.Demo.SEEDING_USERNAME, Constants.Demo.USERNAME, "Demo", "Visitor",
                new BigDecimal("2500.00"), new BigDecimal("2000.00"));
        for (int i = 0; i < MEMBER_COUNT; i++) {
            String first = FIRST_NAMES[i % FIRST_NAMES.length];
            String last = LAST_NAMES[(i / FIRST_NAMES.length + i) % LAST_NAMES.length];
            String username = (first + "_" + last).toLowerCase();
            available.add(createUser(username, username, first, last, new BigDecimal("6000.00"),
                    new BigDecimal("5000.00")));
        }

        // Community circles run first so the demo's co-members arrive with varied track records.
        for (Plan plan : COMMUNITY_CIRCLES) {
            runCircle(null, plan);
        }
        for (Community community : COMMUNITY_ACTIVE) {
            Circle circle = startCircle(null, take(community.size()), community.name(), community.description(),
                    community.size(), community.contribution());
            processRounds(circle, community.roundsProcessed());
        }

        for (Plan plan : DEMO_HISTORY) {
            fundDemoFor(demo, plan.size(), plan.contribution());
            runCircle(demo, plan);
            everydayActivity(demo);
        }
        fundDemoFor(demo, DEMO_ACTIVE.size(), DEMO_ACTIVE.contribution());
        Circle active = startCircle(demo, take(DEMO_ACTIVE.size() - 1), DEMO_ACTIVE.name(),
                DEMO_ACTIVE.description(), DEMO_ACTIVE.size(), DEMO_ACTIVE.contribution());
        // Left mid-rotation. The real scheduler carries it forward from here.
        processRounds(active, DEMO_ACTIVE_ROUNDS_PROCESSED);

        for (Forming forming : OPEN_CIRCLES) {
            openCircle(forming);
        }

        demo.setUsername(Constants.Demo.USERNAME);
        userRepository.save(demo);
    }

    private User createUser(String username, String emailName, String firstName, String lastName,
                            BigDecimal deposit, BigDecimal intoSavings) {
        User user = userRepository.save(User.builder()
                .username(username)
                .firstName(firstName)
                .lastName(lastName)
                .email(emailName + "@" + Constants.Demo.EMAIL_DOMAIN)
                .build());
        accountService.provisionDefaultAccounts(user);
        fundingService.postDemoSeedDeposit(user.getId(), deposit, seedKey());
        moveToSavings(user, intoSavings);
        return user;
    }

    private List<User> take(int count) {
        if (available.size() < count) {
            throw new IllegalStateException("Not enough free demo members for a circle of " + count);
        }
        List<User> taken = new ArrayList<>(available.subList(0, count));
        available.subList(0, count).clear();
        return taken;
    }

    /** Runs a circle through every round. Members go back into the pool afterwards, except anyone made to
     * default or withdraw, since their savings were emptied to cause it. */
    private void runCircle(User demo, Plan plan) {
        List<User> members = take(demo == null ? plan.size() : plan.size() - 1);
        Circle circle = startCircle(demo, members, plan.name(), plan.description(), plan.size(), plan.contribution());

        Optional<User> leaver = Optional.empty();
        for (int round = 0; round < plan.size() + 2 && statusOf(circle) == CircleStatus.ACTIVE; round++) {
            if (leaver.isEmpty()) {
                leaver = arrangeEnding(circle, members, plan.ending());
            }
            circleRoundService.processRoundDeadline(circle.getId());
        }
        if (statusOf(circle) != CircleStatus.COMPLETED) {
            throw new IllegalStateException("Demo circle " + plan.name() + " didn't complete its rotation");
        }
        leaver.ifPresent(members::remove);
        available.addAll(members);
    }

    /** Emptying a member's savings means the next sweep can't collect from them. Whether that's a default
     * or a withdrawal depends only on whether they've already been paid, exactly as for real members. */
    private Optional<User> arrangeEnding(Circle circle, List<User> members, Ending ending) {
        if (ending == Ending.COMPLETED) {
            return Optional.empty();
        }
        Set<UUID> paid = circleRoundService.getPaidRecipientIds(circle.getId());
        boolean wantPaid = ending == Ending.DEFAULT_AFTER_PAYOUT;
        Optional<User> leaver = members.stream()
                .filter(member -> paid.contains(member.getId()) == wantPaid)
                .findFirst();
        leaver.ifPresent(member -> moveOutOfSavings(member, balanceOf(member, UserAccountType.SAVINGS)));
        return leaver;
    }

    /** Filling the last seat activates the circle and pays out its first round. */
    private Circle startCircle(User demo, List<User> members, String name, String description, int size,
                               String contribution) {
        User creator = demo != null ? demo : members.get(0);
        Circle circle = circleService.createCircle(creator.getId(),
                fixedSize(name, description, size, contribution, null));
        for (User member : members) {
            if (!member.getId().equals(creator.getId())) {
                circleService.joinCircle(member.getId(), circle.getId());
            }
        }
        return circle;
    }

    private void processRounds(Circle circle, int rounds) {
        for (int round = 0; round < rounds; round++) {
            circleRoundService.processRoundDeadline(circle.getId());
        }
    }

    private void openCircle(Forming forming) {
        List<User> members = take(forming.joined() + 1);
        Circle circle = circleService.createCircle(members.get(0).getId(), fixedSize(forming.name(),
                forming.description(), forming.size(), forming.contribution(), forming.minReputation()));
        for (User joiner : members.subList(1, members.size())) {
            circleService.joinCircle(joiner.getId(), circle.getId());
        }
    }

    /** Tops up savings so the demo can cover every round of the next circle, the way someone would
     * before committing to one. */
    private void fundDemoFor(User demo, int size, String contribution) {
        BigDecimal needed = new BigDecimal(contribution).multiply(BigDecimal.valueOf(size + 1L));
        BigDecimal shortfall = needed.subtract(balanceOf(demo, UserAccountType.SAVINGS));
        if (shortfall.signum() > 0) {
            BigDecimal deposit = shortfall.add(new BigDecimal("250.00"));
            fundingService.postDemoSeedDeposit(demo.getId(), deposit, seedKey());
            moveToSavings(demo, shortfall);
        }
    }

    /** The smaller movements that fill out a real account's history between circles. */
    private void everydayActivity(User demo) {
        User friend = available.get(0);
        ledgerService.postTransferToUser(demo.getId(), UserAccountType.CHECKING, friend.getUsername(),
                UserAccountType.CHECKING, new BigDecimal("40.00"), seedKey());
        ledgerService.postTransferToUser(friend.getId(), UserAccountType.CHECKING, demo.getUsername(),
                UserAccountType.CHECKING, new BigDecimal("65.00"), seedKey());
        circleTransactionsService.postLienHold(demo.getId(), new BigDecimal("25.00"), seedKey());
        moveOutOfSavings(demo, new BigDecimal("50.00"));
        available.add(available.remove(0));
    }

    private void moveToSavings(User user, BigDecimal amount) {
        ledgerService.postTransfer(user.getId(), UserAccountType.CHECKING, user.getId(), UserAccountType.SAVINGS,
                amount, seedKey());
    }

    private void moveOutOfSavings(User user, BigDecimal amount) {
        if (amount.signum() > 0) {
            ledgerService.postTransfer(user.getId(), UserAccountType.SAVINGS, user.getId(), UserAccountType.CHECKING,
                    amount, seedKey());
        }
    }

    private BigDecimal balanceOf(User user, UserAccountType type) {
        return userAccountRepository.requireByUserIdAndType(user.getId(), type).getBalance();
    }

    private CircleStatus statusOf(Circle circle) {
        return circleRepository.findById(circle.getId()).orElseThrow().getStatus();
    }

    private static CircleCreateRequestDTO fixedSize(String name, String description, int size, String contribution,
                                                    Double minReputation) {
        return CircleCreateRequestDTO.builder()
                .name(name)
                .description(description)
                .sizingPolicyType(CircleSizingPolicyType.FIXED_SIZE)
                .maxMembers(size)
                .term(1)
                .contributionAmount(new BigDecimal(contribution))
                .gated(false)
                .minReputationScore(minReputation)
                .build();
    }

    private static String seedKey() {
        return "demo-seed:" + UUID.randomUUID();
    }
}

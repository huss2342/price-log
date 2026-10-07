package app.pricelog.api.web;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.MatchType;
import app.pricelog.api.domain.SaleSignal;
import app.pricelog.api.domain.TagRule;
import app.pricelog.api.repo.PriceObservationRepository;
import app.pricelog.api.repo.StoreRepository;
import app.pricelog.api.repo.TagRuleRepository;
import app.pricelog.api.repo.UserProductWatchRepository;
import app.pricelog.api.security.AppUser;
import app.pricelog.api.security.CurrentUser;
import app.pricelog.api.security.JwtService;
import app.pricelog.api.security.UserContext;
import app.pricelog.api.security.UserRepository;
import app.pricelog.api.storage.PhotoStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Accounts. A Bearer token from here is how the PWA signs in; the legacy
 * X-API-Key keeps working and acts as the owner.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "auth", description = "Accounts: register, sign in, profile, password, delete")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository users;
    private final BCryptPasswordEncoder passwords;
    private final JwtService jwt;
    private final TagRuleRepository rules;
    private final PriceObservationRepository observations;
    private final StoreRepository stores;
    private final UserProductWatchRepository watches;
    private final PhotoStore photos;
    private final UserContext context;

    public AuthController(UserRepository users,
                          BCryptPasswordEncoder passwords,
                          JwtService jwt,
                          TagRuleRepository rules,
                          PriceObservationRepository observations,
                          StoreRepository stores,
                          UserProductWatchRepository watches,
                          PhotoStore photos,
                          UserContext context) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.rules = rules;
        this.observations = observations;
        this.stores = stores;
        this.watches = watches;
        this.photos = photos;
        this.context = context;
    }

    public record RegisterRequest(String email, String password, String displayName) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record PasswordChangeRequest(String currentPassword, String newPassword) {
    }

    public record AuthResponse(Long id, String email, String displayName, String token) {
    }

    public record ProfileResponse(Long id, String email, String displayName, Instant createdAt) {
    }

    @Operation(summary = "Create an account and sign in")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AuthResponse register(@RequestBody(required = false) RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("A request body is required.");
        }
        String email = normalizedEmail(request.email());
        String password = request.password();
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new UnprocessableException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (users.findByEmail(email).isPresent()) {
            throw new IllegalStateException("An account with that email already exists.");
        }

        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? email.substring(0, email.indexOf('@'))
                : request.displayName().trim();

        AppUser user = new AppUser(email, passwords.encode(password), displayName);
        user = users.save(user);

        seedTagRules(user);

        return new AuthResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                jwt.issue(user));
    }

    @Operation(summary = "Sign in with email and password")
    @PostMapping("/login")
    public AuthResponse login(@RequestBody(required = false) LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("A request body is required.");
        }
        String email = request.email() == null ? "" : request.email().trim().toLowerCase();
        String password = request.password();
        AppUser user = users.findByEmail(email).orElse(null);
        // Same response for unknown email and wrong password: no oracle for
        // which addresses have accounts.
        if (user == null || user.getPasswordHash() == null || password == null
                || !passwords.matches(password, user.getPasswordHash())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Email or password is not correct.");
        }
        return new AuthResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                jwt.issue(user));
    }

    @Operation(summary = "The signed-in account")
    @GetMapping("/me")
    public ProfileResponse me() {
        CurrentUser current = context.require();
        AppUser user = users.findById(current.id())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Sign in to continue."));
        return new ProfileResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getCreatedAt());
    }

    @Operation(summary = "Change the account password")
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void changePassword(@RequestBody(required = false) PasswordChangeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("A request body is required.");
        }
        CurrentUser current = context.require();
        AppUser user = users.findById(current.id())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Sign in to continue."));

        if (request.currentPassword() == null || user.getPasswordHash() == null
                || !passwords.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ForbiddenException("The current password is not correct.");
        }
        if (request.newPassword() == null || request.newPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new UnprocessableException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        user.setPasswordHash(passwords.encode(request.newPassword()));
        users.save(user);
    }

    @Operation(summary = "Delete the account and everything it logged")
    @DeleteMapping("/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteAccount() {
        CurrentUser current = context.require();
        Long userId = current.id();

        // Photos first: their keys live on the observation rows being deleted.
        List<String> photoKeys = observations.findPhotoUrlsByUserId(userId);

        observations.deleteAll(observations.findByUserIdOrderByObservedOnDescIdDesc(userId));
        stores.deleteAll(stores.findByUserIdOrderByChainAscLabelAsc(userId));
        rules.deleteAll(rules.findByUserId(userId));
        watches.deleteByUserId(userId);

        // Storage is not transactional; a stranded file is not worth failing
        // the deletion over, so failures are logged and swallowed.
        for (String key : photoKeys) {
            try {
                photos.delete(key);
            } catch (RuntimeException e) {
                log.warn("Deleted the account but could not delete its photo {}", key, e);
            }
        }

        users.deleteById(userId);
    }

    private String normalizedEmail(String raw) {
        String email = raw == null ? "" : raw.trim().toLowerCase();
        if (email.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(email).matches()) {
            throw new UnprocessableException("That email address does not look valid.");
        }
        return email;
    }

    /**
     * A new account starts with the owner's tag conventions, so day one reads
     * tags as well as the household's own history does. When the owner has no
     * rules (fresh database), the V2 seed content is used instead -- kept here
     * as a constant rather than re-running the migration.
     */
    private void seedTagRules(AppUser user) {
        List<TagRule> ownerRules = rules.findByUserIdOrderByChainAscPriorityAsc(ownerIdExcept(user));
        List<TagRule> seeds = ownerRules.isEmpty()
                ? SEED_RULES.stream().map(s -> s.toRule(user.getId())).toList()
                : ownerRules.stream().map(o -> copyOf(o, user.getId())).toList();
        rules.saveAll(seeds);
    }

    private Long ownerIdExcept(AppUser user) {
        return users.findFirstByOrderByIdAsc()
                .map(AppUser::getId)
                .filter(id -> !id.equals(user.getId()))
                .orElse(-1L);
    }

    private TagRule copyOf(TagRule source, Long userId) {
        TagRule copy = new TagRule();
        copy.setUserId(userId);
        copy.setChain(source.getChain());
        copy.setMatchType(source.getMatchType());
        copy.setPattern(source.getPattern());
        copy.setSignal(source.getSignal());
        copy.setMeaning(source.getMeaning());
        copy.setAdvice(source.getAdvice());
        copy.setPriority(source.getPriority());
        copy.setEnabled(source.isEnabled());
        return copy;
    }

    /** One row of the V2 seed, as code. Do not re-run the migration. */
    private record SeedRule(Chain chain, MatchType matchType, String pattern, SaleSignal signal,
                            String meaning, String advice, int priority) {
        TagRule toRule(Long userId) {
            TagRule rule = new TagRule();
            rule.setUserId(userId);
            rule.setChain(chain);
            rule.setMatchType(matchType);
            rule.setPattern(pattern);
            rule.setSignal(signal);
            rule.setMeaning(meaning);
            rule.setAdvice(advice);
            rule.setPriority(priority);
            rule.setEnabled(true);
            return rule;
        }
    }

    private static final List<SeedRule> SEED_RULES = List.of(
            new SeedRule(Chain.COSTCO, MatchType.MARKER, "ASTERISK", SaleSignal.DISCONTINUED,
                    "Asterisk in the top-right corner: last run of this item, warehouse is not reordering.",
                    "Buy now if you want it. It disappears when the stock is gone, not on a sale cycle.", 10),
            new SeedRule(Chain.COSTCO, MatchType.PRICE_ENDING, ".00", SaleSignal.MANAGER_MARKDOWN,
                    "Price ending in .00: manager markdown, usually the deepest cut on the item.",
                    "Lowest it will go. Often a display or short-dated unit, so check the item.", 20),
            new SeedRule(Chain.COSTCO, MatchType.PRICE_ENDING, ".88", SaleSignal.MANAGER_MARKDOWN,
                    "Price ending in .88: manager markdown, often damaged, returned, or display stock.",
                    "Deep cut, inspect before buying.", 20),
            new SeedRule(Chain.COSTCO, MatchType.PRICE_ENDING, ".97", SaleSignal.CLEARANCE,
                    "Price ending in .97: store-level clearance markdown.",
                    "Genuine markdown, but it may drop again to .00 before it sells out.", 30),
            new SeedRule(Chain.COSTCO, MatchType.PRICE_ENDING, ".49", SaleSignal.INSTANT_SAVINGS,
                    "Price ending in .49 or .79: manufacturer promotion, part of a scheduled sale cycle.",
                    "Recurs. If you can wait, this price usually comes back within a few months.", 40),
            new SeedRule(Chain.COSTCO, MatchType.PRICE_ENDING, ".79", SaleSignal.INSTANT_SAVINGS,
                    "Price ending in .49 or .79: manufacturer promotion, part of a scheduled sale cycle.",
                    "Recurs. If you can wait, this price usually comes back within a few months.", 40),
            new SeedRule(Chain.COSTCO, MatchType.TEXT_CONTAINS, "OFF", SaleSignal.INSTANT_SAVINGS,
                    "Instant savings block with a date range: scheduled manufacturer promotion.",
                    "Runs on a cycle, typically monthly. Expect it again.", 50),
            new SeedRule(Chain.COSTCO, MatchType.PRICE_ENDING, ".99", SaleSignal.REGULAR,
                    "Price ending in .99: standard everyday price, no discount applied.",
                    "Full price. Worth waiting if the item cycles onto instant savings.", 90),
            new SeedRule(Chain.SAMS_CLUB, MatchType.MARKER, "C_MARKER", SaleSignal.CLEARANCE,
                    "A \"C\" printed on the sign: clearance item.",
                    "Being cleared out. Stock will not be replenished at this price.", 10),
            new SeedRule(Chain.SAMS_CLUB, MatchType.PRICE_ENDING, ".01", SaleSignal.CLEARANCE,
                    "Price ending in .01: final markdown, last stage before the item is pulled.",
                    "Cheapest it gets. Buy now or lose it.", 15),
            new SeedRule(Chain.SAMS_CLUB, MatchType.PRICE_ENDING, ".00", SaleSignal.MANAGER_MARKDOWN,
                    "Price ending in .00: manager markdown.",
                    "Real markdown, but a further cut to .01 is still possible.", 25),
            new SeedRule(Chain.SAMS_CLUB, MatchType.TEXT_CONTAINS, "INSTANT SAVINGS", SaleSignal.INSTANT_SAVINGS,
                    "Instant Savings promotion with a date range.",
                    "Scheduled promotion, recurs on a cycle.", 50),
            new SeedRule(Chain.SAMS_CLUB, MatchType.PRICE_ENDING, ".98", SaleSignal.REGULAR,
                    "Price ending in .98: standard everyday price.",
                    "Full price.", 90),
            new SeedRule(Chain.WALMART, MatchType.PRICE_ENDING, ".00", SaleSignal.CLEARANCE,
                    "Price ending in .00 on a yellow tag: clearance.",
                    "Clearance, will not be restocked at this price.", 20),
            new SeedRule(Chain.WALMART, MatchType.TEXT_CONTAINS, "ROLLBACK", SaleSignal.INSTANT_SAVINGS,
                    "Rollback: temporary price reduction, typically 90 days.",
                    "Temporary. Price returns to the regular level when the rollback ends.", 40),
            new SeedRule(Chain.WALMART, MatchType.TEXT_CONTAINS, "CLEARANCE", SaleSignal.CLEARANCE,
                    "Explicit clearance tag.",
                    "Being cleared out.", 20),
            new SeedRule(Chain.ALDI, MatchType.TEXT_CONTAINS, "ALDI FIND", SaleSignal.DISCONTINUED,
                    "Aldi Find (Special Buy): one-time limited stock, not part of the regular range.",
                    "Will not come back. Buy now if you want it.", 10),
            new SeedRule(Chain.ALDI, MatchType.TEXT_CONTAINS, "RED TAG", SaleSignal.CLEARANCE,
                    "Red tag markdown: item being cleared.",
                    "Clearance, stock will not return.", 20));
}

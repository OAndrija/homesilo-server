package com.andrija.homesiloserver.auth;

import com.andrija.homesiloserver.activity.ActivityAction;
import com.andrija.homesiloserver.activity.ActivityRecorder;
import com.andrija.homesiloserver.auth.dto.SetupRequest;
import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.common.exception.BadRequestException;
import com.andrija.homesiloserver.common.exception.ConflictException;
import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserRepository;
import com.andrija.homesiloserver.user.UserRole;
import com.andrija.homesiloserver.user.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Creating the first admin on a fresh install.
 *
 * <p>Until someone has an account, the server makes a one-time setup code and
 * prints it in its log, which only whoever runs the server can read. The setup
 * page asks for it, so a stranger who reaches a new server before its owner
 * can't make themselves its admin. The code can also be set with
 * {@code SETUP_CODE} for an automated install. Once the admin exists, setup is
 * closed for good.
 */
@Slf4j
@Service
public class SetupService {

    /** No 0/O or 1/I/L, so the code can be read off a screen and typed without mix-ups. */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 12; // 31^12: about 59 bits
    /** Wrong codes, from one address, before setup is refused for a while. */
    private static final String ATTEMPT_KEY = "@setup";

    private final UserRepository userRepository;
    private final UserService userService;
    private final LoginAttemptService attempts;
    private final ActivityRecorder activity;
    private final HomesiloProperties properties;
    private final TransactionTemplate transactions;
    private final SecureRandom random = new SecureRandom();

    /** The code that may still be used; null once setup is done (or before one is needed). */
    private final AtomicReference<String> code = new AtomicReference<>();

    public SetupService(UserRepository userRepository, UserService userService, LoginAttemptService attempts,
                        ActivityRecorder activity, HomesiloProperties properties,
                        PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.attempts = attempts;
        this.activity = activity;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void announceIfNeeded() {
        if (isSetupRequired()) currentCode();
    }

    public boolean isSetupRequired() {
        return userRepository.count() == 0;
    }

    /** Creates the first admin if the setup code is right. */
    public User setUp(SetupRequest request, String clientAddress) {
        attempts.assertAllowed(ATTEMPT_KEY, clientAddress);
        if (!isSetupRequired()) throw new ConflictException("HomeSilo is already set up. Sign in instead.");

        String expected = currentCode();
        if (!matches(request.setupCode(), expected)) {
            attempts.recordFailure(ATTEMPT_KEY, clientAddress);
            log.warn("Wrong setup code from {}", clientAddress);
            throw new BadRequestException("That setup code isn't right. Check the server log for the current one.");
        }
        // Taking the code first means two people sending it at once can't both become admin.
        if (!code.compareAndSet(expected, null)) {
            throw new ConflictException("HomeSilo is already set up. Sign in instead.");
        }
        try {
            User admin = transactions.execute(status -> {
                if (userRepository.count() > 0) throw new ConflictException("HomeSilo is already set up. Sign in instead.");
                return userService.createAccount(request.username(), request.email(), request.password(),
                        UserRole.ADMIN, properties.storage().defaultQuota().toBytes(), true);
            });
            attempts.recordSuccess(ATTEMPT_KEY, clientAddress);
            log.info("HomeSilo set up: '{}' is the admin", admin.getUsername());
            activity.recordAs(admin, ActivityAction.SERVER_SET_UP);
            return admin;
        } catch (RuntimeException ex) {
            // A taken username or a bad email: the same code works for the next try.
            code.compareAndSet(null, expected);
            throw ex;
        }
    }

    /** The code for now, making (and printing) one the first time it's needed. */
    private String currentCode() {
        String configured = properties.auth().setupCode();
        String existing = code.get();
        if (existing != null) return existing;
        String fresh = configured.isBlank() ? generate() : configured.trim();
        if (code.compareAndSet(null, fresh)) {
            if (configured.isBlank()) {
                log.warn("""

                        ============================================================
                          HomeSilo has no accounts yet. To create the admin, open
                          HomeSilo in a browser and enter this setup code:

                              {}

                          It changes every time the server starts, until setup is done.
                        ============================================================
                        """, fresh);
            } else {
                log.warn("HomeSilo has no accounts yet. Open it in a browser and use the SETUP_CODE you set to create the admin.");
            }
            return fresh;
        }
        return code.get();
    }

    private String generate() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (i > 0 && i % 4 == 0) out.append('-');
            out.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return out.toString();
    }

    /** Ignores case, spaces and dashes; compares in constant time. */
    static boolean matches(String given, String expected) {
        if (given == null || expected == null) return false;
        byte[] a = normalize(given).getBytes(StandardCharsets.UTF_8);
        byte[] b = normalize(expected).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    private static String normalize(String code) {
        return code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}

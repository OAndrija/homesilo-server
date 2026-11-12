package com.andrija.homesiloserver.auth;

import com.andrija.homesiloserver.activity.ActivityAction;
import com.andrija.homesiloserver.activity.ActivityRecorder;
import com.andrija.homesiloserver.auth.dto.LoginRequest;
import com.andrija.homesiloserver.auth.dto.RegisterRequest;
import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.common.exception.ForbiddenException;
import com.andrija.homesiloserver.common.exception.TooManyRequestsException;
import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserRepository;
import com.andrija.homesiloserver.user.UserRole;
import com.andrija.homesiloserver.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserService userService;
    private final LoginAttemptService loginAttempts;
    private final HomesiloProperties properties;
    private final ActivityRecorder activity;

    /**
     * Creates an account. The very first account becomes the administrator, so a
     * fresh install can always be claimed even with registration disabled.
     */
    @Transactional
    public User register(RegisterRequest request) {
        boolean firstUser = userRepository.count() == 0;
        if (!firstUser && !properties.auth().registrationEnabled()) {
            throw new ForbiddenException("Registration is disabled on this server");
        }

        User user = userService.createAccount(request.username(), request.email(), request.password(),
                firstUser ? UserRole.ADMIN : UserRole.USER, properties.storage().defaultQuota().toBytes(), true);

        log.info("Registered user '{}' ({}) with role {}", user.getUsername(), user.getId(), user.getRole());
        activity.recordAs(user, ActivityAction.SIGNED_UP);
        return user;
    }

    public User login(LoginRequest request, String clientAddress) {
        try {
            loginAttempts.assertAllowed(request.username(), clientAddress);
        } catch (TooManyRequestsException ex) {
            activity.recordSignInProblem(ActivityAction.SIGN_IN_BLOCKED, request.username(),
                    findAccount(request.username()), "too many attempts");
            throw ex;
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            loginAttempts.recordSuccess(request.username(), clientAddress);
            User user = ((ServerUserDetails) authentication.getPrincipal()).getUser();
            userRepository.recordLogin(user.getId(), LocalDateTime.now());
            activity.recordAs(user, ActivityAction.SIGNED_IN);
            return user;
        } catch (BadCredentialsException ex) {
            loginAttempts.recordFailure(request.username(), clientAddress);
            log.warn("Failed sign-in for '{}' from {}", request.username(), clientAddress);
            activity.recordSignInProblem(ActivityAction.SIGN_IN_FAILED, request.username(),
                    findAccount(request.username()), "wrong password");
            throw ex;
        } catch (DisabledException ex) {
            activity.recordSignInProblem(ActivityAction.SIGN_IN_FAILED, request.username(),
                    findAccount(request.username()), "account disabled");
            throw ex;
        }
    }

    /** The account a sign-in attempt was for (by username or email), if there is one. */
    private User findAccount(String usernameOrEmail) {
        String name = usernameOrEmail == null ? "" : usernameOrEmail.trim();
        return userRepository.findByUsername(name)
                .or(() -> userRepository.findByEmailIgnoreCase(name))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean isRegistrationOpen() {
        return properties.auth().registrationEnabled() || userRepository.count() == 0;
    }
}

package com.andrija.homesiloserver.auth;

import com.andrija.homesiloserver.auth.dto.LoginRequest;
import com.andrija.homesiloserver.auth.dto.RegisterRequest;
import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.common.exception.ConflictException;
import com.andrija.homesiloserver.common.exception.ForbiddenException;
import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserRepository;
import com.andrija.homesiloserver.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;
    private final HomesiloProperties properties;

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

        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("Username is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email address is already registered");
        }

        User user = userRepository.save(User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(firstUser ? UserRole.ADMIN : UserRole.USER)
                .storageQuotaBytes(properties.storage().defaultQuota().toBytes())
                .build());

        log.info("Registered user '{}' ({}) with role {}", user.getUsername(), user.getId(), user.getRole());
        return user;
    }

    public User login(LoginRequest request, String clientAddress) {
        loginAttempts.assertAllowed(request.username(), clientAddress);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            loginAttempts.recordSuccess(request.username(), clientAddress);
            return ((ServerUserDetails) authentication.getPrincipal()).getUser();
        } catch (BadCredentialsException ex) {
            loginAttempts.recordFailure(request.username(), clientAddress);
            log.warn("Failed sign-in for '{}' from {}", request.username(), clientAddress);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public boolean isRegistrationOpen() {
        return properties.auth().registrationEnabled() || userRepository.count() == 0;
    }
}

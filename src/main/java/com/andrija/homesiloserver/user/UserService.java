package com.andrija.homesiloserver.user;

import com.andrija.homesiloserver.activity.ActivityAction;
import com.andrija.homesiloserver.activity.ActivityRecorder;
import com.andrija.homesiloserver.common.exception.BadRequestException;
import com.andrija.homesiloserver.common.exception.ConflictException;
import com.andrija.homesiloserver.common.exception.NotFoundException;
import com.andrija.homesiloserver.user.dto.ChangePasswordRequest;
import com.andrija.homesiloserver.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityRecorder activity;

    public User getEntity(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    public UserResponse findById(UUID id) {
        return UserResponse.from(getEntity(id));
    }

    /**
     * Creates an account after checking that the username and email are free (both
     * case-insensitively). Used by sign-up and by admins adding people.
     *
     * @param signedIn whether the person is being signed in right away (sign-up)
     */
    @Transactional
    public User createAccount(String username, String email, String rawPassword, UserRole role,
                              long storageQuotaBytes, boolean signedIn) {
        String cleanUsername = username.trim();
        String cleanEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(cleanUsername)) {
            throw new ConflictException("Username is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new ConflictException("Email address is already registered");
        }
        return userRepository.save(User.builder()
                .username(cleanUsername)
                .email(cleanEmail)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .storageQuotaBytes(storageQuotaBytes)
                .lastLoginAt(signedIn ? LocalDateTime.now() : null)
                .build());
    }

    @Transactional
    public User changePassword(UUID userId, ChangePasswordRequest request) {
        User user = getEntity(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        log.info("User '{}' changed their password", userId);
        activity.recordAs(user, ActivityAction.PASSWORD_CHANGED);
        return user;
    }
}

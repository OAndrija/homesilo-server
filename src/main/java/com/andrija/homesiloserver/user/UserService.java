package com.andrija.homesiloserver.user;

import com.andrija.homesiloserver.common.exception.BadRequestException;
import com.andrija.homesiloserver.common.exception.ConflictException;
import com.andrija.homesiloserver.common.exception.NotFoundException;
import com.andrija.homesiloserver.user.dto.ChangePasswordRequest;
import com.andrija.homesiloserver.user.dto.UpdateUserRequest;
import com.andrija.homesiloserver.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User getEntity(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    public UserResponse findById(UUID id) {
        return UserResponse.from(getEntity(id));
    }

    public List<UserResponse> listAll() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "username"))
                .stream()
                .map(UserResponse::from)
                .toList();
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
        return user;
    }

    @Transactional
    public UserResponse update(UUID actingAdminId, UUID userId, UpdateUserRequest request) {
        User user = getEntity(userId);
        boolean demotingOrDisablingSelf = userId.equals(actingAdminId)
                && (Boolean.FALSE.equals(request.enabled())
                || (request.role() != null && request.role() != UserRole.ADMIN));
        if (demotingOrDisablingSelf) {
            throw new ConflictException("You cannot disable or demote your own account");
        }

        if (request.enabled() != null) user.setEnabled(request.enabled());
        if (request.role() != null) user.setRole(request.role());
        if (request.storageQuotaBytes() != null) user.setStorageQuotaBytes(request.storageQuotaBytes());

        log.info("Admin '{}' updated user '{}': {}", actingAdminId, userId, request);
        return UserResponse.from(user);
    }
}

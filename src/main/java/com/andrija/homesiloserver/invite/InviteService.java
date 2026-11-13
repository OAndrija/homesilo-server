package com.andrija.homesiloserver.invite;

import com.andrija.homesiloserver.activity.ActivityAction;
import com.andrija.homesiloserver.activity.ActivityRecorder;
import com.andrija.homesiloserver.common.exception.BadRequestException;
import com.andrija.homesiloserver.common.exception.NotFoundException;
import com.andrija.homesiloserver.invite.dto.CreateInviteRequest;
import com.andrija.homesiloserver.invite.dto.CreatedInviteResponse;
import com.andrija.homesiloserver.invite.dto.InvitePreviewResponse;
import com.andrija.homesiloserver.invite.dto.InviteResponse;
import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserRepository;
import com.andrija.homesiloserver.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Invite links: an admin makes one, sends it, and whoever opens it can create
 * one account, even while sign-up is closed. Each link works once and expires.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InviteService {

    static final int DEFAULT_DAYS = 7;
    /** Open invites at a time, so a stolen admin session can't mint them without limit. */
    static final int MAX_PENDING = 50;
    /** Used and expired invites stay in the list this long. */
    static final int KEEP_FINISHED_DAYS = 30;
    private static final String INVALID = "This invite link has expired or has already been used. "
            + "Ask whoever sent it for a new one.";

    private final InviteRepository invites;
    private final UserRepository users;
    private final ActivityRecorder activity;
    private final SecureRandom random = new SecureRandom();

    public CreatedInviteResponse create(UUID adminId, CreateInviteRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (invites.countPending(now) >= MAX_PENDING) {
            throw new BadRequestException("There are already " + MAX_PENDING
                    + " open invites. Revoke some you don't need before making more.");
        }
        String token = newToken();
        Invite invite = new Invite();
        invite.setTokenHash(hash(token));
        invite.setRole(request.role() != null ? request.role() : UserRole.USER);
        invite.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        invite.setCreatedBy(users.getReferenceById(adminId));
        invite.setCreatedAt(now);
        invite.setExpiresAt(now.plusDays(request.expiresInDays() != null ? request.expiresInDays() : DEFAULT_DAYS));
        invites.save(invite);

        activity.record(ActivityAction.INVITE_CREATED, null, invite.getId(), invite.getNote(),
                null, invite.getRole().name());
        log.info("Invite {} made by {} ({})", invite.getId(), adminId, invite.getRole());
        return new CreatedInviteResponse(InviteResponse.from(invites.findById(invite.getId()).orElseThrow(), now), token);
    }

    @Transactional(readOnly = true)
    public List<InviteResponse> list() {
        LocalDateTime now = LocalDateTime.now();
        return invites.findAllByOrderByCreatedAtDesc().stream().map(i -> InviteResponse.from(i, now)).toList();
    }

    /** Takes back an invite. Used ones stay in the list as a record of who joined how. */
    public void revoke(UUID inviteId) {
        Invite invite = invites.findById(inviteId).orElseThrow(() -> new NotFoundException("Invite not found"));
        if (invite.getUsedAt() != null) {
            throw new BadRequestException("This invite has already been used, so it can't be revoked");
        }
        invites.delete(invite);
        activity.record(ActivityAction.INVITE_REVOKED, null, inviteId, invite.getNote());
    }

    /** For the sign-up page: who sent the link, if it still works. */
    @Transactional(readOnly = true)
    public InvitePreviewResponse preview(String token) {
        Invite invite = findUsable(token);
        return new InvitePreviewResponse(
                invite.getCreatedBy() != null ? invite.getCreatedBy().getUsername() : null, invite.getExpiresAt());
    }

    /**
     * Uses up an invite for a new account and says what role it gives. Runs in
     * the caller's transaction: if the account can't be made after all, the
     * invite is left unused.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Invite claim(String token) {
        String tokenHash = hashOrNull(token);
        if (tokenHash == null || invites.claim(tokenHash, LocalDateTime.now()) == 0) {
            throw new BadRequestException(INVALID);
        }
        return invites.findByTokenHash(tokenHash).orElseThrow();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordUse(Invite invite, User newUser) {
        invite.setUsedBy(newUser);
        invites.save(invite);
    }

    public int deleteFinished() {
        return invites.deleteFinishedBefore(LocalDateTime.now().minusDays(KEEP_FINISHED_DAYS));
    }

    private Invite findUsable(String token) {
        String tokenHash = hashOrNull(token);
        return (tokenHash == null ? java.util.Optional.<Invite>empty() : invites.findByTokenHash(tokenHash))
                .filter(i -> i.status(LocalDateTime.now()) == InviteStatus.PENDING)
                .orElseThrow(() -> new NotFoundException(INVALID));
    }

    /** 256 random bits, URL-safe. */
    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Null for anything that can't be one of our tokens, so it never reaches the database. */
    private static String hashOrNull(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
        return hash(token);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

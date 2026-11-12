package com.andrija.homesiloserver.activity;

import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.user.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What services call to add to the activity log. The person acting and their IP
 * address come from the current request; the entry is written only if the change
 * commits (see {@link ActivityWriter}), so failed operations never show up as done.
 */
@Component
@RequiredArgsConstructor
public class ActivityRecorder {

    private static final int MAX_TEXT = 1024;

    private final ApplicationEventPublisher events;

    /** By whoever is signed in. */
    public void record(ActivityAction action, TargetType targetType, UUID targetId, String targetName) {
        record(action, targetType, targetId, targetName, null, null);
    }

    /** By whoever is signed in, with a before and after value (e.g. old and new name). */
    public void record(ActivityAction action, TargetType targetType, UUID targetId, String targetName,
                       String oldValue, String newValue) {
        ServerUserDetails actor = currentUser();
        publish(actor != null ? actor.getId() : null, actor != null ? actor.getUsername() : null,
                action, targetType, targetId, targetName, oldValue, newValue);
    }

    /** By a specific account, for moments when nobody is signed in yet (sign-up, sign-in). */
    public void recordAs(User actor, ActivityAction action) {
        publish(actor.getId(), actor.getUsername(), action, TargetType.USER, actor.getId(), actor.getUsername(), null, null);
    }

    /** A sign-in that didn't succeed. {@code account} is the account it was for, if it exists. */
    public void recordSignInProblem(ActivityAction action, String attemptedName, User account, String reason) {
        publish(null, null, action, TargetType.USER, account != null ? account.getId() : null,
                account != null ? account.getUsername() : attemptedName, null, reason);
    }

    /** By HomeSilo itself (scheduled housekeeping). */
    public void recordSystem(ActivityAction action, String newValue) {
        publish(null, null, action, null, null, null, null, newValue);
    }

    private void publish(UUID actorId, String actorUsername, ActivityAction action, TargetType targetType,
                         UUID targetId, String targetName, String oldValue, String newValue) {
        events.publishEvent(new ActivityEvent(LocalDateTime.now(), actorId, actorUsername, action, targetType,
                targetId, clip(targetName), clip(oldValue), clip(newValue), clientAddress()));
    }

    private static ServerUserDetails currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof ServerUserDetails details ? details : null;
    }

    /** The caller's address (nginx's X-Forwarded-For is applied by forward-headers-strategy). */
    private static String clientAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return clip(request.getRemoteAddr(), 64);
        }
        return null;
    }

    private static String clip(String text) {
        return clip(text, MAX_TEXT);
    }

    private static String clip(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}

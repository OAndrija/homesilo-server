package com.andrija.homesiloserver.activity;

import com.andrija.homesiloserver.activity.dto.ActivityResponse;
import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    static final int MAX_PAGE_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "occurredAt");

    private final ActivityRepository repository;
    private final HomesiloProperties properties;

    /**
     * The whole server's log, newest first.
     *
     * @param query    matches the person acting or the name of what it was about
     * @param userId   entries by this account or about it
     */
    public PageResponse<ActivityResponse> search(String query, ActivityCategory category, UUID userId, int page, int size) {
        Specification<ActivityEntry> spec = (root, q, cb) -> cb.conjunction();
        if (query != null && !query.isBlank()) {
            String pattern = "%" + escapeLike(query.trim().toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("actorUsername")), pattern, '\\'),
                    cb.like(cb.lower(root.get("targetName")), pattern, '\\')));
        }
        if (category != null) {
            Set<ActivityAction> actions = ActivityAction.in(category);
            spec = spec.and((root, q, cb) -> root.get("action").in(actions));
        }
        if (userId != null) {
            spec = spec.and(involving(userId));
        }
        return page(spec, page, size);
    }

    /** One person's own account history: sign-ins, password changes and what admins did to the account. */
    public PageResponse<ActivityResponse> forAccount(UUID userId, int page, int size) {
        Set<ActivityAction> fileActions = ActivityAction.in(ActivityCategory.FILES);
        Specification<ActivityEntry> spec = involving(userId)
                .and((root, q, cb) -> cb.not(root.get("action").in(fileActions)));
        return page(spec, page, size);
    }

    /** Deletes entries older than the retention period (0 keeps them forever). */
    @Transactional
    public int purgeExpired() {
        int days = properties.activity().retentionDays();
        if (days <= 0) return 0;
        int removed = repository.deleteOlderThan(LocalDateTime.now().minusDays(days));
        if (removed > 0) log.info("Activity retention ({} days): removed {} entries", days, removed);
        return removed;
    }

    private PageResponse<ActivityResponse> page(Specification<ActivityEntry> spec, int page, int size) {
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), NEWEST_FIRST);
        return PageResponse.from(repository.findAll(spec, request).map(ActivityResponse::from));
    }

    /** By the account, or about it (an admin changing it, someone trying its password). */
    private static Specification<ActivityEntry> involving(UUID userId) {
        return (root, q, cb) -> cb.or(
                cb.equal(root.get("actorId"), userId),
                cb.and(cb.equal(root.get("targetType"), TargetType.USER), cb.equal(root.get("targetId"), userId)));
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

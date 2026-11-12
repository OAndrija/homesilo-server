package com.andrija.homesiloserver.admin;

import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserRole;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/** Optional filters for the admin's user list; a null argument means "any". */
final class UserSpecifications {

    private UserSpecifications() {
    }

    static Specification<User> matching(String query, UserRole role, AccountStatus status) {
        Specification<User> spec = (root, q, cb) -> cb.conjunction();
        if (query != null && !query.isBlank()) {
            String pattern = "%" + escapeLike(query.trim().toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("username")), pattern, '\\'),
                    cb.like(cb.lower(root.get("email")), pattern, '\\')));
        }
        if (role != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("role"), role));
        }
        if (status != null) {
            boolean enabled = status == AccountStatus.ACTIVE;
            spec = spec.and((root, q, cb) -> cb.equal(root.get("enabled"), enabled));
        }
        return spec;
    }

    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

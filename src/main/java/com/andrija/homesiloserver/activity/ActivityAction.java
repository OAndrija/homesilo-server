package com.andrija.homesiloserver.activity;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Everything the activity log records. Starring and viewing aren't logged: they
 * change nothing anyone would need to trace later.
 */
public enum ActivityAction {
    /** newValue: who invited them, when they joined with an invite link. */
    SIGNED_UP(ActivityCategory.ACCOUNT),
    SIGNED_IN(ActivityCategory.ACCOUNT),
    /** Wrong password or disabled account; the target is the account, if it exists. */
    SIGN_IN_FAILED(ActivityCategory.ACCOUNT),
    /** Refused because of too many recent failures. */
    SIGN_IN_BLOCKED(ActivityCategory.ACCOUNT),
    SIGNED_OUT(ActivityCategory.ACCOUNT),
    PASSWORD_CHANGED(ActivityCategory.ACCOUNT),

    /** newValue: size in bytes. */
    FILE_UPLOADED(ActivityCategory.FILES),
    /** oldValue → newValue: names. */
    FILE_RENAMED(ActivityCategory.FILES),
    /** oldValue → newValue: folder names; null means My Files. */
    FILE_MOVED(ActivityCategory.FILES),
    FILE_TRASHED(ActivityCategory.FILES),
    FILE_RESTORED(ActivityCategory.FILES),
    FILE_DELETED(ActivityCategory.FILES),
    FOLDER_CREATED(ActivityCategory.FILES),
    FOLDER_RENAMED(ActivityCategory.FILES),
    FOLDER_MOVED(ActivityCategory.FILES),
    FOLDER_TRASHED(ActivityCategory.FILES),
    FOLDER_RESTORED(ActivityCategory.FILES),
    /** newValue: number of files deleted with it. */
    FOLDER_DELETED(ActivityCategory.FILES),
    /** newValue: number of items deleted. */
    TRASH_EMPTIED(ActivityCategory.FILES),
    /** By HomeSilo itself (no actor): items past the trash retention. newValue: number of items. */
    TRASH_EXPIRED(ActivityCategory.FILES),

    /** The first admin created this HomeSilo with the setup code. */
    SERVER_SET_UP(ActivityCategory.ADMIN),
    USER_CREATED(ActivityCategory.ADMIN),
    /** targetName: the invite's note, if any. newValue: the role it gives (ADMIN or USER). */
    INVITE_CREATED(ActivityCategory.ADMIN),
    /** targetName: the invite's note, if any. */
    INVITE_REVOKED(ActivityCategory.ADMIN),
    /** oldValue → newValue: ADMIN or USER. */
    USER_ROLE_CHANGED(ActivityCategory.ADMIN),
    /** oldValue → newValue: bytes. */
    USER_STORAGE_LIMIT_CHANGED(ActivityCategory.ADMIN),
    USER_DISABLED(ActivityCategory.ADMIN),
    USER_ENABLED(ActivityCategory.ADMIN),
    USER_PASSWORD_RESET(ActivityCategory.ADMIN),
    /** newValue: number of files deleted with the account. */
    USER_DELETED(ActivityCategory.ADMIN);

    private final ActivityCategory category;

    ActivityAction(ActivityCategory category) {
        this.category = category;
    }

    public ActivityCategory category() {
        return category;
    }

    public static Set<ActivityAction> in(ActivityCategory category) {
        return Arrays.stream(values()).filter(a -> a.category == category).collect(Collectors.toSet());
    }
}

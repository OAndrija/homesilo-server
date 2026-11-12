package com.andrija.homesiloserver.activity;

/** How the activity log groups actions, for filtering. */
public enum ActivityCategory {
    /** Signing in and out, sign-ups, password changes. */
    ACCOUNT,
    /** Files, folders and the trash. */
    FILES,
    /** Admins managing accounts. */
    ADMIN
}

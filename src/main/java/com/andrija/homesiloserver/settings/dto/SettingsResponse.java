package com.andrija.homesiloserver.settings.dto;

/**
 * The server's settings as admins see them.
 *
 * @param registrationOpen      anyone can create an account; otherwise it takes an invite
 * @param defaultQuotaBytes     storage for each new account
 * @param trashRetentionDays    days before trashed items are deleted; 0 keeps them
 * @param activityRetentionDays days the activity log is kept; 0 keeps it forever
 * @param maxUploadBytes        largest single file; set with MAX_UPLOAD_SIZE, not here
 */
public record SettingsResponse(
        boolean registrationOpen,
        long defaultQuotaBytes,
        int trashRetentionDays,
        int activityRetentionDays,
        long maxUploadBytes
) {}

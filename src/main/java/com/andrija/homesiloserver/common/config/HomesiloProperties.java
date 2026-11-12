package com.andrija.homesiloserver.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * All application-specific settings, bound from {@code homesilo.*}. Validated at
 * startup so a misconfigured deployment fails fast instead of at first request.
 */
@Validated
@ConfigurationProperties(prefix = "homesilo")
public record HomesiloProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull @DefaultValue Auth auth,
        @Valid @NotNull Storage storage,
        @Valid @NotNull @DefaultValue Trash trash,
        @Valid @NotNull @DefaultValue Cors cors,
        @Valid @NotNull @DefaultValue Activity activity
) {

    public record Jwt(
            /* HMAC signing key; at least 32 bytes (256 bits). */
            @NotBlank @Size(min = 32, message = "homesilo.jwt.secret must be at least 32 characters") String secret,
            @DefaultValue("7d") @NotNull Duration expiration
    ) {}

    public record Auth(
            @DefaultValue("homesilo_session") @NotBlank String cookieName,
            /* Set to true when HomeSilo is served over HTTPS. */
            @DefaultValue("false") boolean cookieSecure,
            /* When false, only the very first account (the admin) can self-register. */
            @DefaultValue("true") boolean registrationEnabled,
            @DefaultValue("5") @Positive int maxLoginAttempts,
            @DefaultValue("15m") @NotNull Duration loginLockout
    ) {}

    public record Storage(
            @NotBlank String basePath,
            @DefaultValue("100GB") @NotNull DataSize defaultQuota,
            /* Where image thumbnails are cached. Safe to delete; they are made again on demand. */
            @DefaultValue("./data/thumbnails") @NotBlank String thumbnailPath
    ) {}

    public record Trash(
            /* Items in trash longer than this are purged automatically. 0 disables purging. */
            @DefaultValue("30") @Min(0) int retentionDays,
            @DefaultValue("0 30 3 * * *") @NotBlank String purgeCron
    ) {}

    /**
     * Origins allowed to call the API cross-origin. Empty (the default) disables
     * CORS handling: the bundled nginx and the Angular dev proxy both serve UI and
     * API from one origin, so it is only needed for a UI hosted elsewhere.
     */
    public record Cors(
            @DefaultValue List<String> allowedOrigins
    ) {}

    public record Activity(
            /* Activity log entries older than this are deleted nightly. 0 keeps them forever. */
            @DefaultValue("90") @Min(0) int retentionDays
    ) {}
}

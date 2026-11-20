package com.andrija.homesiloserver.backup;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Nightly database backups (homesilo.backup.*).
 *
 * @param enabled whether to make them at all
 * @param path    where the dumps go; give it its own volume
 * @param keep    how many to keep; older ones are deleted after each new one
 * @param cron    when (Spring cron: second minute hour day month weekday). Before
 *                the nightly clean-up at 03:30, so the dump and a file backup
 *                taken soon after it agree on which files exist
 * @param command the pg_dump to run; its major version must be at least the database's
 */
@Validated
@ConfigurationProperties("homesilo.backup")
public record BackupProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("./data/backups") @NotBlank String path,
        @DefaultValue("7") @Min(1) int keep,
        @DefaultValue("0 0 2 * * *") @NotBlank String cron,
        @DefaultValue("pg_dump") @NotBlank String command
) {}

package com.andrija.homesiloserver.settings;

import com.andrija.homesiloserver.activity.ActivityAction;
import com.andrija.homesiloserver.activity.ActivityRecorder;
import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.settings.dto.SettingsRequest;
import com.andrija.homesiloserver.settings.dto.SettingsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Settings admins can change while HomeSilo runs. Each starts from its
 * environment variable (REGISTRATION_ENABLED, DEFAULT_QUOTA,
 * TRASH_RETENTION_DAYS, ACTIVITY_RETENTION_DAYS); once an admin changes it in
 * the app, the stored value wins. Read often (every sign-up, every night), so
 * the stored values are kept in memory; HomeSilo runs as a single instance.
 */
@Slf4j
@Service
public class ServerSettings {

    static final String REGISTRATION_OPEN = "registration.open";
    static final String DEFAULT_QUOTA = "storage.default-quota-bytes";
    static final String TRASH_RETENTION = "trash.retention-days";
    static final String ACTIVITY_RETENTION = "activity.retention-days";

    private final ServerSettingRepository repository;
    private final HomesiloProperties properties;
    private final ActivityRecorder activity;
    private final long maxUploadBytes;
    private volatile Map<String, String> stored;

    public ServerSettings(ServerSettingRepository repository, HomesiloProperties properties, ActivityRecorder activity,
                          @Value("${spring.servlet.multipart.max-file-size:5GB}") DataSize maxUpload) {
        this.repository = repository;
        this.properties = properties;
        this.activity = activity;
        this.maxUploadBytes = maxUpload.toBytes();
    }

    public boolean registrationOpen() {
        return read(REGISTRATION_OPEN, Boolean::parseBoolean, properties.auth().registrationEnabled());
    }

    public long defaultQuotaBytes() {
        return read(DEFAULT_QUOTA, Long::parseLong, properties.storage().defaultQuota().toBytes());
    }

    public int trashRetentionDays() {
        return read(TRASH_RETENTION, Integer::parseInt, properties.trash().retentionDays());
    }

    public int activityRetentionDays() {
        return read(ACTIVITY_RETENTION, Integer::parseInt, properties.activity().retentionDays());
    }

    public SettingsResponse get() {
        return new SettingsResponse(registrationOpen(), defaultQuotaBytes(), trashRetentionDays(),
                activityRetentionDays(), maxUploadBytes);
    }

    @Transactional
    public SettingsResponse update(SettingsRequest request, UUID adminId) {
        change(REGISTRATION_OPEN, registrationOpen(), request.registrationOpen(), adminId);
        change(DEFAULT_QUOTA, defaultQuotaBytes(), request.defaultQuotaBytes(), adminId);
        change(TRASH_RETENTION, trashRetentionDays(), request.trashRetentionDays(), adminId);
        change(ACTIVITY_RETENTION, activityRetentionDays(), request.activityRetentionDays(), adminId);
        return get();
    }

    private <T> void change(String key, T current, T wanted, UUID adminId) {
        if (wanted == null || Objects.equals(current, wanted)) return;
        ServerSetting setting = repository.findById(key).orElseGet(() -> {
            ServerSetting fresh = new ServerSetting();
            fresh.setKey(key);
            return fresh;
        });
        setting.setValue(String.valueOf(wanted));
        setting.setUpdatedAt(LocalDateTime.now());
        setting.setUpdatedBy(adminId);
        repository.save(setting);
        storedValues().put(key, setting.getValue());
        log.info("Setting {} changed from {} to {} by {}", key, current, wanted, adminId);
        activity.record(ActivityAction.SETTING_CHANGED, null, null, key, String.valueOf(current), String.valueOf(wanted));
    }

    private <T> T read(String key, Function<String, T> parse, T fallback) {
        String value = storedValues().get(key);
        if (value == null) return fallback;
        try {
            return parse.apply(value);
        } catch (RuntimeException ex) {
            log.warn("Ignoring unreadable setting {}={}", key, value);
            return fallback;
        }
    }

    private Map<String, String> storedValues() {
        Map<String, String> values = stored;
        if (values == null) {
            values = new ConcurrentHashMap<>();
            for (ServerSetting setting : repository.findAll()) values.put(setting.getKey(), setting.getValue());
            stored = values;
        }
        return values;
    }
}

package com.andrija.homesiloserver.backup;

import com.andrija.homesiloserver.activity.ActivityAction;
import com.andrija.homesiloserver.activity.ActivityRecorder;
import com.andrija.homesiloserver.backup.dto.BackupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Database backups, for admins (by the /api/v1/admin/** rule). Listing and making them; not downloading. */
@RestController
@RequestMapping("/api/v1/admin/backups")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backups;
    private final ActivityRecorder activity;

    @GetMapping
    public BackupResponse status() {
        return backups.status();
    }

    @PostMapping
    public ResponseEntity<BackupResponse> backUpNow() {
        BackupResponse.Backup made = backups.backUp();
        activity.record(ActivityAction.BACKUP_CREATED, null, null, null, null, made.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(backups.status());
    }
}

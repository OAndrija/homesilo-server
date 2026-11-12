package com.andrija.homesiloserver.activity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes activity once the change it describes has committed, in a transaction of
 * its own. Events published outside a transaction (a failed sign-in) are written
 * straight away. A logging failure never undoes or fails the user's action.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ActivityWriter {

    private final ActivityRepository repository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(ActivityEvent event) {
        try {
            repository.save(ActivityEntry.builder()
                    .occurredAt(event.occurredAt())
                    .actorId(event.actorId())
                    .actorUsername(event.actorUsername())
                    .action(event.action())
                    .targetType(event.targetType())
                    .targetId(event.targetId())
                    .targetName(event.targetName())
                    .oldValue(event.oldValue())
                    .newValue(event.newValue())
                    .ipAddress(event.ipAddress())
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Couldn't write activity {}: {}", event.action(), ex.getMessage());
        }
    }
}

package com.integrate.modules.auth.service.impl;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/** Explicit opt-in. Run on an isolated process without application traffic. */
@Component
@ConditionalOnProperty("integrate.identity.migration")
@Slf4j
class IdentityMigrationRunner implements ApplicationRunner {
    enum Operation { BACKFILL, VERIFY, ROTATE, VERIFY_ROTATION, ANONYMIZE, COUNT_LEGACY_ALIASES, REPLACE_LEGACY_ALIAS }

    private final IdentityMigration migration;
    private final Operation operation;
    private final int batchSize;
    private final String target;
    private final String alias;

    IdentityMigrationRunner(IdentityMigration migration,
                            @Value("${integrate.identity.migration}") Operation operation,
                            @Value("${integrate.identity.batch-size:200}") int batchSize,
                            @Value("${integrate.identity.migration-target:}") String target,
                            @Value("${integrate.identity.new-alias:}") String alias) {
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalStateException("Identity batch size must be between 1 and 1000");
        }
        this.migration = migration;
        this.operation = operation;
        this.batchSize = batchSize;
        this.target = target;
        this.alias = alias;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            switch (operation) {
                case BACKFILL -> backfill();
                case VERIFY -> verify();
                case ROTATE -> rotate();
                case VERIFY_ROTATION -> log.info("Identity rotation verified, rows={}", migration.verifyRotation(batchSize));
                case COUNT_LEGACY_ALIASES -> log.info("Legacy synthetic aliases, rows={}", migration.countLegacyAliases());
                case ANONYMIZE -> {
                    migration.anonymize(targetId());
                    log.info("Identity anonymization finished");
                }
                case REPLACE_LEGACY_ALIAS -> {
                    migration.replaceLegacyAlias(targetId(), alias);
                    log.info("Legacy synthetic alias replaced");
                }
            }
        } catch (DataAccessException e) {
            // A database error can include failed keys or bind parameters.
            throw new IllegalStateException("Identity operation failed at the database");
        }
    }

    private void backfill() {
        long processed = 0;
        int batch;
        while ((batch = migration.backfillBatch(batchSize)) != 0) {
            processed += batch;
        }
        if (migration.missing() != 0) {
            throw new IllegalStateException("Identity backfill has locked or newly changed rows; run again");
        }
        log.info("Identity backfill finished, rows processed={}", processed);
    }

    private void verify() {
        migration.verify(batchSize).forEach(coverage -> log.info(
                "Identity verification status={}, credentials={}, deleted={}, rows={}",
                coverage.status(), coverage.credentialStatus(), coverage.deleted(), coverage.rows()));
        log.info("Identity verification finished");
    }

    private void rotate() {
        long processed = 0;
        int batch;
        while ((batch = migration.rotateBatch(batchSize)) != 0) {
            processed += batch;
        }
        log.info("Identity rotation verified, rows processed={}, rows verified={}", processed,
                migration.verifyRotation(batchSize));
    }

    private UUID targetId() {
        try {
            return UUID.fromString(target);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Identity operation requires a valid target UUID");
        }
    }
}

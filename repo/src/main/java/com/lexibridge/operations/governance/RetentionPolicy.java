package com.lexibridge.operations.governance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Central definition of the long-term retention window for audit and reconciliation records.
 *
 * <p>The same window is encoded in DB triggers (V12, V15) that reject DELETE before expiry
 * and honor active rows in {@code retention_hold}. This class exposes the policy to the
 * application layer so purge jobs, disclosures, and tests reference a single source of truth.
 */
@Component
public class RetentionPolicy {

    private final int auditRetentionYears;
    private final int reconciliationRetentionYears;

    public RetentionPolicy(
        @Value("${lexibridge.retention.audit-years:7}") int auditRetentionYears,
        @Value("${lexibridge.retention.reconciliation-years:7}") int reconciliationRetentionYears
    ) {
        if (auditRetentionYears < 7) {
            throw new IllegalStateException("Audit retention must be >= 7 years to satisfy compliance policy.");
        }
        if (reconciliationRetentionYears < 7) {
            throw new IllegalStateException("Reconciliation retention must be >= 7 years to satisfy compliance policy.");
        }
        this.auditRetentionYears = auditRetentionYears;
        this.reconciliationRetentionYears = reconciliationRetentionYears;
    }

    public int auditRetentionYears() {
        return auditRetentionYears;
    }

    public int reconciliationRetentionYears() {
        return reconciliationRetentionYears;
    }
}

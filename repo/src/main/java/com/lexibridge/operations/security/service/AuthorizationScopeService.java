package com.lexibridge.operations.security.service;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Thin facade that preserves the legacy authorization-scope API while delegating to
 * single-responsibility collaborators:
 * <ul>
 *   <li>{@link LocationScopeAuthorizer} — location-based scoping of domain entities</li>
 *   <li>{@link ActorScopeAuthorizer} — actor-identity and workflow-approver scoping</li>
 * </ul>
 * Keeping the facade avoids a wide-ranging rename across controllers; new code should depend
 * on the focused collaborators directly.
 */
@Service
public class AuthorizationScopeService {

    private final AuthorizationIdentityService authorizationIdentityService;
    private final LocationScopeAuthorizer locationScopeAuthorizer;
    private final ActorScopeAuthorizer actorScopeAuthorizer;

    public AuthorizationScopeService(AuthorizationIdentityService authorizationIdentityService,
                                     LocationScopeAuthorizer locationScopeAuthorizer,
                                     ActorScopeAuthorizer actorScopeAuthorizer) {
        this.authorizationIdentityService = authorizationIdentityService;
        this.locationScopeAuthorizer = locationScopeAuthorizer;
        this.actorScopeAuthorizer = actorScopeAuthorizer;
    }

    public void assertLocationAccess(Long locationId) {
        locationScopeAuthorizer.assertLocationAccess(locationId);
    }

    public void assertActorUser(Long actorUserId) {
        actorScopeAuthorizer.assertActorUser(actorUserId);
    }

    public long requireCurrentUserId() {
        return authorizationIdentityService.requireCurrentUserId();
    }

    public void assertBookingAccess(long bookingOrderId) {
        locationScopeAuthorizer.assertBookingAccess(bookingOrderId);
    }

    public void assertRefundScope(long refundId) {
        locationScopeAuthorizer.assertRefundScope(refundId);
    }

    public void assertReconciliationRunScope(long runId) {
        locationScopeAuthorizer.assertReconciliationRunScope(runId);
    }

    public void assertContentItemScope(long contentItemId) {
        locationScopeAuthorizer.assertContentItemScope(contentItemId);
    }

    public void assertLeaveRequestRequester(long leaveRequestId) {
        actorScopeAuthorizer.assertLeaveRequestRequester(leaveRequestId);
    }

    public void assertLeaveRequestReadAccess(long leaveRequestId) {
        actorScopeAuthorizer.assertLeaveRequestReadAccess(leaveRequestId);
    }

    public void assertApprovalTaskApprover(long approvalTaskId) {
        actorScopeAuthorizer.assertApprovalTaskApprover(approvalTaskId);
    }

    public void assertTenderLocationScope(long tenderEntryId) {
        locationScopeAuthorizer.assertTenderLocationScope(tenderEntryId);
    }

    public void assertReconciliationExceptionScope(long exceptionId) {
        locationScopeAuthorizer.assertReconciliationExceptionScope(exceptionId);
    }

    public void assertModerationCaseScope(long caseId) {
        locationScopeAuthorizer.assertModerationCaseScope(caseId);
    }

    public void assertUserReportScope(long reportId) {
        locationScopeAuthorizer.assertUserReportScope(reportId);
    }

    public Optional<Long> currentLocationScope() {
        return locationScopeAuthorizer.currentLocationScope();
    }
}

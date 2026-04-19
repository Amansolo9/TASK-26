package com.lexibridge.operations.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Actor-identity authorization: "is this principal the actor they claim to be, or a valid
 * approver on a workflow record?". Lives alongside {@link LocationScopeAuthorizer} so each
 * file owns one concern.
 */
@Service
public class ActorScopeAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(ActorScopeAuthorizer.class);

    private final AuthorizationIdentityService authorizationIdentityService;
    private final ScopeLookupService scopeLookupService;

    public ActorScopeAuthorizer(AuthorizationIdentityService authorizationIdentityService,
                                ScopeLookupService scopeLookupService) {
        this.authorizationIdentityService = authorizationIdentityService;
        this.scopeLookupService = scopeLookupService;
    }

    public void assertActorUser(Long actorUserId) {
        if (actorUserId == null) {
            log.warn("Actor scope denied: missing actor user id");
            throw new AccessDeniedException("Actor user is required.");
        }

        if (authorizationIdentityService.isAdmin()) {
            return;
        }

        Authentication auth = authorizationIdentityService.requireAuthentication();
        if (authorizationIdentityService.hasRole(auth, "ROLE_DEVICE_SERVICE")) {
            Long actorLocation = scopeLookupService.activeUserLocationById(actorUserId);
            Long deviceLocation = authorizationIdentityService.activeDeviceLocation(auth.getName());
            if (actorLocation == null || deviceLocation == null || !deviceLocation.equals(actorLocation)) {
                log.warn("Actor scope denied for device '{}' to actorUserId {}", auth.getName(), actorUserId);
                throw new AccessDeniedException("Actor user is outside device location scope.");
            }
            return;
        }

        Long currentUserId = authorizationIdentityService.activeUserId(auth.getName());
        if (currentUserId == null || !currentUserId.equals(actorUserId)) {
            log.warn("Actor scope denied: principal '{}' does not match actorUserId {}", auth.getName(), actorUserId);
            throw new AccessDeniedException("Actor user does not match authenticated principal.");
        }
    }

    public void assertLeaveRequestRequester(long leaveRequestId) {
        if (authorizationIdentityService.isAdmin()) {
            return;
        }
        Long requesterId = scopeLookupService.leaveRequester(leaveRequestId);
        if (requesterId == null) {
            log.warn("Leave requester scope denied: leaveRequestId {} not found", leaveRequestId);
            throw new AccessDeniedException("Leave request not found.");
        }
        assertActorUser(requesterId);
    }

    public void assertLeaveRequestReadAccess(long leaveRequestId) {
        if (authorizationIdentityService.isAdmin()) {
            return;
        }
        long currentUserId = authorizationIdentityService.requireCurrentUserId();
        Long requesterId = scopeLookupService.leaveRequester(leaveRequestId);
        if (requesterId == null) {
            log.warn("Leave read scope denied: leaveRequestId {} not found", leaveRequestId);
            throw new AccessDeniedException("Leave request not found.");
        }
        if (requesterId.equals(currentUserId)) {
            return;
        }
        if (scopeLookupService.activeApproverTaskCount(leaveRequestId, currentUserId) > 0) {
            return;
        }
        log.warn("Leave read scope denied: user {} is neither requester nor active approver for request {}", currentUserId, leaveRequestId);
        throw new AccessDeniedException("Leave request is outside requester/approver scope.");
    }

    public void assertApprovalTaskApprover(long approvalTaskId) {
        if (authorizationIdentityService.isAdmin()) {
            return;
        }
        Long approverId = scopeLookupService.approvalTaskApprover(approvalTaskId);
        if (approverId == null) {
            log.warn("Approval task scope denied: approvalTaskId {} not found", approvalTaskId);
            throw new AccessDeniedException("Approval task not found.");
        }
        assertActorUser(approverId);
    }
}

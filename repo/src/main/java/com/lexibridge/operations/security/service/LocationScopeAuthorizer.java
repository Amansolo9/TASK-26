package com.lexibridge.operations.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Location-scoped authorization checks. Encapsulates the "does this principal have access to
 * a location, or to an entity that lives inside a location?" concern so it can be tested and
 * extended without touching actor-identity or leave-specific logic.
 */
@Service
public class LocationScopeAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(LocationScopeAuthorizer.class);

    private final AuthorizationIdentityService authorizationIdentityService;
    private final ScopeLookupService scopeLookupService;

    public LocationScopeAuthorizer(AuthorizationIdentityService authorizationIdentityService,
                                   ScopeLookupService scopeLookupService) {
        this.authorizationIdentityService = authorizationIdentityService;
        this.scopeLookupService = scopeLookupService;
    }

    public void assertLocationAccess(Long locationId) {
        if (locationId == null) {
            log.warn("Location scope denied: missing locationId");
            throw new AccessDeniedException("Location is required.");
        }

        if (authorizationIdentityService.isAdmin()) {
            return;
        }

        Authentication auth = authorizationIdentityService.requireAuthentication();
        if (authorizationIdentityService.hasRole(auth, "ROLE_DEVICE_SERVICE")) {
            Long deviceLocation = authorizationIdentityService.activeDeviceLocation(auth.getName());
            if (deviceLocation == null || !deviceLocation.equals(locationId)) {
                log.warn("Location scope denied for device client '{}' on location {}", auth.getName(), locationId);
                throw new AccessDeniedException("Requested location is outside device scope.");
            }
            return;
        }

        Long userLocation = authorizationIdentityService.activeUserLocation(auth.getName());
        if (userLocation == null || !userLocation.equals(locationId)) {
            log.warn("Location scope denied for user '{}' on location {}", auth.getName(), locationId);
            throw new AccessDeniedException("Requested location is outside user scope.");
        }
    }

    public void assertBookingAccess(long bookingOrderId) {
        assertByLookup("Booking order", scopeLookupService.bookingLocation(bookingOrderId), bookingOrderId);
    }

    public void assertRefundScope(long refundId) {
        assertByLookup("Refund request", scopeLookupService.refundLocation(refundId), refundId);
    }

    public void assertReconciliationRunScope(long runId) {
        assertByLookup("Reconciliation run", scopeLookupService.reconciliationRunLocation(runId), runId);
    }

    public void assertContentItemScope(long contentItemId) {
        assertByLookup("Content item", scopeLookupService.contentItemLocation(contentItemId), contentItemId);
    }

    public void assertTenderLocationScope(long tenderEntryId) {
        assertByLookup("Tender entry", scopeLookupService.tenderLocation(tenderEntryId), tenderEntryId);
    }

    public void assertReconciliationExceptionScope(long exceptionId) {
        assertByLookup("Reconciliation exception", scopeLookupService.reconciliationExceptionLocation(exceptionId), exceptionId);
    }

    public void assertModerationCaseScope(long caseId) {
        assertByLookup("Moderation case", scopeLookupService.moderationCaseLocation(caseId), caseId);
    }

    public void assertUserReportScope(long reportId) {
        assertByLookup("User report", scopeLookupService.userReportLocation(reportId), reportId);
    }

    public Optional<Long> currentLocationScope() {
        Authentication auth = authorizationIdentityService.requireAuthentication();
        if (authorizationIdentityService.hasRole(auth, "ROLE_ADMIN")) {
            return Optional.empty();
        }
        if (authorizationIdentityService.hasRole(auth, "ROLE_DEVICE_SERVICE")) {
            return Optional.ofNullable(authorizationIdentityService.activeDeviceLocation(auth.getName()));
        }
        return Optional.ofNullable(authorizationIdentityService.activeUserLocation(auth.getName()));
    }

    private void assertByLookup(String entityName, Long locationId, long entityId) {
        if (locationId == null) {
            log.warn("{} scope denied: id {} not found", entityName, entityId);
            throw new AccessDeniedException(entityName + " not found.");
        }
        assertLocationAccess(locationId);
    }
}

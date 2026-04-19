package com.lexibridge.operations.web;

import com.lexibridge.operations.modules.admin.api.AdminApiController;
import com.lexibridge.operations.modules.admin.service.AdminUserManagementService;
import com.lexibridge.operations.modules.admin.service.DeviceHmacKeyRotationService;
import com.lexibridge.operations.modules.admin.service.WebhookDeliveryService;
import com.lexibridge.operations.modules.admin.service.WebhookSecurityService;
import com.lexibridge.operations.modules.booking.api.BookingApiController;
import com.lexibridge.operations.modules.booking.service.BookingService;
import com.lexibridge.operations.modules.content.api.ContentApiController;
import com.lexibridge.operations.modules.content.service.ContentImportService;
import com.lexibridge.operations.modules.content.service.ContentMediaService;
import com.lexibridge.operations.modules.content.service.ContentService;
import com.lexibridge.operations.modules.content.service.MediaValidationService;
import com.lexibridge.operations.monitoring.TracePersistenceService;
import com.lexibridge.operations.modules.leave.api.LeaveApiController;
import com.lexibridge.operations.modules.leave.service.LeaveService;
import com.lexibridge.operations.modules.moderation.api.ModerationApiController;
import com.lexibridge.operations.modules.moderation.service.ModerationService;
import com.lexibridge.operations.modules.payments.api.PaymentsApiController;
import com.lexibridge.operations.modules.payments.service.PaymentsService;
import com.lexibridge.operations.security.api.ApiRateLimiterService;
import com.lexibridge.operations.security.api.HmacAuthService;
import com.lexibridge.operations.security.service.AuthorizationScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
    AdminApiController.class,
    BookingApiController.class,
    ContentApiController.class,
    LeaveApiController.class,
    ModerationApiController.class,
    PaymentsApiController.class
})
@Import(ApiAuthorizationWebMvcTest.TestSecurityConfig.class)
class ApiAuthorizationWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookSecurityService webhookSecurityService;
    @MockBean
    private WebhookDeliveryService webhookDeliveryService;
    @MockBean
    private AdminUserManagementService adminUserManagementService;
    @MockBean
    private DeviceHmacKeyRotationService deviceHmacKeyRotationService;
    @MockBean
    private BookingService bookingService;
    @MockBean
    private ContentService contentService;
    @MockBean
    private ContentImportService contentImportService;
    @MockBean
    private ContentMediaService contentMediaService;
    @MockBean
    private MediaValidationService mediaValidationService;
    @MockBean
    private TracePersistenceService tracePersistenceService;
    @MockBean
    private LeaveService leaveService;
    @MockBean
    private ModerationService moderationService;
    @MockBean
    private PaymentsService paymentsService;
    @MockBean
    private AuthorizationScopeService authorizationScopeService;
    @MockBean
    private HmacAuthService hmacAuthService;
    @MockBean
    private ApiRateLimiterService apiRateLimiterService;

    @BeforeEach
    void setUp() {
        when(apiRateLimiterService.allow(anyString())).thenReturn(true);
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void adminApi_shouldRejectNonAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/status"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminHmacRotate_shouldAllowAdminRole() throws Exception {
        when(authorizationScopeService.requireCurrentUserId()).thenReturn(1L);
        when(deviceHmacKeyRotationService.rotate(eq("demo-device"), anyString(), eq(30), anyString(), eq(1L)))
            .thenReturn(Map.of("status", "ROTATED", "newKeyVersion", 2));

        mockMvc.perform(post("/api/v1/admin/device-clients/demo-device/hmac/rotate")
                .with(csrf())
                .contentType("application/json")
                .content("{\"sharedSecret\":\"strong-secret-value-0000000000000000\",\"reason\":\"Routine key rotation\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DEVICE_SERVICE")
    void paymentsCallback_shouldAllowDeviceRole() throws Exception {
        when(authorizationScopeService.currentLocationScope()).thenReturn(java.util.Optional.of(2L));
        when(paymentsService.processCallback("T", "X", Map.of(), 2L, "user")).thenReturn(Map.of("status", "PROCESSED"));
        mockMvc.perform(post("/api/v1/payments/callbacks")
                .with(csrf())
                .contentType("application/json")
                .content("{\"terminalId\":\"T\",\"terminalTxnId\":\"X\",\"payload\":{}}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void paymentsCallback_shouldRequireLocationScopeForNonAdminActor() throws Exception {
        when(authorizationScopeService.currentLocationScope()).thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/api/v1/payments/callbacks")
                .with(csrf())
                .contentType("application/json")
                .content("{\"terminalId\":\"T\",\"terminalTxnId\":\"X\",\"payload\":{}}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void paymentsCallback_shouldForwardScopedLocationForNonAdminActor() throws Exception {
        when(authorizationScopeService.currentLocationScope()).thenReturn(java.util.Optional.of(7L));
        when(paymentsService.processCallback("T", "X", Map.of(), 7L, "user")).thenReturn(Map.of("status", "PROCESSED"));

        mockMvc.perform(post("/api/v1/payments/callbacks")
                .with(csrf())
                .contentType("application/json")
                .content("{\"terminalId\":\"T\",\"terminalTxnId\":\"X\",\"payload\":{}}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DEVICE_SERVICE")
    void paymentsTenderCreate_shouldDenyDeviceRole() throws Exception {
        mockMvc.perform(post("/api/v1/payments/tenders")
                .with(csrf())
                .contentType("application/json")
                .content("{\"bookingOrderId\":77,\"tenderType\":\"CARD\",\"amount\":10.00,\"currency\":\"USD\",\"createdBy\":1}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void paymentsCallback_shouldAllowHmacWithoutCsrfToken() throws Exception {
        when(hmacAuthService.authenticate(anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(java.util.Optional.of("demo-device"));
        when(authorizationScopeService.currentLocationScope()).thenReturn(java.util.Optional.of(2L));
        when(paymentsService.processCallback("T", "X", Map.of(), 2L, "demo-device")).thenReturn(Map.of("status", "PROCESSED"));

        mockMvc.perform(post("/api/v1/payments/callbacks")
                .header("X-Client-Key", "demo-device")
                .header("X-Key-Version", "1")
                .header("X-Timestamp", String.valueOf(System.currentTimeMillis() / 1000))
                .header("X-Nonce", "n-123")
                .header("X-Signature", "sig")
                .contentType("application/json")
                .content("{\"terminalId\":\"T\",\"terminalTxnId\":\"X\",\"payload\":{}}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void leaveWithdraw_shouldReturnForbiddenOnOwnershipViolation() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("out of scope"))
            .when(authorizationScopeService).assertLeaveRequestRequester(10L);
        mockMvc.perform(post("/api/v1/leave/requests/10/withdraw").with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void leaveWithdraw_shouldReturnConflictWhenAlreadyFinalized() throws Exception {
        when(leaveService.withdraw(10L)).thenReturn(false);

        mockMvc.perform(post("/api/v1/leave/requests/10/withdraw").with(csrf()))
            .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void leaveResubmit_shouldReturnForbiddenOnOwnershipViolation() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("out of scope"))
            .when(authorizationScopeService).assertLeaveRequestRequester(11L);

        mockMvc.perform(post("/api/v1/leave/requests/11/resubmit")
                .with(csrf())
                .contentType("application/json")
                .content("{\"locationId\":1,\"requesterUserId\":9,\"leaveType\":\"ANNUAL_LEAVE\",\"startDate\":\"2026-05-01\",\"endDate\":\"2026-05-01\",\"durationMinutes\":60}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void leaveFormDefinitionCreate_shouldDenyEmployeeRole() throws Exception {
        mockMvc.perform(post("/api/v1/leave/forms/definitions")
                .with(csrf())
                .contentType("application/json")
                .content("{\"locationId\":1,\"name\":\"Default Leave Form\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void leaveFormDefinitionCreate_shouldUseAuthenticatedActorIdentity() throws Exception {
        when(authorizationScopeService.requireCurrentUserId()).thenReturn(44L);
        when(leaveService.createFormDefinition(1L, "Default Leave Form", 44L))
            .thenReturn(Map.of("formDefinitionId", 5L, "status", "CREATED"));

        mockMvc.perform(post("/api/v1/leave/forms/definitions")
                .with(csrf())
                .contentType("application/json")
                .content("{\"locationId\":1,\"name\":\"Default Leave Form\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void leaveApprove_shouldRejectForgedApproverUserIdFieldInPayload() throws Exception {
        mockMvc.perform(post("/api/v1/leave/approvals/12/approve")
                .with(csrf())
                .contentType("application/json")
                .content("{\"approverUserId\":999,\"note\":\"ok\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void leaveApprove_shouldUseAuthenticatedActorIdentity() throws Exception {
        when(authorizationScopeService.requireCurrentUserId()).thenReturn(44L);
        when(leaveService.approveTask(12L, 44L, "ok")).thenReturn(Map.of("taskId", 12L, "status", "APPROVED"));

        mockMvc.perform(post("/api/v1/leave/approvals/12/approve")
                .with(csrf())
                .contentType("application/json")
                .content("{\"note\":\"ok\"}"))
            .andExpect(status().isOk());

        verify(leaveService).approveTask(12L, 44L, "ok");
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void leaveCorrection_shouldRejectForgedApproverUserIdFieldInPayload() throws Exception {
        mockMvc.perform(post("/api/v1/leave/approvals/12/correction")
                .with(csrf())
                .contentType("application/json")
                .content("{\"approverUserId\":999,\"note\":\"needs changes\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void leaveCorrection_shouldUseAuthenticatedActorIdentity() throws Exception {
        when(authorizationScopeService.requireCurrentUserId()).thenReturn(44L);
        when(leaveService.requestCorrection(12L, 44L, "needs changes"))
            .thenReturn(Map.of("taskId", 12L, "status", "CORRECTION_REQUESTED"));

        mockMvc.perform(post("/api/v1/leave/approvals/12/correction")
                .with(csrf())
                .contentType("application/json")
                .content("{\"note\":\"needs changes\"}"))
            .andExpect(status().isOk());

        verify(leaveService).requestCorrection(12L, 44L, "needs changes");
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void bookingReserve_shouldReturnForbiddenOnLocationViolation() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("out of scope"))
            .when(authorizationScopeService).assertLocationAccess(2L);

        mockMvc.perform(post("/api/v1/bookings")
                .with(csrf())
                .contentType("application/json")
                .content("{\"locationId\":2,\"createdBy\":1,\"customerName\":\"A\",\"startAt\":\"2026-04-01T10:00:00\",\"durationMinutes\":30}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void bookingSummary_shouldUseScopedLocationForNonAdmin() throws Exception {
        when(authorizationScopeService.currentLocationScope()).thenReturn(java.util.Optional.of(2L));
        when(bookingService.dashboardSummary(2L)).thenReturn(Map.of("reservedNow", 1));

        mockMvc.perform(get("/api/v1/bookings/summary"))
            .andExpect(status().isOk());

        verify(bookingService).dashboardSummary(2L);
        verify(bookingService, never()).dashboardSummary((Long) null);
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void attendanceScan_shouldReturnForbiddenOnBookingScopeViolation() throws Exception {
        when(bookingService.decodeAttendanceToken("signed-token")).thenReturn(77L);
        doThrow(new org.springframework.security.access.AccessDeniedException("out of scope"))
            .when(authorizationScopeService).assertBookingAccess(77L);

        mockMvc.perform(post("/api/v1/bookings/attendance/scan")
                .with(csrf())
                .contentType("application/json")
                .content("{\"token\":\"signed-token\",\"scannedBy\":9}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void leaveSummary_shouldUseScopedLocationForNonAdmin() throws Exception {
        when(authorizationScopeService.currentLocationScope()).thenReturn(java.util.Optional.of(3L));
        when(leaveService.dashboardSummary(3L)).thenReturn(Map.of("pendingApprovals", 2));

        mockMvc.perform(get("/api/v1/leave/summary"))
            .andExpect(status().isOk());

        verify(leaveService).dashboardSummary(3L);
        verify(leaveService, never()).dashboardSummary((Long) null);
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderationSummary_shouldUseScopedLocationForNonAdmin() throws Exception {
        when(authorizationScopeService.currentLocationScope()).thenReturn(java.util.Optional.of(6L));
        when(moderationService.dashboardSummary(6L)).thenReturn(Map.of("pendingCount", 3));

        mockMvc.perform(get("/api/v1/moderation/summary"))
            .andExpect(status().isOk());

        verify(moderationService).dashboardSummary(6L);
        verify(moderationService, never()).dashboardSummary();
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderationSummary_shouldReturnForbiddenWhenLocationScopeMissing() throws Exception {
        when(authorizationScopeService.currentLocationScope()).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/v1/moderation/summary"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void paymentsSummary_shouldUseScopedLocationForNonAdmin() throws Exception {
        when(authorizationScopeService.currentLocationScope()).thenReturn(java.util.Optional.of(4L));
        when(paymentsService.dashboardSummary(4L)).thenReturn(Map.of("tendersToday", 4));

        mockMvc.perform(get("/api/v1/payments/summary"))
            .andExpect(status().isOk());

        verify(paymentsService).dashboardSummary(4L);
        verify(paymentsService, never()).dashboardSummary((Long) null);
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void moderationReportSubmit_shouldAllowAuthenticatedReporter() throws Exception {
        when(moderationService.submitUserReport(eq(9L), eq(1L), eq("POST"), eq(77L), eq("abuse")))
            .thenReturn(Map.of("reportId", 101L, "disposition", "OPEN"));

        mockMvc.perform(post("/api/v1/moderation/reports")
                .with(csrf())
                .contentType("application/json")
                .content("{\"locationId\":1,\"reporterUserId\":9,\"targetType\":\"POST\",\"targetId\":77,\"reasonText\":\"abuse\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void moderationCommunityPostCreate_shouldAllowAuthenticatedUser() throws Exception {
        when(authorizationScopeService.requireCurrentUserId()).thenReturn(9L);
        when(moderationService.createPostTarget(1L, 9L, "Hello", "<p>Body</p>")).thenReturn(101L);

        mockMvc.perform(post("/api/v1/moderation/community/posts")
                .with(csrf())
                .contentType("application/json")
                .content("{\"locationId\":1,\"title\":\"Hello\",\"bodyHtml\":\"<p>Body</p>\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void moderationCommunityPostCreate_shouldReturnForbiddenForSuspendedAuthor() throws Exception {
        when(authorizationScopeService.requireCurrentUserId()).thenReturn(9L);
        doThrow(new org.springframework.security.access.AccessDeniedException("suspended"))
            .when(moderationService).createPostTarget(1L, 9L, "Hello", "<p>Body</p>");

        mockMvc.perform(post("/api/v1/moderation/community/posts")
                .with(csrf())
                .contentType("application/json")
                .content("{\"locationId\":1,\"title\":\"Hello\",\"bodyHtml\":\"<p>Body</p>\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void moderationCommunityPostMediaUpload_shouldAllowAuthenticatedOwner() throws Exception {
        when(authorizationScopeService.requireCurrentUserId()).thenReturn(9L);
        when(moderationService.requireTargetLocation("POST", 101L)).thenReturn(1L);
        when(moderationService.addTargetMedia(eq("POST"), eq(101L), eq("p.png"), org.mockito.ArgumentMatchers.any(), eq(9L)))
            .thenReturn(Map.of("mediaId", 4L, "targetType", "POST", "targetId", 101L, "status", "UPLOADED"));

        MockMultipartFile file = new MockMultipartFile("file", "p.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/moderation/community/posts/101/media")
                .file(file)
                .with(csrf()))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void leaveAttachmentDownload_shouldReturnForbiddenWhenReadScopeViolation() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("out of scope"))
            .when(authorizationScopeService).assertLeaveRequestReadAccess(20L);

        mockMvc.perform(get("/api/v1/leave/requests/20/attachments/3/download"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void moderationReportResolve_shouldDenyNonModeratorRole() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/reports/10/resolve")
                .with(csrf())
                .contentType("application/json")
                .content("{\"disposition\":\"DISMISSED\",\"moderatorUserId\":1}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CONTENT_EDITOR")
    void contentPublish_shouldReturnForbiddenOnScopeViolation() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("out of scope"))
            .when(authorizationScopeService).assertContentItemScope(15L);

        mockMvc.perform(post("/api/v1/content/items/15/publish").with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void paymentsTenderCreate_shouldReturnForbiddenOnBookingScopeViolation() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("out of scope"))
            .when(authorizationScopeService).assertBookingAccess(77L);

        mockMvc.perform(post("/api/v1/payments/tenders")
                .with(csrf())
                .contentType("application/json")
                .content("{\"bookingOrderId\":77,\"tenderType\":\"CARD_PRESENT\",\"amount\":10.00,\"currency\":\"USD\",\"createdBy\":1}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void paymentsTenderCreate_shouldRequireCsrfForSessionAuthenticatedApiCalls() throws Exception {
        mockMvc.perform(post("/api/v1/payments/tenders")
                .contentType("application/json")
                .content("{\"bookingOrderId\":77,\"tenderType\":\"CARD_PRESENT\",\"amount\":10.00,\"currency\":\"USD\",\"createdBy\":1}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CONTENT_EDITOR")
    void contentMediaDownload_shouldReturnForbiddenOnScopeViolation() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("out of scope"))
            .when(authorizationScopeService).assertContentItemScope(15L);

        mockMvc.perform(get("/api/v1/content/items/15/media/2/download"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void bookingAttachmentDownload_shouldReturnForbiddenOnScopeViolation() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("out of scope"))
            .when(authorizationScopeService).assertBookingAccess(77L);

        mockMvc.perform(get("/api/v1/bookings/77/attachments/2/download"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void leaveAttachmentDownload_shouldReturnForbiddenOnScopeViolation() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("out of scope"))
            .when(authorizationScopeService).assertLeaveRequestReadAccess(20L);

        mockMvc.perform(get("/api/v1/leave/requests/20/attachments/3/download"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderationMediaDownload_shouldReturnForbiddenOnScopeViolation() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("out of scope"))
            .when(authorizationScopeService).assertModerationCaseScope(9L);

        mockMvc.perform(get("/api/v1/moderation/cases/9/media/1/download"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderationTargetMediaDownload_shouldReturnForbiddenOnScopeViolation() throws Exception {
        when(moderationService.requireTargetLocation("POST", 77L)).thenReturn(1L);
        doThrow(new org.springframework.security.access.AccessDeniedException("out of scope"))
            .when(authorizationScopeService).assertLocationAccess(1L);

        mockMvc.perform(get("/api/v1/moderation/targets/POST/77/media/1/download"))
            .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // Expanded endpoint coverage: previously-uncovered HTTP endpoints are
    // exercised below to lift endpoint-level HTTP coverage beyond the narrow
    // set the audit report flagged as covered.
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminWebhookList_shouldReturn200ForAdmin() throws Exception {
        when(webhookSecurityService.activeWebhooks()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/admin/webhooks"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void adminWebhookList_shouldDenyNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/webhooks"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminUsersList_shouldReturn200ForAdmin() throws Exception {
        when(adminUserManagementService.users(100)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminTraces_shouldReturn200ForAdmin() throws Exception {
        when(tracePersistenceService.latest(10)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/admin/traces?limit=10"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminHmacKeys_shouldReturn200ForAdmin() throws Exception {
        when(deviceHmacKeyRotationService.keyInventory("demo-device")).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/admin/device-clients/demo-device/hmac/keys"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void bookingReschedule_shouldReturn200ForFrontDesk() throws Exception {
        when(bookingService.reschedule(eq(7L), org.mockito.ArgumentMatchers.any(), eq(30), eq(1L), eq("customer request")))
            .thenReturn(Map.of("bookingId", 7L, "status", "RESERVED"));
        mockMvc.perform(post("/api/v1/bookings/7/reschedule")
                .with(csrf())
                .contentType("application/json")
                .content("{\"startAt\":\"2026-05-01T10:00:00\",\"durationMinutes\":30,\"actorUserId\":1,\"reason\":\"customer request\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void bookingReschedule_shouldReturnForbiddenOnScopeViolation() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("out of scope"))
            .when(authorizationScopeService).assertBookingAccess(7L);
        mockMvc.perform(post("/api/v1/bookings/7/reschedule")
                .with(csrf())
                .contentType("application/json")
                .content("{\"startAt\":\"2026-05-01T10:00:00\",\"durationMinutes\":30,\"actorUserId\":1,\"reason\":\"customer request\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void bookingNoShowOverride_shouldReturn200ForFrontDesk() throws Exception {
        when(bookingService.setNoShowAutoCloseOverride(eq(7L), eq(true), eq("manager approved"), eq(1L)))
            .thenReturn(Map.of("bookingId", 7L, "noShowAutoCloseDisabled", true, "reason", "manager approved"));
        mockMvc.perform(post("/api/v1/bookings/7/no-show-override")
                .with(csrf())
                .contentType("application/json")
                .content("{\"disableAutoClose\":true,\"reason\":\"manager approved\",\"actorUserId\":1}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void bookingAttachmentsList_shouldReturn200ForFrontDesk() throws Exception {
        when(bookingService.attachments(7L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/bookings/7/attachments"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void bookingAttachmentsList_shouldDenyEmployeeRole() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/7/attachments"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void paymentsSplitPolicy_shouldReturn200ForAdmin() throws Exception {
        when(paymentsService.configureSplitPolicy(eq(1L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), eq(5L)))
            .thenReturn(Map.of("locationId", 1L, "merchantRatio", "0.80", "platformRatio", "0.20"));
        mockMvc.perform(post("/api/v1/payments/split-policy")
                .with(csrf())
                .contentType("application/json")
                .content("{\"locationId\":1,\"merchantRatio\":0.80,\"platformRatio\":0.20,\"actorUserId\":5}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void paymentsSplitPolicy_shouldDenyFrontDesk() throws Exception {
        mockMvc.perform(post("/api/v1/payments/split-policy")
                .with(csrf())
                .contentType("application/json")
                .content("{\"locationId\":1,\"merchantRatio\":0.80,\"platformRatio\":0.20,\"actorUserId\":5}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void paymentsReconciliationExceptions_shouldReturn200ForSupervisor() throws Exception {
        when(authorizationScopeService.currentLocationScope()).thenReturn(java.util.Optional.of(1L));
        when(paymentsService.exceptions(1L, null)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/payments/reconciliation/exceptions"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void paymentsRefundApprove_shouldReturn200ForSupervisor() throws Exception {
        mockMvc.perform(post("/api/v1/payments/refunds/33/approve")
                .with(csrf())
                .contentType("application/json")
                .content("{\"supervisorUserId\":5}"))
            .andExpect(status().isOk());
        verify(paymentsService).approveRefund(33L, 5L);
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void paymentsRefundApprove_shouldDenyFrontDesk() throws Exception {
        mockMvc.perform(post("/api/v1/payments/refunds/33/approve")
                .with(csrf())
                .contentType("application/json")
                .content("{\"supervisorUserId\":5}"))
            .andExpect(status().isForbidden());
        verify(paymentsService, never()).approveRefund(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void paymentsExceptionResolve_shouldReturn200ForSupervisor() throws Exception {
        mockMvc.perform(post("/api/v1/payments/reconciliation/exceptions/9/resolve")
                .with(csrf())
                .contentType("application/json")
                .content("{\"actorUserId\":5,\"note\":\"matched\"}"))
            .andExpect(status().isOk());
        verify(paymentsService).resolveException(9L, 5L, "matched");
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void paymentsExceptionReopen_shouldReturn200ForSupervisor() throws Exception {
        mockMvc.perform(post("/api/v1/payments/reconciliation/exceptions/9/reopen")
                .with(csrf())
                .contentType("application/json")
                .content("{\"actorUserId\":5,\"note\":\"re-check\"}"))
            .andExpect(status().isOk());
        verify(paymentsService).reopenException(9L, 5L, "re-check");
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void paymentsReconciliationRunExport_shouldReturn200ForFrontDesk() throws Exception {
        when(paymentsService.exportReconciliationRun(7L, "csv")).thenReturn(new byte[]{'a',',','b'});
        mockMvc.perform(get("/api/v1/payments/reconciliation/runs/7/export"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CONTENT_EDITOR")
    void contentSummary_shouldReturn200ForContentEditor() throws Exception {
        when(authorizationScopeService.currentLocationScope()).thenReturn(java.util.Optional.of(1L));
        when(contentService.dashboardSummary(1L)).thenReturn(Map.of("totalItems", 0));
        mockMvc.perform(get("/api/v1/content/summary"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void contentSummary_shouldDenyFrontDesk() throws Exception {
        mockMvc.perform(get("/api/v1/content/summary"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderationSummary_shouldReturn200ForModerator() throws Exception {
        when(authorizationScopeService.currentLocationScope()).thenReturn(java.util.Optional.of(1L));
        when(moderationService.dashboardSummary(1L)).thenReturn(Map.of("openCases", 0));
        mockMvc.perform(get("/api/v1/moderation/summary"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void moderationSummary_shouldDenyEmployee() throws Exception {
        mockMvc.perform(get("/api/v1/moderation/summary"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void leaveSummary_shouldReturn200ForEmployee() throws Exception {
        when(authorizationScopeService.currentLocationScope()).thenReturn(java.util.Optional.of(1L));
        when(leaveService.dashboardSummary(1L)).thenReturn(Map.of("pendingApprovals", 0));
        mockMvc.perform(get("/api/v1/leave/summary"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void leaveSummary_shouldDenyFrontDesk() throws Exception {
        mockMvc.perform(get("/api/v1/leave/summary"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    void bookingSummary_shouldReturn200ForFrontDesk() throws Exception {
        when(authorizationScopeService.currentLocationScope()).thenReturn(java.util.Optional.of(1L));
        when(bookingService.dashboardSummary(1L)).thenReturn(Map.of("activeBookings", 0));
        mockMvc.perform(get("/api/v1/bookings/summary"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void bookingSummary_shouldDenyEmployee() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/summary"))
            .andExpect(status().isForbidden());
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                HmacAuthService hmacAuthService,
                                                ApiRateLimiterService apiRateLimiterService) throws Exception {
            http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/moderation/community/**").authenticated()
                .requestMatchers("/api/v1/moderation/reports", "/api/v1/moderation/reports/by-reporter/**", "/api/v1/moderation/penalties/**").authenticated()
                .requestMatchers("/api/v1/moderation/**").hasAnyRole("ADMIN", "MODERATOR")
                .requestMatchers("/api/v1/leave/**").hasAnyRole("ADMIN", "EMPLOYEE", "MANAGER", "HR_APPROVER")
                .requestMatchers("/api/v1/payments/callbacks").hasAnyRole("ADMIN", "SUPERVISOR", "FRONT_DESK", "DEVICE_SERVICE")
                .requestMatchers("/api/v1/payments/**").hasAnyRole("ADMIN", "SUPERVISOR", "FRONT_DESK")
                .requestMatchers("/api/v1/bookings/**").hasAnyRole("ADMIN", "FRONT_DESK", "DEVICE_SERVICE")
                .requestMatchers("/api/v1/content/**").hasAnyRole("ADMIN", "CONTENT_EDITOR", "DEVICE_SERVICE")
                .anyRequest().authenticated());
            http.csrf(csrf -> csrf.ignoringRequestMatchers(request ->
                request.getRequestURI().startsWith("/api/")
                    && request.getHeader("X-Client-Key") != null
                    && !request.getHeader("X-Client-Key").isBlank()
            ));
            http.addFilterBefore(new com.lexibridge.operations.security.api.ApiSecurityFilter(
                hmacAuthService,
                apiRateLimiterService
            ), UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }

        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager(List.of(
                User.withUsername("admin").password("{noop}x").roles("ADMIN").build(),
                User.withUsername("device").password("{noop}x").roles("DEVICE_SERVICE").build()
            ));
        }
    }
}

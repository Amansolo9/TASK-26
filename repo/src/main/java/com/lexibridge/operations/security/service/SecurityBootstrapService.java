package com.lexibridge.operations.security.service;

import com.lexibridge.operations.security.repository.SecurityBootstrapRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class SecurityBootstrapService implements CommandLineRunner {

    private static final List<DemoRoleUser> DEMO_ROLE_USERS = List.of(
        new DemoRoleUser("content_editor", "Demo Content Editor", "ContentPass2026!", "CONTENT_EDITOR"),
        new DemoRoleUser("moderator", "Demo Moderator", "ModeratorPass2026!", "MODERATOR"),
        new DemoRoleUser("front_desk", "Demo Front Desk", "FrontDeskPass2026!", "FRONT_DESK"),
        new DemoRoleUser("employee", "Demo Employee", "EmployeePass2026!", "EMPLOYEE"),
        new DemoRoleUser("manager", "Demo Manager", "ManagerPass2026!", "MANAGER"),
        new DemoRoleUser("hr_approver", "Demo HR Approver", "HrApproverPass2026!", "HR_APPROVER"),
        new DemoRoleUser("supervisor", "Demo Supervisor", "SupervisorPass2026!", "SUPERVISOR")
    );

    private record DemoRoleUser(String username, String fullName, String password, String roleCode) {}

    private static final Logger log = LoggerFactory.getLogger(SecurityBootstrapService.class);

    private final SecurityBootstrapRepository bootstrapRepository;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordEncoder passwordEncoder;

    @Value("${lexibridge.bootstrap.enabled:false}")
    private boolean bootstrapEnabled;

    @Value("${lexibridge.bootstrap.admin.username:admin}")
    private String adminUsername;

    @Value("${lexibridge.bootstrap.admin.full-name:LexiBridge Admin}")
    private String adminFullName;

    @Value("${lexibridge.bootstrap.admin.password:}")
    private String adminPassword;

    @Value("${lexibridge.bootstrap.device.client-key:demo-device}")
    private String deviceClientKey;

    @Value("${lexibridge.bootstrap.device.shared-secret:}")
    private String deviceSharedSecret;

    public SecurityBootstrapService(SecurityBootstrapRepository bootstrapRepository,
                                    PasswordPolicyValidator passwordPolicyValidator,
                                    PasswordEncoder passwordEncoder) {
        this.bootstrapRepository = bootstrapRepository;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!bootstrapEnabled) {
            return;
        }
        validateBootstrapSecrets();
        bootstrapAdminUser();
        bootstrapDemoRoleUsers();
        bootstrapDeviceClient();
    }

    private void bootstrapDemoRoleUsers() {
        for (DemoRoleUser demo : DEMO_ROLE_USERS) {
            if (bootstrapRepository.findUserIdByUsername(demo.username()).isPresent()) {
                continue;
            }
            passwordPolicyValidator.validateOrThrow(demo.password());
            long userId = bootstrapRepository.createUser(
                demo.username(),
                demo.fullName(),
                passwordEncoder.encode(demo.password())
            );
            long roleId = bootstrapRepository.findRoleIdByCode(demo.roleCode())
                .orElseThrow(() -> new IllegalStateException(demo.roleCode() + " role not found in bootstrap migration."));
            bootstrapRepository.assignRole(userId, roleId);
            log.warn("Bootstrapped demo role user '{}' ({}). For local development only.", demo.username(), demo.roleCode());
        }
    }

    private void validateBootstrapSecrets() {
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD is required when bootstrap is enabled.");
        }
        if (deviceSharedSecret == null || deviceSharedSecret.isBlank()) {
            throw new IllegalStateException("BOOTSTRAP_DEVICE_SHARED_SECRET is required when bootstrap is enabled.");
        }
        if ("ChangeMe123!@".equals(adminPassword) || "demo-device-shared-secret".equals(deviceSharedSecret)) {
            throw new IllegalStateException("Bootstrap secrets must be rotated and may not use known insecure defaults.");
        }
    }

    private void bootstrapAdminUser() {
        if (bootstrapRepository.findUserIdByUsername(adminUsername).isPresent()) {
            return;
        }

        passwordPolicyValidator.validateOrThrow(adminPassword);

        long userId = bootstrapRepository.createAdminUser(
            adminUsername,
            adminFullName,
            passwordEncoder.encode(adminPassword)
        );

        long adminRoleId = bootstrapRepository.findRoleIdByCode("ADMIN")
            .orElseThrow(() -> new IllegalStateException("ADMIN role not found in bootstrap migration."));

        bootstrapRepository.assignRole(userId, adminRoleId);
        log.warn("Bootstrapped default admin user '{}'. Change password immediately.", adminUsername);
    }

    private void bootstrapDeviceClient() {
        if (bootstrapRepository.findDeviceClientIdByKey(deviceClientKey).isPresent()) {
            return;
        }

        long clientId = bootstrapRepository.createDeviceClient(deviceClientKey, "Demo Local Device");
        bootstrapRepository.createHmacSecret(clientId, 1, deviceSharedSecret.getBytes(StandardCharsets.UTF_8));
        log.warn("Bootstrapped demo device client '{}'. Rotate shared secret for production.", deviceClientKey);
    }
}

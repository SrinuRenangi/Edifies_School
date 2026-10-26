package com.edifies.attendance.modules.security.service;

import com.edifies.attendance.modules.security.model.AdminUser;
import com.edifies.attendance.modules.security.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class ManagerAccountInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ManagerAccountInitializer.class);

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.manager.default-username:manager}")
    private String defaultUsername;

    @Value("${app.manager.default-password:Manager@12345}")
    private String defaultPassword;

    @Value("${app.manager.default-fullname:Operations Manager}")
    private String defaultFullName;

    public ManagerAccountInitializer(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!adminUserRepository.existsByUsernameIgnoreCase(defaultUsername)) {
            String encoded = passwordEncoder.encode(defaultPassword);
            AdminUser manager = new AdminUser(
                    defaultUsername,
                    encoded,
                    defaultFullName,
                    "ROLE_MANAGER",
                    true
            );
            adminUserRepository.save(manager);
            log.info("Provisioned initial manager account: username='{}', role='ROLE_MANAGER'", defaultUsername);
        }
    }
}

package com.edifies.attendance.modules.security.controller;

import com.edifies.attendance.common.exception.InvalidOperationException;
import com.edifies.attendance.modules.security.dto.AdminLoginRequest;
import com.edifies.attendance.modules.security.dto.AdminLoginResponse;
import com.edifies.attendance.modules.security.dto.AdminProfileResponse;
import com.edifies.attendance.modules.security.model.AdminUser;
import com.edifies.attendance.modules.security.repository.AdminUserRepository;
import com.edifies.attendance.modules.security.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/admin")
@CrossOrigin(origins = "*")
public class AdminAuthController {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthController.class);

    private final AuthenticationManager authenticationManager;
    private final AdminUserRepository adminUserRepository;
    private final LoginAttemptService loginAttemptService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    public AdminAuthController(AuthenticationManager authenticationManager,
                               AdminUserRepository adminUserRepository,
                               LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.adminUserRepository = adminUserRepository;
        this.loginAttemptService = loginAttemptService;
    }

    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String username = request.username().trim();

        if (loginAttemptService.isBlocked(username)) {
            long remaining = loginAttemptService.getRemainingLockoutSeconds(username);
            log.warn("Admin login blocked: username='{}' is locked out (remainingSeconds={})", username, remaining);
            throw new InvalidOperationException("Account temporarily locked due to multiple failed login attempts. Try again in "
                    + Math.max(1, remaining / 60) + " minutes.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.password())
            );

            SecurityContext context = securityContextHolderStrategy.createEmptyContext();
            context.setAuthentication(authentication);
            securityContextHolderStrategy.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            loginAttemptService.recordSuccess(username);
            log.info("Administrator logged in successfully: username='{}'", username);

            AdminUser admin = adminUserRepository.findByUsernameIgnoreCase(username)
                    .orElseThrow(() -> new BadCredentialsException("Invalid username or password."));

            return ResponseEntity.ok(new AdminLoginResponse(
                    admin.getUsername(),
                    admin.getFullName(),
                    admin.getRole(),
                    "Authentication successful"
            ));
        } catch (BadCredentialsException | DisabledException ex) {
            loginAttemptService.recordFailure(username);
            log.warn("Administrator login failed for username='{}': {}", username, ex.getMessage());
            throw new BadCredentialsException("Invalid username or password.");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            log.info("Administrator logged out: username='{}'", auth.getName());
        }

        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<AdminProfileResponse> getCurrentAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() 
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = authentication.getName();
        AdminUser admin = adminUserRepository.findByUsernameIgnoreCase(username)
                .orElse(null);

        if (admin == null || !admin.isActive()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(new AdminProfileResponse(
                admin.getUsername(),
                admin.getFullName(),
                admin.getRole()
        ));
    }
}

package com.edifies.attendance.modules.kiosk.service;

import com.edifies.attendance.common.exception.InvalidOperationException;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import com.edifies.attendance.modules.kiosk.dto.KioskAuthRequest;
import com.edifies.attendance.modules.kiosk.dto.KioskAuthResponse;
import com.edifies.attendance.modules.security.service.LoginAttemptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class KioskAuthService {

    private static final Logger log = LoggerFactory.getLogger(KioskAuthService.class);

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final KioskSessionService kioskSessionService;

    public KioskAuthService(EmployeeRepository employeeRepository,
                            PasswordEncoder passwordEncoder,
                            LoginAttemptService loginAttemptService,
                            KioskSessionService kioskSessionService) {
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.kioskSessionService = kioskSessionService;
    }

    public KioskAuthResponse authenticate(KioskAuthRequest request) {
        String empId = request.employeeId().trim();

        // 1. Check if employee is currently locked out
        if (loginAttemptService.isBlocked(empId)) {
            long remaining = loginAttemptService.getRemainingLockoutSeconds(empId);
            log.warn("Kiosk auth rejected: employeeId='{}' is locked out (remainingSeconds={})", empId, remaining);
            throw new InvalidOperationException("Account temporarily locked due to multiple failed attempts. Please try again in " 
                    + Math.max(1, remaining / 60) + " minutes or contact administrator.");
        }

        // 2. Find employee
        Optional<Employee> employeeOpt = employeeRepository.findByEmployeeIdIgnoreCase(empId);
        if (employeeOpt.isEmpty()) {
            loginAttemptService.recordFailure(empId);
            log.warn("Kiosk auth failed: employeeId='{}' not found", empId);
            throw new BadCredentialsException("Invalid Employee ID or PIN.");
        }

        Employee employee = employeeOpt.get();

        // 3. Confirm employee is active (inactive employees cannot authenticate)
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            loginAttemptService.recordFailure(empId);
            log.warn("Kiosk auth failed: employeeId='{}' is INACTIVE", empId);
            throw new BadCredentialsException("Invalid Employee ID or PIN.");
        }

        // 4. Verify PIN hash
        if (employee.getPinHash() == null || !passwordEncoder.matches(request.pin().trim(), employee.getPinHash())) {
            loginAttemptService.recordFailure(empId);
            log.warn("Kiosk auth failed: invalid PIN for employeeId='{}'", empId);
            throw new BadCredentialsException("Invalid Employee ID or PIN.");
        }

        // 5. Successful authentication: reset failed attempts
        loginAttemptService.recordSuccess(empId);
        log.info("Kiosk auth successful: employeeId='{}', fullName='{}'", employee.getEmployeeId(), employee.getFullName());

        // 6. Create short-lived session
        KioskSessionService.KioskSession session = kioskSessionService.createSession(employee);

        return new KioskAuthResponse(
                session.token(),
                session.employeeId(),
                session.fullName(),
                session.department(),
                KioskSessionService.SESSION_DURATION_SECONDS
        );
    }

    public void resetSession(String token) {
        kioskSessionService.invalidateSession(token);
    }
}

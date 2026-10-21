# Acceptance Criteria & Given/When/Then Test Scenarios
## Staff Attendance Management System — Phase 2 Acceptance Specification

**Document Version:** 1.0.0  
**Status:** Approved Acceptance Baseline  

---

### Scenario 1: Successful First Check-In
```gherkin
Feature: Employee Check-In
  Scenario: Employee punches in on a new workday
    Given employee "EMP001" is ACTIVE and has state "NOT_CHECKED_IN" for today
    And the kiosk camera is active with a live preview
    When the employee captures a photo and taps "Confirm Check-In"
    Then the server records a new attendance session with the DB server timestamp
    And the photo is durably saved to the protected storage directory
    And the kiosk displays a green success banner: "Welcome, John Doe! Check-In Recorded"
    And after 3 seconds, the kiosk resets to the clean start screen
```

### Scenario 2: Successful Check-Out
```gherkin
Feature: Employee Check-Out
  Scenario: Employee punches out after working
    Given employee "EMP001" has an active session with state "CHECKED_IN"
    And the kiosk camera is active with a live preview
    When the employee captures a photo and taps "Confirm Check-Out"
    Then the server updates the existing session with out_time and out_photo_id
    And the session status transitions to "PRESENT"
    And the kiosk displays a green success banner: "Goodbye, John Doe! Check-Out Recorded"
    And after 3 seconds, the kiosk resets to the clean start screen
```

### Scenario 3: Duplicate Check-In Attempt on Same Day
```gherkin
Feature: Duplicate Check-In Prevention
  Scenario: Employee attempts to check in again after completing check-out
    Given employee "EMP001" has already completed check-in and check-out for today (state "CHECKED_OUT")
    When employee "EMP001" enters their ID and PIN on the kiosk
    Then the system rejects any punch action
    And the kiosk displays: "You have already completed attendance for today."
    And the kiosk returns to the idle screen after 5 seconds
```

### Scenario 4: Checkout Attempt Without an Open Session
```gherkin
Feature: Sequential Punch Enforcement
  Scenario: Employee attempts checkout with no prior check-in
    Given employee "EMP002" has state "NOT_CHECKED_IN" for today
    When employee "EMP002" enters credentials and attempts to trigger Check-Out
    Then the system rejects the operation with HTTP 400
    And the kiosk displays: "No active check-in found for today. Please Check-In first."
    And no attendance record is created
```

### Scenario 5: Invalid Employee ID or PIN (Brute-Force Lockout)
```gherkin
Feature: PIN Brute-Force Protection
  Scenario: Bad actor enters incorrect PIN repeatedly
    Given employee "EMP003" exists
    When an incorrect PIN is entered 5 consecutive times within 15 minutes
    Then the system locks the employee PIN for 15 minutes
    And the kiosk displays: "Account locked due to repeated failed attempts. Try again in 15 minutes."
    And a security warning is written to the audit log
```

### Scenario 6: Camera Permission Denied
```gherkin
Feature: Camera Permission Guard
  Scenario: Browser camera permission is revoked on kiosk device
    Given the kiosk browser has camera access denied
    When an employee enters their ID and PIN
    Then the kiosk UI detects the permission rejection
    And the kiosk displays: "Camera permission is required to continue. Please enable camera access in device settings."
    And the punch submission button remains disabled
```

### Scenario 7: Photo Upload Failure / Network Disconnect
```gherkin
Feature: Zero False Success on Upload Failure
  Scenario: Network drops while uploading photo
    Given employee "EMP001" initiates a Check-In punch
    When the network connection drops during image transmission
    Then the backend transaction aborts and rolls back completely
    And zero partial records are stored in the database
    And the kiosk displays: "Photo upload failed due to network error. Please try again."
    And the employee's state remains "NOT_CHECKED_IN"
```

### Scenario 8: Database Write Failure / Rollback
```gherkin
Feature: Database Transaction Atomicity
  Scenario: Database connection fails during commit
    Given an employee submits a valid Check-In payload
    When the database encounters a connection or constraint failure during commit
    Then the backend transaction rolls back
    And any temporarily written image file is purged
    And the server returns HTTP 500
    And the kiosk displays: "Your attendance could not be saved. Please contact the administrator."
    And the kiosk NEVER displays a success confirmation
```

### Scenario 9: Duplicate Request After Uncertain Network Response (Idempotency Replay)
```gherkin
Feature: Idempotency Replay Safety
  Scenario: Kiosk retransmits request with identical idempotency key
    Given a Check-In request was processed by DB but response timed out on the wire
    When the kiosk re-sends the exact same payload and X-Idempotency-Key
    Then the server recognizes the existing key in the idempotency table
    And returns the original HTTP 201 response without creating a second record
    And the kiosk displays the normal success message
```

### Scenario 10: Concurrent Check-Ins from Multiple Devices
```gherkin
Feature: Concurrency Race-Condition Defense
  Scenario: Employee punches simultaneously from two kiosks
    Given employee "EMP004" is in state "NOT_CHECKED_IN"
    When Request A and Request B arrive at the server at the exact same millisecond
    Then PostgreSQL row locking and partial unique index permits exactly one insert
    And the winning request returns HTTP 201 Created
    And the losing request receives HTTP 409 Conflict
    And exactly one attendance session exists in the database
```

### Scenario 11: Missing Checkout from Previous Day (Auto-Close)
```gherkin
Feature: Workday Cut-Off Maintenance
  Scenario: Employee forgot to check out yesterday
    Given employee "EMP001" checked in yesterday but never checked out (status "INCOMPLETE")
    When the system clock strikes 04:00 AM IST today
    Then the scheduled maintenance job updates the session status to "AUTO_CLOSED"
    And logs a system audit entry
    When employee "EMP001" arrives today at 09:00 AM
    Then the employee is free to Check-In for today's new work-date
```

### Scenario 12: Employee Deactivation with Historical Audit Preservation
```gherkin
Feature: Staff Deactivation Lifecycle
  Scenario: Admin deactivates an employee who has 2 years of attendance
    Given employee "EMP005" has 400 attendance sessions recorded
    When the administrator marks "EMP005" as INACTIVE
    Then "EMP005" can no longer authenticate at the kiosk
    And all 400 historical attendance sessions and photos remain fully viewable in admin reports
    And zero foreign keys are deleted or nullified
```

### Scenario 13: Administrative Timestamp Correction with Mandatory Reason
```gherkin
Feature: Audited Administrative Correction
  Scenario: Admin adjusts a missed check-out time
    Given employee "EMP001" has an "AUTO_CLOSED" session from yesterday
    When the administrator supplies an out_time of "18:00:00" and reason "Employee confirmed checkout forgot at end of shift"
    Then the session status updates to "MANUAL_OVERRIDE"
    And the out_time is saved
    And an audit log is recorded containing admin ID, old state, new state, and reason
    And the monthly report immediately reflects the updated work duration
```

### Scenario 14: Unauthorized Attempt to View Employee Photo
```gherkin
Feature: Attendance Photo Privacy
  Scenario: Direct URL access or unauthenticated request to photo stream
    Given an attendance photo exists at "/api/v1/photos/{uuid}"
    When an unauthenticated user or kiosk client attempts to access the URL
    Then the server returns HTTP 401 Unauthorized or HTTP 403 Forbidden
    And no image bytes are transferred
```

### Scenario 15: Monthly Report Rendering with Incomplete / Adjusted Flags
```gherkin
Feature: Reporting Integrity
  Scenario: Admin reviews monthly report with varied session states
    Given a monthly report for October 2026 is generated for 20 employees
    Then completed sessions display standard green checkmarks
    And "AUTO_CLOSED" sessions display prominent yellow warning badges
    And "MANUAL_OVERRIDE" sessions display an audit information icon showing the correction reason
    And days with no punches are clearly rendered as "No Record" rather than assumed leave
```

### Scenario 16: Historical Reporting After Employee Departure
```gherkin
Feature: Ex-Employee Historical Traceability
  Scenario: Generating a 3-year compliance report including departed staff
    Given employee "EMP006" left the company 18 months ago and is INACTIVE
    When the admin runs a report covering the past 24 months
    Then "EMP006" is listed with an "(Inactive)" badge
    And all attendance sessions during their tenure are accurately calculated in totals
```

/**
 * Staff Attendance Management System — Client Application
 * Phase 5: Authentication, Authorization, Employee PIN Security & Kiosk Access
 */

const API_ADMIN_EMPLOYEES = '/api/admin/employees';
const API_ADMIN_AUTH = '/api/auth/admin';
const API_ADMIN_ATTENDANCE = '/api/admin/attendance';
const API_ADMIN_REPORTS = '/api/admin/reports';
const API_ADMIN_STORAGE = '/api/admin/storage';
const API_ADMIN_DEVICES = '/api/admin/devices';
const API_ADMIN_DASHBOARD = '/api/admin/dashboard';
const API_KIOSK = '/api/kiosk';
const API_KIOSK_ATTENDANCE = '/api/kiosk/attendance';
const API_KIOSK_DEVICES = '/api/kiosk/devices';

const KIOSK_DEVICE_TOKEN_KEY = 'kiosk_device_token';
const KIOSK_DEVICE_INFO_KEY = 'kiosk_device_info';

// Application State
let isAdminAuthenticated = false;
let activeAdmin = null;
let currentView = 'DASHBOARD'; // 'DASHBOARD', 'DIRECTORY', 'ATTENDANCE', 'REPORTS', 'STORAGE', 'DEVICES', 'KIOSK'
let allEmployees = [];
let currentFilter = 'ACTIVE';
let searchQuery = '';
let searchDebounceTimer = null;

// Dashboard State (Phase 12)
let currentDashboardDate = null;
let currentDashboardSummary = null;
let currentDashboardRosterRecords = [];
let currentDashboardRosterFilter = 'ALL';
let dashboardQuickSearchTimer = null;
let dashboardAutoRefreshTimer = null;

// Attendance Log State
let currentAttendancePage = 0;
let totalAttendancePages = 1;

// Reports State
let currentMonthlyReportData = null;

// Device State
let pairingCountdownInterval = null;
let currentRevokingDeviceId = null;

// Kiosk State
let currentKioskToken = null;
let kioskTimerInterval = null;
let kioskRemainingSeconds = 60;
let punchResetTimer = null;

// DOM Elements
const viewAdminLogin = document.getElementById('view-admin-login');
const viewAdminDashboard = document.getElementById('view-admin-dashboard');
const viewAdminDirectory = document.getElementById('view-admin-directory');
const viewAdminAttendance = document.getElementById('view-admin-attendance');
const viewAdminReports = document.getElementById('view-admin-reports');
const viewAdminStorage = document.getElementById('view-admin-storage');
const viewAdminDevices = document.getElementById('view-admin-devices');
const viewKiosk = document.getElementById('view-kiosk');

const navDashboard = document.getElementById('nav-dashboard');
const navEmployees = document.getElementById('nav-employees');
const navAttendance = document.getElementById('nav-attendance');
const navReports = document.getElementById('nav-reports');
const navStorage = document.getElementById('nav-storage');
const navDevices = document.getElementById('nav-devices');
const navKiosk = document.getElementById('nav-kiosk');

const adminUserInfo = document.getElementById('admin-user-info');
const adminUserName = document.getElementById('admin-user-name');

const tableBody = document.getElementById('employee-table-body');
const tableWrapper = document.getElementById('table-wrapper');
const loadingSpinner = document.getElementById('loading-spinner');
const errorBox = document.getElementById('error-box');
const errorMessage = document.getElementById('error-message');
const emptyState = document.getElementById('empty-state');
const searchInput = document.getElementById('search-input');
const clearSearchBtn = document.getElementById('clear-search-btn');
const statusFilter = document.getElementById('status-filter');

const statTotal = document.getElementById('stat-total');
const statActive = document.getElementById('stat-active');
const statInactive = document.getElementById('stat-inactive');

// Modals
const registerModal = document.getElementById('register-modal');
const editModal = document.getElementById('edit-modal');
const resetPinModal = document.getElementById('reset-pin-modal');
const confirmModal = document.getElementById('confirm-modal');

// Kiosk Elements
const kioskNeutralState = document.getElementById('kiosk-neutral-state');
const kioskAuthState = document.getElementById('kiosk-authenticated-state');
const kioskEmployeeIdInput = document.getElementById('kiosk-employee-id');
const kioskPinInput = document.getElementById('kiosk-pin');
const kioskErrorBanner = document.getElementById('kiosk-error-banner');

// ==========================================
// Initialization
// ==========================================
document.addEventListener('DOMContentLoaded', () => {
    setupEventListeners();
    checkAdminSession();
});

function setupEventListeners() {
    // Navigation Tabs
    if (navDashboard) {
        navDashboard.addEventListener('click', () => switchView('DASHBOARD'));
    }
    navEmployees.addEventListener('click', () => switchView('DIRECTORY'));
    if (navAttendance) {
        navAttendance.addEventListener('click', () => switchView('ATTENDANCE'));
    }
    if (navReports) {
        navReports.addEventListener('click', () => switchView('REPORTS'));
    }
    if (navStorage) {
        navStorage.addEventListener('click', () => switchView('STORAGE'));
    }
    if (navDevices) {
        navDevices.addEventListener('click', () => switchView('DEVICES'));
    }
    navKiosk.addEventListener('click', () => switchView('KIOSK'));

    // Dashboard Work Date Picker
    const dashDateSelect = document.getElementById('dash-work-date-select');
    if (dashDateSelect) {
        dashDateSelect.addEventListener('change', (e) => {
            currentDashboardDate = e.target.value;
            loadDashboard(currentDashboardDate);
        });
    }

    // Dashboard Quick Employee Search
    const dashSearchInput = document.getElementById('dash-employee-search-input');
    if (dashSearchInput) {
        dashSearchInput.addEventListener('input', (e) => {
            const query = e.target.value.trim();
            clearTimeout(dashboardQuickSearchTimer);
            dashboardQuickSearchTimer = setTimeout(() => {
                handleDashboardEmployeeSearch(query);
            }, 250);
        });

        document.addEventListener('click', (e) => {
            const searchContainer = document.querySelector('.dash-search-container');
            const dropdown = document.getElementById('dash-employee-search-results');
            if (searchContainer && dropdown && !searchContainer.contains(e.target)) {
                dropdown.style.display = 'none';
            }
        });
    }

    // Open Register Modal
    document.getElementById('btn-open-register-modal').addEventListener('click', openRegisterModal);

    // Live Search with Debounce
    searchInput.addEventListener('input', (e) => {
        searchQuery = e.target.value.trim();
        clearSearchBtn.style.display = searchQuery ? 'block' : 'none';
        
        clearTimeout(searchDebounceTimer);
        searchDebounceTimer = setTimeout(() => {
            loadEmployees();
        }, 300);
    });

    // Clear Search Button
    clearSearchBtn.addEventListener('click', () => {
        searchInput.value = '';
        searchQuery = '';
        clearSearchBtn.style.display = 'none';
        loadEmployees();
    });

    // Status Filter Change
    statusFilter.addEventListener('change', (e) => {
        currentFilter = e.target.value;
        renderFilteredTable();
    });

    // Physical Keyboard support for Kiosk PIN input
    kioskEmployeeIdInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            if (kioskPinInput.value.length >= 4) {
                handleKioskAuthenticate();
            }
        }
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            closeRegisterModal();
            closeEditModal();
            closeResetPinModal();
            closeConfirmModal();
            closeCleanupConfirmModal();
            closePairDeviceModal();
            closeRevokeDeviceModal();
        }

        // Allow typing numbers directly into kiosk PIN when on Kiosk screen
        if (currentView === 'KIOSK' && kioskAuthState.style.display === 'none') {
            if (document.activeElement !== kioskEmployeeIdInput) {
                if (e.key >= '0' && e.key <= '9') {
                    appendKioskDigit(e.key);
                } else if (e.key === 'Backspace') {
                    backspaceKioskPin();
                } else if (e.key === 'Enter') {
                    handleKioskAuthenticate();
                }
            }
        }
    });
}

// ==========================================
// View Routing & State Switching
// ==========================================
function switchView(viewName) {
    currentView = viewName;

    if (navDashboard) {
        navDashboard.classList.toggle('active', viewName === 'DASHBOARD');
    }
    navEmployees.classList.toggle('active', viewName === 'DIRECTORY');
    if (navAttendance) {
        navAttendance.classList.toggle('active', viewName === 'ATTENDANCE');
    }
    if (navReports) {
        navReports.classList.toggle('active', viewName === 'REPORTS');
    }
    if (navStorage) {
        navStorage.classList.toggle('active', viewName === 'STORAGE');
    }
    if (navDevices) {
        navDevices.classList.toggle('active', viewName === 'DEVICES');
    }
    navKiosk.classList.toggle('active', viewName === 'KIOSK');

    if (viewName === 'KIOSK') {
        if (viewAdminDashboard) viewAdminDashboard.style.display = 'none';
        viewAdminDirectory.style.display = 'none';
        if (viewAdminAttendance) viewAdminAttendance.style.display = 'none';
        if (viewAdminReports) viewAdminReports.style.display = 'none';
        if (viewAdminStorage) viewAdminStorage.style.display = 'none';
        if (viewAdminDevices) viewAdminDevices.style.display = 'none';
        viewAdminLogin.style.display = 'none';
        viewKiosk.style.display = 'block';
        resetKioskScreen();
        checkKioskDeviceAuthorization();
    } else if (viewName === 'DASHBOARD') {
        viewKiosk.style.display = 'none';
        viewAdminDirectory.style.display = 'none';
        if (viewAdminAttendance) viewAdminAttendance.style.display = 'none';
        if (viewAdminReports) viewAdminReports.style.display = 'none';
        if (viewAdminStorage) viewAdminStorage.style.display = 'none';
        if (viewAdminDevices) viewAdminDevices.style.display = 'none';
        if (isAdminAuthenticated) {
            viewAdminLogin.style.display = 'none';
            if (viewAdminDashboard) viewAdminDashboard.style.display = 'block';
            loadDashboard();
        } else {
            if (viewAdminDashboard) viewAdminDashboard.style.display = 'none';
            viewAdminLogin.style.display = 'flex';
        }
    } else if (viewName === 'ATTENDANCE') {
        viewKiosk.style.display = 'none';
        if (viewAdminDashboard) viewAdminDashboard.style.display = 'none';
        viewAdminDirectory.style.display = 'none';
        if (viewAdminReports) viewAdminReports.style.display = 'none';
        if (viewAdminStorage) viewAdminStorage.style.display = 'none';
        if (viewAdminDevices) viewAdminDevices.style.display = 'none';
        if (isAdminAuthenticated) {
            viewAdminLogin.style.display = 'none';
            if (viewAdminAttendance) viewAdminAttendance.style.display = 'block';
            loadAttendanceRecords(0);
        } else {
            if (viewAdminAttendance) viewAdminAttendance.style.display = 'none';
            viewAdminLogin.style.display = 'flex';
        }
    } else if (viewName === 'REPORTS') {
        viewKiosk.style.display = 'none';
        if (viewAdminDashboard) viewAdminDashboard.style.display = 'none';
        viewAdminDirectory.style.display = 'none';
        if (viewAdminAttendance) viewAdminAttendance.style.display = 'none';
        if (viewAdminStorage) viewAdminStorage.style.display = 'none';
        if (viewAdminDevices) viewAdminDevices.style.display = 'none';
        if (isAdminAuthenticated) {
            viewAdminLogin.style.display = 'none';
            if (viewAdminReports) viewAdminReports.style.display = 'block';
            loadMonthlyReport();
        } else {
            if (viewAdminReports) viewAdminReports.style.display = 'none';
            viewAdminLogin.style.display = 'flex';
        }
    } else if (viewName === 'STORAGE') {
        viewKiosk.style.display = 'none';
        if (viewAdminDashboard) viewAdminDashboard.style.display = 'none';
        viewAdminDirectory.style.display = 'none';
        if (viewAdminAttendance) viewAdminAttendance.style.display = 'none';
        if (viewAdminReports) viewAdminReports.style.display = 'none';
        if (viewAdminDevices) viewAdminDevices.style.display = 'none';
        if (isAdminAuthenticated) {
            viewAdminLogin.style.display = 'none';
            if (viewAdminStorage) viewAdminStorage.style.display = 'block';
            refreshStorageDashboard();
        } else {
            if (viewAdminStorage) viewAdminStorage.style.display = 'none';
            viewAdminLogin.style.display = 'flex';
        }
    } else if (viewName === 'DEVICES') {
        viewKiosk.style.display = 'none';
        if (viewAdminDashboard) viewAdminDashboard.style.display = 'none';
        viewAdminDirectory.style.display = 'none';
        if (viewAdminAttendance) viewAdminAttendance.style.display = 'none';
        if (viewAdminReports) viewAdminReports.style.display = 'none';
        if (viewAdminStorage) viewAdminStorage.style.display = 'none';
        if (isAdminAuthenticated) {
            viewAdminLogin.style.display = 'none';
            if (viewAdminDevices) viewAdminDevices.style.display = 'block';
            loadAdminDevices();
            loadDeviceAuditLogs();
        } else {
            if (viewAdminDevices) viewAdminDevices.style.display = 'none';
            viewAdminLogin.style.display = 'flex';
        }
    } else { // DIRECTORY
        viewKiosk.style.display = 'none';
        if (viewAdminDashboard) viewAdminDashboard.style.display = 'none';
        if (viewAdminAttendance) viewAdminAttendance.style.display = 'none';
        if (viewAdminReports) viewAdminReports.style.display = 'none';
        if (viewAdminStorage) viewAdminStorage.style.display = 'none';
        if (viewAdminDevices) viewAdminDevices.style.display = 'none';
        if (isAdminAuthenticated) {
            viewAdminLogin.style.display = 'none';
            viewAdminDirectory.style.display = 'block';
            loadEmployees();
        } else {
            viewAdminDirectory.style.display = 'none';
            viewAdminLogin.style.display = 'flex';
        }
    }
}

// ==========================================
// Administrator Authentication
// ==========================================
async function checkAdminSession() {
    try {
        const response = await fetch(`${API_ADMIN_AUTH}/me`, {
            credentials: 'include'
        });

        if (response.ok) {
            const admin = await response.json();
            setAuthenticatedAdmin(admin);
            switchView('DASHBOARD');
        } else {
            setUnauthenticatedAdmin();
            switchView('DASHBOARD');
        }
    } catch (err) {
        console.warn('Session verification failed, defaulting to unauthenticated:', err);
        setUnauthenticatedAdmin();
        switchView('DASHBOARD');
    }
}

function setAuthenticatedAdmin(admin) {
    isAdminAuthenticated = true;
    activeAdmin = admin;
    adminUserName.textContent = admin.fullName || admin.username;
    adminUserInfo.style.display = 'flex';
}

function setUnauthenticatedAdmin() {
    isAdminAuthenticated = false;
    activeAdmin = null;
    adminUserInfo.style.display = 'none';
}

async function handleAdminLogin(event) {
    event.preventDefault();
    const errorBanner = document.getElementById('admin-login-error');
    errorBanner.style.display = 'none';

    const submitBtn = document.getElementById('btn-submit-admin-login');
    setButtonLoading(submitBtn, true);

    const username = document.getElementById('admin-username').value.trim();
    const password = document.getElementById('admin-password').value;

    try {
        const response = await fetch(`${API_ADMIN_AUTH}/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password }),
            credentials: 'include'
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || 'Authentication failed. Please verify credentials.');
        }

        setAuthenticatedAdmin(data);
        showToast('Administrator authenticated successfully.', 'success');
        switchView('DASHBOARD');
    } catch (err) {
        errorBanner.textContent = err.message;
        errorBanner.style.display = 'block';
    } finally {
        setButtonLoading(submitBtn, false);
    }
}

async function handleAdminLogout() {
    try {
        await fetch(`${API_ADMIN_AUTH}/logout`, {
            method: 'POST',
            credentials: 'include'
        });
    } catch (err) {
        console.warn('Logout request completed with warning:', err);
    } finally {
        setUnauthenticatedAdmin();
        showToast('Administrator session ended.', 'success');
        switchView('DIRECTORY');
    }
}

// ==========================================
// Employee Management (Admin Protected)
// ==========================================
async function loadEmployees() {
    if (!isAdminAuthenticated) return;

    showLoading();

    try {
        let url = API_ADMIN_EMPLOYEES;
        if (searchQuery) {
            url += `?search=${encodeURIComponent(searchQuery)}`;
        }

        const response = await fetch(url, { credentials: 'include' });

        if (response.status === 401) {
            setUnauthenticatedAdmin();
            switchView('DIRECTORY');
            return;
        }

        if (!response.ok) {
            throw new Error(`Server returned HTTP ${response.status} (${response.statusText})`);
        }

        allEmployees = await response.json();
        updateStats(allEmployees);
        renderFilteredTable();
    } catch (err) {
        console.error('Failed to load employees:', err);
        showError(err.message || 'Could not fetch records from the server.');
    }
}

function updateStats(employees) {
    const total = employees.length;
    const active = employees.filter(e => e.status === 'ACTIVE').length;
    const inactive = total - active;

    statTotal.textContent = total;
    statActive.textContent = active;
    statInactive.textContent = inactive;
}

function renderFilteredTable() {
    hideStates();

    let filtered = allEmployees;
    if (currentFilter === 'ACTIVE') {
        filtered = allEmployees.filter(e => e.status === 'ACTIVE');
    } else if (currentFilter === 'INACTIVE') {
        filtered = allEmployees.filter(e => e.status === 'INACTIVE');
    }

    if (filtered.length === 0) {
        emptyState.style.display = 'block';
        tableWrapper.style.display = 'none';
        return;
    }

    tableWrapper.style.display = 'block';
    tableBody.innerHTML = filtered.map(emp => createEmployeeRow(emp)).join('');
}

function createEmployeeRow(emp) {
    const formattedDate = formatDateTime(emp.createdAt);
    const isActive = emp.status === 'ACTIVE';
    const statusBadge = isActive
        ? `<span class="badge badge-active">ACTIVE</span>`
        : `<span class="badge badge-inactive">INACTIVE</span>`;

    const toggleBtn = isActive
        ? `<button class="btn btn-danger btn-sm" onclick="openDeactivateConfirm('${emp.id}', '${escapeHtml(emp.fullName)}')">Deactivate</button>`
        : `<button class="btn btn-success btn-sm" onclick="openActivateConfirm('${emp.id}', '${escapeHtml(emp.fullName)}')">Activate</button>`;

    return `
        <tr>
            <td class="font-mono font-medium">${escapeHtml(emp.employeeId)}</td>
            <td class="font-medium">${escapeHtml(emp.fullName)}</td>
            <td><span class="dept-pill">${escapeHtml(emp.department)}</span></td>
            <td>${statusBadge}</td>
            <td class="text-secondary text-sm">${formattedDate}</td>
            <td class="text-right table-actions">
                <button class="btn btn-secondary btn-sm" onclick="viewEmployeeAttendanceHistory('${escapeHtml(emp.employeeId)}')">History</button>
                <button class="btn btn-secondary btn-sm" onclick="openEditModal('${emp.id}', '${escapeHtml(emp.employeeId)}', '${escapeHtml(emp.fullName)}', '${escapeHtml(emp.department)}')">Edit</button>
                <button class="btn btn-secondary btn-sm" onclick="openResetPinModal('${emp.id}', '${escapeHtml(emp.fullName)}', '${escapeHtml(emp.employeeId)}')">Reset PIN</button>
                ${toggleBtn}
            </td>
        </tr>
    `;
}

// ==========================================
// Register Modal & PIN Assignment
// ==========================================
function openRegisterModal() {
    clearFormErrors('register-form');
    document.getElementById('register-form').reset();
    document.getElementById('register-error-banner').style.display = 'none';
    registerModal.style.display = 'flex';
    document.getElementById('reg-employee-id').focus();
}

function closeRegisterModal() {
    registerModal.style.display = 'none';
}

async function handleRegisterSubmit(event) {
    event.preventDefault();
    clearFormErrors('register-form');
    const errorBanner = document.getElementById('register-error-banner');
    errorBanner.style.display = 'none';

    const submitBtn = document.getElementById('btn-submit-register');
    setButtonLoading(submitBtn, true);

    const employeeId = document.getElementById('reg-employee-id').value.trim();
    const fullName = document.getElementById('reg-full-name').value.trim();
    const department = document.getElementById('reg-department').value.trim();
    const pin = document.getElementById('reg-pin').value.trim();

    try {
        const response = await fetch(API_ADMIN_EMPLOYEES, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ employeeId, fullName, department, pin }),
            credentials: 'include'
        });

        const data = await response.json();

        if (!response.ok) {
            if (data.validationErrors) {
                displayValidationErrors(data.validationErrors);
            } else {
                errorBanner.textContent = data.message || 'Failed to register employee.';
                errorBanner.style.display = 'block';
            }
            return;
        }

        closeRegisterModal();
        showToast(`Employee ${data.fullName} registered with secure PIN.`, 'success');
        loadEmployees();
    } catch (err) {
        errorBanner.textContent = err.message || 'A network error occurred.';
        errorBanner.style.display = 'block';
    } finally {
        setButtonLoading(submitBtn, false);
    }
}

// ==========================================
// Edit Modal
// ==========================================
function openEditModal(uuid, employeeId, fullName, department) {
    clearFormErrors('edit-form');
    document.getElementById('edit-error-banner').style.display = 'none';
    document.getElementById('edit-employee-uuid').value = uuid;
    document.getElementById('edit-employee-id').value = employeeId;
    document.getElementById('edit-full-name').value = fullName;
    document.getElementById('edit-department').value = department;
    editModal.style.display = 'flex';
    document.getElementById('edit-full-name').focus();
}

function closeEditModal() {
    editModal.style.display = 'none';
}

async function handleEditSubmit(event) {
    event.preventDefault();
    clearFormErrors('edit-form');
    const errorBanner = document.getElementById('edit-error-banner');
    errorBanner.style.display = 'none';

    const submitBtn = document.getElementById('btn-submit-edit');
    setButtonLoading(submitBtn, true);

    const uuid = document.getElementById('edit-employee-uuid').value;
    const fullName = document.getElementById('edit-full-name').value.trim();
    const department = document.getElementById('edit-department').value.trim();

    try {
        const response = await fetch(`${API_ADMIN_EMPLOYEES}/${uuid}`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ fullName, department }),
            credentials: 'include'
        });

        const data = await response.json();

        if (!response.ok) {
            errorBanner.textContent = data.message || 'Failed to update employee details.';
            errorBanner.style.display = 'block';
            return;
        }

        closeEditModal();
        showToast(`Employee details updated for ${data.fullName}.`, 'success');
        loadEmployees();
    } catch (err) {
        errorBanner.textContent = err.message || 'A network error occurred.';
        errorBanner.style.display = 'block';
    } finally {
        setButtonLoading(submitBtn, false);
    }
}

// ==========================================
// Reset Staff PIN Modal
// ==========================================
function openResetPinModal(uuid, fullName, employeeId) {
    document.getElementById('reset-pin-employee-uuid').value = uuid;
    document.getElementById('reset-pin-subtext').textContent = 
        `Set a confidential new 4–6 digit numeric PIN for ${fullName} (${employeeId}).`;
    document.getElementById('reset-new-pin').value = '';
    document.getElementById('reset-pin-error-banner').style.display = 'none';
    resetPinModal.style.display = 'flex';
    document.getElementById('reset-new-pin').focus();
}

function closeResetPinModal() {
    resetPinModal.style.display = 'none';
}

async function handleResetPinSubmit(event) {
    event.preventDefault();
    const errorBanner = document.getElementById('reset-pin-error-banner');
    errorBanner.style.display = 'none';

    const submitBtn = document.getElementById('btn-submit-reset-pin');
    setButtonLoading(submitBtn, true);

    const uuid = document.getElementById('reset-pin-employee-uuid').value;
    const newPin = document.getElementById('reset-new-pin').value.trim();

    if (!newPin.match(/^[0-9]{4,6}$/)) {
        errorBanner.textContent = 'PIN must consist of 4 to 6 numeric digits.';
        errorBanner.style.display = 'block';
        setButtonLoading(submitBtn, false);
        return;
    }

    try {
        const response = await fetch(`${API_ADMIN_EMPLOYEES}/${uuid}/reset-pin`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ newPin }),
            credentials: 'include'
        });

        if (!response.ok) {
            const data = await response.json();
            throw new Error(data.message || 'Failed to reset PIN.');
        }

        closeResetPinModal();
        showToast('PIN reset successfully. Any active failed-attempt lockouts cleared.', 'success');
    } catch (err) {
        errorBanner.textContent = err.message;
        errorBanner.style.display = 'block';
    } finally {
        setButtonLoading(submitBtn, false);
    }
}

// ==========================================
// Status Toggle Confirmation Dialog
// ==========================================
let pendingStatusAction = null;

function openDeactivateConfirm(uuid, fullName) {
    document.getElementById('confirm-modal-title').textContent = 'Deactivate Staff Member?';
    document.getElementById('confirm-modal-message').textContent = 
        `Are you sure you want to deactivate ${fullName}?`;
    document.getElementById('confirm-modal-subtext').textContent = 
        'Deactivated staff immediately lose attendance verification privileges at the kiosk. Their 3-year historical attendance records will remain preserved.';
    
    const confirmBtn = document.getElementById('confirm-modal-btn');
    confirmBtn.className = 'btn btn-danger';
    confirmBtn.textContent = 'Deactivate Staff';

    pendingStatusAction = () => updateEmployeeStatus(uuid, 'INACTIVE', fullName);
    confirmModal.style.display = 'flex';
}

function openActivateConfirm(uuid, fullName) {
    document.getElementById('confirm-modal-title').textContent = 'Reactivate Staff Member?';
    document.getElementById('confirm-modal-message').textContent = 
        `Restore attendance privileges for ${fullName}?`;
    document.getElementById('confirm-modal-subtext').textContent = 
        'This employee will immediately become eligible to log attendance punches at the kiosk using their ID and PIN.';

    const confirmBtn = document.getElementById('confirm-modal-btn');
    confirmBtn.className = 'btn btn-success';
    confirmBtn.textContent = 'Activate Staff';

    pendingStatusAction = () => updateEmployeeStatus(uuid, 'ACTIVE', fullName);
    confirmModal.style.display = 'flex';
}

function closeConfirmModal() {
    confirmModal.style.display = 'none';
    pendingStatusAction = null;
}

document.getElementById('confirm-modal-btn').addEventListener('click', () => {
    if (pendingStatusAction) {
        pendingStatusAction();
    }
    closeConfirmModal();
});

async function updateEmployeeStatus(uuid, newStatus, fullName) {
    try {
        const response = await fetch(`${API_ADMIN_EMPLOYEES}/${uuid}/status`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: newStatus }),
            credentials: 'include'
        });

        if (!response.ok) {
            throw new Error(`Failed to update status.`);
        }

        const actionText = newStatus === 'ACTIVE' ? 'activated' : 'deactivated';
        showToast(`${fullName} has been ${actionText}.`, 'success');
        loadEmployees();
    } catch (err) {
        showToast(err.message, 'danger');
    }
}

// ==========================================
// Attendance Kiosk Mode & PIN Touchpad
// ==========================================
function appendKioskDigit(digit) {
    if (kioskPinInput.value.length < 6) {
        kioskPinInput.value += digit;
    }
}

function backspaceKioskPin() {
    kioskPinInput.value = kioskPinInput.value.slice(0, -1);
}

function clearKioskPin() {
    kioskPinInput.value = '';
}

function resetKioskScreen() {
    clearInterval(kioskTimerInterval);
    currentKioskToken = null;
    kioskEmployeeIdInput.value = '';
    kioskPinInput.value = '';
    kioskErrorBanner.style.display = 'none';
    kioskAuthState.style.display = 'none';
    kioskNeutralState.style.display = 'block';
}

async function handleKioskAuthenticate() {
    kioskErrorBanner.style.display = 'none';
    const employeeId = kioskEmployeeIdInput.value.trim();
    const pin = kioskPinInput.value.trim();

    if (!employeeId) {
        kioskErrorBanner.textContent = 'Please enter your Employee ID.';
        kioskErrorBanner.style.display = 'block';
        kioskEmployeeIdInput.focus();
        return;
    }

    if (!pin || pin.length < 4) {
        kioskErrorBanner.textContent = 'Please enter your 4 to 6 digit numeric security PIN.';
        kioskErrorBanner.style.display = 'block';
        return;
    }

    const verifyBtn = document.getElementById('btn-kiosk-verify');
    setButtonLoading(verifyBtn, true);

    try {
        const response = await fetch(`${API_KIOSK}/authenticate`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Kiosk-Device-Token': getKioskDeviceToken()
            },
            body: JSON.stringify({ employeeId, pin })
        });

        const data = await response.json();

        if (!response.ok) {
            if (response.status === 401 && (data.code === 'DEVICE_NOT_AUTHORIZED' || data.code === 'DEVICE_REVOKED')) {
                handleKioskDeviceUnauthorized(data.message);
                return;
            }
            throw new Error(data.message || 'Invalid Employee ID or PIN.');
        }

        // Authentication Success: Transition to Authenticated Kiosk Screen
        currentKioskToken = data.token;
        document.getElementById('kiosk-auth-name').textContent = data.fullName;
        document.getElementById('kiosk-auth-id').textContent = data.employeeId;
        document.getElementById('kiosk-auth-dept').textContent = data.department;

        kioskNeutralState.style.display = 'none';
        kioskAuthState.style.display = 'block';

        startKioskCountdown(data.expiresInSeconds || 60);

        // Fetch current attendance status from server
        fetchKioskAttendanceStatus();
    } catch (err) {
        kioskErrorBanner.textContent = err.message;
        kioskErrorBanner.style.display = 'block';
        clearKioskPin();
    } finally {
        setButtonLoading(verifyBtn, false);
    }
}

async function fetchKioskAttendanceStatus() {
    const loadingEl = document.getElementById('kiosk-attendance-loading');
    const errorEl = document.getElementById('kiosk-attendance-error');
    const checkinCard = document.getElementById('kiosk-action-checkin');
    const checkoutCard = document.getElementById('kiosk-action-checkout');
    const completedCard = document.getElementById('kiosk-action-completed');
    const successCard = document.getElementById('kiosk-punch-success');

    loadingEl.style.display = 'block';
    errorEl.style.display = 'none';
    checkinCard.style.display = 'none';
    checkoutCard.style.display = 'none';
    completedCard.style.display = 'none';
    successCard.style.display = 'none';

    try {
        const response = await fetch(`${API_KIOSK_ATTENDANCE}/status`, {
            headers: {
                'X-Kiosk-Session': currentKioskToken,
                'X-Kiosk-Device-Token': getKioskDeviceToken()
            }
        });

        const data = await response.json();

        if (!response.ok) {
            if (response.status === 401 && (data.code === 'DEVICE_NOT_AUTHORIZED' || data.code === 'DEVICE_REVOKED')) {
                handleKioskDeviceUnauthorized(data.message);
                return;
            }
            throw new Error(data.message || 'Failed to determine attendance status.');
        }

        loadingEl.style.display = 'none';

        if (data.allowedAction === 'CHECK_IN') {
            document.getElementById('kiosk-work-date-in').textContent = data.workDate;
            checkinCard.style.display = 'block';
        } else if (data.allowedAction === 'CHECK_OUT') {
            document.getElementById('kiosk-checkin-time-out').textContent = formatISTTime(data.checkInTime);
            checkoutCard.style.display = 'block';
        } else {
            // Completed for today
            const inTimeStr = formatISTTime(data.checkInTime);
            const outTimeStr = formatISTTime(data.checkOutTime);
            document.getElementById('kiosk-completed-summary').textContent = `In: ${inTimeStr} • Out: ${outTimeStr}`;
            completedCard.style.display = 'block';
        }
    } catch (err) {
        loadingEl.style.display = 'none';
        errorEl.textContent = err.message;
        errorEl.style.display = 'block';
    }
}

// ==========================================
// Phase 7: Camera & Attendance Photo Workflow
// ==========================================
let activeCameraStream = null;
let capturedPhotoBlob = null;
let capturedPreviewUrl = null;
let currentCameraAction = null; // 'CHECK_IN' or 'CHECK_OUT'

async function startKioskCamera(actionType) {
    currentCameraAction = actionType;
    const checkinCard = document.getElementById('kiosk-action-checkin');
    const checkoutCard = document.getElementById('kiosk-action-checkout');
    const cameraSection = document.getElementById('kiosk-camera-section');
    const cameraTitle = document.getElementById('kiosk-camera-mode-title');
    const errorBanner = document.getElementById('kiosk-camera-error');
    const confirmText = document.getElementById('confirm-punch-text');
    const streamWrapper = document.getElementById('camera-stream-wrapper');
    const previewWrapper = document.getElementById('camera-preview-wrapper');
    const liveControls = document.getElementById('camera-live-controls');
    const capturedControls = document.getElementById('camera-captured-controls');
    const video = document.getElementById('kiosk-camera-video');

    if (checkinCard) checkinCard.style.display = 'none';
    if (checkoutCard) checkoutCard.style.display = 'none';
    if (errorBanner) errorBanner.style.display = 'none';

    if (cameraTitle) {
        cameraTitle.textContent = actionType === 'CHECK_IN' ? 'Check-In Photo Evidence' : 'Check-Out Photo Evidence';
    }
    if (confirmText) {
        confirmText.textContent = actionType === 'CHECK_IN' ? 'Confirm & Check In' : 'Confirm & Check Out';
    }

    streamWrapper.style.display = 'flex';
    previewWrapper.style.display = 'none';
    liveControls.style.display = 'flex';
    capturedControls.style.display = 'none';
    cameraSection.style.display = 'block';

    stopKioskCamera();
    revokePhotoPreview();
    capturedPhotoBlob = null;

    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        showCameraError('Camera API is not supported in this browser or context is not secure (requires HTTPS or localhost).');
        return;
    }

    try {
        const stream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } },
            audio: false
        });
        activeCameraStream = stream;
        video.srcObject = stream;
        await video.play();
    } catch (err) {
        console.error('Camera access error:', err);
        if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') {
            showCameraError('Camera access is required to record attendance. Please allow camera permissions in your browser.');
        } else if (err.name === 'NotFoundError' || err.name === 'DevicesNotFoundError') {
            showCameraError('No camera hardware detected on this device.');
        } else if (err.name === 'NotReadableError' || err.name === 'TrackStartError') {
            showCameraError('Camera is currently in use by another application.');
        } else {
            showCameraError(`Camera error: ${err.message || 'Unable to open camera.'}`);
        }
    }
}

function showCameraError(msg) {
    const errorBanner = document.getElementById('kiosk-camera-error');
    const errorText = document.getElementById('kiosk-camera-error-text');
    if (errorText) errorText.textContent = msg;
    if (errorBanner) errorBanner.style.display = 'block';
}

function retryKioskCamera() {
    if (currentCameraAction) {
        startKioskCamera(currentCameraAction);
    }
}

function captureKioskPhoto() {
    const video = document.getElementById('kiosk-camera-video');
    const previewImg = document.getElementById('kiosk-captured-preview');
    const streamWrapper = document.getElementById('camera-stream-wrapper');
    const previewWrapper = document.getElementById('camera-preview-wrapper');
    const liveControls = document.getElementById('camera-live-controls');
    const capturedControls = document.getElementById('camera-captured-controls');

    if (!video || !video.videoWidth || !video.videoHeight) {
        showToast('Camera feed is still initializing. Please wait a moment.', 'warning');
        return;
    }

    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext('2d');
    
    // Draw mirrored video for natural orientation
    ctx.translate(canvas.width, 0);
    ctx.scale(-1, 1);
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

    canvas.toBlob((blob) => {
        if (!blob) {
            showToast('Failed to capture photo frame.', 'danger');
            return;
        }

        capturedPhotoBlob = blob;
        revokePhotoPreview();
        capturedPreviewUrl = URL.createObjectURL(blob);
        if (previewImg) previewImg.src = capturedPreviewUrl;

        streamWrapper.style.display = 'none';
        previewWrapper.style.display = 'flex';
        liveControls.style.display = 'none';
        capturedControls.style.display = 'flex';

        // Immediately release live camera tracks
        stopKioskCamera();
    }, 'image/jpeg', 0.88);
}

function retakeKioskPhoto() {
    revokePhotoPreview();
    capturedPhotoBlob = null;
    if (currentCameraAction) {
        startKioskCamera(currentCameraAction);
    }
}

function cancelKioskCamera() {
    stopKioskCamera();
    revokePhotoPreview();
    capturedPhotoBlob = null;

    const cameraSection = document.getElementById('kiosk-camera-section');
    if (cameraSection) cameraSection.style.display = 'none';

    if (currentCameraAction === 'CHECK_IN') {
        const card = document.getElementById('kiosk-action-checkin');
        if (card) card.style.display = 'block';
    } else if (currentCameraAction === 'CHECK_OUT') {
        const card = document.getElementById('kiosk-action-checkout');
        if (card) card.style.display = 'block';
    }
    currentCameraAction = null;
}

function stopKioskCamera() {
    if (activeCameraStream) {
        activeCameraStream.getTracks().forEach(track => {
            try {
                track.stop();
            } catch (e) {
                console.warn('Track stop error:', e);
            }
        });
        activeCameraStream = null;
    }
    const video = document.getElementById('kiosk-camera-video');
    if (video) video.srcObject = null;
}

function revokePhotoPreview() {
    if (capturedPreviewUrl) {
        URL.revokeObjectURL(capturedPreviewUrl);
        capturedPreviewUrl = null;
    }
    const previewImg = document.getElementById('kiosk-captured-preview');
    if (previewImg) previewImg.src = '';
}

async function confirmAndSubmitPunch() {
    if (!capturedPhotoBlob) {
        showToast('Please capture an attendance photo first.', 'warning');
        return;
    }

    const btn = document.getElementById('btn-camera-confirm');
    const retakeBtn = document.getElementById('btn-camera-retake');
    setButtonLoading(btn, true);
    if (retakeBtn) retakeBtn.disabled = true;

    const idempotencyKey = (typeof crypto !== 'undefined' && crypto.randomUUID) 
        ? crypto.randomUUID() 
        : 'idem-' + Date.now() + '-' + Math.random().toString(36).substring(2, 9);

    const formData = new FormData();
    const fileName = (currentCameraAction === 'CHECK_IN' ? 'check-in.jpg' : 'check-out.jpg');
    formData.append('photo', capturedPhotoBlob, fileName);

    const endpoint = currentCameraAction === 'CHECK_IN'
        ? `${API_KIOSK_ATTENDANCE}/check-in`
        : `${API_KIOSK_ATTENDANCE}/check-out`;

    try {
        const response = await fetch(endpoint, {
            method: 'POST',
            headers: {
                'X-Kiosk-Session': currentKioskToken,
                'Idempotency-Key': idempotencyKey,
                'X-Kiosk-Device-Token': getKioskDeviceToken()
            },
            body: formData
        });

        const data = await response.json();

        if (!response.ok) {
            if (response.status === 401 && (data.code === 'DEVICE_NOT_AUTHORIZED' || data.code === 'DEVICE_REVOKED')) {
                handleKioskDeviceUnauthorized(data.message);
                return;
            }
            throw new Error(data.message || 'Attendance action failed. Please try again.');
        }

        // Clean up camera & preview state
        stopKioskCamera();
        revokePhotoPreview();
        capturedPhotoBlob = null;
        const cameraSection = document.getElementById('kiosk-camera-section');
        if (cameraSection) cameraSection.style.display = 'none';

        // Show Success Confirmation
        const successCard = document.getElementById('kiosk-punch-success');
        if (currentCameraAction === 'CHECK_IN') {
            document.getElementById('punch-success-title').textContent = 'Checked In Successfully!';
            document.getElementById('punch-success-message').textContent = 'Shift started with verified photo evidence. Saved to PostgreSQL.';
            document.getElementById('punch-server-time').textContent = formatISTTime(data.checkInTime);
        } else {
            document.getElementById('punch-success-title').textContent = 'Checked Out Successfully!';
            document.getElementById('punch-success-message').textContent = 'Shift completed with verified photo evidence. Saved to PostgreSQL.';
            document.getElementById('punch-server-time').textContent = formatISTTime(data.checkOutTime);
        }
        successCard.style.display = 'block';

        showToast(`${currentCameraAction === 'CHECK_IN' ? 'Check-in' : 'Check-out'} recorded with photo evidence!`, 'success');
        startPunchAutoReset(5);
    } catch (err) {
        showToast(err.message, 'danger');
        showCameraError(err.message);
        setButtonLoading(btn, false);
        if (retakeBtn) retakeBtn.disabled = false;
    }
}

// Fallback direct punch handlers for backward compatibility
async function handleKioskCheckIn() {
    startKioskCamera('CHECK_IN');
}

async function handleKioskCheckOut() {
    startKioskCamera('CHECK_OUT');
}

function startPunchAutoReset(seconds) {
    clearInterval(punchResetTimer);
    let remaining = seconds;
    const countdownEl = document.getElementById('punch-reset-countdown');
    if (countdownEl) countdownEl.textContent = `${remaining}s`;

    punchResetTimer = setInterval(() => {
        remaining--;
        if (countdownEl) countdownEl.textContent = `${remaining}s`;
        if (remaining <= 0) {
            clearInterval(punchResetTimer);
            resetKioskSession();
        }
    }, 1000);
}

function startKioskCountdown(totalSeconds) {
    clearInterval(kioskTimerInterval);
    kioskRemainingSeconds = totalSeconds;

    const timerText = document.getElementById('session-timer-text');
    const timerBar = document.getElementById('session-timer-bar');

    timerText.textContent = `Resets in ${kioskRemainingSeconds}s`;
    timerBar.style.width = '100%';

    kioskTimerInterval = setInterval(() => {
        kioskRemainingSeconds--;
        if (kioskRemainingSeconds <= 0) {
            clearInterval(kioskTimerInterval);
            resetKioskSession();
        } else {
            timerText.textContent = `Resets in ${kioskRemainingSeconds}s`;
            const percentage = (kioskRemainingSeconds / totalSeconds) * 100;
            timerBar.style.width = `${percentage}%`;
        }
    }, 1000);
}

async function resetKioskSession() {
    clearInterval(kioskTimerInterval);
    clearInterval(punchResetTimer);

    if (currentKioskToken) {
        try {
            await fetch(`${API_KIOSK}/reset`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Kiosk-Device-Token': getKioskDeviceToken()
                },
                body: JSON.stringify({ token: currentKioskToken })
            });
        } catch (err) {
            console.warn('Kiosk session reset reported warning:', err);
        }
    }

    resetKioskScreen();
    showToast('Kiosk screen reset for next staff member.', 'success');
}

function resetKioskScreen() {
    currentKioskToken = null;
    currentCameraAction = null;
    clearInterval(kioskTimerInterval);
    clearInterval(punchResetTimer);

    // Completely stop and release camera hardware
    stopKioskCamera();
    revokePhotoPreview();
    capturedPhotoBlob = null;

    kioskEmployeeIdInput.value = '';
    kioskPinInput.value = '';
    kioskErrorBanner.style.display = 'none';

    // Reset action panels
    const checkinCard = document.getElementById('kiosk-action-checkin');
    const checkoutCard = document.getElementById('kiosk-action-checkout');
    const completedCard = document.getElementById('kiosk-action-completed');
    const cameraSection = document.getElementById('kiosk-camera-section');
    const successCard = document.getElementById('kiosk-punch-success');
    const errorEl = document.getElementById('kiosk-attendance-error');

    if (checkinCard) checkinCard.style.display = 'none';
    if (checkoutCard) checkoutCard.style.display = 'none';
    if (completedCard) completedCard.style.display = 'none';
    if (cameraSection) cameraSection.style.display = 'none';
    if (successCard) successCard.style.display = 'none';
    if (errorEl) errorEl.style.display = 'none';

    kioskAuthState.style.display = 'none';
    kioskNeutralState.style.display = 'block';
}


// ==========================================
// Admin Attendance Log Functions
// ==========================================
async function loadAttendanceRecords(page = 0) {
    const tableBody = document.getElementById('attendance-table-body');
    const emptyState = document.getElementById('att-empty-state');
    const pagination = document.getElementById('att-pagination');
    const pageInfo = document.getElementById('att-page-info');
    const btnPrev = document.getElementById('btn-att-prev');
    const btnNext = document.getElementById('btn-att-next');

    if (!tableBody) return;

    tableBody.innerHTML = '<tr><td colspan="9" class="text-center p-4"><div class="spinner-small" style="display:inline-block"></div> Loading attendance records...</td></tr>';
    if (emptyState) emptyState.style.display = 'none';

    const search = document.getElementById('att-search-input')?.value.trim() || '';
    const fromDate = document.getElementById('att-from-date')?.value || '';
    const toDate = document.getElementById('att-to-date')?.value || '';
    const status = document.getElementById('att-status-filter')?.value || '';

    const params = new URLSearchParams({
        page: page,
        size: 20
    });

    if (search) params.append('search', search);
    if (fromDate) params.append('fromDate', fromDate);
    if (toDate) params.append('toDate', toDate);
    if (status) params.append('status', status);

    try {
        const response = await fetch(`${API_ADMIN_ATTENDANCE}?${params.toString()}`, {
            credentials: 'include'
        });

        if (response.status === 401) {
            setUnauthenticatedAdmin();
            switchView('ATTENDANCE');
            return;
        }

        const data = await response.json();
        currentAttendancePage = data.number || 0;
        totalAttendancePages = data.totalPages || 1;

        const records = data.content || [];

        if (records.length === 0) {
            tableBody.innerHTML = '';
            if (emptyState) emptyState.style.display = 'block';
            if (pagination) pagination.style.display = 'none';
            return;
        }

        if (emptyState) emptyState.style.display = 'none';
        if (pagination) pagination.style.display = 'flex';

        tableBody.innerHTML = records.map(r => {
            let statusBadge = '';
            if (r.status === 'CHECKED_IN') {
                statusBadge = '<span class="badge-status-in">Checked In</span> <span class="badge-status-incomplete">Incomplete</span>';
            } else if (r.status === 'CHECKED_OUT') {
                statusBadge = '<span class="badge-status-out">Completed</span>';
            } else if (r.status === 'VOIDED') {
                statusBadge = '<span class="badge-status-voided">Voided</span>';
            } else {
                statusBadge = '<span class="badge-status-auto">Auto Closed</span> <span class="badge-status-incomplete">Cut-off</span>';
            }

            let indicatorBadge = '';
            if (r.correctionIndicator === 'Manual Entry') {
                indicatorBadge = ' <span class="badge-indicator-manual">📝 Manual</span>';
            } else if (r.correctionIndicator === 'Corrected') {
                indicatorBadge = ' <span class="badge-indicator-corrected">✏️ Corrected</span>';
            }

            const checkInFormatted = formatISTTime(r.checkInTime);
            const checkOutFormatted = r.checkOutTime ? formatISTTime(r.checkOutTime) : '<span class="text-muted">Missing / Open</span>';
            const durationDisplay = r.durationFormatted ? `<span class="badge-duration">${escapeHtml(r.durationFormatted)}</span>` : '<span class="text-muted">—</span>';

            let photoButtons = [];
            if (r.hasCheckInPhoto) {
                photoButtons.push(`<button class="btn-photo-view" onclick="viewAttendancePhoto('${r.id}', 'CHECK_IN', '${escapeHtml(r.employeeName)}', '${r.workDate}')">📷 In</button>`);
            }
            if (r.hasCheckOutPhoto) {
                photoButtons.push(`<button class="btn-photo-view" onclick="viewAttendancePhoto('${r.id}', 'CHECK_OUT', '${escapeHtml(r.employeeName)}', '${r.workDate}')">📷 Out</button>`);
            }
            const photoEvidenceHtml = photoButtons.length > 0 ? photoButtons.join(' ') : '<span class="text-muted" style="font-size:0.8rem">No Photo</span>';

            const actionsHtml = `
                <div class="actions-cell">
                    <button class="btn-action-correct" onclick="openCorrectionModal('${r.id}')" title="Correct check-in/out or resolve missed checkout">
                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                        </svg>
                        <span>Correct</span>
                    </button>
                    <button class="btn-action-audit" onclick="openAuditHistoryModal('${r.id}', '${escapeHtml(r.employeeName)}', '${escapeHtml(r.employeeCode)}', '${r.workDate}')" title="View change history and audit trail">
                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <circle cx="12" cy="12" r="10"></circle>
                            <polyline points="12 6 12 12 16 14"></polyline>
                        </svg>
                        <span>Audit</span>
                    </button>
                </div>
            `;

            return `
                <tr>
                    <td><strong>${r.workDate}</strong></td>
                    <td><code>${escapeHtml(r.employeeCode)}</code></td>
                    <td><strong>${escapeHtml(r.employeeName)}</strong></td>
                    <td>${escapeHtml(r.department)}</td>
                    <td>${checkInFormatted}</td>
                    <td>${checkOutFormatted}</td>
                    <td>${durationDisplay}</td>
                    <td>${statusBadge}${indicatorBadge}</td>
                    <td>${photoEvidenceHtml}</td>
                    <td>${actionsHtml}</td>
                </tr>
            `;
        }).join('');

        if (pageInfo) pageInfo.textContent = `Page ${currentAttendancePage + 1} of ${Math.max(1, totalAttendancePages)} (${data.totalElements || records.length} records)`;
        if (btnPrev) btnPrev.disabled = currentAttendancePage <= 0;
        if (btnNext) btnNext.disabled = currentAttendancePage >= totalAttendancePages - 1;

    } catch (err) {
        tableBody.innerHTML = `<tr><td colspan="10" class="text-center p-4 text-danger">Failed to load attendance records: ${escapeHtml(err.message)}</td></tr>`;
    }
}

function setAttendanceDateQuick(type) {
    const fromEl = document.getElementById('att-from-date');
    const toEl = document.getElementById('att-to-date');
    const today = new Date();

    if (type === 'today') {
        const todayStr = today.toISOString().split('T')[0];
        if (fromEl) fromEl.value = todayStr;
        if (toEl) toEl.value = todayStr;
    } else if (type === 'yesterday') {
        const yesterday = new Date(today);
        yesterday.setDate(yesterday.getDate() - 1);
        const yestStr = yesterday.toISOString().split('T')[0];
        if (fromEl) fromEl.value = yestStr;
        if (toEl) toEl.value = yestStr;
    }
    loadAttendanceRecords(0);
}

async function exportAttendanceRecordsCsv() {
    const search = document.getElementById('att-search-input')?.value.trim() || '';
    const fromDate = document.getElementById('att-from-date')?.value || '';
    const toDate = document.getElementById('att-to-date')?.value || '';
    const status = document.getElementById('att-status-filter')?.value || '';

    const params = new URLSearchParams();
    if (search) params.append('search', search);
    if (fromDate) params.append('fromDate', fromDate);
    if (toDate) params.append('toDate', toDate);
    if (status) params.append('status', status);

    try {
        showToast('Preparing attendance CSV export...', 'info');
        const response = await fetch(`${API_ADMIN_REPORTS}/attendance/export?${params.toString()}`, {
            credentials: 'include'
        });
        if (!response.ok) {
            throw new Error('Failed to generate attendance CSV export.');
        }
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.style.display = 'none';
        a.href = url;
        a.download = `attendance-report-${fromDate || 'all'}-to-${toDate || 'all'}.csv`;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        a.remove();
        showToast('Attendance CSV downloaded successfully.', 'success');
    } catch (err) {
        showToast('Export failed: ' + err.message, 'danger');
    }
}

// ==========================================
// Phase 9: Attendance Corrections, Manual Entries & Audit History
// ==========================================

let activeCorrectionRecord = null;

function formatISTTime(isoString) {
    if (!isoString) return '—';
    try {
        const date = new Date(isoString);
        return date.toLocaleTimeString('en-IN', {
            timeZone: 'Asia/Kolkata',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: false
        }) + ' IST';
    } catch {
        return isoString;
    }
}

function formatISTDateTime(isoString) {
    if (!isoString) return '—';
    try {
        const date = new Date(isoString);
        return date.toLocaleString('en-IN', {
            timeZone: 'Asia/Kolkata',
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: false
        }) + ' IST';
    } catch {
        return isoString;
    }
}

function parseTimeToISTIso(workDateStr, timeVal) {
    if (!timeVal) return null;
    const parts = timeVal.split(':');
    const hh = parts[0].padStart(2, '0');
    const mm = parts[1].padStart(2, '0');
    const ss = parts.length > 2 ? parts[2].padStart(2, '0') : '00';
    return `${workDateStr}T${hh}:${mm}:${ss}+05:30`;
}

function isoToTimeInput(isoString) {
    if (!isoString) return '';
    try {
        const date = new Date(isoString);
        return date.toLocaleTimeString('en-GB', {
            timeZone: 'Asia/Kolkata',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: false
        });
    } catch {
        return '';
    }
}

async function openCorrectionModal(recordId) {
    const modal = document.getElementById('correction-modal');
    const errorBanner = document.getElementById('correction-error-banner');
    if (errorBanner) errorBanner.style.display = 'none';

    try {
        const response = await fetch(`${API_ADMIN_ATTENDANCE}/${recordId}`, {
            credentials: 'include'
        });
        if (!response.ok) {
            throw new Error('Could not retrieve latest attendance record from server.');
        }
        const record = await response.json();
        activeCorrectionRecord = record;

        document.getElementById('corr-record-id').value = record.id;
        document.getElementById('corr-record-version').value = record.version !== undefined ? record.version : 0;
        document.getElementById('corr-emp-name').textContent = record.employeeName;
        document.getElementById('corr-emp-code').textContent = record.employeeCode;
        document.getElementById('corr-work-date').textContent = record.workDate;
        document.getElementById('corr-version-badge').textContent = `v${record.version !== undefined ? record.version : 0}`;

        const statusBadge = document.getElementById('corr-current-status-badge');
        if (statusBadge) {
            statusBadge.textContent = record.status;
            statusBadge.className = `badge badge-${record.status.toLowerCase()}`;
        }

        const actionSelect = document.getElementById('corr-action-type');
        if (record.status === 'AUTO_CLOSED' || !record.checkOutTime) {
            actionSelect.value = 'RESOLVE_MISSED_CHECKOUT';
        } else {
            actionSelect.value = 'CORRECT_CHECK_IN';
        }

        const inHint = document.getElementById('corr-in-hint');
        const outHint = document.getElementById('corr-out-hint');
        if (inHint) inHint.textContent = `Current: ${formatISTTime(record.checkInTime)}`;
        if (outHint) outHint.textContent = `Current: ${record.checkOutTime ? formatISTTime(record.checkOutTime) : 'Not recorded (Open / Missed)'}`;

        const inInput = document.getElementById('corr-in-time');
        const outInput = document.getElementById('corr-out-time');
        if (inInput) inInput.value = isoToTimeInput(record.checkInTime);
        if (outInput) outInput.value = record.checkOutTime ? isoToTimeInput(record.checkOutTime) : '';

        const reasonInput = document.getElementById('corr-reason');
        if (reasonInput) reasonInput.value = '';
        onCorrectionReasonInput();

        onCorrectionActionChanged();
        updateCorrectionPreview();

        modal.style.display = 'flex';
    } catch (err) {
        showToast(err.message, 'danger');
    }
}

function closeCorrectionModal() {
    const modal = document.getElementById('correction-modal');
    if (modal) modal.style.display = 'none';
    activeCorrectionRecord = null;
}

function onCorrectionActionChanged() {
    const action = document.getElementById('corr-action-type')?.value;
    const inGroup = document.getElementById('corr-in-group');
    const outGroup = document.getElementById('corr-out-group');
    const timesContainer = document.getElementById('corr-times-container');
    const statusGroup = document.getElementById('corr-status-group');

    if (!action) return;

    if (action === 'VOID_RECORD') {
        if (timesContainer) timesContainer.style.display = 'none';
        if (statusGroup) statusGroup.style.display = 'none';
    } else {
        if (timesContainer) timesContainer.style.display = 'grid';
        if (statusGroup) statusGroup.style.display = 'none';

        if (action === 'CORRECT_CHECK_IN') {
            if (inGroup) inGroup.style.display = 'block';
            if (outGroup) outGroup.style.display = 'none';
        } else if (action === 'CORRECT_CHECK_OUT' || action === 'RESOLVE_MISSED_CHECKOUT') {
            if (inGroup) inGroup.style.display = 'none';
            if (outGroup) outGroup.style.display = 'block';
        } else {
            if (inGroup) inGroup.style.display = 'block';
            if (outGroup) outGroup.style.display = 'block';
        }
    }

    updateCorrectionPreview();
}

function updateCorrectionPreview() {
    if (!activeCorrectionRecord) return;
    const action = document.getElementById('corr-action-type')?.value;
    const diffContent = document.getElementById('corr-diff-content');
    const inVal = document.getElementById('corr-in-time')?.value;
    const outVal = document.getElementById('corr-out-time')?.value;
    const reason = document.getElementById('corr-reason')?.value.trim();

    if (!diffContent) return;

    let html = '';

    if (action === 'VOID_RECORD') {
        html = `
            <div><strong>Action:</strong> <span style="color:#dc2626; font-weight:700;">VOID ATTENDANCE RECORD</span></div>
            <div><strong>Status Before:</strong> ${activeCorrectionRecord.status} &rarr; <strong>Status After:</strong> <span style="color:#dc2626; font-weight:700;">VOIDED</span></div>
            <div style="color:#64748b; font-size:0.8rem; margin-top:4px;">This record will be permanently marked as superseded/erroneous and will no longer count in daily reports or active summaries.</div>
        `;
    } else {
        const curIn = formatISTTime(activeCorrectionRecord.checkInTime);
        const curOut = activeCorrectionRecord.checkOutTime ? formatISTTime(activeCorrectionRecord.checkOutTime) : 'Not recorded (Open / Missed)';

        let targetIn = curIn;
        let targetOut = curOut;

        if (action === 'CORRECT_CHECK_IN' || action === 'CORRECT_BOTH') {
            targetIn = inVal ? `${inVal} IST` : '—';
        }
        if (action === 'CORRECT_CHECK_OUT' || action === 'CORRECT_BOTH' || action === 'RESOLVE_MISSED_CHECKOUT') {
            targetOut = outVal ? `${outVal} IST` : '—';
        }

        html = `
            <div><strong>Action:</strong> <span style="color:#0284c7; font-weight:600;">${action.replace(/_/g, ' ')}</span></div>
            <div style="margin-top:4px;"><strong>Check-In:</strong> <span style="color:#64748b; text-decoration:line-through;">${curIn}</span> &rarr; <span style="color:#16a34a; font-weight:600;">${targetIn}</span></div>
            <div><strong>Check-Out:</strong> <span style="color:#64748b; text-decoration:line-through;">${curOut}</span> &rarr; <span style="color:#16a34a; font-weight:600;">${targetOut}</span></div>
        `;
    }

    if (reason) {
        html += `<div style="margin-top:6px; color:#334155;"><strong>Reason:</strong> <em>"${escapeHtml(reason)}"</em></div>`;
    } else {
        html += `<div style="margin-top:6px; color:#dc2626;"><strong>Reason:</strong> <em>Required for audit trail (mandatory)</em></div>`;
    }

    diffContent.innerHTML = html;
}

function onCorrectionReasonInput() {
    const reasonEl = document.getElementById('corr-reason');
    const counterEl = document.getElementById('corr-reason-counter');
    if (reasonEl && counterEl) {
        const len = reasonEl.value.length;
        counterEl.textContent = `${len} / 500 chars`;
        counterEl.style.color = len > 480 ? '#dc2626' : '#64748b';
    }
    updateCorrectionPreview();
}

async function submitCorrection(event) {
    event.preventDefault();
    if (!activeCorrectionRecord) return;

    const errorBanner = document.getElementById('correction-error-banner');
    const btnSubmit = document.getElementById('btn-submit-correction');
    if (errorBanner) errorBanner.style.display = 'none';

    const action = document.getElementById('corr-action-type').value;
    const reason = document.getElementById('corr-reason').value.trim();
    const version = parseInt(document.getElementById('corr-record-version').value, 10);

    if (!reason) {
        if (errorBanner) {
            errorBanner.textContent = 'Correction reason is mandatory and cannot be empty.';
            errorBanner.style.display = 'block';
        }
        return;
    }

    const workDate = activeCorrectionRecord.workDate;
    let checkInIso = null;
    let checkOutIso = null;
    let targetStatus = null;

    if (action === 'VOID_RECORD') {
        targetStatus = 'VOIDED';
    } else {
        const inVal = document.getElementById('corr-in-time')?.value;
        const outVal = document.getElementById('corr-out-time')?.value;

        if (action === 'CORRECT_CHECK_IN' || action === 'CORRECT_BOTH') {
            if (!inVal) {
                if (errorBanner) {
                    errorBanner.textContent = 'Please enter a valid check-in time.';
                    errorBanner.style.display = 'block';
                }
                return;
            }
            checkInIso = new Date(parseTimeToISTIso(workDate, inVal)).toISOString();
        }

        if (action === 'CORRECT_CHECK_OUT' || action === 'CORRECT_BOTH' || action === 'RESOLVE_MISSED_CHECKOUT') {
            if (!outVal) {
                if (errorBanner) {
                    errorBanner.textContent = 'Please enter a valid check-out time.';
                    errorBanner.style.display = 'block';
                }
                return;
            }
            checkOutIso = new Date(parseTimeToISTIso(workDate, outVal)).toISOString();
        }

        const effIn = checkInIso ? new Date(checkInIso) : new Date(activeCorrectionRecord.checkInTime);
        const effOut = checkOutIso ? new Date(checkOutIso) : (activeCorrectionRecord.checkOutTime ? new Date(activeCorrectionRecord.checkOutTime) : null);
        if (effOut && effOut.getTime() < effIn.getTime()) {
            if (errorBanner) {
                errorBanner.textContent = 'Check-out time cannot precede check-in time.';
                errorBanner.style.display = 'block';
            }
            return;
        }

        targetStatus = (checkOutIso || activeCorrectionRecord.checkOutTime) ? 'CHECKED_OUT' : 'CHECKED_IN';
    }

    const payload = {
        actionType: action,
        checkInTime: checkInIso,
        checkOutTime: checkOutIso,
        status: targetStatus,
        reason: reason,
        recordVersion: version
    };

    try {
        setButtonLoading(btnSubmit, true);
        const response = await fetch(`${API_ADMIN_ATTENDANCE}/${activeCorrectionRecord.id}/corrections`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify(payload)
        });

        if (response.status === 409) {
            throw new Error('Concurrency Conflict: This attendance record was modified by another administrator. Please close this modal, reload the latest attendance records, and retry.');
        }

        if (!response.ok) {
            const errData = await response.json().catch(() => ({}));
            throw new Error(errData.message || 'Failed to apply attendance correction.');
        }

        setButtonLoading(btnSubmit, false);
        closeCorrectionModal();
        showToast('Attendance correction successfully applied and audit event recorded.', 'success');
        loadAttendanceRecords(currentAttendancePage);
    } catch (err) {
        setButtonLoading(btnSubmit, false);
        if (errorBanner) {
            errorBanner.textContent = err.message;
            errorBanner.style.display = 'block';
        }
    }
}

async function openAuditHistoryModal(recordId, empName, empCode, workDate) {
    const modal = document.getElementById('audit-history-modal');
    const loading = document.getElementById('audit-history-loading');
    const empty = document.getElementById('audit-history-empty');
    const timeline = document.getElementById('audit-timeline-container');

    document.getElementById('audit-header-emp-name').textContent = empName || 'Staff Member';
    document.getElementById('audit-header-emp-code').textContent = empCode ? `(${empCode})` : '';
    document.getElementById('audit-header-work-date').textContent = `Work Date: ${workDate || '--'}`;

    if (loading) loading.style.display = 'block';
    if (empty) empty.style.display = 'none';
    if (timeline) timeline.innerHTML = '';
    modal.style.display = 'flex';

    try {
        const response = await fetch(`${API_ADMIN_ATTENDANCE}/${recordId}/audit-history`, {
            credentials: 'include'
        });

        if (!response.ok) {
            throw new Error('Failed to retrieve audit history from server.');
        }

        const data = await response.json();
        const events = data.content || [];

        if (loading) loading.style.display = 'none';

        if (events.length === 0) {
            if (empty) empty.style.display = 'block';
            return;
        }

        renderAuditTimeline(events, timeline);
    } catch (err) {
        if (loading) loading.style.display = 'none';
        showToast(err.message, 'danger');
    }
}

function closeAuditHistoryModal() {
    const modal = document.getElementById('audit-history-modal');
    if (modal) modal.style.display = 'none';
}

function renderAuditTimeline(events, container) {
    container.innerHTML = events.map(e => {
        let diffRows = [];
        if (e.previousCheckInTime || e.correctedCheckInTime) {
            diffRows.push(`
                <tr>
                    <td><strong>Check-In</strong></td>
                    <td><span class="diff-val-old">${formatISTTime(e.previousCheckInTime)}</span></td>
                    <td><span class="diff-val-new">${formatISTTime(e.correctedCheckInTime)}</span></td>
                </tr>
            `);
        }
        if (e.previousCheckOutTime || e.correctedCheckOutTime) {
            diffRows.push(`
                <tr>
                    <td><strong>Check-Out</strong></td>
                    <td><span class="diff-val-old">${e.previousCheckOutTime ? formatISTTime(e.previousCheckOutTime) : 'None'}</span></td>
                    <td><span class="diff-val-new">${formatISTTime(e.correctedCheckOutTime)}</span></td>
                </tr>
            `);
        }
        if (e.previousStatus !== e.correctedStatus) {
            diffRows.push(`
                <tr>
                    <td><strong>Status</strong></td>
                    <td><span class="diff-val-old">${e.previousStatus || '—'}</span></td>
                    <td><span class="diff-val-new">${e.correctedStatus}</span></td>
                </tr>
            `);
        }

        const diffHtml = diffRows.length > 0
            ? `<table class="diff-table">
                 <thead>
                   <tr><th>Field</th><th>Original</th><th>Corrected</th></tr>
                 </thead>
                 <tbody>${diffRows.join('')}</tbody>
               </table>`
            : '<div class="text-muted text-sm mt-1">Status / entry recorded</div>';

        return `
            <div class="audit-card">
                <div class="audit-card-header">
                    <div>
                        <span class="audit-action-badge audit-action-${escapeHtml(e.actionType)}">${escapeHtml(e.actionType.replace(/_/g, ' '))}</span>
                        <span class="audit-meta" style="margin-left: 0.5rem;">by <strong>${escapeHtml(e.performedBy)}</strong></span>
                    </div>
                    <span class="audit-meta">${formatISTDateTime(e.createdAt)}</span>
                </div>
                <div class="audit-reason-box">
                    <strong>Reason:</strong> "${escapeHtml(e.reason)}"
                </div>
                ${diffHtml}
                <div style="margin-top: 0.5rem; font-size: 0.75rem; color: #94a3b8;">
                    Record State: <code>v${e.recordVersion}</code> &bull; Audit Ref: <code>${e.id.substring(0, 8)}</code>
                </div>
            </div>
        `;
    }).join('');
}

async function openManualAttendanceModal() {
    const modal = document.getElementById('manual-entry-modal');
    const errorBanner = document.getElementById('manual-error-banner');
    if (errorBanner) errorBanner.style.display = 'none';

    const select = document.getElementById('manual-emp-select');
    select.innerHTML = '<option value="">-- Select Employee --</option>';

    try {
        const response = await fetch(`${API_ADMIN_EMPLOYEES}?size=200`, {
            credentials: 'include'
        });
        if (response.ok) {
            const data = await response.json();
            const emps = data.content || [];
            emps.forEach(emp => {
                const opt = document.createElement('option');
                opt.value = emp.id;
                opt.textContent = `${emp.fullName} (${emp.employeeId} - ${emp.department || 'General'})`;
                select.appendChild(opt);
            });
        }
    } catch (err) {
        console.error('Error loading employees for manual entry', err);
    }

    const todayStr = new Date().toISOString().split('T')[0];
    document.getElementById('manual-work-date').value = todayStr;
    document.getElementById('manual-in-time').value = '09:00:00';
    document.getElementById('manual-out-time').value = '17:00:00';
    document.getElementById('manual-reason').value = '';
    onManualReasonInput();

    modal.style.display = 'flex';
}

function closeManualEntryModal() {
    const modal = document.getElementById('manual-entry-modal');
    if (modal) modal.style.display = 'none';
}

function onManualReasonInput() {
    const reasonEl = document.getElementById('manual-reason');
    const counterEl = document.getElementById('manual-reason-counter');
    if (reasonEl && counterEl) {
        const len = reasonEl.value.length;
        counterEl.textContent = `${len} / 500 chars`;
    }
}

async function submitManualEntry(event) {
    event.preventDefault();
    const errorBanner = document.getElementById('manual-error-banner');
    const btnSubmit = document.getElementById('btn-submit-manual');
    if (errorBanner) errorBanner.style.display = 'none';

    const empId = document.getElementById('manual-emp-select').value;
    const workDate = document.getElementById('manual-work-date').value;
    const inTime = document.getElementById('manual-in-time').value;
    const outTime = document.getElementById('manual-out-time').value;
    const reason = document.getElementById('manual-reason').value.trim();

    if (!empId) {
        errorBanner.textContent = 'Please select a staff member.';
        errorBanner.style.display = 'block';
        return;
    }
    if (!workDate) {
        errorBanner.textContent = 'Please enter a valid work date.';
        errorBanner.style.display = 'block';
        return;
    }
    if (!inTime) {
        errorBanner.textContent = 'Please enter a valid check-in time.';
        errorBanner.style.display = 'block';
        return;
    }
    if (!reason) {
        errorBanner.textContent = 'Documented reason for manual entry is mandatory.';
        errorBanner.style.display = 'block';
        return;
    }

    const inIso = new Date(parseTimeToISTIso(workDate, inTime)).toISOString();
    const outIso = outTime ? new Date(parseTimeToISTIso(workDate, outTime)).toISOString() : null;

    if (outIso && new Date(outIso).getTime() < new Date(inIso).getTime()) {
        errorBanner.textContent = 'Check-out time cannot precede check-in time.';
        errorBanner.style.display = 'block';
        return;
    }

    const payload = {
        employeeId: empId,
        workDate: workDate,
        checkInTime: inIso,
        checkOutTime: outIso,
        reason: reason
    };

    try {
        setButtonLoading(btnSubmit, true);
        const response = await fetch(`${API_ADMIN_ATTENDANCE}/manual`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            const errData = await response.json().catch(() => ({}));
            throw new Error(errData.message || 'Failed to create manual attendance entry.');
        }

        setButtonLoading(btnSubmit, false);
        closeManualEntryModal();
        showToast('Manual attendance entry created successfully with audit log.', 'success');
        loadAttendanceRecords(0);
    } catch (err) {
        setButtonLoading(btnSubmit, false);
        errorBanner.textContent = err.message;
        errorBanner.style.display = 'block';
    }
}

// ==========================================
// Monthly Reports & Summaries
// ==========================================
async function loadMonthlyReport() {
    if (!isAdminAuthenticated) {
        switchView('REPORTS');
        return;
    }

    const monthEl = document.getElementById('report-month-select');
    const yearEl = document.getElementById('report-year-select');
    const searchEl = document.getElementById('report-staff-search');

    const tableBody = document.getElementById('report-table-body');
    const emptyState = document.getElementById('report-empty-state');

    const now = new Date();
    const month = monthEl?.value || (now.getMonth() + 1);
    const year = yearEl?.value || now.getFullYear();
    const search = searchEl?.value.trim() || '';

    if (tableBody) {
        tableBody.innerHTML = '<tr><td colspan="9" class="text-center p-4"><div class="spinner-small" style="display:inline-block"></div> Aggregating monthly records from PostgreSQL...</td></tr>';
    }
    if (emptyState) emptyState.style.display = 'none';

    const params = new URLSearchParams({
        month: month,
        year: year
    });
    if (search) params.append('search', search);

    try {
        const response = await fetch(`${API_ADMIN_REPORTS}/monthly?${params.toString()}`, {
            credentials: 'include'
        });

        if (response.status === 401) {
            setUnauthenticatedAdmin();
            switchView('REPORTS');
            return;
        }

        if (!response.ok) {
            const errData = await response.json().catch(() => ({}));
            throw new Error(errData.message || 'Failed to aggregate monthly report.');
        }

        const data = await response.json();
        currentMonthlyReportData = data;

        // Populate KPI Cards
        const kpiStaff = document.getElementById('kpi-total-staff');
        const kpiSessions = document.getElementById('kpi-total-sessions');
        const kpiCompleted = document.getElementById('kpi-completed-sessions');
        const kpiIncomplete = document.getElementById('kpi-incomplete-sessions');
        const kpiDuration = document.getElementById('kpi-total-duration');

        if (kpiStaff) kpiStaff.textContent = data.totalEmployees || 0;
        if (kpiSessions) kpiSessions.textContent = data.totalRecordedSessions || 0;
        if (kpiCompleted) kpiCompleted.textContent = data.totalCompletedSessions || 0;
        if (kpiIncomplete) kpiIncomplete.textContent = data.totalIncompleteSessions || 0;
        if (kpiDuration) kpiDuration.textContent = data.overallDurationFormatted || '0h 00m';

        const summaries = data.employeeSummaries || [];
        if (summaries.length === 0) {
            tableBody.innerHTML = '';
            if (emptyState) emptyState.style.display = 'block';
            return;
        }

        if (emptyState) emptyState.style.display = 'none';

        tableBody.innerHTML = summaries.map(s => {
            const statusBadge = s.employeeStatus === 'ACTIVE'
                ? '<span class="badge badge-active">Active</span>'
                : '<span class="badge badge-inactive">Inactive</span>';

            const incompleteBadge = s.incompleteSessions > 0
                ? `<span class="badge-status-incomplete">${s.incompleteSessions} open</span>`
                : '<span class="text-muted text-sm">—</span>';

            return `
                <tr>
                    <td><code>${escapeHtml(s.employeeCode)}</code></td>
                    <td><strong>${escapeHtml(s.employeeName)}</strong></td>
                    <td>${escapeHtml(s.department)}</td>
                    <td>${statusBadge}</td>
                    <td><strong>${s.totalSessions}</strong></td>
                    <td><span class="text-success font-weight-bold">${s.completedSessions}</span></td>
                    <td>${incompleteBadge}</td>
                    <td><span class="badge-duration">${escapeHtml(s.totalDurationFormatted)}</span></td>
                    <td class="text-right">
                        <button class="btn-report-breakdown" onclick="viewEmployeeMonthlyDetail('${s.employeeId}', '${escapeHtml(s.employeeName)}')">
                            <span>Inspect Days</span> &rarr;
                        </button>
                    </td>
                </tr>
            `;
        }).join('');

    } catch (err) {
        if (tableBody) {
            tableBody.innerHTML = `<tr><td colspan="9" class="text-center p-4 text-danger">Failed to load monthly report: ${escapeHtml(err.message)}</td></tr>`;
        }
    }
}

async function exportMonthlyReportCsv() {
    const month = document.getElementById('report-month-select')?.value || (new Date().getMonth() + 1);
    const year = document.getElementById('report-year-select')?.value || new Date().getFullYear();
    const search = document.getElementById('report-staff-search')?.value.trim() || '';

    const params = new URLSearchParams({
        month: month,
        year: year
    });
    if (search) params.append('search', search);

    try {
        showToast('Generating monthly summary CSV...', 'info');
        const response = await fetch(`${API_ADMIN_REPORTS}/monthly/export?${params.toString()}`, {
            credentials: 'include'
        });
        if (!response.ok) {
            throw new Error('Failed to generate monthly summary CSV.');
        }
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.style.display = 'none';
        a.href = url;
        a.download = `attendance-monthly-summary-${year}-${String(month).padStart(2, '0')}.csv`;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        a.remove();
        showToast('Monthly summary CSV downloaded.', 'success');
    } catch (err) {
        showToast('Export failed: ' + err.message, 'danger');
    }
}

function resetReportFilters() {
    const now = new Date();
    const monthEl = document.getElementById('report-month-select');
    const yearEl = document.getElementById('report-year-select');
    const searchEl = document.getElementById('report-staff-search');
    if (monthEl) monthEl.value = (now.getMonth() + 1).toString();
    if (yearEl) yearEl.value = now.getFullYear().toString();
    if (searchEl) searchEl.value = '';
    loadMonthlyReport();
}

function viewEmployeeMonthlyDetail(employeeId, employeeName) {
    if (!currentMonthlyReportData || !currentMonthlyReportData.employeeSummaries) return;

    const empSummary = currentMonthlyReportData.employeeSummaries.find(e => e.employeeId === employeeId);
    if (!empSummary) return;

    const modal = document.getElementById('report-detail-modal');
    const title = document.getElementById('report-detail-title');
    const subtitle = document.getElementById('report-detail-subtitle');
    const footer = document.getElementById('report-detail-footer-summary');
    const tbody = document.getElementById('report-detail-table-body');

    const monthNames = ['', 'January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
    const monthName = monthNames[currentMonthlyReportData.month] || currentMonthlyReportData.month;

    title.textContent = `${empSummary.employeeName} (${empSummary.employeeCode}) — ${monthName} ${currentMonthlyReportData.year}`;
    subtitle.textContent = `Department: ${empSummary.department} • Total Sessions: ${empSummary.totalSessions} • Completed: ${empSummary.completedSessions} • Incomplete: ${empSummary.incompleteSessions}`;
    footer.textContent = `Total Monthly Duration: ${empSummary.totalDurationFormatted}`;

    const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

    tbody.innerHTML = empSummary.dailyRecords.map(d => {
        const dateObj = new Date(d.workDate + 'T00:00:00');
        const dayOfWeek = days[dateObj.getDay()] || '';

        if (!d.hasRecord) {
            return `
                <tr class="text-muted" style="opacity: 0.75;">
                    <td>${d.workDate}</td>
                    <td>${dayOfWeek}</td>
                    <td>—</td>
                    <td>—</td>
                    <td>—</td>
                    <td><span class="badge-day-no-record">No Record</span></td>
                    <td>—</td>
                    <td><span class="text-muted text-sm">No attendance record</span></td>
                </tr>
            `;
        }

        let statusBadge = '';
        if (d.status === 'CHECKED_IN') {
            statusBadge = '<span class="badge-status-in">Checked In</span> <span class="badge-status-incomplete">Incomplete</span>';
        } else if (d.status === 'CHECKED_OUT') {
            statusBadge = '<span class="badge-status-out">Completed</span>';
        } else {
            statusBadge = '<span class="badge-status-auto">Auto Closed</span>';
        }

        const checkIn = formatISTTime(d.checkInTime);
        const checkOut = d.checkOutTime ? formatISTTime(d.checkOutTime) : '<span class="text-muted">Missing</span>';
        const duration = d.durationFormatted ? `<span class="badge-duration">${escapeHtml(d.durationFormatted)}</span>` : '—';

        let photoButtons = [];
        if (d.hasCheckInPhoto) {
            photoButtons.push(`<button class="btn-photo-view" onclick="viewAttendancePhoto('${d.attendanceId}', 'CHECK_IN', '${escapeHtml(empSummary.employeeName)}', '${d.workDate}')">📷 In</button>`);
        }
        if (d.hasCheckOutPhoto) {
            photoButtons.push(`<button class="btn-photo-view" onclick="viewAttendancePhoto('${d.attendanceId}', 'CHECK_OUT', '${escapeHtml(empSummary.employeeName)}', '${d.workDate}')">📷 Out</button>`);
        }
        const photoHtml = photoButtons.length > 0 ? photoButtons.join(' ') : '—';

        return `
            <tr>
                <td><strong>${d.workDate}</strong></td>
                <td><strong>${dayOfWeek}</strong></td>
                <td>${checkIn}</td>
                <td>${checkOut}</td>
                <td>${duration}</td>
                <td>${statusBadge}</td>
                <td>${photoHtml}</td>
                <td><span class="text-sm">${escapeHtml(d.note)}</span></td>
            </tr>
        `;
    }).join('');

    modal.style.display = 'flex';
}

function closeReportDetailModal() {
    const modal = document.getElementById('report-detail-modal');
    if (modal) modal.style.display = 'none';
}

function viewEmployeeAttendanceHistory(code) {
    switchView('ATTENDANCE');
    const searchInput = document.getElementById('att-search-input');
    if (searchInput) {
        searchInput.value = code;
    }
    loadAttendanceRecords(0);
}

function viewAttendancePhoto(attendanceId, photoType, employeeName, workDate) {
    const modal = document.getElementById('photo-modal');
    const title = document.getElementById('photo-modal-title');
    const loading = document.getElementById('photo-modal-loading');
    const img = document.getElementById('photo-modal-img');
    const details = document.getElementById('photo-modal-details');

    if (!modal) return;

    const actionLabel = photoType === 'CHECK_IN' ? 'Check-In' : 'Check-Out';
    title.textContent = `Attendance Photo Evidence — ${actionLabel}`;
    details.innerHTML = `<strong>Staff:</strong> ${escapeHtml(employeeName)} &bull; <strong>Work Date:</strong> ${workDate} &bull; <strong>Type:</strong> ${actionLabel}`;

    loading.style.display = 'block';
    loading.innerHTML = '<div class="spinner-small" style="display:inline-block"></div><div class="mt-2 text-muted">Loading photo securely...</div>';
    img.style.display = 'none';
    modal.style.display = 'flex';

    const photoUrl = `/api/admin/attendance/${attendanceId}/photo/${photoType}?t=${Date.now()}`;
    img.onload = () => {
        loading.style.display = 'none';
        img.style.display = 'inline-block';
    };
    img.onerror = () => {
        loading.innerHTML = '<div class="text-danger p-3">Photo not found or inaccessible.</div>';
    };
    img.src = photoUrl;
}

function closePhotoModal() {
    const modal = document.getElementById('photo-modal');
    const img = document.getElementById('photo-modal-img');
    if (img) img.src = '';
    if (modal) modal.style.display = 'none';
}


function applyAttendanceFilters() {
    loadAttendanceRecords(0);
}

function resetAttendanceFilters() {
    const s = document.getElementById('att-search-input');
    const f = document.getElementById('att-from-date');
    const t = document.getElementById('att-to-date');
    const st = document.getElementById('att-status-filter');
    if (s) s.value = '';
    if (f) f.value = '';
    if (t) t.value = '';
    if (st) st.value = '';
    loadAttendanceRecords(0);
}

function changeAttendancePage(delta) {
    const target = currentAttendancePage + delta;
    if (target >= 0 && target < totalAttendancePages) {
        loadAttendanceRecords(target);
    }
}

function formatISTTime(isoString) {
    if (!isoString) return '—';
    try {
        const d = new Date(isoString);
        return d.toLocaleTimeString('en-IN', {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: true
        });
    } catch {
        return isoString;
    }
}

// ==========================================
// Phase 9: Attendance Corrections, Manual Entries & Audit
// ==========================================
let currentEditingRecord = null;

async function openCorrectionModal(recordId) {
    const modal = document.getElementById('correction-modal');
    const errBanner = document.getElementById('correction-error-banner');
    if (errBanner) errBanner.style.display = 'none';

    try {
        const response = await fetch(`${API_ADMIN_ATTENDANCE}/${recordId}`, {
            credentials: 'include'
        });
        if (!response.ok) {
            throw new Error('Failed to load authoritative attendance record.');
        }
        const record = await response.json();
        currentEditingRecord = record;

        document.getElementById('corr-record-id').value = record.id;
        document.getElementById('corr-record-version').value = record.version != null ? record.version : 0;
        document.getElementById('corr-emp-name').textContent = record.employeeName;
        document.getElementById('corr-emp-code').textContent = record.employeeCode;
        document.getElementById('corr-work-date').textContent = record.workDate;
        document.getElementById('corr-version-badge').textContent = `v${record.version != null ? record.version : 0}`;

        const statusBadge = document.getElementById('corr-current-status-badge');
        if (statusBadge) {
            statusBadge.className = `badge ${record.status === 'CHECKED_OUT' ? 'badge-status-out' : (record.status === 'CHECKED_IN' ? 'badge-status-in' : (record.status === 'VOIDED' ? 'badge-status-voided' : 'badge-status-auto'))}`;
            statusBadge.textContent = record.status;
        }

        const inTimeInput = document.getElementById('corr-in-time');
        const outTimeInput = document.getElementById('corr-out-time');
        const inHint = document.getElementById('corr-in-hint');
        const outHint = document.getElementById('corr-out-hint');

        const inFormatted = formatISTTime(record.checkInTime);
        const outFormatted = record.checkOutTime ? formatISTTime(record.checkOutTime) : 'None';

        inHint.textContent = `Original / Current: ${inFormatted}`;
        outHint.textContent = `Original / Current: ${outFormatted}`;

        inTimeInput.value = isoToTimeString(record.checkInTime);
        outTimeInput.value = record.checkOutTime ? isoToTimeString(record.checkOutTime) : '';

        // Select default action
        const actionSelect = document.getElementById('corr-action-type');
        if (record.status === 'CHECKED_IN' || record.status === 'AUTO_CLOSED' || !record.checkOutTime) {
            actionSelect.value = 'RESOLVE_MISSED_CHECKOUT';
        } else if (record.status === 'VOIDED') {
            actionSelect.value = 'CORRECT_BOTH';
        } else {
            actionSelect.value = 'CORRECT_BOTH';
        }

        document.getElementById('corr-reason').value = '';
        document.getElementById('corr-reason-counter').textContent = '0 / 500 chars';

        onCorrectionActionChanged();
        updateCorrectionPreview();

        modal.style.display = 'flex';
    } catch (err) {
        showToast('Error: ' + err.message, 'danger');
    }
}

function closeCorrectionModal() {
    const modal = document.getElementById('correction-modal');
    if (modal) modal.style.display = 'none';
    currentEditingRecord = null;
}

function onCorrectionActionChanged() {
    const action = document.getElementById('corr-action-type')?.value;
    const inInput = document.getElementById('corr-in-time');
    const outInput = document.getElementById('corr-out-time');

    if (!action) return;

    if (action === 'RESOLVE_MISSED_CHECKOUT') {
        if (inInput) inInput.disabled = true;
        if (outInput) {
            outInput.disabled = false;
            if (!outInput.value) {
                const now = new Date();
                outInput.value = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`;
            }
        }
    } else if (action === 'CORRECT_CHECK_IN') {
        if (inInput) inInput.disabled = false;
        if (outInput) outInput.disabled = true;
    } else if (action === 'CORRECT_CHECK_OUT') {
        if (inInput) inInput.disabled = true;
        if (outInput) outInput.disabled = false;
    } else if (action === 'VOID_RECORD') {
        if (inInput) inInput.disabled = true;
        if (outInput) outInput.disabled = true;
    } else { // CORRECT_BOTH
        if (inInput) inInput.disabled = false;
        if (outInput) outInput.disabled = false;
    }

    updateCorrectionPreview();
}

function onCorrectionReasonInput() {
    const reason = document.getElementById('corr-reason')?.value || '';
    const counter = document.getElementById('corr-reason-counter');
    if (counter) counter.textContent = `${reason.length} / 500 chars`;
    updateCorrectionPreview();
}

function updateCorrectionPreview() {
    const diffContainer = document.getElementById('corr-diff-content');
    if (!diffContainer || !currentEditingRecord) return;

    const action = document.getElementById('corr-action-type')?.value;
    const inVal = document.getElementById('corr-in-time')?.value;
    const outVal = document.getElementById('corr-out-time')?.value;
    const reason = document.getElementById('corr-reason')?.value?.trim() || '';

    const origIn = formatISTTime(currentEditingRecord.checkInTime);
    const origOut = currentEditingRecord.checkOutTime ? formatISTTime(currentEditingRecord.checkOutTime) : 'Missing / Open';
    const origStatus = currentEditingRecord.status;

    let targetInStr = origIn;
    let targetOutStr = origOut;
    let targetStatusStr = origStatus;

    if (action === 'VOID_RECORD') {
        targetStatusStr = 'VOIDED';
    } else if (action === 'RESOLVE_MISSED_CHECKOUT') {
        targetOutStr = outVal || 'Missing';
        targetStatusStr = 'CHECKED_OUT';
    } else if (action === 'CORRECT_CHECK_IN') {
        targetInStr = inVal || origIn;
    } else if (action === 'CORRECT_CHECK_OUT') {
        targetOutStr = outVal || 'None';
        targetStatusStr = outVal ? 'CHECKED_OUT' : origStatus;
    } else { // CORRECT_BOTH
        targetInStr = inVal || origIn;
        targetOutStr = outVal || (currentEditingRecord.checkOutTime ? origOut : 'None');
        targetStatusStr = outVal ? 'CHECKED_OUT' : 'CHECKED_IN';
    }

    diffContainer.innerHTML = `
        <table class="diff-table">
            <thead>
                <tr>
                    <th>Field</th>
                    <th>Before (Current)</th>
                    <th>After (Proposed)</th>
                </tr>
            </thead>
            <tbody>
                <tr>
                    <td><strong>Check-In</strong></td>
                    <td><span class="diff-val-old">${escapeHtml(origIn)}</span></td>
                    <td><span class="diff-val-new">${escapeHtml(targetInStr)}</span></td>
                </tr>
                <tr>
                    <td><strong>Check-Out</strong></td>
                    <td><span class="diff-val-old">${escapeHtml(origOut)}</span></td>
                    <td><span class="diff-val-new">${escapeHtml(targetOutStr)}</span></td>
                </tr>
                <tr>
                    <td><strong>Status</strong></td>
                    <td><span class="diff-val-old">${escapeHtml(origStatus)}</span></td>
                    <td><span class="diff-val-new">${escapeHtml(targetStatusStr)}</span></td>
                </tr>
            </tbody>
        </table>
        <div class="mt-2 text-sm text-muted"><strong>Reason:</strong> ${reason ? escapeHtml(reason) : '<span class="text-danger">Required — please provide justification</span>'}</div>
    `;
}

function isoToTimeString(isoString) {
    if (!isoString) return '';
    try {
        const d = new Date(isoString);
        const hours = String(d.getHours()).padStart(2, '0');
        const minutes = String(d.getMinutes()).padStart(2, '0');
        const seconds = String(d.getSeconds()).padStart(2, '0');
        return `${hours}:${minutes}:${seconds}`;
    } catch {
        return '';
    }
}

function timeStringToUtcIso(workDateStr, timeStr) {
    if (!timeStr) return null;
    const parts = timeStr.split(':');
    const hours = parseInt(parts[0] || '0', 10);
    const minutes = parseInt(parts[1] || '0', 10);
    const seconds = parseInt(parts[2] || '0', 10);

    // Interpret work date and local time in Asia/Kolkata (+05:30)
    // Convert to UTC epoch
    const [year, month, day] = workDateStr.split('-').map(n => parseInt(n, 10));
    // Offset in milliseconds for IST is +5h 30m = 19800000 ms
    const utcEpoch = Date.UTC(year, month - 1, day, hours, minutes, seconds) - (5.5 * 3600 * 1000);
    return new Date(utcEpoch).toISOString();
}

async function submitCorrection(event) {
    event.preventDefault();
    if (!currentEditingRecord) return;

    const recordId = document.getElementById('corr-record-id')?.value;
    const recordVersion = parseInt(document.getElementById('corr-record-version')?.value || '0', 10);
    const actionType = document.getElementById('corr-action-type')?.value;
    const inTimeStr = document.getElementById('corr-in-time')?.value;
    const outTimeStr = document.getElementById('corr-out-time')?.value;
    const reason = document.getElementById('corr-reason')?.value?.trim();

    const errBanner = document.getElementById('correction-error-banner');
    const submitBtn = document.getElementById('btn-submit-correction');

    if (!reason) {
        if (errBanner) {
            errBanner.textContent = 'Correction reason is mandatory.';
            errBanner.style.display = 'block';
        }
        return;
    }

    const payload = {
        actionType: actionType,
        reason: reason,
        recordVersion: recordVersion
    };

    if (actionType === 'VOID_RECORD') {
        payload.status = 'VOIDED';
    } else if (actionType === 'RESOLVE_MISSED_CHECKOUT') {
        if (!outTimeStr) {
            if (errBanner) {
                errBanner.textContent = 'Check-out time is required to resolve a missed checkout.';
                errBanner.style.display = 'block';
            }
            return;
        }
        payload.checkOutTime = timeStringToUtcIso(currentEditingRecord.workDate, outTimeStr);
        payload.status = 'CHECKED_OUT';
    } else if (actionType === 'CORRECT_CHECK_IN') {
        payload.checkInTime = timeStringToUtcIso(currentEditingRecord.workDate, inTimeStr);
    } else if (actionType === 'CORRECT_CHECK_OUT') {
        payload.checkOutTime = timeStringToUtcIso(currentEditingRecord.workDate, outTimeStr);
        if (outTimeStr) payload.status = 'CHECKED_OUT';
    } else { // CORRECT_BOTH
        payload.checkInTime = timeStringToUtcIso(currentEditingRecord.workDate, inTimeStr);
        payload.checkOutTime = timeStringToUtcIso(currentEditingRecord.workDate, outTimeStr);
        if (outTimeStr) payload.status = 'CHECKED_OUT';
    }

    setButtonLoading(submitBtn, true);
    if (errBanner) errBanner.style.display = 'none';

    try {
        const response = await fetch(`${API_ADMIN_ATTENDANCE}/${recordId}/corrections`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify(payload)
        });

        if (response.status === 409) {
            throw new Error('Concurrency Conflict: This record was modified by another administrator. Please reload and try again.');
        }

        if (!response.ok) {
            const err = await response.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to apply correction.');
        }

        showToast('Attendance record corrected and audited successfully.', 'success');
        closeCorrectionModal();
        loadAttendanceRecords(currentAttendancePage);
        if (currentView === 'REPORTS') {
            loadMonthlyReport();
        }
    } catch (err) {
        if (errBanner) {
            errBanner.textContent = err.message;
            errBanner.style.display = 'block';
        }
    } finally {
        setButtonLoading(submitBtn, false);
    }
}

async function openManualAttendanceModal() {
    const modal = document.getElementById('manual-entry-modal');
    const select = document.getElementById('manual-emp-select');
    const workDateInput = document.getElementById('manual-work-date');
    const errBanner = document.getElementById('manual-error-banner');

    if (errBanner) errBanner.style.display = 'none';

    // Populate employee options if needed
    if (select) {
        if (allEmployees.length === 0) {
            try {
                const res = await fetch(`${API_ADMIN_EMPLOYEES}?status=ALL`, { credentials: 'include' });
                if (res.ok) allEmployees = await res.json();
            } catch (e) {
                console.error(e);
            }
        }
        select.innerHTML = '<option value="">-- Select Employee --</option>' +
            allEmployees.map(e => `<option value="${e.id}">${escapeHtml(e.fullName)} (${escapeHtml(e.employeeId)}) - ${escapeHtml(e.department)}</option>`).join('');
    }

    if (workDateInput) {
        workDateInput.value = new Date().toISOString().split('T')[0];
    }

    document.getElementById('manual-in-time').value = '09:00:00';
    document.getElementById('manual-out-time').value = '17:00:00';
    document.getElementById('manual-reason').value = '';
    document.getElementById('manual-reason-counter').textContent = '0 / 500 chars';

    modal.style.display = 'flex';
}

function closeManualEntryModal() {
    const modal = document.getElementById('manual-entry-modal');
    if (modal) modal.style.display = 'none';
}

function onManualReasonInput() {
    const reason = document.getElementById('manual-reason')?.value || '';
    const counter = document.getElementById('manual-reason-counter');
    if (counter) counter.textContent = `${reason.length} / 500 chars`;
}

async function submitManualEntry(event) {
    event.preventDefault();
    const empId = document.getElementById('manual-emp-select')?.value;
    const workDate = document.getElementById('manual-work-date')?.value;
    const inTime = document.getElementById('manual-in-time')?.value;
    const outTime = document.getElementById('manual-out-time')?.value;
    const reason = document.getElementById('manual-reason')?.value?.trim();

    const errBanner = document.getElementById('manual-error-banner');
    const submitBtn = document.getElementById('btn-submit-manual');

    if (!empId || !workDate || !inTime || !reason) {
        if (errBanner) {
            errBanner.textContent = 'Please fill all required fields and provide a reason.';
            errBanner.style.display = 'block';
        }
        return;
    }

    const payload = {
        employeeId: empId,
        workDate: workDate,
        checkInTime: timeStringToUtcIso(workDate, inTime),
        checkOutTime: outTime ? timeStringToUtcIso(workDate, outTime) : null,
        reason: reason
    };

    setButtonLoading(submitBtn, true);
    if (errBanner) errBanner.style.display = 'none';

    try {
        const response = await fetch(`${API_ADMIN_ATTENDANCE}/manual`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            const err = await response.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to create manual attendance entry.');
        }

        showToast('Manual attendance entry created successfully.', 'success');
        closeManualEntryModal();
        loadAttendanceRecords(0);
        if (currentView === 'REPORTS') {
            loadMonthlyReport();
        }
    } catch (err) {
        if (errBanner) {
            errBanner.textContent = err.message;
            errBanner.style.display = 'block';
        }
    } finally {
        setButtonLoading(submitBtn, false);
    }
}

async function openAuditHistoryModal(recordId, employeeName, employeeCode, workDate) {
    const modal = document.getElementById('audit-history-modal');
    const nameEl = document.getElementById('audit-header-emp-name');
    const codeEl = document.getElementById('audit-header-emp-code');
    const dateEl = document.getElementById('audit-header-work-date');
    const loading = document.getElementById('audit-history-loading');
    const emptyState = document.getElementById('audit-history-empty');
    const timeline = document.getElementById('audit-timeline-container');

    if (nameEl) nameEl.textContent = employeeName || 'Employee';
    if (codeEl) codeEl.textContent = employeeCode ? `(${employeeCode})` : '';
    if (dateEl) dateEl.textContent = `Work Date: ${workDate || '--'}`;

    timeline.innerHTML = '';
    loading.style.display = 'block';
    emptyState.style.display = 'none';
    modal.style.display = 'flex';

    try {
        const response = await fetch(`${API_ADMIN_ATTENDANCE}/${recordId}/audit-history`, {
            credentials: 'include'
        });

        if (!response.ok) {
            throw new Error('Failed to load audit history.');
        }

        const data = await response.json();
        const logs = data.content || [];

        loading.style.display = 'none';

        if (logs.length === 0) {
            emptyState.style.display = 'block';
            return;
        }

        timeline.innerHTML = logs.map(a => {
            const formattedTime = formatISTTime(a.createdAt);
            const dateStr = a.createdAt ? new Date(a.createdAt).toLocaleDateString('en-IN', {
                day: '2-digit', month: 'short', year: 'numeric'
            }) : '';

            const prevIn = a.previousCheckInTime ? formatISTTime(a.previousCheckInTime) : 'None';
            const newIn = a.correctedCheckInTime ? formatISTTime(a.correctedCheckInTime) : 'None';
            const prevOut = a.previousCheckOutTime ? formatISTTime(a.previousCheckOutTime) : 'None';
            const newOut = a.correctedCheckOutTime ? formatISTTime(a.correctedCheckOutTime) : 'None';
            const prevStatus = a.previousStatus || 'None';
            const newStatus = a.correctedStatus;

            return `
                <div class="audit-card">
                    <div class="audit-card-header">
                        <span class="audit-action-badge audit-action-${a.actionType}">${escapeHtml(a.actionType.replace(/_/g, ' '))}</span>
                        <div class="audit-meta">
                            <strong>${escapeHtml(a.performedBy)}</strong> &bull; ${dateStr} at ${formattedTime} (IST)
                        </div>
                    </div>
                    <div class="audit-reason-box">
                        <strong>Reason:</strong> &ldquo;${escapeHtml(a.reason)}&rdquo;
                    </div>
                    <table class="diff-table">
                        <thead>
                            <tr>
                                <th>Field</th>
                                <th>Previous Snapshot</th>
                                <th>Authoritative Value</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td>Check-In</td>
                                <td><span class="diff-val-old">${escapeHtml(prevIn)}</span></td>
                                <td><span class="diff-val-new">${escapeHtml(newIn)}</span></td>
                            </tr>
                            <tr>
                                <td>Check-Out</td>
                                <td><span class="diff-val-old">${escapeHtml(prevOut)}</span></td>
                                <td><span class="diff-val-new">${escapeHtml(newOut)}</span></td>
                            </tr>
                            <tr>
                                <td>Status</td>
                                <td><span class="diff-val-old">${escapeHtml(prevStatus)}</span></td>
                                <td><span class="diff-val-new">${escapeHtml(newStatus)}</span></td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            `;
        }).join('');

    } catch (err) {
        loading.style.display = 'none';
        timeline.innerHTML = `<div class="alert alert-danger">${escapeHtml(err.message)}</div>`;
    }
}

function closeAuditHistoryModal() {
    const modal = document.getElementById('audit-history-modal');
    if (modal) modal.style.display = 'none';
}

// ==========================================
// UI Helpers & Utilities
// ==========================================
function showLoading() {
    hideStates();
    loadingSpinner.style.display = 'block';
}

function showError(msg) {
    hideStates();
    errorMessage.textContent = msg;
    errorBox.style.display = 'block';
}

function hideStates() {
    loadingSpinner.style.display = 'none';
    errorBox.style.display = 'none';
    emptyState.style.display = 'none';
}

function setButtonLoading(button, isLoading) {
    const textSpan = button.querySelector('.btn-text');
    const spinner = button.querySelector('.spinner-small');

    button.disabled = isLoading;
    if (textSpan) textSpan.style.display = isLoading ? 'none' : 'inline';
    if (spinner) spinner.style.display = isLoading ? 'inline-block' : 'none';
}

function clearFormErrors(formId) {
    const form = document.getElementById(formId);
    if (!form) return;
    form.querySelectorAll('.field-error').forEach(el => el.textContent = '');
}

function displayValidationErrors(errors) {
    for (const [field, msg] of Object.entries(errors)) {
        const errorEl = document.getElementById(`err-${field}`);
        if (errorEl) {
            errorEl.textContent = msg;
        }
    }
}

function showPlaceholderNotice(featureName) {
    showToast(`${featureName} is planned for upcoming implementation phases.`, 'success');
}

function showToast(message, type = 'success') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;

    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(-10px)';
        toast.style.transition = 'all 0.3s ease';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

function formatDateTime(isoString) {
    if (!isoString) return '—';
    try {
        const date = new Date(isoString);
        return date.toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
    } catch {
        return isoString;
    }
}

function escapeHtml(text) {
    if (!text) return '';
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// ==========================================
// Phase 10: Storage Management & Photo Retention
// ==========================================

function formatBytes(bytes) {
    if (bytes === undefined || bytes === null || isNaN(bytes) || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return (bytes / Math.pow(k, i)).toFixed(1) + ' ' + sizes[i];
}

async function refreshStorageDashboard() {
    await Promise.all([
        loadStorageHealth(),
        loadRetentionPolicy(),
        loadCleanupHistory()
    ]);
}

async function loadStorageHealth() {
    try {
        const response = await fetch(`${API_ADMIN_STORAGE}/health`, { credentials: 'include' });
        if (!response.ok) {
            throw new Error(`Failed to load storage health: ${response.status}`);
        }
        const data = await response.json();

        // Warning Badge
        const badge = document.getElementById('storage-health-badge');
        if (badge) {
            badge.textContent = data.warningLevel || 'NORMAL';
            badge.className = 'badge';
            if (data.warningLevel === 'NORMAL') {
                badge.classList.add('badge-normal');
            } else if (data.warningLevel === 'WARNING') {
                badge.classList.add('badge-warning');
            } else if (data.warningLevel === 'CRITICAL') {
                badge.classList.add('badge-critical');
            }
        }

        // Space & Meter
        const freeSpaceEl = document.getElementById('storage-free-space');
        const meterBar = document.getElementById('storage-meter-bar');
        const totalSpaceEl = document.getElementById('storage-total-space');

        if (freeSpaceEl) freeSpaceEl.textContent = formatBytes(data.usableSpaceBytes) + ' Free';
        if (totalSpaceEl) {
            totalSpaceEl.textContent = `Usable: ${formatBytes(data.usableSpaceBytes)} / Total: ${formatBytes(data.totalSpaceBytes)}`;
        }

        if (meterBar && data.totalSpaceBytes > 0) {
            const usedBytes = Math.max(0, data.totalSpaceBytes - data.usableSpaceBytes);
            const pct = Math.min(100, Math.max(0, (usedBytes / data.totalSpaceBytes) * 100));
            meterBar.style.width = pct.toFixed(1) + '%';
            meterBar.className = 'storage-meter-bar';
            if (data.warningLevel === 'NORMAL') meterBar.classList.add('meter-normal');
            else if (data.warningLevel === 'WARNING') meterBar.classList.add('meter-warning');
            else meterBar.classList.add('meter-critical');
        }

        // Photo Metrics
        const activePhotosEl = document.getElementById('storage-active-photos');
        const photosSizeEl = document.getElementById('storage-photos-size');
        const purgedPhotosEl = document.getElementById('storage-purged-photos');
        const missingPhotosEl = document.getElementById('storage-missing-photos');
        const tempFilesEl = document.getElementById('storage-temp-files');
        const tempSizeEl = document.getElementById('storage-temp-size');

        if (activePhotosEl) activePhotosEl.textContent = data.managedPhotoCount !== undefined ? data.managedPhotoCount : 0;
        if (photosSizeEl) photosSizeEl.textContent = `Total Size: ${formatBytes(data.managedPhotoBytes || 0)}`;
        if (purgedPhotosEl) purgedPhotosEl.textContent = data.purgedPhotoCount !== undefined ? data.purgedPhotoCount : 0;
        if (missingPhotosEl) {
            missingPhotosEl.textContent = `Missing: ${data.missingPhotoCount || 0} • Failed: ${data.cleanupFailedCount || 0}`;
        }
        if (tempFilesEl) tempFilesEl.textContent = data.tempFileCount !== undefined ? data.tempFileCount : 0;
        if (tempSizeEl) tempSizeEl.textContent = `Size: ${formatBytes(data.tempFileBytes || 0)}`;

    } catch (err) {
        console.error('loadStorageHealth error:', err);
        showToast('Error loading storage health: ' + err.message, 'danger');
    }
}

async function loadRetentionPolicy() {
    try {
        const response = await fetch(`${API_ADMIN_STORAGE}/retention/policy`, { credentials: 'include' });
        if (!response.ok) {
            throw new Error(`Failed to load retention policy: ${response.status}`);
        }
        const policy = await response.json();

        const chk = document.getElementById('policy-enabled-checkbox');
        const days = document.getElementById('policy-retention-days');
        const batch = document.getElementById('policy-batch-size');
        const notes = document.getElementById('policy-notes');
        const pill = document.getElementById('policy-status-pill');
        const updatedInfo = document.getElementById('policy-updated-info');

        const isEnabled = policy.retentionEnabled !== undefined ? policy.retentionEnabled : policy.enabled;
        if (chk) chk.checked = !!isEnabled;
        if (days) days.value = policy.retentionDays || 90;
        if (batch) batch.value = policy.batchSize || 50;

        if (pill) {
            pill.textContent = isEnabled ? 'ACTIVE' : 'DISABLED';
            pill.className = 'badge badge-pill ' + (isEnabled ? 'badge-success' : 'badge-secondary');
        }

        if (updatedInfo) {
            const timeStr = policy.updatedAt ? new Date(policy.updatedAt).toLocaleString() : 'System Default';
            updatedInfo.textContent = `Last updated: ${timeStr} by ${policy.updatedBy || 'admin'}`;
        }
    } catch (err) {
        console.error('loadRetentionPolicy error:', err);
    }
}

async function handleSaveRetentionPolicy(event) {
    event.preventDefault();
    const btn = document.getElementById('btn-save-policy');
    const spinner = btn ? btn.querySelector('.spinner-small') : null;

    try {
        if (btn) btn.disabled = true;
        if (spinner) spinner.style.display = 'inline-block';

        const retentionEnabled = document.getElementById('policy-enabled-checkbox').checked;
        const retentionDays = parseInt(document.getElementById('policy-retention-days').value, 10);
        const batchSize = parseInt(document.getElementById('policy-batch-size').value, 10);
        const notes = document.getElementById('policy-notes').value.trim();

        const payload = { retentionEnabled, retentionDays, batchSize, reason: notes };

        const response = await fetch(`${API_ADMIN_STORAGE}/retention/policy`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            const err = await response.json();
            throw new Error(err.message || 'Failed to update retention policy');
        }

        showToast('Photo retention policy updated successfully', 'success');
        await loadRetentionPolicy();
        await loadStorageHealth();
    } catch (err) {
        console.error('handleSaveRetentionPolicy error:', err);
        showToast(err.message, 'danger');
    } finally {
        if (btn) btn.disabled = false;
        if (spinner) spinner.style.display = 'none';
    }
}

async function handleRunDryRun() {
    const btn = document.getElementById('btn-run-dryrun');
    try {
        if (btn) btn.disabled = true;

        const response = await fetch(`${API_ADMIN_STORAGE}/retention/dry-run`, { credentials: 'include' });
        if (!response.ok) {
            const err = await response.json();
            throw new Error(err.message || 'Failed to execute retention dry run');
        }

        const data = await response.json();
        const box = document.getElementById('dry-run-results-box');
        const cutoffEl = document.getElementById('dry-run-cutoff-time');
        const countEl = document.getElementById('dry-run-eligible-count');
        const bytesEl = document.getElementById('dry-run-eligible-bytes');
        const detailsEl = document.getElementById('dry-run-details-text');

        const eligibleCount = data.eligibleCount !== undefined ? data.eligibleCount : (data.eligibleForDeletionCount || 0);
        const eligibleBytes = data.eligibleBytes !== undefined ? data.eligibleBytes : (data.estimatedBytesEligible || 0);
        const cutoff = data.retentionCutoff || data.cutoffTime;

        if (cutoffEl) {
            cutoffEl.textContent = 'Cutoff: ' + (cutoff ? new Date(cutoff).toLocaleString() : '--');
        }
        if (countEl) countEl.textContent = eligibleCount;
        if (bytesEl) bytesEl.textContent = formatBytes(eligibleBytes);
        if (detailsEl) {
            detailsEl.textContent = `Evaluated: ${data.evaluatedCount || 0} • Excluded (active/recent): ${data.excludedCount || 0} • Abandoned Temp: ${data.abandonedTempCount || 0}`;
        }
        if (box) box.style.display = 'block';

        showToast(`Dry run simulation complete: ${eligibleCount} photos eligible for cleanup`, 'info');
    } catch (err) {
        console.error('handleRunDryRun error:', err);
        showToast(err.message, 'danger');
    } finally {
        if (btn) btn.disabled = false;
    }
}

async function handleScanOrphans() {
    const btn = document.getElementById('btn-scan-orphans');
    try {
        if (btn) btn.disabled = true;

        const response = await fetch(`${API_ADMIN_STORAGE}/orphans/scan`, { credentials: 'include' });
        if (!response.ok) {
            const err = await response.json();
            throw new Error(err.message || 'Failed to scan orphans');
        }

        const data = await response.json();
        const box = document.getElementById('orphan-results-box');
        const tempCleanedEl = document.getElementById('orphan-temp-cleaned');
        const unregEl = document.getElementById('orphan-unregistered-count');
        const missingEl = document.getElementById('orphan-missing-db-count');
        const notesEl = document.getElementById('orphan-scan-notes');

        const tempCount = data.abandonedTempFilesCount !== undefined ? data.abandonedTempFilesCount : 0;
        const unregCount = data.unregisteredDiskFilesCount !== undefined ? data.unregisteredDiskFilesCount : 0;
        const missingCount = data.missingDbFilesCount !== undefined ? data.missingDbFilesCount : 0;

        if (tempCleanedEl) tempCleanedEl.textContent = tempCount;
        if (unregEl) unregEl.textContent = unregCount;
        if (missingEl) missingEl.textContent = missingCount;
        if (notesEl) {
            const scanTime = data.scannedAt ? new Date(data.scannedAt).toLocaleTimeString() : 'now';
            notesEl.textContent = `Integrity inspection complete at ${scanTime}.`;
        }
        if (box) box.style.display = 'block';

        showToast(`Storage integrity scan complete. Abandoned temp files: ${tempCount}`, 'success');
        await loadStorageHealth();
    } catch (err) {
        console.error('handleScanOrphans error:', err);
        showToast(err.message, 'danger');
    } finally {
        if (btn) btn.disabled = false;
    }
}

async function loadCleanupHistory() {
    const tbody = document.getElementById('cleanup-history-table-body');
    if (!tbody) return;

    try {
        const response = await fetch(`${API_ADMIN_STORAGE}/cleanup/history?limit=15`, { credentials: 'include' });
        if (!response.ok) {
            throw new Error(`Failed to load cleanup history: ${response.status}`);
        }

        const pageData = await response.json();
        const historyList = Array.isArray(pageData) ? pageData : (pageData.content || []);

        if (!historyList || historyList.length === 0) {
            tbody.innerHTML = '<tr><td colspan="9" class="text-center text-muted p-4">No cleanup operations recorded yet.</td></tr>';
            return;
        }

        tbody.innerHTML = historyList.map(h => {
            const startedStr = h.startedAt ? new Date(h.startedAt).toLocaleString() : '—';
            let durationStr = '—';
            if (h.startedAt && h.completedAt) {
                const diffMs = new Date(h.completedAt) - new Date(h.startedAt);
                durationStr = diffMs > 0 ? `${diffMs}ms` : '<1ms';
            }

            const statusClass = (h.status === 'SUCCESS' || h.status === 'COMPLETED') ? 'badge-success' : (h.status === 'PARTIAL' ? 'badge-warning' : 'badge-danger');
            const evaluated = h.photosEvaluated !== undefined ? h.photosEvaluated : (h.itemsExamined || 0);
            const deleted = h.photosDeleted !== undefined ? h.photosDeleted : (h.photosPurged || 0);
            const tempPurged = h.tempFilesCleaned !== undefined ? h.tempFilesCleaned : (h.tempFilesPurged || 0);
            const summary = h.errorMessage ? `Error: ${h.errorMessage}` : `Evaluated: ${evaluated}, Deleted: ${deleted}, Temp: ${tempPurged}`;

            return `
                <tr>
                    <td class="text-sm">${startedStr}</td>
                    <td><span class="badge badge-info">${escapeHtml(h.runType)}</span></td>
                    <td><span class="badge ${statusClass}">${escapeHtml(h.status)}</span></td>
                    <td class="text-center">${evaluated}</td>
                    <td class="text-center font-bold text-success">${deleted}</td>
                    <td class="text-sm">${formatBytes(h.bytesReclaimed)}</td>
                    <td class="text-center">${tempPurged}</td>
                    <td class="text-sm text-muted">${durationStr}</td>
                    <td class="text-xs text-muted" title="${escapeHtml(summary)}">${escapeHtml(summary)}</td>
                </tr>
            `;
        }).join('');
    } catch (err) {
        console.error('loadCleanupHistory error:', err);
        tbody.innerHTML = `<tr><td colspan="9" class="text-center text-danger p-4">Error loading cleanup history: ${escapeHtml(err.message)}</td></tr>`;
    }
}

// Confirmation modal for manual cleanup
function openCleanupConfirmModal() {
    const modal = document.getElementById('storage-cleanup-confirm-modal');
    const input = document.getElementById('cleanup-confirm-input');
    const btn = document.getElementById('btn-submit-execute-cleanup');
    if (input) input.value = '';
    if (btn) btn.disabled = true;
    if (modal) modal.style.display = 'flex';
}

function closeCleanupConfirmModal() {
    const modal = document.getElementById('storage-cleanup-confirm-modal');
    if (modal) modal.style.display = 'none';
}

function onCleanupConfirmInput() {
    const input = document.getElementById('cleanup-confirm-input');
    const btn = document.getElementById('btn-submit-execute-cleanup');
    if (input && btn) {
        btn.disabled = input.value.trim().toUpperCase() !== 'CONFIRM';
    }
}

async function handleExecuteCleanup() {
    const btn = document.getElementById('btn-submit-execute-cleanup');
    const spinner = btn ? btn.querySelector('.spinner-small') : null;

    try {
        if (btn) btn.disabled = true;
        if (spinner) spinner.style.display = 'inline-block';

        const response = await fetch(`${API_ADMIN_STORAGE}/cleanup/execute`, {
            method: 'POST',
            credentials: 'include'
        });

        if (!response.ok) {
            const err = await response.json();
            throw new Error(err.message || 'Cleanup execution failed');
        }

        const result = await response.json();
        closeCleanupConfirmModal();
        showToast(`Cleanup completed: ${result.photosPurged} photos purged, ${formatBytes(result.bytesReclaimed)} reclaimed.`, 'success');

        // Hide dry run box since cleanup has run
        const dryRunBox = document.getElementById('dry-run-results-box');
        if (dryRunBox) dryRunBox.style.display = 'none';

        // Refresh dashboard
        await refreshStorageDashboard();
    } catch (err) {
        console.error('handleExecuteCleanup error:', err);
        showToast(err.message, 'danger');
    } finally {
        if (btn) btn.disabled = false;
        if (spinner) spinner.style.display = 'none';
    }
}

// ========================================================
// Phase 11: Kiosk Registration & Device Management
// ========================================================

function getKioskDeviceToken() {
    return localStorage.getItem(KIOSK_DEVICE_TOKEN_KEY) || '';
}

function getKioskDeviceInfo() {
    const raw = localStorage.getItem(KIOSK_DEVICE_INFO_KEY);
    if (!raw) return null;
    try { return JSON.parse(raw); } catch { return null; }
}

function saveKioskDeviceCredentials(token, info) {
    localStorage.setItem(KIOSK_DEVICE_TOKEN_KEY, token);
    localStorage.setItem(KIOSK_DEVICE_INFO_KEY, JSON.stringify(info));
}

function clearKioskDeviceCredentials() {
    localStorage.removeItem(KIOSK_DEVICE_TOKEN_KEY);
    localStorage.removeItem(KIOSK_DEVICE_INFO_KEY);
}

function handleKioskDeviceUnauthorized(message) {
    clearKioskDeviceCredentials();
    const unpairedState = document.getElementById('kiosk-unpaired-state');
    const neutralState = document.getElementById('kiosk-neutral-state');
    const authState = document.getElementById('kiosk-authenticated-state');
    const badge = document.getElementById('kiosk-workstation-badge');
    const errorEl = document.getElementById('kiosk-pairing-error');

    if (neutralState) neutralState.style.display = 'none';
    if (authState) authState.style.display = 'none';
    if (badge) badge.style.display = 'none';
    if (unpairedState) unpairedState.style.display = 'block';
    if (errorEl && message) {
        errorEl.textContent = message;
        errorEl.style.display = 'block';
    }
}

async function checkKioskDeviceAuthorization() {
    const unpairedState = document.getElementById('kiosk-unpaired-state');
    const neutralState = document.getElementById('kiosk-neutral-state');
    const authState = document.getElementById('kiosk-authenticated-state');
    const badge = document.getElementById('kiosk-workstation-badge');
    const nameEl = document.getElementById('kiosk-workstation-name');
    const errorEl = document.getElementById('kiosk-pairing-error');

    if (errorEl) errorEl.style.display = 'none';
    const token = getKioskDeviceToken();

    if (!token) {
        if (neutralState) neutralState.style.display = 'none';
        if (authState) authState.style.display = 'none';
        if (badge) badge.style.display = 'none';
        if (unpairedState) unpairedState.style.display = 'block';
        const pairInput = document.getElementById('kiosk-pairing-code-input');
        if (pairInput) pairInput.focus();
        return;
    }

    try {
        const response = await fetch(`${API_KIOSK_DEVICES}/status`, {
            headers: { 'X-Kiosk-Device-Token': token }
        });

        if (response.ok) {
            const data = await response.json();
            if (data.registered && data.status === 'ACTIVE') {
                if (unpairedState) unpairedState.style.display = 'none';
                if (neutralState) neutralState.style.display = 'block';
                if (badge) badge.style.display = 'inline-flex';
                if (nameEl) nameEl.textContent = data.deviceName || 'Workstation';
                const input = document.getElementById('kiosk-employee-id');
                if (input) input.focus();
                return;
            }
        }

        // If 401 Unauthorized or revoked
        handleKioskDeviceUnauthorized('Workstation registration has expired or was revoked by an administrator.');
    } catch (err) {
        console.warn('Device status check offline/fallback:', err);
        // Fallback: if network fails temporarily, show neutral state
        if (unpairedState) unpairedState.style.display = 'none';
        if (neutralState) neutralState.style.display = 'block';
    }
}

async function handleKioskPairingSubmit(e) {
    if (e) e.preventDefault();
    const input = document.getElementById('kiosk-pairing-code-input');
    const errorEl = document.getElementById('kiosk-pairing-error');
    const btn = document.getElementById('btn-submit-kiosk-pairing');
    const spinner = btn ? btn.querySelector('.spinner-small') : null;

    if (errorEl) errorEl.style.display = 'none';

    const code = input ? input.value.trim().toUpperCase() : '';
    if (!code || code.length < 4) {
        if (errorEl) {
            errorEl.textContent = 'Please enter a valid pairing authorization code.';
            errorEl.style.display = 'block';
        }
        return;
    }

    try {
        if (btn) btn.disabled = true;
        if (spinner) spinner.style.display = 'inline-block';

        const response = await fetch(`${API_KIOSK_DEVICES}/pair`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                pairingCode: code,
                clientInfo: navigator.userAgent
            })
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || 'Device pairing failed. Check code or request a new one.');
        }

        // Success! Save credentials
        saveKioskDeviceCredentials(data.deviceToken, {
            deviceId: data.deviceId,
            deviceIdentifier: data.deviceIdentifier,
            deviceName: data.deviceName,
            pairedAt: data.pairedAt
        });

        showToast(`Workstation '${data.deviceName}' paired successfully!`, 'success');

        if (input) input.value = '';

        const unpairedState = document.getElementById('kiosk-unpaired-state');
        const neutralState = document.getElementById('kiosk-neutral-state');
        const badge = document.getElementById('kiosk-workstation-badge');
        const nameEl = document.getElementById('kiosk-workstation-name');

        if (unpairedState) unpairedState.style.display = 'none';
        if (neutralState) neutralState.style.display = 'block';
        if (badge) badge.style.display = 'inline-flex';
        if (nameEl) nameEl.textContent = data.deviceName;

        const empInput = document.getElementById('kiosk-employee-id');
        if (empInput) empInput.focus();
    } catch (err) {
        console.error('handleKioskPairingSubmit error:', err);
        if (errorEl) {
            errorEl.textContent = err.message;
            errorEl.style.display = 'block';
        }
    } finally {
        if (btn) btn.disabled = false;
        if (spinner) spinner.style.display = 'none';
    }
}

function unpairKioskWorkstation() {
    if (!confirm('Unpair this workstation? It will no longer be authorized to record attendance until re-paired by an administrator.')) {
        return;
    }
    clearKioskDeviceCredentials();
    showToast('Workstation credentials cleared.', 'info');
    checkKioskDeviceAuthorization();
}

// ---------------- Admin Device Management ----------------

async function loadAdminDevices() {
    const tbody = document.getElementById('devices-table-body');
    if (!tbody) return;

    try {
        const response = await fetch(API_ADMIN_DEVICES, {
            credentials: 'include'
        });

        if (!response.ok) {
            throw new Error('Failed to load registered devices');
        }

        const devices = await response.json();

        // Update Stats
        const statTotal = document.getElementById('stat-devices-total');
        const statActive = document.getElementById('stat-devices-active');
        const statRevoked = document.getElementById('stat-devices-revoked');

        const totalCount = devices.length;
        const activeCount = devices.filter(d => d.status === 'ACTIVE').length;
        const revokedCount = devices.filter(d => d.status === 'REVOKED').length;

        if (statTotal) statTotal.textContent = totalCount;
        if (statActive) statActive.textContent = activeCount;
        if (statRevoked) statRevoked.textContent = revokedCount;

        if (devices.length === 0) {
            tbody.innerHTML = `<tr><td colspan="8" class="text-center text-muted p-4">No devices registered. Click "+ Pair New Device" to authorize a workstation.</td></tr>`;
            return;
        }

        tbody.innerHTML = devices.map(d => {
            const statusBadge = d.status === 'ACTIVE'
                ? `<span class="badge badge-success">ACTIVE</span>`
                : `<span class="badge badge-danger">REVOKED</span>`;

            const pairedAtStr = d.pairedAt ? formatISTDateTime(d.pairedAt) : '—';
            const lastSeenStr = d.lastSeenAt ? formatISTDateTime(d.lastSeenAt) : 'Never';
            const revocationDetails = d.status === 'REVOKED'
                ? `<span class="text-xs text-danger">${d.revokedAt ? formatISTDateTime(d.revokedAt) : 'Revoked'}<br><small class="text-muted">${escapeHtml(d.revocationReason || 'No reason provided')}</small></span>`
                : '—';

            const actionsHtml = d.status === 'ACTIVE'
                ? `<button class="btn btn-danger btn-xs" onclick="openRevokeDeviceModal('${d.id}', '${escapeHtml(d.deviceName)}')">Revoke</button>`
                : `<span class="text-xs text-muted">Disabled</span>`;

            return `
                <tr>
                    <td><strong>${escapeHtml(d.deviceName)}</strong></td>
                    <td><code>${escapeHtml(d.deviceIdentifier)}</code></td>
                    <td>${statusBadge}</td>
                    <td><span class="text-xs">${pairedAtStr}</span></td>
                    <td><span class="text-xs">${lastSeenStr}</span></td>
                    <td><span class="text-xs">${escapeHtml(d.createdBy || 'admin')}</span></td>
                    <td>${revocationDetails}</td>
                    <td class="text-right">${actionsHtml}</td>
                </tr>
            `;
        }).join('');
    } catch (err) {
        console.error('loadAdminDevices error:', err);
        tbody.innerHTML = `<tr><td colspan="8" class="text-center text-danger p-4">Error loading devices: ${escapeHtml(err.message)}</td></tr>`;
    }
}

async function loadDeviceAuditLogs() {
    const tbody = document.getElementById('device-audit-table-body');
    if (!tbody) return;

    try {
        const response = await fetch(`${API_ADMIN_DEVICES}/audit`, {
            credentials: 'include'
        });

        if (!response.ok) {
            throw new Error('Failed to load device audit logs');
        }

        const logs = await response.json();

        if (logs.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" class="text-center text-muted p-4">No device audit events recorded yet.</td></tr>`;
            return;
        }

        tbody.innerHTML = logs.map(l => {
            let eventBadge = `<span class="badge badge-info">${escapeHtml(l.eventType)}</span>`;
            if (l.eventType === 'PAIRING_COMPLETED') eventBadge = `<span class="badge badge-success">PAIRING_COMPLETED</span>`;
            if (l.eventType === 'DEVICE_REVOKED') eventBadge = `<span class="badge badge-danger">DEVICE_REVOKED</span>`;
            if (l.eventType === 'PAIRING_FAILED' || l.eventType === 'DEVICE_AUTH_FAILED') eventBadge = `<span class="badge badge-warning">${escapeHtml(l.eventType)}</span>`;

            return `
                <tr>
                    <td><span class="text-xs font-mono">${formatISTDateTime(l.createdAt)}</span></td>
                    <td><strong>${escapeHtml(l.deviceName || '—')}</strong></td>
                    <td>${eventBadge}</td>
                    <td><span class="text-xs">${escapeHtml(l.actor || 'System')}</span></td>
                    <td><span class="text-xs font-mono">${escapeHtml(l.ipAddress || '—')}</span></td>
                    <td><span class="text-xs text-muted">${escapeHtml(l.details || '—')}</span></td>
                </tr>
            `;
        }).join('');
    } catch (err) {
        console.error('loadDeviceAuditLogs error:', err);
        tbody.innerHTML = `<tr><td colspan="6" class="text-center text-danger p-4">Error loading audit events: ${escapeHtml(err.message)}</td></tr>`;
    }
}

function openPairDeviceModal() {
    const modal = document.getElementById('pair-device-modal');
    const input = document.getElementById('pair-device-name-input');
    const errorEl = document.getElementById('pair-device-error');
    const stepInput = document.getElementById('pair-step-input');
    const stepDisplay = document.getElementById('pair-step-display');

    if (errorEl) errorEl.style.display = 'none';
    if (input) input.value = '';
    if (stepInput) stepInput.style.display = 'block';
    if (stepDisplay) stepDisplay.style.display = 'none';
    if (modal) modal.style.display = 'flex';
    if (input) input.focus();
}

function closePairDeviceModal() {
    const modal = document.getElementById('pair-device-modal');
    if (modal) modal.style.display = 'none';
    if (pairingCountdownInterval) {
        clearInterval(pairingCountdownInterval);
        pairingCountdownInterval = null;
    }
}

async function handleInitiatePairing() {
    const input = document.getElementById('pair-device-name-input');
    const errorEl = document.getElementById('pair-device-error');
    const btn = document.getElementById('btn-submit-initiate-pairing');
    const spinner = btn ? btn.querySelector('.spinner-small') : null;

    if (errorEl) errorEl.style.display = 'none';

    const deviceName = input ? input.value.trim() : '';
    if (!deviceName) {
        if (errorEl) {
            errorEl.textContent = 'Please enter a name for the device.';
            errorEl.style.display = 'block';
        }
        return;
    }

    try {
        if (btn) btn.disabled = true;
        if (spinner) spinner.style.display = 'inline-block';

        const response = await fetch(`${API_ADMIN_DEVICES}/pair/initiate`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ deviceName })
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || 'Failed to initiate pairing');
        }

        // Show code step
        const stepInput = document.getElementById('pair-step-input');
        const stepDisplay = document.getElementById('pair-step-display');
        const codeDisplay = document.getElementById('display-pairing-code');
        const countdownDisplay = document.getElementById('display-pairing-countdown');

        if (stepInput) stepInput.style.display = 'none';
        if (stepDisplay) stepDisplay.style.display = 'block';
        if (codeDisplay) codeDisplay.textContent = data.pairingCode;

        // Start countdown timer
        let secondsLeft = data.expiresInSeconds || 600;
        if (pairingCountdownInterval) clearInterval(pairingCountdownInterval);

        function updateCountdown() {
            const mins = Math.floor(secondsLeft / 60);
            const secs = secondsLeft % 60;
            if (countdownDisplay) {
                countdownDisplay.textContent = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
            }
            if (secondsLeft <= 0) {
                clearInterval(pairingCountdownInterval);
                if (countdownDisplay) countdownDisplay.textContent = 'EXPIRED';
            }
            secondsLeft--;
        }

        updateCountdown();
        pairingCountdownInterval = setInterval(updateCountdown, 1000);
        showToast('Pairing code generated! Valid for 10 minutes.', 'info');
    } catch (err) {
        console.error('handleInitiatePairing error:', err);
        if (errorEl) {
            errorEl.textContent = err.message;
            errorEl.style.display = 'block';
        }
    } finally {
        if (btn) btn.disabled = false;
        if (spinner) spinner.style.display = 'none';
    }
}

function openRevokeDeviceModal(deviceId, deviceName) {
    currentRevokingDeviceId = deviceId;
    const modal = document.getElementById('revoke-device-modal');
    const nameEl = document.getElementById('revoke-device-name-display');
    const reasonInput = document.getElementById('revoke-reason-input');
    const errorEl = document.getElementById('revoke-device-error');

    if (nameEl) nameEl.textContent = deviceName;
    if (reasonInput) reasonInput.value = '';
    if (errorEl) errorEl.style.display = 'none';
    if (modal) modal.style.display = 'flex';
}

function closeRevokeDeviceModal() {
    currentRevokingDeviceId = null;
    const modal = document.getElementById('revoke-device-modal');
    if (modal) modal.style.display = 'none';
}

async function handleConfirmRevokeDevice() {
    if (!currentRevokingDeviceId) return;

    const btn = document.getElementById('btn-submit-revoke-device');
    const spinner = btn ? btn.querySelector('.spinner-small') : null;
    const errorEl = document.getElementById('revoke-device-error');
    const reasonInput = document.getElementById('revoke-reason-input');

    if (errorEl) errorEl.style.display = 'none';

    try {
        if (btn) btn.disabled = true;
        if (spinner) spinner.style.display = 'inline-block';

        const response = await fetch(`${API_ADMIN_DEVICES}/${currentRevokingDeviceId}/revoke`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({
                reason: reasonInput ? reasonInput.value.trim() : ''
            })
        });

        if (!response.ok) {
            const err = await response.json();
            throw new Error(err.message || 'Failed to revoke device');
        }

        const data = await response.json();
        closeRevokeDeviceModal();
        showToast(`Workstation '${data.deviceName}' revoked successfully.`, 'success');

        await loadAdminDevices();
        await loadDeviceAuditLogs();
    } catch (err) {
        console.error('handleConfirmRevokeDevice error:', err);
        if (errorEl) {
            errorEl.textContent = err.message;
            errorEl.style.display = 'block';
        }
    } finally {
        if (btn) btn.disabled = false;
        if (spinner) spinner.style.display = 'none';
    }
}

// ==========================================
// Phase 12: Administrator Dashboard Implementation
// ==========================================

async function loadDashboard(targetDate) {
    if (!isAdminAuthenticated) return;

    const refreshBtn = document.getElementById('btn-dash-refresh');
    const lastUpdatedEl = document.getElementById('dash-last-updated-text');
    const dateInput = document.getElementById('dash-work-date-select');

    try {
        if (refreshBtn) refreshBtn.disabled = true;

        const url = targetDate
                ? `${API_ADMIN_DASHBOARD}/summary?workDate=${encodeURIComponent(targetDate)}`
                : `${API_ADMIN_DASHBOARD}/summary`;

        const response = await fetch(url, { credentials: 'include' });

        if (response.status === 401 || response.status === 403) {
            setUnauthenticatedAdmin();
            switchView('DASHBOARD');
            return;
        }

        if (!response.ok) {
            const err = await response.json();
            throw new Error(err.message || 'Failed to load dashboard summary');
        }

        const data = await response.json();
        currentDashboardDate = data.workDate;
        currentDashboardSummary = data;

        if (dateInput) {
            dateInput.value = data.workDate;
        }

        // 1. Populate Metric Cards
        const statActive = document.getElementById('dash-stat-active-emp');
        const statTotal = document.getElementById('dash-stat-total-emp');
        const statIn = document.getElementById('dash-stat-checked-in');
        const statCompleted = document.getElementById('dash-stat-completed');
        const statAttention = document.getElementById('dash-stat-attention');
        const statNotCheckedIn = document.getElementById('dash-stat-not-checked-in');

        if (statActive) statActive.textContent = data.activeEmployeeCount;
        if (statTotal) statTotal.textContent = `Total Staff: ${data.totalEmployeeCount}`;
        if (statIn) statIn.textContent = data.currentlyCheckedInCount;
        if (statCompleted) statCompleted.textContent = data.completedSessionCount;
        if (statAttention) statAttention.textContent = data.incompleteSessionCount;
        if (statNotCheckedIn) statNotCheckedIn.textContent = data.notCheckedInCount;

        // 2. Populate Sessions Requiring Review / Attention Panel
        const attentionCard = document.getElementById('dash-attention-card');
        const attentionTbody = document.getElementById('dash-attention-tbody');
        const attentionCountBadge = document.getElementById('dash-attention-count-badge');

        if (data.attentionRequiredSessions && data.attentionRequiredSessions.length > 0) {
            if (attentionCard) attentionCard.style.display = 'block';
            if (attentionCountBadge) attentionCountBadge.textContent = `${data.attentionRequiredSessions.length} Action Needed`;

            if (attentionTbody) {
                attentionTbody.innerHTML = data.attentionRequiredSessions.map(item => `
                    <tr>
                        <td>
                            <strong>${escapeHtml(item.employeeName)}</strong>
                            <div class="text-muted" style="font-size: 0.75rem;">${escapeHtml(item.employeeCode)} • ${escapeHtml(item.department)}</div>
                        </td>
                        <td>${escapeHtml(item.workDate)}</td>
                        <td>
                            <span class="badge-status badge-attention">${escapeHtml(item.issueType.replace(/_/g, ' '))}</span>
                            <div class="text-muted" style="font-size: 0.75rem; margin-top: 2px;">${escapeHtml(item.issueDescription)}</div>
                        </td>
                        <td style="font-size: 0.82rem; color: #475569;">
                            ${escapeHtml(item.recommendation)}
                        </td>
                        <td>
                            <button class="btn btn-outline-primary btn-sm" onclick="openAttendanceCorrectionModal('${item.recordId}')">
                                Resolve
                            </button>
                        </td>
                    </tr>
                `).join('');
            }
        } else {
            if (attentionCard) attentionCard.style.display = 'none';
        }

        // 3. Populate Recent Activity Feed
        const activityFeed = document.getElementById('dash-activity-feed');
        if (activityFeed) {
            if (!data.recentActivities || data.recentActivities.length === 0) {
                activityFeed.innerHTML = '<div class="text-center py-4 text-muted">No attendance events recorded yet for this work date.</div>';
            } else {
                activityFeed.innerHTML = data.recentActivities.map(act => {
                    let iconClass = 'dash-timeline-icon-in';
                    let iconSvg = '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="7" y1="17" x2="17" y2="7"></line><polyline points="7 7 17 7 17 17"></polyline></svg>';

                    if (act.activityType === 'CHECK_OUT') {
                        iconClass = 'dash-timeline-icon-out';
                        iconSvg = '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="17" y1="7" x2="7" y2="17"></line><polyline points="17 17 7 17 7 7"></polyline></svg>';
                    } else if (act.activityType === 'CORRECTION') {
                        iconClass = 'dash-timeline-icon-correction';
                        iconSvg = '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"></path><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path></svg>';
                    } else if (act.activityType === 'AUTO_CLOSED') {
                        iconClass = 'dash-timeline-icon-autoclosed';
                        iconSvg = '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>';
                    }

                    const timeStr = act.timestamp ? new Date(act.timestamp).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true }) : '';

                    return `
                        <div class="dash-timeline-item">
                            <div class="dash-timeline-icon ${iconClass}">
                                ${iconSvg}
                            </div>
                            <div class="dash-timeline-content">
                                <div class="dash-timeline-header">
                                    <span class="dash-timeline-name">${escapeHtml(act.employeeName)} (${escapeHtml(act.employeeCode)})</span>
                                    <span class="dash-timeline-time">${timeStr}</span>
                                </div>
                                <div class="dash-timeline-desc">${escapeHtml(act.description)}</div>
                            </div>
                        </div>
                    `;
                }).join('');
            }
        }

        // 4. Load Today's Attendance Roster
        await loadDashboardRoster(data.workDate);

        // 5. Update timestamp
        if (lastUpdatedEl) {
            const timeNow = new Date().toLocaleTimeString('en-US', { hour12: true });
            lastUpdatedEl.textContent = `Updated: ${timeNow}`;
        }
    } catch (err) {
        console.error('Failed to load dashboard:', err);
        showToast('Error loading dashboard: ' + err.message, 'error');
    } finally {
        if (refreshBtn) refreshBtn.disabled = false;
    }
}

async function refreshDashboard() {
    const dateInput = document.getElementById('dash-work-date-select');
    const targetDate = dateInput ? dateInput.value : currentDashboardDate;
    await loadDashboard(targetDate);
    showToast('Dashboard overview refreshed.', 'success');
}

async function loadDashboardRoster(workDate) {
    const tbody = document.getElementById('dash-roster-tbody');
    if (!tbody) return;

    try {
        const response = await fetch(`${API_ADMIN_ATTENDANCE}?fromDate=${encodeURIComponent(workDate)}&toDate=${encodeURIComponent(workDate)}&size=100`, {
            credentials: 'include'
        });

        if (!response.ok) {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center py-4 text-danger">Failed to load attendance records.</td></tr>';
            return;
        }

        const data = await response.json();
        currentDashboardRosterRecords = data.content || [];

        // Count for filter pills
        let inCount = 0;
        let outCount = 0;
        let autoCount = 0;

        currentDashboardRosterRecords.forEach(r => {
            if (r.status === 'CHECKED_IN') inCount++;
            else if (r.status === 'CHECKED_OUT') outCount++;
            else if (r.status === 'AUTO_CLOSED') autoCount++;
        });

        const pillIn = document.getElementById('pill-count-in');
        const pillOut = document.getElementById('pill-count-out');
        const pillAuto = document.getElementById('pill-count-autoclosed');

        if (pillIn) pillIn.textContent = inCount;
        if (pillOut) pillOut.textContent = outCount;
        if (pillAuto) pillAuto.textContent = autoCount;

        renderDashboardRoster();
    } catch (err) {
        console.error('loadDashboardRoster error:', err);
        tbody.innerHTML = '<tr><td colspan="8" class="text-center py-4 text-danger">Network error loading attendance roster.</td></tr>';
    }
}

function filterDashboardRoster(filter) {
    currentDashboardRosterFilter = filter;

    document.querySelectorAll('.roster-pill').forEach(pill => {
        pill.classList.remove('active');
    });

    if (filter === 'ALL') {
        const p = document.getElementById('pill-filter-all');
        if (p) p.classList.add('active');
    } else if (filter === 'CHECKED_IN') {
        const p = document.getElementById('pill-filter-in');
        if (p) p.classList.add('active');
    } else if (filter === 'CHECKED_OUT') {
        const p = document.getElementById('pill-filter-out');
        if (p) p.classList.add('active');
    } else if (filter === 'AUTO_CLOSED') {
        const p = document.getElementById('pill-filter-autoclosed');
        if (p) p.classList.add('active');
    }

    renderDashboardRoster();
}

function renderDashboardRoster() {
    const tbody = document.getElementById('dash-roster-tbody');
    if (!tbody) return;

    let filtered = currentDashboardRosterRecords;
    if (currentDashboardRosterFilter !== 'ALL') {
        filtered = filtered.filter(r => r.status === currentDashboardRosterFilter);
    }

    if (filtered.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" class="text-center py-4 text-muted">No records matching filter '${currentDashboardRosterFilter}' for this work date.</td></tr>`;
        return;
    }

    tbody.innerHTML = filtered.map(record => {
        const checkInFormatted = record.checkInTime ? formatDateTime(record.checkInTime) : '--:--';
        const checkOutFormatted = record.checkOutTime ? formatDateTime(record.checkOutTime) : '<span class="text-muted">--:--</span>';

        let statusBadgeClass = 'badge-active';
        let statusLabel = record.status;

        if (record.status === 'CHECKED_IN') {
            statusBadgeClass = 'badge-active';
            statusLabel = 'Checked In';
        } else if (record.status === 'CHECKED_OUT') {
            statusBadgeClass = 'badge-inactive';
            statusLabel = 'Completed';
        } else if (record.status === 'AUTO_CLOSED') {
            statusBadgeClass = 'badge-danger';
            statusLabel = 'Auto-Closed';
        } else if (record.status === 'VOIDED') {
            statusBadgeClass = 'badge-secondary';
            statusLabel = 'Voided';
        }

        // Calculate duration if checkOutTime exists
        let durationStr = 'In Progress';
        if (record.checkInTime && record.checkOutTime) {
            const start = new Date(record.checkInTime);
            const end = new Date(record.checkOutTime);
            const diffMs = end - start;
            if (diffMs > 0) {
                const diffMins = Math.floor(diffMs / 60000);
                const hrs = Math.floor(diffMins / 60);
                const mins = diffMins % 60;
                durationStr = `${hrs}h ${mins}m`;
            }
        }

        let photoButton = '<span class="text-muted" style="font-size:0.75rem;">None</span>';
        if (record.hasCheckInPhoto || record.hasCheckOutPhoto) {
            photoButton = `<button class="btn btn-outline-secondary btn-sm" onclick="openPhotoViewerModal('${record.id}')" title="View Verification Photo">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"></path><circle cx="12" cy="13" r="4"></circle></svg>
                <span>Photo</span>
            </button>`;
        }

        return `
            <tr>
                <td>
                    <strong>${escapeHtml(record.employeeName)}</strong>
                    <div class="text-muted" style="font-size:0.75rem;">${escapeHtml(record.employeeId)}</div>
                </td>
                <td>${escapeHtml(record.department || 'General')}</td>
                <td>${checkInFormatted}</td>
                <td>${checkOutFormatted}</td>
                <td><span class="badge-status ${statusBadgeClass}">${statusLabel}</span></td>
                <td><span style="font-size:0.82rem; color:#475569;">${durationStr}</span></td>
                <td>${photoButton}</td>
                <td>
                    <button class="btn btn-outline-primary btn-sm" onclick="openAttendanceCorrectionModal('${record.id}')" title="Attendance Correction">
                        Correct
                    </button>
                </td>
            </tr>
        `;
    }).join('');
}

async function handleDashboardEmployeeSearch(query) {
    const dropdown = document.getElementById('dash-employee-search-results');
    if (!dropdown) return;

    if (!query || query.trim().length === 0) {
        dropdown.style.display = 'none';
        dropdown.innerHTML = '';
        return;
    }

    try {
        const url = `${API_ADMIN_DASHBOARD}/employee-search?query=${encodeURIComponent(query.trim())}${currentDashboardDate ? '&workDate=' + currentDashboardDate : ''}`;
        const response = await fetch(url, { credentials: 'include' });

        if (!response.ok) return;

        const results = await response.json();

        if (results.length === 0) {
            dropdown.innerHTML = '<div class="p-3 text-muted text-center" style="font-size:0.85rem;">No active employees matching "' + escapeHtml(query) + '"</div>';
            dropdown.style.display = 'block';
            return;
        }

        dropdown.innerHTML = results.map(emp => {
            let statusPill = '<span class="badge-status" style="background:#f1f5f9; color:#64748b; font-size:0.7rem;">No Attendance Today</span>';
            if (emp.todayStatus === 'CHECKED_IN') {
                statusPill = '<span class="badge-status badge-active" style="font-size:0.7rem;">Checked In</span>';
            } else if (emp.todayStatus === 'CHECKED_OUT') {
                statusPill = '<span class="badge-status badge-inactive" style="font-size:0.7rem;">Completed</span>';
            } else if (emp.todayStatus === 'AUTO_CLOSED') {
                statusPill = '<span class="badge-status badge-danger" style="font-size:0.7rem;">Auto-Closed</span>';
            }

            return `
                <div class="dash-search-item">
                    <div>
                        <strong>${escapeHtml(emp.fullName)}</strong>
                        <div class="text-muted" style="font-size:0.75rem;">${escapeHtml(emp.employeeId)} • ${escapeHtml(emp.department)}</div>
                    </div>
                    <div style="display:flex; align-items:center; gap:0.5rem;">
                        ${statusPill}
                        <button class="btn btn-outline-secondary btn-sm" onclick="viewEmployeeHistoryFromDashboard('${emp.id}', '${escapeHtml(emp.employeeId)}')">
                            History
                        </button>
                        <button class="btn btn-outline-primary btn-sm" onclick="openManualAttendanceModal('${escapeHtml(emp.employeeId)}')">
                            Punch
                        </button>
                    </div>
                </div>
            `;
        }).join('');

        dropdown.style.display = 'block';
    } catch (err) {
        console.error('handleDashboardEmployeeSearch error:', err);
    }
}

function viewEmployeeHistoryFromDashboard(employeeUuid, employeeId) {
    const dropdown = document.getElementById('dash-employee-search-results');
    if (dropdown) dropdown.style.display = 'none';

    const attSearch = document.getElementById('att-search-input');
    if (attSearch) attSearch.value = employeeId;

    switchView('ATTENDANCE');
}




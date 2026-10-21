/**
 * Staff Attendance Management System — Employee Directory Client
 * Communicates with Spring Boot 3 REST API at /api/admin/employees
 */

const API_BASE = '/api/admin/employees';

// Application State
let allEmployees = [];
let currentFilter = 'ACTIVE';
let searchQuery = '';
let searchDebounceTimer = null;

// DOM Elements
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
const confirmModal = document.getElementById('confirm-modal');

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    setupEventListeners();
    loadEmployees();
});

function setupEventListeners() {
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

    // Close modals on Escape key
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            closeRegisterModal();
            closeEditModal();
            closeConfirmModal();
        }
    });
}

/**
 * Fetch employees from backend REST API
 */
async function loadEmployees() {
    showLoading();

    try {
        let url = API_BASE;
        if (searchQuery) {
            url += `?search=${encodeURIComponent(searchQuery)}`;
        }

        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`Server returned HTTP ${response.status} (${response.statusText})`);
        }

        allEmployees = await response.json();
        updateStats(allEmployees);
        renderFilteredTable();
    } catch (err) {
        console.error('Failed to load employees:', err);
        showError(err.message || 'Could not connect to the backend server. Ensure Spring Boot is running.');
    }
}

/**
 * Filter and render table based on current status filter
 */
function renderFilteredTable() {
    let filtered = allEmployees;
    if (currentFilter !== 'ALL') {
        filtered = allEmployees.filter(emp => emp.status === currentFilter);
    }

    if (filtered.length === 0) {
        showEmpty();
        return;
    }

    tableBody.innerHTML = '';
    filtered.forEach(emp => {
        const tr = document.createElement('tr');
        
        const isStatusActive = emp.status === 'ACTIVE';
        const formattedDate = formatDateTime(emp.createdAt);

        tr.innerHTML = `
            <td>
                <span class="mono-pill">${escapeHtml(emp.employeeId)}</span>
            </td>
            <td>
                <strong>${escapeHtml(emp.fullName)}</strong>
            </td>
            <td>${escapeHtml(emp.department || 'General')}</td>
            <td>
                <span class="status-badge ${isStatusActive ? 'active' : 'inactive'}">
                    <span class="status-dot"></span>
                    ${isStatusActive ? 'Active' : 'Inactive'}
                </span>
            </td>
            <td>${formattedDate}</td>
            <td class="text-right">
                <div class="action-buttons">
                    <button class="btn btn-secondary btn-xs" onclick="openEditModal('${emp.id}')">
                        Edit
                    </button>
                    ${isStatusActive ? `
                        <button class="btn btn-secondary btn-xs" style="color: var(--color-danger);" onclick="promptStatusToggle('${emp.id}', 'INACTIVE')">
                            Deactivate
                        </button>
                    ` : `
                        <button class="btn btn-secondary btn-xs" style="color: var(--color-success);" onclick="promptStatusToggle('${emp.id}', 'ACTIVE')">
                            Activate
                        </button>
                    `}
                </div>
            </td>
        `;
        tableBody.appendChild(tr);
    });

    showTable();
}

/**
 * Update stats summary cards
 */
function updateStats(employees) {
    const total = employees.length;
    const active = employees.filter(e => e.status === 'ACTIVE').length;
    const inactive = total - active;

    statTotal.textContent = total;
    statActive.textContent = active;
    statInactive.textContent = inactive;
}

/**
 * Handle Register Employee Form Submission
 */
async function handleRegisterSubmit(e) {
    e.preventDefault();
    clearErrors('reg');

    const employeeId = document.getElementById('reg-employee-id').value.trim();
    const fullName = document.getElementById('reg-full-name').value.trim();
    const department = document.getElementById('reg-department').value.trim();

    // Client-side quick check
    if (!employeeId || !fullName) {
        showFieldError('err-employeeId', 'Employee ID is required');
        showFieldError('err-fullName', 'Full name is required');
        return;
    }

    const submitBtn = document.getElementById('btn-submit-register');
    setButtonLoading(submitBtn, true);

    try {
        const response = await fetch(API_BASE, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ employeeId, fullName, department })
        });

        const data = await response.json();

        if (response.status === 201) {
            closeRegisterModal();
            showToast(`Employee "${data.fullName}" registered successfully!`, 'success');
            await loadEmployees();
        } else if (response.status === 409) {
            showBannerError('register-error-banner', data.message || 'An employee with this ID already exists.');
        } else if (response.status === 400 && data.validationErrors) {
            for (const [field, msg] of Object.entries(data.validationErrors)) {
                showFieldError(`err-${field}`, msg);
            }
        } else {
            showBannerError('register-error-banner', data.message || 'Failed to save employee.');
        }
    } catch (err) {
        showBannerError('register-error-banner', 'Network error. Could not connect to API.');
    } finally {
        setButtonLoading(submitBtn, false);
    }
}

/**
 * Handle Edit Employee Form Submission
 */
async function handleEditSubmit(e) {
    e.preventDefault();
    clearErrors('edit');

    const id = document.getElementById('edit-employee-uuid').value;
    const fullName = document.getElementById('edit-full-name').value.trim();
    const department = document.getElementById('edit-department').value.trim();

    if (!fullName) {
        showFieldError('err-edit-fullName', 'Full name is required');
        return;
    }

    const submitBtn = document.getElementById('btn-submit-edit');
    setButtonLoading(submitBtn, true);

    try {
        const response = await fetch(`${API_BASE}/${id}`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ fullName, department })
        });

        const data = await response.json();

        if (response.ok) {
            closeEditModal();
            showToast(`Employee "${data.fullName}" updated successfully!`, 'success');
            await loadEmployees();
        } else if (response.status === 400 && data.validationErrors) {
            for (const [field, msg] of Object.entries(data.validationErrors)) {
                showFieldError(`err-edit-${field}`, msg);
            }
        } else {
            showBannerError('edit-error-banner', data.message || 'Failed to update employee.');
        }
    } catch (err) {
        showBannerError('edit-error-banner', 'Network error. Could not connect to API.');
    } finally {
        setButtonLoading(submitBtn, false);
    }
}

/**
 * Prompt Deactivate / Activate Confirmation Dialog
 */
function promptStatusToggle(employeeUuid, targetStatus) {
    const employee = allEmployees.find(e => e.id === employeeUuid);
    if (!employee) return;

    const titleEl = document.getElementById('confirm-modal-title');
    const msgEl = document.getElementById('confirm-modal-message');
    const subtextEl = document.getElementById('confirm-modal-subtext');
    const iconBox = document.getElementById('confirm-icon-box');
    const confirmBtn = document.getElementById('confirm-modal-btn');

    if (targetStatus === 'INACTIVE') {
        titleEl.textContent = 'Deactivate Employee';
        msgEl.textContent = `Deactivate ${employee.fullName} (${employee.employeeId})?`;
        subtextEl.textContent = 'This employee will no longer be permitted to punch attendance at the kiosk. Their 3-year historical attendance and photo records will remain fully intact.';
        iconBox.className = 'confirm-icon-box error-icon';
        confirmBtn.className = 'btn btn-danger';
        confirmBtn.textContent = 'Deactivate Employee';
    } else {
        titleEl.textContent = 'Reactivate Employee';
        msgEl.textContent = `Reactivate ${employee.fullName} (${employee.employeeId})?`;
        subtextEl.textContent = 'This employee will immediately be restored to active status and permitted to record attendance punches at the kiosk.';
        iconBox.className = 'confirm-icon-box stat-green';
        confirmBtn.className = 'btn btn-success';
        confirmBtn.textContent = 'Reactivate Employee';
    }

    confirmBtn.onclick = async () => {
        confirmBtn.disabled = true;
        try {
            const response = await fetch(`${API_BASE}/${employeeUuid}/status`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ status: targetStatus })
            });

            if (response.ok) {
                closeConfirmModal();
                showToast(`Employee ${employee.fullName} is now ${targetStatus}.`, 'success');
                await loadEmployees();
            } else {
                const err = await response.json();
                showToast(err.message || 'Status update failed.', 'danger');
            }
        } catch (e) {
            showToast('Network error while updating status.', 'danger');
        } finally {
            confirmBtn.disabled = false;
        }
    };

    confirmModal.style.display = 'flex';
}

// Modal Open / Close Helpers
function openRegisterModal() {
    document.getElementById('register-form').reset();
    clearErrors('reg');
    document.getElementById('register-error-banner').style.display = 'none';
    registerModal.style.display = 'flex';
    document.getElementById('reg-employee-id').focus();
}

function closeRegisterModal() {
    registerModal.style.display = 'none';
}

function openEditModal(employeeUuid) {
    const employee = allEmployees.find(e => e.id === employeeUuid);
    if (!employee) return;

    document.getElementById('edit-employee-uuid').value = employee.id;
    document.getElementById('edit-employee-id').value = employee.employeeId;
    document.getElementById('edit-full-name').value = employee.fullName;
    document.getElementById('edit-department').value = employee.department || 'General';

    clearErrors('edit');
    document.getElementById('edit-error-banner').style.display = 'none';
    editModal.style.display = 'flex';
    document.getElementById('edit-full-name').focus();
}

function closeEditModal() {
    editModal.style.display = 'none';
}

function closeConfirmModal() {
    confirmModal.style.display = 'none';
}

// State Display Helpers
function showLoading() {
    loadingSpinner.style.display = 'flex';
    errorBox.style.display = 'none';
    emptyState.style.display = 'none';
    tableWrapper.style.display = 'none';
}

function showTable() {
    loadingSpinner.style.display = 'none';
    errorBox.style.display = 'none';
    emptyState.style.display = 'none';
    tableWrapper.style.display = 'block';
}

function showEmpty() {
    loadingSpinner.style.display = 'none';
    errorBox.style.display = 'none';
    tableWrapper.style.display = 'none';
    emptyState.style.display = 'flex';
}

function showError(msg) {
    loadingSpinner.style.display = 'none';
    tableWrapper.style.display = 'none';
    emptyState.style.display = 'none';
    errorMessage.textContent = msg;
    errorBox.style.display = 'flex';
}

function showBannerError(bannerId, message) {
    const banner = document.getElementById(bannerId);
    banner.textContent = message;
    banner.style.display = 'block';
}

function showFieldError(errorElId, message) {
    const el = document.getElementById(errorElId);
    if (el) {
        el.textContent = message;
        el.classList.add('visible');
    }
}

function clearErrors(prefix) {
    document.querySelectorAll('.field-error').forEach(el => {
        el.textContent = '';
        el.classList.remove('visible');
    });
}

function setButtonLoading(button, isLoading) {
    button.disabled = isLoading;
    const textEl = button.querySelector('.btn-text');
    const spinnerEl = button.querySelector('.spinner-small');
    if (textEl && spinnerEl) {
        textEl.style.display = isLoading ? 'none' : 'inline';
        spinnerEl.style.display = isLoading ? 'inline-block' : 'none';
    }
}

// Toast Notifications
function showToast(message, type = 'success') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            ${type === 'success' 
                ? '<polyline points="20 6 9 17 4 12"></polyline>' 
                : '<circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line>'}
        </svg>
        <span>${escapeHtml(message)}</span>
    `;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.animation = 'toastIn 0.25s reverse ease-in forwards';
        setTimeout(() => toast.remove(), 250);
    }, 3500);
}

// Notice for Unimplemented Future Phases
function showPlaceholderNotice(featureName) {
    showToast(`${featureName} is planned for upcoming implementation phases. Employee Management is active.`, 'danger');
}

// Utility: Date formatter (Asia/Kolkata IST)
function formatDateTime(isoString) {
    if (!isoString) return '—';
    try {
        const date = new Date(isoString);
        return new Intl.DateTimeFormat('en-IN', {
            timeZone: 'Asia/Kolkata',
            day: '2-digit',
            month: 'short',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        }).format(date);
    } catch (e) {
        return isoString;
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return str
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

# Frontend Architecture Specification
## Staff Attendance Management System — Phase 3 Technical Specification

**Document Version:** 1.0.0  
**Status:** Approved Frontend Architecture  
**Framework:** Angular 18+ (TypeScript 5.x)  
**Component Model:** Angular Standalone Components  
**Styling:** Responsive SCSS / Custom Design System Tokens  

---

### 1. Dual User Experience (UX) Architecture
The frontend is structured as two completely isolated user experiences within a single Angular application:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ANGULAR APPLICATION ROOT                        │
│                           (app.routes.ts)                              │
├───────────────────────────────────┬────────────────────────────────────┤
│           KIOSK ROUTE             │            ADMIN ROUTE             │
│            (/kiosk/*)             │            (/admin/*)              │
├───────────────────────────────────┼────────────────────────────────────┤
│ - Touch-First Fullscreen Shell    │ - Desktop/Tablet Responsive Grid   │
│ - On-Screen Numeric Keypad        │ - Data Tables & Multi-Filter UI    │
│ - Live WebCam Stream & Capture    │ - Photo Review Modal Dialogs       │
│ - Inactivity Reset Timer (15s)    │ - Manual Edit Form with Reason     │
│ - Zero Admin Navigation Links     │ - JWT Auth & Session Inactivity    │
└───────────────────────────────────┴────────────────────────────────────┘
```

---

### 2. Angular Standalone Component Choice & Rationale
- **Decision:** Use **Angular Standalone Components** (`standalone: true`) throughout the entire application.
- **Rationale:**
  1. Eliminates legacy `NgModule` boilerplate, reducing bundle size and speeding up compilation.
  2. Clear, explicit dependency graph per component via `imports: [...]`.
  3. Seamless integration with modern Angular **Signals** for high-performance reactive state management.
  4. Native lazy loading with `loadComponent()` and `loadChildren()` in `app.routes.ts`.

---

### 3. Route Structure & Navigation Boundaries

```typescript
// Proposed Angular Route Map
export const routes: Routes = [
  { path: '', redirectTo: 'kiosk', pathMatch: 'full' },
  {
    path: 'kiosk',
    children: [
      { path: '', loadComponent: () => import('./features/kiosk/kiosk-home.component') },
      { path: 'pin-entry', loadComponent: () => import('./features/kiosk/kiosk-pin.component') },
      { path: 'camera', loadComponent: () => import('./features/kiosk/kiosk-camera.component') },
      { path: 'confirmation', loadComponent: () => import('./features/kiosk/kiosk-confirmation.component') }
    ]
  },
  {
    path: 'admin',
    children: [
      { path: 'login', loadComponent: () => import('./features/admin/login.component') },
      {
        path: '',
        loadComponent: () => import('./features/admin/admin-layout.component'),
        canActivate: [AdminAuthGuard], // Navigation UX guard (Backend enforces actual security)
        children: [
          { path: 'dashboard', loadComponent: () => import('./features/admin/dashboard.component') },
          { path: 'employees', loadComponent: () => import('./features/admin/employees.component') },
          { path: 'history', loadComponent: () => import('./features/admin/history.component') },
          { path: 'audit', loadComponent: () => import('./features/admin/audit-logs.component') },
          { path: 'settings', loadComponent: () => import('./features/admin/settings.component') }
        ]
      }
    ]
  },
  { path: '**', redirectTo: 'kiosk' }
];
```

---

### 4. Camera Integration & Hardware Lifecycle Management
- **API:** `navigator.mediaDevices.getUserMedia({ video: { width: { ideal: 1280 }, height: { ideal: 720 }, facingMode: 'user' } })`.
- **Stream Teardown Invariant:** To prevent camera hardware lights remaining on or draining kiosk battery, every video stream track is explicitly stopped in the component's `ngOnDestroy()` lifecycle hook:
  ```typescript
  ngOnDestroy(): void {
    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach(track => track.stop());
    }
  }
  ```
- **Canvas Capture:** Captured frames are rendered to an off-screen HTML5 `<canvas>` element and exported as a JPEG blob (`canvas.toBlob(callback, 'image/jpeg', 0.85)`), guaranteeing client-side compression below 200KB prior to upload.

---

### 5. Reactive State Management & In-Memory Sanitization
- **State Engine:** Modern **Angular Signals** (`signal()`, `computed()`) inside dedicated domain services (`KioskStateService`, `AdminAttendanceService`). Avoids heavyweight NgRx/Redux dependencies.
- **Kiosk Memory Sanitization:** When a punch completes or the 15-second inactivity timer fires, `KioskStateService.reset()` explicitly wipes employee tokens, names, and image buffers:
  ```typescript
  resetKioskState(): void {
    this.employeeToken.set(null);
    this.employeeName.set('');
    this.capturedPhotoBlob.set(null);
    this.eligibleAction.set(null);
  }
  ```

---

### 6. HTTP Communication & Interceptor Pipeline
1. **`KioskDeviceInterceptor`:** Attaches `X-Kiosk-Device-Token` header to requests routed to `/api/v1/kiosk/*`.
2. **`CredentialsInterceptor`:** Ensures `withCredentials: true` is enabled on all admin API calls so `HttpOnly` JWT session cookies are transmitted securely.
3. **`ErrorInterceptor`:** Catches HTTP 401 (redirects Admin to login; resets Kiosk), HTTP 409 (displays optimistic lock / concurrency alert), and HTTP 503 (displays friendly offline message).

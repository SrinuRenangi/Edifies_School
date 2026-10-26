import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const roleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated) {
    router.navigate(['/login']);
    return false;
  }

  const expectedRoles = route.data?.['roles'] as Array<string>;
  const userRole = authService.currentRole;

  if (expectedRoles && expectedRoles.length > 0) {
    if (!userRole || !expectedRoles.includes(userRole)) {
      if (userRole === 'ROLE_MANAGER') {
        router.navigate(['/employees']);
      } else {
        router.navigate(['/dashboard']);
      }
      return false;
    }
  }

  return true;
};

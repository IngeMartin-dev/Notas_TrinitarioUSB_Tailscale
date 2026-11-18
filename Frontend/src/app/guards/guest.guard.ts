import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/**
 * Para la pantalla de login (y la raíz "/"): si ya hay una sesión guardada, entra directo a la
 * aplicación en vez de pedir usuario y contraseña otra vez. La sesión solo se cierra con
 * "Cerrar sesión" (o si el servidor la rechaza, y en ese caso ya se borró el token).
 */
export const guestGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  if (authService.isAuthenticated()) {
    return router.createUrlTree(['/dashboard']);
  }
  return true;
};

import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Guard de rutas por rol. Complementa el filtrado del menú lateral en
 * app.html: aunque un enlace no aparezca en el sidebar, sin este guard
 * cualquiera podría escribir la URL a mano (p. ej. /students) y entrar
 * igual. Aquí se bloquea eso y se manda de vuelta al Panel.
 *
 * Importante: esto es una capa de UX/seguridad en el cliente. La
 * autorización real (la que de verdad protege los datos) debe reforzarse
 * también en el backend con @PreAuthorize, como ya se hizo para los
 * endpoints de notas/boletines.
 */
export function roleGuard(allowedRoles: string[]): CanActivateFn {
  return async (_route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    // Sin sesión: al login (en vez de rebotar entre rutas protegidas).
    if (!authService.isAuthenticated()) {
      return router.createUrlTree(['/login']);
    }

    // Al recargar la página (F5) hay token pero el usuario todavía no está
    // cargado en memoria. Antes el guard lo veía como "sin rol", lo mandaba
    // a /dashboard, el guard volvía a rechazarlo y se formaba un bucle de
    // redirecciones infinito que congelaba la pestaña. Ahora se espera a que
    // el usuario cargue antes de decidir.
    let user = authService.getCurrentUserValue();
    if (!user) {
      try {
        user = await firstValueFrom(authService.getCurrentUser());
      } catch {
        return router.createUrlTree(['/login']);
      }
    }

    const roleName: string = user?.role?.name ?? '';

    // Cuenta también el rol "extra" de Admin sumado desde Configuración de
    // Año (ver User.additionalAdmin en el backend): si la ruta admite
    // ADMIN, un padre o profesor con ese extra debe poder entrar igual que
    // un admin nativo, o de lo contrario el guard lo manda de vuelta al
    // Panel aunque el backend sí le esté dando el permiso.
    const esAdminExtra = !!user?.additionalAdmin && allowedRoles.includes('ADMIN');

    if (allowedRoles.includes(roleName) || esAdminExtra) {
      return true;
    }

    // Sin permiso: al Panel. Si ya se intentaba entrar al Panel y tampoco
    // tiene permiso, no se redirige a la misma ruta (eso causaba el bucle).
    if (state.url.split('?')[0].startsWith('/dashboard')) {
      return false;
    }
    return router.createUrlTree(['/dashboard']);
  };
}

// Grupos de roles reutilizables, alineados con la restricción del sidebar:
export const ADMIN_ONLY = ['ADMIN'];
export const TEACHER_OR_DIRECTOR = ['TEACHER', 'DIRECTOR_DE_GRUPO'];
export const DIRECTOR_OR_ADMIN = ['ADMIN', 'DIRECTOR_DE_GRUPO'];
// Ruta /grades: solo quien la EDITA (profesor/director). El padre de
// familia usa la ruta separada /mis-notas (solo lectura, filtrada a sus
// hijos activos, sin posibilidad de editar).
export const GRADES_VISIBLE_TO = ['TEACHER', 'DIRECTOR_DE_GRUPO'];
// Ruta /boletines: solo ADMIN (generación). El padre de familia usa la
// ruta separada /mis-boletines (solo lectura, filtrada a sus hijos activos).
export const BOLETINES_VISIBLE_TO = ['ADMIN'];
export const PARENT_ONLY = ['PARENT'];
export const ANY_KNOWN_ROLE = ['ADMIN', 'TEACHER', 'DIRECTOR_DE_GRUPO', 'PARENT', 'STUDENT'];
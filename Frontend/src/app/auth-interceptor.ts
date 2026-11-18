import { HttpInterceptorFn, HttpErrorResponse, HttpEvent } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError, of, Observable, EMPTY, defer, map, finalize, shareReplay } from 'rxjs';
import { AuthService } from './services/auth.service';
import { ConnectionService } from './services/connection.service';
import { adoptAlternateSession } from './session-storage-shim';
import { logAuth } from './session-debug';

interface RenewResult { token: string | null; rejected: boolean; error?: any; }
let renewal$: Observable<RenewResult> | null = null;

/** Renueva la sesión una sola vez aunque varias peticiones fallen al mismo tiempo. */
function renewSession(authService: AuthService): Observable<RenewResult> {
  if (!renewal$) {
    renewal$ = defer(() => authService.refreshToken()).pipe(
      map((res: any): RenewResult => {
        if (res && res.token) {
          localStorage.setItem('token', res.token);
          if (res.refreshToken) localStorage.setItem('refreshToken', res.refreshToken);
          return { token: res.token, rejected: false };
        }
        return { token: null, rejected: true };
      }),
      catchError((err: any) => {
        const st = err?.status;
        // Sin status (p. ej. no hay refresh token) o 400/401/403 = rechazo definitivo.
        const rejected = st === undefined || st === 400 || st === 401 || st === 403;
        return of<RenewResult>({ token: null, rejected, error: err });
      }),
      finalize(() => { renewal$ = null; }),
      shareReplay(1)
    );
  }
  return renewal$;
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const connection = inject(ConnectionService);
  const token = authService.getToken();

  const handleConnectionError = (error: HttpErrorResponse): Observable<HttpEvent<any>> => {
    // status 0 = no se pudo conectar con el servidor
    // Se excluye /api/health para que el sondeo de reconexión propague el error
    if (error.status === 0 && !req.url.includes('/api/health')) {
      // Pantalla completa "Sin conexión" (sin cambiar de ruta); al reconectar
      // se vuelve solo a la vista en la que estaba el usuario.
      connection.markOffline();
      return EMPTY;
    }
    return throwError(() => error);
  };

  if (token && !req.url.includes('/api/health')) {
    const cloned = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
    
    return next(cloned).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 0) {
          return handleConnectionError(error);
        }
        if (error.status === 401 && !req.url.includes('/auth/login') && !req.url.includes('/auth/refresh')) {
          logAuth('401 en', req.url.replace(/https?:\/\/[^/]+/, '').slice(0, 60));
          // Otra pestaña con la MISMA cuenta pudo renovar la sesión (el servidor invalida el
          // refresh token anterior): se toma esa sesión vigente en vez de cerrar esta pestaña.
          const alt = adoptAlternateSession();
          if (alt && alt.token !== token) {
            return next(req.clone({ setHeaders: { Authorization: `Bearer ${alt.token}` } }));
          }
          // Todas las peticiones que fallen a la vez comparten UNA sola renovación y esperan su
          // resultado (antes la segunda petición veía "renovación en curso" y cerraba la sesión).
          return renewSession(authService).pipe(
            switchMap((r) => {
              logAuth('renovacion', { ok: !!r.token, rechazada: r.rejected, status: r.error?.status });
              if (r.token) {
                return next(req.clone({ setHeaders: { Authorization: `Bearer ${r.token}` } }));
              }
              const alt2 = adoptAlternateSession();
              if (alt2) {
                return next(req.clone({ setHeaders: { Authorization: `Bearer ${alt2.token}` } }));
              }
              // La sesión solo se cierra si el servidor RECHAZA el refresh token. Un fallo pasajero
              // (servidor reiniciándose, 502/503/504, timeout) conserva la sesión guardada.
              if (r.rejected) {
                authService.logout();
                window.location.href = '/login';
              }
              return throwError(() => r.error || error);
            })
          );
        }
        return throwError(() => error);
      })
    ) as Observable<HttpEvent<any>>;
  }

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => handleConnectionError(error))
  ) as Observable<HttpEvent<any>>;
};
import { HttpInterceptorFn, HttpErrorResponse, HttpEvent } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError, of, Observable, EMPTY } from 'rxjs';
import { AuthService } from './services/auth.service';
import { ConnectionService } from './services/connection.service';

let isRefreshing = false;
let refreshTokenSubject: any = null;

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
          if (!isRefreshing) {
            isRefreshing = true;
            refreshTokenSubject = of(null);
            
            return authService.refreshToken().pipe(
              switchMap((refreshResponse: any) => {
                isRefreshing = false;
                if (refreshResponse.token) {
                  localStorage.setItem('token', refreshResponse.token);
                  localStorage.setItem('refreshToken', refreshResponse.refreshToken);
                  refreshTokenSubject = of(refreshResponse.token);
                  const retryReq = req.clone({
                    setHeaders: {
                      Authorization: `Bearer ${refreshResponse.token}`
                    }
                  });
                  return next(retryReq);
                }
                authService.logout();
                window.location.href = '/login';
                return throwError(() => error);
              }),
              catchError((refreshError) => {
                isRefreshing = false;
                authService.logout();
                window.location.href = '/login';
                return throwError(() => refreshError);
              })
            );
          } else {
            return refreshTokenSubject.pipe(
              switchMap((newToken: string) => {
                if (newToken) {
                  const retryReq = req.clone({
                    setHeaders: {
                      Authorization: `Bearer ${newToken}`
                    }
                  });
                  return next(retryReq);
                }
                authService.logout();
                window.location.href = '/login';
                return throwError(() => error);
              })
            );
          }
        }
        return throwError(() => error);
      })
    ) as Observable<HttpEvent<any>>;
  }

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => handleConnectionError(error))
  ) as Observable<HttpEvent<any>>;
};
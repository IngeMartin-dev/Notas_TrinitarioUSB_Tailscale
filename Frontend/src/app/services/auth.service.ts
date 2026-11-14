import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError, BehaviorSubject } from 'rxjs';
import { API_BASE_URL } from '../config/api-base';
import { catchError, tap } from 'rxjs/operators';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  refreshToken: string;
  role: string;
  // Presentes solo cuando el usuario tiene 2FA activado: en ese caso NO
  // vienen token/refreshToken todavía, hay que llamar a verifyTwoFactor().
  twoFactorRequired?: boolean;
  userId?: number;
  emailHint?: string;
}

export interface RefreshResponse {
  token: string;
  refreshToken: string;
  role: string;
}

export interface AuthError {
  error: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_URL = 'http://localhost:8080/api/auth';
  private readonly API_BASE = 'http://localhost:8080/api';
  public readonly API_BASE_URL = 'http://localhost:8080/api';
  private currentUserSubject = new BehaviorSubject<any>(AuthService.readStoredUser());
  public currentUser$ = this.currentUserSubject.asObservable();
  /** true solo cuando el servidor entregó el usuario en esta carga (no el guardado). */
  public serverUserLoaded = false;

  constructor(private http: HttpClient) {}

  /**
   * Último usuario que entregó el servidor, si todavía hay sesión. Con él el menú y los
   * guards de rutas funcionan al instante aunque /me tarde o falle un momento (si el
   * servidor dice que la sesión ya no vale, el interceptor la cierra igual).
   */
  private static readStoredUser(): any | null {
    try {
      if (!localStorage.getItem('token')) return null;
      const raw = localStorage.getItem('currentUser');
      const user = raw ? JSON.parse(raw) : null;
      return user && typeof user === 'object' && user.role ? user : null;
    } catch {
      return null;
    }
  }

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.API_URL}/login`, credentials)
      .pipe(
        tap(response => {
          if (response.token) {
            localStorage.setItem('token', response.token);
            localStorage.setItem('refreshToken', response.refreshToken);
            localStorage.setItem('role', response.role);
            this.getCurrentUser().subscribe();
          }
          // Si response.twoFactorRequired es true, todavía NO hay sesión:
          // el componente de login debe pedir el código y llamar a
          // verifyTwoFactor() para completar el inicio de sesión.
        }),
        catchError(this.handleError)
      );
  }

  /** Segundo paso del login cuando el usuario tiene 2FA activado. */
  verifyTwoFactor(userId: number, code: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.API_URL}/verify-2fa`, { userId, code })
      .pipe(
        tap(response => {
          if (response.token) {
            localStorage.setItem('token', response.token);
            localStorage.setItem('refreshToken', response.refreshToken);
            localStorage.setItem('role', response.role);
            this.getCurrentUser().subscribe();
          }
        }),
        catchError(this.handleError)
      );
  }

  logout(): void {
    // Cierra también la sesión en el servidor. Antes solo se borraban los datos del
    // navegador: la sesión seguía viva en la base y "Sesiones activas" se llenaba de
    // dispositivos que ya no estaban conectados. Es "dispara y olvida": si no hay red,
    // igual se cierra aquí. Se usa fetch (no HttpClient) para no pasar por el
    // interceptor, que ante un 401 intentaría refrescar la sesión.
    const refreshToken = localStorage.getItem('refreshToken');
    if (refreshToken && typeof fetch === 'function') {
      try {
        fetch(`${API_BASE_URL}/api/auth/logout`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
          keepalive: true
        }).catch(() => { /* sin conexión: no importa */ });
      } catch { /* no importa */ }
    }
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('role');
    localStorage.removeItem('currentUser');
    this.serverUserLoaded = false;
    this.currentUserSubject.next(null);
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  getRefreshToken(): string | null {
    return localStorage.getItem('refreshToken');
  }

  getRole(): string | null {
    return localStorage.getItem('role');
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  getCurrentUser(): Observable<any> {
    return this.http.get<any>(`${this.API_URL}/me`)
      .pipe(
        tap(user => {
          this.serverUserLoaded = true;
          this.currentUserSubject.next(user);
          localStorage.setItem('currentUser', JSON.stringify(user));
        }),
        catchError(this.handleError)
      );
  }

  getCurrentUserValue(): any {
    return this.currentUserSubject.value;
  }

  refreshToken(): Observable<RefreshResponse> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      throw new Error('No refresh token available');
    }
    return this.http.post<RefreshResponse>(`${this.API_URL}/refresh`, { refreshToken });
  }

  private handleError(error: HttpErrorResponse) {
    let errorMessage = 'Error al iniciar sesión';
    
    if (error.status === 0 || error.statusText === 'Unknown Error') {
      errorMessage = 'Error: no se pudo conectar al servidor';
    } else if (error.status === 423) {
      const errorObj = error.error;
      if (errorObj && typeof errorObj === 'object' && errorObj.error) {
        errorMessage = errorObj.error;
      }
    } else if (error.status === 401) {
      const errorObj = error.error;
      if (errorObj && typeof errorObj === 'object' && errorObj.error) {
        errorMessage = errorObj.error;
      } else if (typeof errorObj === 'string') {
        try {
          const parsed = JSON.parse(errorObj);
          errorMessage = parsed.error || errorMessage;
        } catch {
          errorMessage = errorObj || errorMessage;
        }
      }
    } else if (error.error instanceof ErrorEvent) {
      errorMessage = error.error.message;
    } else {
      errorMessage = error.error?.message || error.message || errorMessage;
    }
    console.error('Auth Error:', errorMessage);
    return throwError(() => new Error(errorMessage));
  }
}

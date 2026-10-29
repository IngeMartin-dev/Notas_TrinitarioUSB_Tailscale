import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError, BehaviorSubject } from 'rxjs';
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
  private currentUserSubject = new BehaviorSubject<any>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {}

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
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('role');
    localStorage.removeItem('currentUser');
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

import { Component, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { forkJoin, Observable } from 'rxjs';

import { AuthService } from '../../services/auth.service';
import { TermsContentComponent } from '../terms-content/terms-content';
import { PrivacyContentComponent } from '../privacy-content/privacy-content';

/**
 * ConsentGate — bloqueo obligatorio de consentimiento legal.
 *
 * Antes de este cambio, los modales de Términos/Privacidad solo existían
 * dentro de Configuración: un usuario podía usar toda la aplicación (ver
 * notas, boletines, datos de otros) sin haber aceptado nunca nada, porque
 * nada lo obligaba a visitar esa pantalla. El backend sí tenía un endpoint
 * para registrar la aceptación (`/api/auth/accept-legal`), pero nunca se
 * llamaba de forma obligatoria.
 *
 * Este componente se monta una sola vez en la raíz de la aplicación
 * (app.html) y se muestra a sí mismo SOLO cuando hay un usuario
 * autenticado que todavía no tiene registrada la aceptación de términos
 * y/o privacidad (según lo que devuelve /api/auth/me). Mientras está
 * visible, no se puede cerrar haciendo clic afuera ni con Escape: la
 * única forma de continuar es aceptar, o cerrar sesión.
 */
@Component({
  selector: 'app-consent-gate',
  standalone: true,
  imports: [TermsContentComponent, PrivacyContentComponent],
  templateUrl: './consent-gate.html',
  styleUrl: './consent-gate.css',
})
export class ConsentGate {
  private authService = inject(AuthService);
  private http = inject(HttpClient);

  private currentUser = signal<any>(null);
  activeTab = signal<'terms' | 'privacy'>('terms');
  acceptedTerms = signal(false);
  acceptedPrivacy = signal(false);
  submitting = signal(false);
  errorMessage = signal<string | null>(null);

  constructor() {
    this.authService.currentUser$.subscribe(user => this.currentUser.set(user));
  }

  /** Solo se muestra si hay sesión y falta aceptar algo. */
  pending = computed(() => {
    const user = this.currentUser();
    if (!user) return false;
    return !user.termsAcceptedAt || !user.privacyAcceptedAt;
  });

  needsTerms = computed(() => !this.currentUser()?.termsAcceptedAt);
  needsPrivacy = computed(() => !this.currentUser()?.privacyAcceptedAt);

  canContinue = computed(() => {
    const termsOk = !this.needsTerms() || this.acceptedTerms();
    const privacyOk = !this.needsPrivacy() || this.acceptedPrivacy();
    return termsOk && privacyOk;
  });

  confirmAcceptance() {
    if (!this.canContinue() || this.submitting()) return;
    this.submitting.set(true);
    this.errorMessage.set(null);

    const base = this.authService.API_BASE_URL;
    const calls: Record<string, Observable<any>> = {};
    if (this.needsTerms()) calls['terms'] = this.http.post<any>(`${base}/auth/accept-legal`, { type: 'terms' });
    if (this.needsPrivacy()) calls['privacy'] = this.http.post<any>(`${base}/auth/accept-legal`, { type: 'privacy' });

    forkJoin(calls).subscribe({
      next: () => {
        this.authService.getCurrentUser().subscribe({
          next: () => this.submitting.set(false),
          error: () => {
            this.submitting.set(false);
            this.errorMessage.set('No pudimos confirmar tu aceptación. Intenta de nuevo.');
          }
        });
      },
      error: () => {
        this.submitting.set(false);
        this.errorMessage.set('No pudimos registrar tu aceptación. Revisa tu conexión e intenta de nuevo.');
      }
    });
  }

  logout() {
    this.authService.logout();
    window.location.href = '/login';
  }
}

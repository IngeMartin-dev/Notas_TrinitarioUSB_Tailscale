import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { API_BASE_URL } from '../config/api-base';

/**
 * Estado de la conexión con el servidor.
 *
 * Cuando una petición no llega al servidor (status 0) o el navegador pierde
 * la red, `offline` pasa a true y `app.html` cubre TODA la pantalla con la
 * página "Sin conexión" (sin sidebar). La ruta NO cambia: la URL sigue siendo
 * la misma en la que estaba el usuario. Mientras tanto se sondea /api/health y,
 * apenas responde, se vuelve a cargar la vista actual y se quita la pantalla,
 * así el usuario queda exactamente donde estaba.
 */
@Injectable({ providedIn: 'root' })
export class ConnectionService {
  private router = inject(Router);

  readonly offline = signal(false);
  readonly checking = signal(false);

  private timer: any = null;

  constructor() {
    if (typeof window !== 'undefined') {
      // Se cortó la red del equipo: no hace falta esperar a que falle una petición.
      window.addEventListener('offline', () => this.markOffline());
      // Volvió la red: comprobar el servidor de inmediato.
      window.addEventListener('online', () => this.checkNow());
    }
  }

  /** Llamado por el interceptor HTTP cuando una petición no pudo llegar al servidor. */
  markOffline() {
    if (this.offline()) return;
    this.offline.set(true);
    this.schedule(1500);
  }

  /** Intento inmediato (botón "Reintentar" o evento "online"). */
  checkNow() {
    if (!this.offline() || this.checking()) return;
    if (this.timer) clearTimeout(this.timer);
    void this.check();
  }

  private schedule(ms: number) {
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => void this.check(), ms);
  }

  private async check() {
    if (!this.offline()) return;
    this.checking.set(true);
    const up = await this.serverReachable();
    this.checking.set(false);
    if (up) {
      await this.restore();
    } else {
      this.schedule(3000);
    }
  }

  private async serverReachable(): Promise<boolean> {
    if (typeof navigator !== 'undefined' && navigator.onLine === false) return false;
    const ctrl = new AbortController();
    const to = setTimeout(() => ctrl.abort(), 4000);
    try {
      const res = await fetch(`${API_BASE_URL}/api/health`, { signal: ctrl.signal, cache: 'no-store' });
      // Cualquier respuesta del servidor (incluso 401/404) significa que está arriba.
      return res.status < 500;
    } catch {
      return false;
    } finally {
      clearTimeout(to);
    }
  }

  /**
   * Vuelve a cargar la vista actual sin cambiar la URL (las peticiones que
   * fallaron durante la caída no se completaron) y luego quita la pantalla.
   */
  private async restore() {
    const url = this.router.url;
    try {
      if (!url.startsWith('/not-found')) {
        await this.router.navigateByUrl('/not-found', { skipLocationChange: true });
        await this.router.navigateByUrl(url);
      }
    } catch {
      /* si falla la recarga suave, igual se quita la pantalla */
    }
    this.offline.set(false);
  }
}
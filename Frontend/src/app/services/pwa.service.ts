import { Injectable, inject, signal } from '@angular/core';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter } from 'rxjs/operators';
import { DialogService } from './dialog.service';

/** Evento no estándar de Chromium (Chrome/Edge/Android) que permite instalar la PWA. */
interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

/**
 * PWA: instalación de la app y avisos de versión nueva.
 *
 *  - Chrome/Edge (PC y Android): el navegador avisa con `beforeinstallprompt`;
 *    se guarda y se dispara al pulsar el botón "Instalar app".
 *  - iPhone/iPad (Safari): no existe ese evento; se muestran las instrucciones
 *    (Compartir → Agregar a inicio).
 *  - Versión nueva: el service worker descarga la actualización en segundo
 *    plano y aquí solo se avisa; NO se recarga sola para no perder notas que
 *    alguien esté digitando.
 */
@Injectable({ providedIn: 'root' })
export class PwaService {
  // Opcional: en pruebas unitarias o si algún día se quita provideServiceWorker, no existe.
  private readonly swUpdate = inject(SwUpdate, { optional: true });
  private readonly dialog = inject(DialogService);

  private deferredPrompt: BeforeInstallPromptEvent | null = null;

  /** Ya se está usando la app instalada (ventana propia, sin barra del navegador). */
  readonly isStandalone = signal(this.detectStandalone());
  /** El navegador permite instalar con un clic (Chrome/Edge/Android). */
  readonly canPrompt = signal(false);
  /** iPhone/iPad en Safari: se instala a mano desde el menú Compartir. */
  readonly isIos = this.detectIos();
  /** Hay una versión nueva ya descargada, lista para aplicar. */
  readonly updateReady = signal(false);

  /** Mostrar el botón "Instalar app": solo si aún no está instalada y hay forma de hacerlo. */
  canInstall(): boolean {
    if (this.isStandalone()) return false;
    return this.canPrompt() || this.isIos;
  }

  constructor() {
    if (typeof window === 'undefined') return;

    window.addEventListener('beforeinstallprompt', (event) => {
      event.preventDefault(); // evita la mini-barra automática; se instala con nuestro botón
      this.deferredPrompt = event as BeforeInstallPromptEvent;
      this.canPrompt.set(true);
    });

    window.addEventListener('appinstalled', () => {
      this.deferredPrompt = null;
      this.canPrompt.set(false);
      this.isStandalone.set(true);
    });

    window.matchMedia('(display-mode: standalone)').addEventListener('change', (e) => {
      this.isStandalone.set(e.matches || this.detectStandalone());
    });

    this.watchUpdates();
  }

  /** Abre el cuadro de instalación del navegador (o las instrucciones en iPhone). */
  async install(): Promise<void> {
    if (this.deferredPrompt) {
      const prompt = this.deferredPrompt;
      this.deferredPrompt = null; // el evento solo se puede usar una vez
      this.canPrompt.set(false);
      await prompt.prompt();
      await prompt.userChoice;
      return;
    }
    if (this.isIos) {
      await this.dialog.alert(
        'En Safari toca el botón Compartir (el cuadro con la flecha hacia arriba) y elige "Agregar a pantalla de inicio".',
        'Instalar Notas Trinitario'
      );
    }
  }

  /** Activa la versión nueva y recarga la página. */
  async applyUpdate(): Promise<void> {
    try {
      if (this.swUpdate?.isEnabled) {
        await this.swUpdate.activateUpdate();
      }
    } finally {
      document.location.reload();
    }
  }

  private watchUpdates(): void {
    const swUpdate = this.swUpdate;
    if (!swUpdate?.isEnabled) return; // desarrollo (ng serve) o navegador sin service worker

    swUpdate.versionUpdates
      .pipe(filter((e): e is VersionReadyEvent => e.type === 'VERSION_READY'))
      .subscribe(() => this.updateReady.set(true));

    // Si el service worker queda en un estado irrecuperable (caché corrupta),
    // lo único seguro es recargar para traer todo de nuevo.
    swUpdate.unrecoverable.subscribe(() => document.location.reload());

    // Buscar versión nueva al volver a la pestaña y cada 30 minutos.
    const check = () => swUpdate.checkForUpdate().catch(() => undefined);
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') check();
    });
    setInterval(check, 30 * 60 * 1000);
  }

  private detectStandalone(): boolean {
    if (typeof window === 'undefined') return false;
    return (
      window.matchMedia?.('(display-mode: standalone)').matches ||
      (navigator as any).standalone === true // Safari iOS
    );
  }

  private detectIos(): boolean {
    if (typeof navigator === 'undefined') return false;
    const ua = navigator.userAgent || '';
    const iPadOs = navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1;
    return /iPad|iPhone|iPod/.test(ua) || iPadOs;
  }
}

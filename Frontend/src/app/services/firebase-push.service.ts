import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, EMPTY } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';
import { initializeApp, FirebaseApp } from 'firebase/app';
import { getMessaging, getToken, onMessage, Messaging } from 'firebase/messaging';
import { FIREBASE_CONFIG, FIREBASE_VAPID_KEY, isFirebaseConfigured } from '../firebase-config';

export interface PushNotificationPayload {
  title: string;
  body: string;
  icon?: string;
  data?: any;
}

/**
 * Notificaciones push reales vía Firebase Cloud Messaging.
 *
 * A diferencia de la versión anterior (que generaba un token falso tipo
 * `local_...` y nunca llegaba realmente al backend de Firebase), este
 * servicio registra el dispositivo con un token FCM auténtico, así que las
 * notificaciones sí llegan aunque la pestaña esté cerrada o el celular
 * bloqueado — necesario tanto para el chat como para los avisos de
 * apertura/cierre de período.
 *
 * Requiere que Frontend/src/app/firebase-config.ts tenga la configuración
 * real del proyecto de Firebase del colegio (ver comentarios en ese
 * archivo). Si no está configurado, el resto de la app sigue funcionando
 * con normalidad; simplemente no llegan push en segundo plano.
 */
@Injectable({
  providedIn: 'root'
})
export class FirebasePushService {
  private readonly http = inject(HttpClient);
  private readonly API_BASE = 'http://localhost:8080/api/notifications';

  private app: FirebaseApp | null = null;
  private messaging: Messaging | null = null;
  private initialized = false;

  constructor() {
    this.initializeService();
  }

  private initializeService(): void {
    if (typeof window === 'undefined') {
      this.initialized = true;
      return;
    }
    if (!isFirebaseConfigured()) {
      console.warn(
        '[FirebasePushService] Firebase no está configurado (Frontend/src/app/firebase-config.ts). ' +
        'Las notificaciones push en segundo plano no funcionarán hasta configurarlo.'
      );
      this.initialized = true;
      return;
    }

    try {
      this.app = initializeApp(FIREBASE_CONFIG);
      this.messaging = getMessaging(this.app);
      this.initialized = true;

      // Notificación mientras la app está ABIERTA en primer plano.
      onMessage(this.messaging, (payload) => {
        const title = payload.notification?.title || 'Notas Trinitario';
        const body = payload.notification?.body || '';
        this.showPushNotification(title, body, payload.data);
      });
    } catch (error) {
      console.error('[FirebasePushService] Error inicializando Firebase:', error);
      this.initialized = true;
    }
  }

  /**
   * Registra el service worker de Firebase pasándole la configuración por
   * query string (así solo hace falta mantener las llaves en un lugar:
   * firebase-config.ts).
   */
  private async registerServiceWorker(): Promise<ServiceWorkerRegistration | null> {
    if (!('serviceWorker' in navigator)) return null;
    const params = new URLSearchParams({
      apiKey: FIREBASE_CONFIG.apiKey,
      authDomain: FIREBASE_CONFIG.authDomain,
      projectId: FIREBASE_CONFIG.projectId,
      storageBucket: FIREBASE_CONFIG.storageBucket,
      messagingSenderId: FIREBASE_CONFIG.messagingSenderId,
      appId: FIREBASE_CONFIG.appId
    });
    return navigator.serviceWorker.register(`/firebase-messaging-sw.js?${params.toString()}`);
  }

  /**
   * Solicita permiso de notificaciones, registra el token FCM real y lo
   * guarda en el backend para que FcmPushService.java pueda usarlo.
   */
  async requestPermissionAndGetToken(userId: number): Promise<string | null> {
    if (!('Notification' in window)) {
      console.warn('Este navegador no soporta notificaciones push');
      return null;
    }
    if (!isFirebaseConfigured() || !this.messaging) {
      console.warn('[FirebasePushService] Firebase no configurado: no se puede registrar el token.');
      return null;
    }

    try {
      const permission = await Notification.requestPermission();
      if (permission !== 'granted') {
        console.log('Permiso de notificaciones denegado');
        return null;
      }

      const registration = await this.registerServiceWorker();
      const token = await getToken(this.messaging, {
        vapidKey: FIREBASE_VAPID_KEY,
        serviceWorkerRegistration: registration ?? undefined
      });

      if (!token) {
        console.warn('No se pudo obtener el token FCM');
        return null;
      }

      await this.saveTokenToServer(userId, token, 'web-push');
      return token;
    } catch (error) {
      console.error('Error solicitando permiso/token de notificaciones:', error);
      return null;
    }
  }

  private async saveTokenToServer(userId: number, token: string, deviceType: string = 'web'): Promise<void> {
    try {
      const deviceInfo = this.getDeviceInfo();
      await this.http.post(`${this.API_BASE}/fcm-token`, {
        userId,
        token,
        deviceType: deviceType || deviceInfo.deviceType,
        deviceName: deviceInfo.deviceName
      }).toPromise();
    } catch (error) {
      console.error('Error guardando token en el servidor:', error);
    }
  }

  private getDeviceInfo(): { deviceType: string; deviceName: string } {
    const userAgent = navigator.userAgent || '';
    let deviceType = 'web';
    let deviceName = 'Web Browser';

    if (/mobile/i.test(userAgent)) deviceType = 'mobile';
    if (/iPad|iPhone|iPod/.test(userAgent)) {
      deviceType = 'ios';
      deviceName = 'iOS Device';
    } else if (/Android/.test(userAgent)) {
      deviceType = 'android';
      deviceName = 'Android Device';
    } else if (/Windows/.test(userAgent)) {
      deviceName = 'Windows PC';
    } else if (/Mac/.test(userAgent)) {
      deviceName = 'Mac';
    } else if (/Linux/.test(userAgent)) {
      deviceName = 'Linux PC';
    }

    return { deviceType, deviceName };
  }

  /** Notificación nativa del navegador, usada cuando la app está en primer plano. */
  showPushNotification(title: string, body: string, data?: any): void {
    if (Notification.permission !== 'granted') return;

    const notification = new Notification(title, {
      body,
      icon: '/Logo Colegio.png',
      tag: 'notas-trinitario-push',
      requireInteraction: false,
      data
    });

    notification.onclick = (event) => {
      event.preventDefault();
      window.focus();
      notification.close();
    };

    setTimeout(() => notification.close(), 8000);
  }

  removeTokenFromServer(userId: number): Observable<any> {
    return this.http.delete(`${this.API_BASE}/fcm-token/user/${userId}`).pipe(
      tap(() => console.log('Tokens eliminados del servidor')),
      catchError(error => {
        console.error('Error eliminando tokens:', error);
        return EMPTY;
      })
    );
  }

  isInitialized(): boolean {
    return this.initialized;
  }

  hasNotificationPermission(): boolean {
    return Notification.permission === 'granted';
  }

  getPermissionStatus(): NotificationPermission | 'unsupported' {
    if (!('Notification' in window)) return 'unsupported';
    return Notification.permission;
  }

  isFirebaseReady(): boolean {
    return isFirebaseConfigured();
  }
}
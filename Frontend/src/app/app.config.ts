import { ApplicationConfig, isDevMode, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideServiceWorker } from '@angular/service-worker';

import { routes } from './app.routes';
import { authInterceptor } from './auth-interceptor';
import { retryBackoffInterceptor } from './retry-backoff.interceptor';
import { apiRewriteInterceptor } from './api-rewrite.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    // Orden importa: apiRewriteInterceptor va PRIMERO para que las
    // peticiones ya salgan con la URL correcta (localhost o devtunnel)
    // antes de que retryBackoffInterceptor/authInterceptor las procesen.
    provideHttpClient(withInterceptors([apiRewriteInterceptor, retryBackoffInterceptor, authInterceptor])),
    // PWA: service worker de Angular (solo en la compilación de producción).
    // Cachea la app (HTML/JS/CSS/íconos), NUNCA las llamadas a /api. Las
    // notificaciones push van por otro service worker (firebase-messaging-sw.js)
    // registrado en su propio alcance para que no se pisen entre sí.
    // 'registerWhenStable:30000' = se registra cuando la app se estabiliza o,
    // como tope, a los 30 s (la app sondea el servidor y quizá nunca "descansa").
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000'
    })
  ]
};
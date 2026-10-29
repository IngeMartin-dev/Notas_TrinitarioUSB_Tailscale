import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

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
    provideHttpClient(withInterceptors([apiRewriteInterceptor, retryBackoffInterceptor, authInterceptor]))
  ]
};
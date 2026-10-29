import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { throwError, timer } from 'rxjs';
import { mergeMap, retryWhen } from 'rxjs/operators';

/**
 * Interceptor de "backoff + jitter" (anti-DDoS inversivo).
 *
 * Cuando una petición falla por sobrecarga del servidor (429 Too Many
 * Requests, 502/503/504 de un proxy/gateway caído momentáneamente) o por un
 * corte de red puntual, en vez de reintentar de inmediato — lo que
 * amplificaría el problema si muchos clientes reintentan a la vez, un patrón
 * parecido a un DDoS accidental — se espera un tiempo que crece
 * exponencialmente en cada intento y se le suma un "jitter" aleatorio para
 * que los reintentos de distintos usuarios no queden sincronizados.
 *
 *   intento 1 → ~500ms  (400-600ms)
 *   intento 2 → ~1000ms (800-1200ms)
 *   intento 3 → ~2000ms (1600-2400ms)
 *   intento 4 → ~4000ms (3200-4800ms)  (máximo 4 reintentos)
 *
 * No reintenta peticiones que no tiene sentido reintentar: errores de
 * autenticación (401/403), errores de validación (400/404/409/422), ni
 * peticiones que no sean idempotentes salvo que el propio backend indique
 * que es seguro reintentarlas.
 */

const MAX_RETRIES = 4;
const BASE_DELAY_MS = 500;
const MAX_DELAY_MS = 8000;

// Códigos de estado ante los que SÍ tiene sentido reintentar: el servidor
// está sobrecargado / temporalmente caído, o hubo un corte de red (status 0).
const RETRYABLE_STATUS = new Set([0, 408, 429, 502, 503, 504]);

// Solo se reintentan automáticamente los métodos idempotentes. Un POST/PATCH
// que crea o modifica datos no se reintenta solo para evitar duplicar
// información si la petición sí llegó a procesarse en el servidor.
const IDEMPOTENT_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'PUT', 'DELETE']);

function calcularEsperaConJitter(intento: number): number {
  const exponencial = Math.min(BASE_DELAY_MS * Math.pow(2, intento - 1), MAX_DELAY_MS);
  // Jitter "full-ish": entre el 80% y el 120% del valor exponencial, para
  // desincronizar reintentos concurrentes de distintos clientes.
  const jitter = exponencial * (0.8 + Math.random() * 0.4);
  return Math.round(jitter);
}

export const retryBackoffInterceptor: HttpInterceptorFn = (req, next) => {
  // Las rutas de streaming/descarga de archivos grandes (PDFs) y de
  // notificaciones en tiempo real (websocket/SSE) quedan fuera del retry
  // automático para no duplicar descargas ni golpear el socket.
  const excluida = req.url.includes('/api/notifications/stream')
    || req.url.includes('/ws')
    || req.url.includes('/sse');

  if (excluida || !IDEMPOTENT_METHODS.has(req.method)) {
    return next(req);
  }

  return next(req).pipe(
    retryWhen(errors =>
      errors.pipe(
        mergeMap((error: HttpErrorResponse, index) => {
          const intento = index + 1;
          const esReintentable = error instanceof HttpErrorResponse
            && RETRYABLE_STATUS.has(error.status);

          if (!esReintentable || intento > MAX_RETRIES) {
            return throwError(() => error);
          }

          const espera = calcularEsperaConJitter(intento);
          return timer(espera);
        })
      )
    )
  );
};
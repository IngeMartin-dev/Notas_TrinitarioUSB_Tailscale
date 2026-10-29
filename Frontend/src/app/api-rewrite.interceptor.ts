import { HttpInterceptorFn } from '@angular/common/http';
import { API_BASE_URL, LOCALHOST_API_PREFIX } from './config/api-base';

/**
 * Todo el código sigue armando las URLs de la API con el prefijo fijo
 * "http://localhost:8080" (no se tocó ningún servicio/componente). Este
 * interceptor es el único lugar que sabe que, si la app se está sirviendo
 * desde un DevTunnel (o cualquier host que no sea localhost), ese prefijo
 * hay que cambiarlo por la URL real del backend — ver `config/api-base.ts`
 * para el detalle de cómo se calcula.
 *
 * En localhost, API_BASE_URL === LOCALHOST_API_PREFIX, así que el
 * `replace` no cambia nada y las peticiones siguen exactamente igual que
 * antes.
 */
export const apiRewriteInterceptor: HttpInterceptorFn = (req, next) => {
  if (API_BASE_URL === LOCALHOST_API_PREFIX || !req.url.startsWith(LOCALHOST_API_PREFIX)) {
    return next(req);
  }

  const nuevaUrl = API_BASE_URL + req.url.slice(LOCALHOST_API_PREFIX.length);
  return next(req.clone({ url: nuevaUrl }));
};
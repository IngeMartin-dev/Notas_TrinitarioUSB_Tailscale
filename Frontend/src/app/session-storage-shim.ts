/**
 * Sesión independiente por pestaña (como Edunova / WhatsApp Web con varias cuentas).
 *
 * Antes la sesión (token, refreshToken, role, currentUser, userName) vivía en
 * localStorage, que se comparte entre TODAS las pestañas: al abrir otra cuenta en
 * una pestaña nueva y recargar la primera, esta pasaba a mostrar la cuenta que se
 * abrió de último.
 *
 * Ahora esas claves se guardan en sessionStorage, que es propio de cada pestaña y
 * sobrevive a las recargas (F5). Así cada pestaña conserva su cuenta hasta que el
 * usuario pulse "Cerrar sesión" en ESA pestaña.
 *
 * Para que seguir iniciado no se pierda al cerrar la pestaña o el navegador, cada
 * vez que se escribe la sesión también se deja una copia en localStorage ("última
 * sesión"): una pestaña NUEVA parte de esa copia y desde ahí vive aparte.
 *
 * El resto del código sigue usando localStorage.getItem/setItem/removeItem sin
 * cambios; este módulo debe importarse ANTES que cualquier otro en main.ts.
 */
const SESSION_KEYS = new Set(['token', 'refreshToken', 'role', 'currentUser', 'userName']);
const OWNER_KEY = '__sessionOwner'; // pestaña que escribió por última vez la "última sesión"
const TAB_ID_KEY = '__tabId';

(function installSessionShim(): void {
  try {
    if (typeof window === 'undefined' || !window.localStorage || !window.sessionStorage) return;

    const local = window.localStorage;
    const session = window.sessionStorage;
    const proto = Storage.prototype;
    const rawGet = proto.getItem;
    const rawSet = proto.setItem;
    const rawRemove = proto.removeItem;

    // Identificador de esta pestaña (sessionStorage es por pestaña).
    let tabId = rawGet.call(session, TAB_ID_KEY);
    if (!tabId) {
      tabId = Math.random().toString(36).slice(2) + Date.now().toString(36);
      rawSet.call(session, TAB_ID_KEY, tabId);
    }

    // Pestaña nueva (sin sesión propia): hereda la última sesión guardada.
    if (!rawGet.call(session, 'token') && rawGet.call(local, 'token')) {
      SESSION_KEYS.forEach((k) => {
        const v = rawGet.call(local, k);
        if (v !== null) rawSet.call(session, k, v);
      });
    }

    proto.getItem = function (this: Storage, key: string): string | null {
      if (this === local && SESSION_KEYS.has(key)) return rawGet.call(session, key);
      return rawGet.call(this, key);
    };

    proto.setItem = function (this: Storage, key: string, value: string): void {
      if (this === local && SESSION_KEYS.has(key)) {
        rawSet.call(session, key, value);
        rawSet.call(local, key, value); // copia de "última sesión" para pestañas nuevas
        rawSet.call(local, OWNER_KEY, tabId as string);
        return;
      }
      rawSet.call(this, key, value);
    };

    proto.removeItem = function (this: Storage, key: string): void {
      if (this === local && SESSION_KEYS.has(key)) {
        rawRemove.call(session, key);
        // Solo se borra la copia compartida si esta pestaña fue la última en escribirla:
        // cerrar sesión aquí no debe tocar la cuenta de otra pestaña.
        const owner = rawGet.call(local, OWNER_KEY);
        if (!owner || owner === tabId) rawRemove.call(local, key);
        return;
      }
      rawRemove.call(this, key);
    };
  } catch (e) {
    console.warn('No se pudo activar la sesión por pestaña', e);
  }
})();

export {};

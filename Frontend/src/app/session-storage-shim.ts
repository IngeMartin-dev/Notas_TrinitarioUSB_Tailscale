/**
 * Sesión independiente por pestaña (mismo esquema que Edu_Nova / tabSession.js).
 *
 *  - Cada pestaña guarda SU sesión (token, refreshToken, role, currentUser, userName) en
 *    sessionStorage: es propio de la pestaña y sobrevive a F5. Así se puede tener un administrador
 *    en una pestaña y un padre en otra sin que se pisen al recargar o al cambiar de pantalla.
 *  - localStorage guarda solo la "última sesión usada": una pestaña NUEVA arranca con ella para no
 *    tener que iniciar sesión cada vez, y desde ahí vive aparte.
 *  - Una pestaña solo actualiza o borra esa "última sesión" si es la misma cuenta que está guardada
 *    allí (se compara por refreshToken). Cerrar sesión en una pestaña no toca la cuenta de otra.
 *
 * El resto del código sigue usando localStorage.getItem/setItem/removeItem tal cual: este módulo
 * debe importarse ANTES que cualquier otro en main.ts.
 */
const SESSION_KEYS = new Set(['token', 'refreshToken', 'role', 'currentUser', 'userName']);

const hasStorage = typeof window !== 'undefined' && typeof Storage !== 'undefined';
const proto: Storage | null = hasStorage ? Storage.prototype : null;
// Métodos originales, guardados ANTES de parchear, para poder leer/escribir cada almacén de verdad.
const rawGet = proto ? proto.getItem : null;
const rawSet = proto ? proto.setItem : null;
const rawRemove = proto ? proto.removeItem : null;

const safe = <T>(fn: () => T, fallback: T): T => { try { return fn(); } catch { return fallback; } };
const S = (): Storage | null => safe(() => window.sessionStorage, null);
const L = (): Storage | null => safe(() => window.localStorage, null);
const rg = (st: Storage | null, k: string): string | null =>
  st && rawGet ? safe(() => rawGet.call(st, k), null) : null;
const rs = (st: Storage | null, k: string, v: string): void => {
  if (st && rawSet) safe(() => rawSet.call(st, k, v), undefined);
};
const rd = (st: Storage | null, k: string): void => {
  if (st && rawRemove) safe(() => rawRemove.call(st, k), undefined);
};

/** ¿Esta pestaña usa la misma sesión que quedó guardada como "última"? */
const isLastSession = (): boolean => {
  const mine = rg(S(), 'refreshToken');
  return !!mine && mine === rg(L(), 'refreshToken');
};

/** Marca la sesión de esta pestaña como la última usada (se llama al iniciar sesión). */
export function publishLastSession(): void {
  SESSION_KEYS.forEach((k) => {
    const v = rg(S(), k);
    if (v !== null) rs(L(), k, v);
    else rd(L(), k); // que no quede el dato de la cuenta anterior
  });
}

/**
 * Si otra pestaña con la MISMA cuenta renovó la sesión (el servidor invalida el refreshToken
 * anterior al renovar), toma los datos vigentes guardados como "última sesión" y los adopta en
 * esta pestaña. Devuelve el nuevo token, o null si no hay nada que adoptar.
 */
export function adoptAlternateSession(): { token: string; refreshToken: string } | null {
  const lr = rg(L(), 'refreshToken');
  const lt = rg(L(), 'token');
  if (!lr || !lt || lr === rg(S(), 'refreshToken')) return null;
  try {
    const a = JSON.parse(rg(L(), 'currentUser') || 'null');
    const b = JSON.parse(rg(S(), 'currentUser') || 'null');
    if (!a || !b || a.id !== b.id) return null;
  } catch {
    return null;
  }
  rs(S(), 'token', lt);
  rs(S(), 'refreshToken', lr);
  return { token: lt, refreshToken: lr };
}

(function installSessionShim(): void {
  try {
    if (!proto || !rawGet || !rawSet || !rawRemove || !S() || !L()) return;
    const local = L();

    // Pestaña nueva (sin sesión propia): hereda la última sesión usada.
    if (!rg(S(), 'token') && rg(local, 'token')) {
      SESSION_KEYS.forEach((k) => {
        const v = rg(local, k);
        if (v !== null) rs(S(), k, v);
      });
    }

    // El cierre de sesión borra varias claves seguidas: se decide UNA vez (al empezar) si hay que
    // borrar también la copia compartida, para que no queden restos de role/currentUser.
    let removeBurst: boolean | null = null;

    proto.getItem = function (this: Storage, key: string): string | null {
      if (this === local && SESSION_KEYS.has(key)) return rg(S(), key);
      return rawGet.call(this, key);
    };

    proto.setItem = function (this: Storage, key: string, value: string): void {
      if (this === local && SESSION_KEYS.has(key)) {
        const mirror = isLastSession(); // se calcula antes de cambiar nada
        const freshLogin = key === 'refreshToken' && !rg(S(), 'refreshToken');
        rs(S(), key, value);
        if (mirror) rs(local, key, value);
        else if (freshLogin) publishLastSession();
        return;
      }
      rawSet.call(this, key, value);
    };

    proto.removeItem = function (this: Storage, key: string): void {
      if (this === local && SESSION_KEYS.has(key)) {
        if (removeBurst === null) {
          removeBurst = isLastSession();
          setTimeout(() => { removeBurst = null; }, 0);
        }
        rd(S(), key);
        if (removeBurst) rd(local, key);
        return;
      }
      rawRemove.call(this, key);
    };
  } catch (e) {
    console.warn('No se pudo activar la sesión por pestaña', e);
  }
})();

/**
 * Registro de diagnóstico de la sesión (últimos eventos). Sirve para ver por qué una sesión se
 * cerró o no se recuperó. Se guarda en localStorage ('authDebugLog'); no contiene tokens ni claves.
 */
const KEY = 'authDebugLog';
const MAX = 60;

function now(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getDate())}/${p(d.getMonth() + 1)} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

export function logAuth(event: string, detail?: unknown): void {
  try {
    const raw = window.localStorage.getItem(KEY);
    const arr: string[] = raw ? JSON.parse(raw) : [];
    let d = '';
    if (detail !== undefined) d = ' ' + (typeof detail === 'string' ? detail : JSON.stringify(detail));
    arr.push(`${now()} ${event}${d}`.slice(0, 400));
    window.localStorage.setItem(KEY, JSON.stringify(arr.slice(-MAX)));
  } catch { /* sin almacenamiento: no importa */ }
}

export function readAuthLog(): string {
  try {
    const raw = window.localStorage.getItem(KEY);
    const arr: string[] = raw ? JSON.parse(raw) : [];
    return arr.join('\n');
  } catch { return ''; }
}

export function callerStack(skip = 2): string {
  return (new Error().stack || '').split('\n').slice(skip, skip + 5).map(s => s.trim().replace(/https?:\/\/[^/]+/, '')).join(' | ');
}

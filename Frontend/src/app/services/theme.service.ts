import { Injectable, signal, effect } from '@angular/core';

/**
 * Servicio único de tema (claro/oscuro) para toda la app.
 * Mantiene el estado en una signal y lo persiste en localStorage.
 * Aplica el atributo data-theme tanto a <html> como a <body> para
 * máxima compatibilidad con los selectores CSS.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  isDark = signal(false);

  private readonly storageKey = 'theme';

  constructor() {
    this.isDark.set(this.readInitial());

    // Efecto reactivo: cada vez que cambia isDark, aplicamos y persistimos.
    effect(() => {
      const dark = this.isDark();
      this.apply(dark);
      localStorage.setItem(this.storageKey, dark ? 'dark' : 'light');
    });
  }

  toggle() {
    this.isDark.update(v => !v);
  }

  setDark(dark: boolean) {
    this.isDark.set(dark);
  }

  private readInitial(): boolean {
    const saved = localStorage.getItem(this.storageKey);
    if (saved) {
      return saved === 'dark';
    }
    // Si no hay preferencia guardada, respetamos la del sistema.
    try {
      return !!window.matchMedia('(prefers-color-scheme: dark)').matches;
    } catch {
      return false;
    }
  }

  private apply(dark: boolean) {
    const root = document.documentElement;
    const body = document.body;
    if (dark) {
      root.setAttribute('data-theme', 'dark');
      body.setAttribute('data-theme', 'dark');
    } else {
      root.removeAttribute('data-theme');
      body.removeAttribute('data-theme');
    }
  }
}

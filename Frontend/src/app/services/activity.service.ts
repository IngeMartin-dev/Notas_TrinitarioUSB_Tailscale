import { Injectable } from '@angular/core';

export interface Activity {
  icon: string;
  description: string;
  timestamp: number; // Unix timestamp en ms
}

/**
 * "Actividad reciente" del Panel. Se guarda en localStorage para que se pueda
 * registrar desde cualquier pantalla (Estudiantes, asistente de año, etc.) y
 * el Panel la muestre al abrirse.
 */
@Injectable({ providedIn: 'root' })
export class ActivityService {
  static readonly KEY = 'recent_activities';
  static readonly MAX = 5;

  load(): Activity[] {
    try {
      const saved = localStorage.getItem(ActivityService.KEY);
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  }

  add(icon: string, description: string): Activity[] {
    const list = [{ icon, description, timestamp: Date.now() }, ...this.load()].slice(0, ActivityService.MAX);
    try { localStorage.setItem(ActivityService.KEY, JSON.stringify(list)); } catch { /* sin almacenamiento */ }
    return list;
  }
}

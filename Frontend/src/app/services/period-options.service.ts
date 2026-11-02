import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { GlobalRealtimeService } from './global-realtime.service';

/**
 * Lista de números de período para los selectores (1 .. último período que
 * exista en Gestión de Períodos). Se actualiza sola cuando se agrega o se
 * quita un período.
 */
@Injectable({ providedIn: 'root' })
export class PeriodOptionsService {
  private http = inject(HttpClient);
  private realtime = inject(GlobalRealtimeService);

  private subject = new BehaviorSubject<number[]>([1, 2, 3, 4]);
  readonly periods$: Observable<number[]> = this.subject.asObservable();

  constructor() {
    this.refresh();
    this.realtime.periods$.subscribe(list => this.apply(list));
  }

  get current(): number[] {
    return this.subject.value;
  }

  refresh() {
    this.http.get<{ periodNumber: number }[]>('http://localhost:8080/api/periods').subscribe({
      next: list => this.apply(list),
      error: () => { /* se conserva la lista actual */ }
    });
  }

  private apply(list: { periodNumber: number }[]) {
    const nums = (list || []).map(p => p.periodNumber).filter(n => n >= 1);
    if (nums.length === 0) return;
    const last = Math.max(...nums);
    const range = Array.from({ length: last }, (_, i) => i + 1);
    const prev = this.subject.value;
    if (range.length !== prev.length || range.some((n, i) => n !== prev[i])) {
      this.subject.next(range);
    }
  }
}

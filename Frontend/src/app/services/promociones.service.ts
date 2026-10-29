import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface PromocionDto {
  id: number;
  name: string;
  surname: string;
  documentNumber: string | null;
  grade: string;
  classGroup: string | null;
  academicYear: number;
  promotedAt: string | null;
  expiresAt: string | null;
  daysLeft: number | null;
  /** Períodos (1..4) que tienen boletín guardado. */
  periodos: number[];
}

@Injectable({ providedIn: 'root' })
export class PromocionesService {
  private readonly http = inject(HttpClient);
  private readonly API_BASE = 'http://localhost:8080/api/promociones';

  listar(): Observable<PromocionDto[]> {
    return this.http.get<PromocionDto[]>(this.API_BASE);
  }

  /** PDF vía HttpClient para que el interceptor adjunte el token (igual que Boletines Generados). */
  boletin(id: number, periodo: number): Observable<Blob> {
    return this.http.get(`${this.API_BASE}/${id}/boletines/${periodo}`, { responseType: 'blob' });
  }
}
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface BoletinGeneradoDto {
  fileName: string;
  period: number;
  grade: string;
  classroom: string;
  sizeBytes: number;
  generatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class BoletinesGeneradosService {
  private readonly http = inject(HttpClient);
  private readonly API_BASE = 'http://localhost:8080/api/boletines';

  listar(grade: string, classroom: string, period?: number): Observable<BoletinGeneradoDto[]> {
    const params: any = { grade, classroom };
    if (period) params.period = String(period);
    return this.http.get<BoletinGeneradoDto[]>(`${this.API_BASE}/generados`, { params });
  }

  /**
   * Descarga el PDF vía HttpClient (no window.open directo): así el
   * interceptor de Angular sí adjunta el token de autenticación. Abrir la
   * URL cruda en una pestaña nueva navega sin el header Authorization y el
   * backend responde 403 (Whitelabel Error Page), que era el bug.
   */
  descargarPdf(b: BoletinGeneradoDto): Observable<Blob> {
    const params = new URLSearchParams({
      period: String(b.period),
      grade: b.grade,
      classroom: b.classroom,
      fileName: b.fileName
    });
    return this.http.get(`${this.API_BASE}/generados/descargar?${params.toString()}`, { responseType: 'blob' });
  }
}
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Un consolidado ya generado y guardado en disco ("Consolidados
 * Generados/Periodo N/{grado}{salón}/archivo.pdf"). `salonCarpeta` viene
 * tal cual el nombre de la carpeta en disco (ej. "7A": grado 7, salón A).
 */
export interface ConsolidadoGeneradoDto {
  fileName: string;
  period: number;
  salonCarpeta: string;
  sizeBytes: number;
  generatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class ConsolidadosGeneradosService {
  private readonly http = inject(HttpClient);
  private readonly API_BASE = 'http://localhost:8080/api/consolidado';

  /** Lista todos los consolidados guardados, opcionalmente filtrando por período. */
  listar(period?: number): Observable<ConsolidadoGeneradoDto[]> {
    const params: any = {};
    if (period) params.period = String(period);
    return this.http.get<ConsolidadoGeneradoDto[]>(`${this.API_BASE}/generados`, { params });
  }

  /**
   * Descarga el PDF vía HttpClient (no window.open directo): así el
   * interceptor de Angular adjunta el token de autenticación, igual que en
   * BoletinesGeneradosService.
   */
  descargarPdf(c: ConsolidadoGeneradoDto): Observable<Blob> {
    const params = new URLSearchParams({
      period: String(c.period),
      salonCarpeta: c.salonCarpeta,
      fileName: c.fileName
    });
    return this.http.get(`${this.API_BASE}/generados/descargar?${params.toString()}`, { responseType: 'blob' });
  }
}

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface MiBoletinDto {
  studentId: number;
  studentName: string;
  grade: string;
  classroom: string;
  period: number;
  fileName: string;
  sizeBytes: number;
  generatedAt: string;
}

/**
 * Servicio para la vista de solo lectura de boletines de una cuenta de
 * PADRE DE FAMILIA. Solo devuelve boletines de estudiantes enlazados a
 * ese padre y que estén ACTIVOS (si el estudiante se desactiva, el
 * backend deja de incluirlo aquí hasta que se reactive).
 */
@Injectable({ providedIn: 'root' })
export class MisBoletinesService {
  private readonly http = inject(HttpClient);
  private readonly API_BASE = 'http://localhost:8080/api/boletines';

  listar(): Observable<MiBoletinDto[]> {
    return this.http.get<MiBoletinDto[]>(`${this.API_BASE}/mis-boletines`);
  }

  descargarPdf(b: MiBoletinDto): Observable<Blob> {
    const params = new URLSearchParams({ period: String(b.period) });
    return this.http.get(`${this.API_BASE}/mis-boletines/${b.studentId}/descargar?${params.toString()}`, {
      responseType: 'blob'
    });
  }
}
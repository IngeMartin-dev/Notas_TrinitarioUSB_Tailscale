import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface MiNotaItem {
  gradeName: string;
  gradeValue: number | null;
  appreciative: string | null;
  isEvaluation: boolean;
}

export interface MiMateria {
  subjectName: string;
  items: MiNotaItem[];
  finalGrade: number | null;
}

export interface MiPeriodo {
  period: number;
  subjects: MiMateria[];
}

export interface MisNotasHijo {
  studentId: number;
  studentName: string;
  grade: string;
  classroom: string;
  minPassingGrade: number;
  periods: MiPeriodo[];
}

/**
 * Servicio para la vista de solo lectura de notas de una cuenta de PADRE
 * DE FAMILIA. Solo devuelve notas de estudiantes enlazados a ese padre y
 * que estén ACTIVOS. Nunca permite editar/guardar (el backend rechaza
 * cualquier intento de guardar nota que no venga de ADMIN/TEACHER/
 * DIRECTOR_DE_GRUPO).
 */
@Injectable({ providedIn: 'root' })
export class MisNotasService {
  private readonly http = inject(HttpClient);
  private readonly API_BASE = 'http://localhost:8080/api/grades';

  listar(): Observable<MisNotasHijo[]> {
    return this.http.get<MisNotasHijo[]>(`${this.API_BASE}/mis-notas`);
  }
}
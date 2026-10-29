import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type GradeColumnType = 'QUIZ' | 'TALLER' | 'ACTIVIDAD';

export interface GradeColumn {
  id: string;
  name: string;
  type: GradeColumnType;
  /** Porcentaje (1-100) que aporta esta columna a la nota final. */
  pct?: number | null;
  /** Nombre que se muestra (editable). `name` es la clave con la que se guardan las notas y no cambia. */
  label?: string;
  /** 'cat' = nombre con porcentaje; 'col' = columna de la tabla que pertenece a una categoría. */
  kind?: 'cat' | 'col';
  /** Solo en kind 'col': id de la categoría a la que pertenece. */
  cat?: string;
  /** true para ACT1..ACT7 (columnas fijas): solo guardan su porcentaje. */
  base?: boolean;
}

export interface GradeColumnConfigDto {
  exists: boolean;
  id?: number;
  columnsJson: string;
  quizzesPct: number;
  talleresPct: number;
  actividadesPct: number;
}

@Injectable({ providedIn: 'root' })
export class GradeColumnConfigService {
  private readonly http = inject(HttpClient);
  private readonly API_BASE = 'http://localhost:8080/api/grade-columns';

  getConfig(teacherId: number, subjectName: string, grade: string, classroom: string, period: number): Observable<GradeColumnConfigDto> {
    return this.http.get<GradeColumnConfigDto>(this.API_BASE, {
      params: { teacherId: String(teacherId), subjectName, grade, classroom, period: String(period) }
    });
  }

  saveConfig(
    teacherId: number, subjectName: string, grade: string, classroom: string, period: number,
    columns: GradeColumn[], quizzesPct: number, talleresPct: number, actividadesPct: number
  ): Observable<GradeColumnConfigDto> {
    return this.http.post<GradeColumnConfigDto>(this.API_BASE, {
      teacherId, subjectName, grade, classroom, period,
      columnsJson: JSON.stringify(columns),
      quizzesPct, talleresPct, actividadesPct
    });
  }
}
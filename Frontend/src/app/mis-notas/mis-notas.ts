import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription, interval, startWith, switchMap, catchError, of } from 'rxjs';
import { MisNotasService, MisNotasHijo } from '../services/mis-notas.service';

/**
 * Vista de solo lectura para cuentas de PADRE DE FAMILIA: muestra
 * únicamente las notas del/los estudiante(s) enlazados a este padre, y
 * solo mientras el estudiante esté ACTIVO. No permite editar nada — es
 * puramente informativa. Se refresca sola cada 15s para que una nota
 * recién puesta por el profesor aparezca sin recargar la página, y para
 * que si el estudiante se desactiva, sus notas desaparezcan de aquí
 * automáticamente hasta que se reactive.
 */
@Component({
  selector: 'app-mis-notas',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './mis-notas.html',
  styleUrl: './mis-notas.css'
})
export class MisNotas implements OnInit, OnDestroy {
  private service = inject(MisNotasService);

  hijos: MisNotasHijo[] = [];
  isLoading = true;
  errorMessage = '';
  selectedPeriodByStudent: { [studentId: number]: number } = {};

  private pollSub?: Subscription;
  private readonly POLL_MS = 15000;

  ngOnInit(): void {
    this.pollSub = interval(this.POLL_MS)
      .pipe(
        startWith(0),
        switchMap(() => this.service.listar().pipe(
          catchError(() => {
            this.errorMessage = 'No se pudieron cargar tus notas. Verifica tu conexión.';
            return of<MisNotasHijo[]>([]);
          })
        ))
      )
      .subscribe((data) => {
        this.errorMessage = data.length === 0 && this.errorMessage ? this.errorMessage : '';
        this.hijos = data;
        // Si no hay período seleccionado aún para un hijo, selecciona el más reciente disponible.
        for (const h of data) {
          if (!this.selectedPeriodByStudent[h.studentId] && h.periods.length > 0) {
            this.selectedPeriodByStudent[h.studentId] = h.periods[h.periods.length - 1].period;
          }
        }
        this.isLoading = false;
      });
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  seleccionarPeriodo(studentId: number, period: number) {
    this.selectedPeriodByStudent[studentId] = period;
  }

  periodoActivo(hijo: MisNotasHijo): MiPeriodoLocal | undefined {
    const sel = this.selectedPeriodByStudent[hijo.studentId];
    return hijo.periods.find(p => p.period === sel) as MiPeriodoLocal | undefined;
  }

  esAprobatoria(finalGrade: number | null, minPassing: number): boolean {
    if (finalGrade === null || finalGrade === undefined) return true;
    return finalGrade >= minPassing;
  }

  trackByStudent(index: number, h: MisNotasHijo): number {
    return h.studentId;
  }

  trackBySubject(index: number, s: { subjectName: string }): string {
    return s.subjectName;
  }
}

// Alias de tipo local solo para el método periodoActivo (evita importar el tipo completo aquí arriba dos veces).
type MiPeriodoLocal = MisNotasHijo['periods'][number];
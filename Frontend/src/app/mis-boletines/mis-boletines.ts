import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription, interval, startWith, switchMap, catchError, of } from 'rxjs';
import { MisBoletinesService, MiBoletinDto } from '../services/mis-boletines.service';
import { DialogService } from '../services/dialog.service';

interface StudentGroup {
  studentId: number;
  studentName: string;
  grade: string;
  classroom: string;
  boletines: MiBoletinDto[];
}

/**
 * Vista de solo lectura para cuentas de PADRE DE FAMILIA: muestra
 * únicamente los boletines ya generados del/los estudiante(s) enlazados a
 * este padre, y solo mientras el estudiante esté ACTIVO. Se refresca sola
 * cada 15s (sin recargar la página) para que:
 *  - un boletín recién generado aparezca solo,
 *  - si el estudiante se desactiva, sus boletines desaparezcan de aquí
 *    automáticamente hasta que se reactive.
 */
@Component({
  selector: 'app-mis-boletines',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './mis-boletines.html',
  styleUrl: './mis-boletines.css'
})
export class MisBoletines implements OnInit, OnDestroy {
  private service = inject(MisBoletinesService);
  private dialogService = inject(DialogService);

  grupos: StudentGroup[] = [];
  isLoading = true;
  errorMessage = '';
  descargando: { [key: string]: boolean } = {};

  private pollSub?: Subscription;
  private readonly POLL_MS = 15000;

  ngOnInit(): void {
    // startWith(0) para cargar inmediatamente y luego cada POLL_MS.
    this.pollSub = interval(this.POLL_MS)
      .pipe(
        startWith(0),
        switchMap(() => this.service.listar().pipe(
          catchError(() => {
            this.errorMessage = 'No se pudieron cargar tus boletines. Verifica tu conexión.';
            return of<MiBoletinDto[]>([]);
          })
        ))
      )
      .subscribe((data) => {
        this.errorMessage = data.length === 0 && this.errorMessage ? this.errorMessage : '';
        this.grupos = this.agrupar(data);
        this.isLoading = false;
      });
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  private agrupar(data: MiBoletinDto[]): StudentGroup[] {
    const mapa = new Map<number, StudentGroup>();
    for (const b of data) {
      let g = mapa.get(b.studentId);
      if (!g) {
        g = { studentId: b.studentId, studentName: b.studentName, grade: b.grade, classroom: b.classroom, boletines: [] };
        mapa.set(b.studentId, g);
      }
      g.boletines.push(b);
    }
    for (const g of mapa.values()) {
      g.boletines.sort((a, b) => a.period - b.period);
    }
    return Array.from(mapa.values());
  }

  descargar(b: MiBoletinDto) {
    const key = b.studentId + '-' + b.period;
    this.descargando[key] = true;
    this.service.descargarPdf(b).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        this.descargando[key] = false;
      },
      error: () => {
        this.descargando[key] = false;
        this.dialogService.alert('No se pudo descargar el boletín. Verifica que tu sesión siga activa.', 'Error al descargar');
      }
    });
  }

  formatearFecha(iso: string): string {
    try {
      return new Date(iso).toLocaleString('es-CO', {
        day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
      });
    } catch {
      return iso;
    }
  }

  trackByStudent(index: number, g: StudentGroup): number {
    return g.studentId;
  }

  trackByPeriod(index: number, b: MiBoletinDto): string {
    return b.studentId + '-' + b.period;
  }
}
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PeriodOptionsService } from '../services/period-options.service';
import { FormsModule } from '@angular/forms';
import { BoletinesGeneradosService, BoletinGeneradoDto } from '../services/boletines-generados.service';
import { DialogService } from '../services/dialog.service';
import { PdfViewer } from '../pdf-viewer/pdf-viewer';
import { downloadBlob, pdfFileName } from '../pdf-viewer/pdf-download';

interface BoletinCard extends BoletinGeneradoDto {
  displayName: string;
}

@Component({
  selector: 'app-boletines-generados',
  standalone: true,
  imports: [CommonModule, FormsModule, PdfViewer],
  templateUrl: './boletines-generados.html',
  styleUrls: ['./boletines-generados.css']
})
export class BoletinesGenerados implements OnInit {
  private service = inject(BoletinesGeneradosService);
  private dialogService = inject(DialogService);
  private periodOptions = inject(PeriodOptionsService);
  /** 1 .. último período existente (se actualiza al agregar/quitar períodos). */
  get periodos(): number[] { return this.periodOptions.current; }

  grades: string[] = ['Grado 1º', 'Grado 2º', 'Grado 3º', 'Grado 4º', 'Grado 5º', 'Grado 6º',
                       'Grado 7º', 'Grado 8º', 'Grado 9º', 'Grado 10º', 'Grado 11º'];
  classrooms: string[] = ['Salon A', 'Salon B'];

  selectedGrade: string | null = null;
  selectedClassroom: string | null = null;
  selectedPeriod: number | null = null; // null = todos los períodos


  boletines: BoletinCard[] = [];
  isLoading = false;
  errorMessage = '';

  ngOnInit(): void {}

  selectGrade(grade: string, classroom: string) {
    this.selectedGrade = grade;
    this.selectedClassroom = classroom;
    this.cargarBoletines();
  }

  onPeriodFilterChange() {
    this.cargarBoletines();
  }

  private numeroGrado(grade: string): string {
    const m = grade.match(/\d+/);
    return m ? m[0] : grade;
  }

  private letraSalon(classroom: string): string {
    const partes = classroom.trim().split(/\s+/);
    return (partes[partes.length - 1] || '').toUpperCase();
  }

  cargarBoletines() {
    if (!this.selectedGrade || !this.selectedClassroom) return;

    this.isLoading = true;
    this.errorMessage = '';

    const grade = this.numeroGrado(this.selectedGrade);
    const classroom = this.letraSalon(this.selectedClassroom);

    this.service.listar(grade, classroom, this.selectedPeriod ?? undefined).subscribe({
      next: (data) => {
        this.boletines = data.map(b => ({ ...b, displayName: this.formatearNombre(b.fileName) }));
        this.isLoading = false;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.status === 403
          ? 'Solo un administrador puede ver los boletines generados.'
          : 'No se pudieron cargar los boletines generados.';
      }
    });
  }

  // "BOLETIN_Perez_Juan.pdf" → "Juan Perez"
  private formatearNombre(fileName: string): string {
    const base = fileName.replace(/\.pdf$/i, '').replace(/^BOLETIN_/i, '');
    const partes = base.split('_').filter(Boolean);
    if (partes.length >= 2) {
      const apellido = partes[0];
      const nombre = partes.slice(1).join(' ');
      return `${nombre} ${apellido}`;
    }
    return base.replace(/_/g, ' ');
  }

  descargando: { [key: string]: boolean } = {};

  /** Vista previa abierta (null = cerrada). */
  preview: { blob: Blob; fileName: string; title: string } | null = null;
  /** Evita pedir el PDF dos veces si se toca la tarjeta varias veces seguidas. */
  private pidiendo = new Set<string>();

  /** Botón "Descargar": guarda el archivo en el dispositivo (NO abre la vista previa). */
  descargar(b: BoletinGeneradoDto) {
    this.obtenerPdf(b, (blob) => downloadBlob(blob, pdfFileName(b.fileName)));
  }

  /** Tocar el boletín (el PDF): abre la vista previa. */
  verVistaPrevia(b: BoletinGeneradoDto) {
    this.obtenerPdf(b, (blob) => {
      this.preview = { blob, fileName: pdfFileName(b.fileName), title: this.nombreLegible(b) };
    });
  }

  cerrarVistaPrevia() {
    this.preview = null;
  }

  private nombreLegible(b: BoletinGeneradoDto): string {
    return b.fileName.replace(/\.pdf$/i, '').replace(/_/g, ' ');
  }

  private obtenerPdf(b: BoletinGeneradoDto, alTerminar: (blob: Blob) => void) {
    const key = b.fileName + b.period;
    if (this.pidiendo.has(key)) return;
    this.pidiendo.add(key);
    this.descargando[key] = true;
    this.service.descargarPdf(b).subscribe({
      next: (blob) => {
        this.pidiendo.delete(key);
        this.descargando[key] = false;
        alTerminar(blob);
      },
      error: () => {
        this.pidiendo.delete(key);
        this.descargando[key] = false;
        this.dialogService.alert('No se pudo obtener el boletín. Verifica que tu sesión siga activa.', 'Error al abrir el boletín');
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

  formatearTamano(bytes: number): string {
    if (!bytes) return '';
    const kb = bytes / 1024;
    if (kb < 1024) return `${kb.toFixed(0)} KB`;
    return `${(kb / 1024).toFixed(1)} MB`;
  }

  trackByFileName(index: number, b: BoletinCard): string {
    return b.fileName + b.period;
  }
}
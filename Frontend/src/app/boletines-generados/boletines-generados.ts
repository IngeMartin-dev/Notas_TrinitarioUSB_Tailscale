import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BoletinesGeneradosService, BoletinGeneradoDto } from '../services/boletines-generados.service';
import { DialogService } from '../services/dialog.service';

interface BoletinCard extends BoletinGeneradoDto {
  displayName: string;
}

@Component({
  selector: 'app-boletines-generados',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './boletines-generados.html',
  styleUrls: ['./boletines-generados.css']
})
export class BoletinesGenerados implements OnInit {
  private service = inject(BoletinesGeneradosService);
  private dialogService = inject(DialogService);

  grades: string[] = ['Grado 1º', 'Grado 2º', 'Grado 3º', 'Grado 4º', 'Grado 5º', 'Grado 6º',
                       'Grado 7º', 'Grado 8º', 'Grado 9º', 'Grado 10º', 'Grado 11º'];
  classrooms: string[] = ['Salon A', 'Salon B'];

  selectedGrade: string | null = null;
  selectedClassroom: string | null = null;
  selectedPeriod: number | null = null; // null = todos los períodos

  periodos = [1, 2, 3, 4];

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

  descargar(b: BoletinGeneradoDto) {
    const key = b.fileName + b.period;
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
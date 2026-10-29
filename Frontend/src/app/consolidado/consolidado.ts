import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Router } from '@angular/router';

/**
 * Antes "Reports" (src/app/reports). Renombrado a "Consolidado":
 * mismo flujo de UI, pero ahora contra /api/consolidado, que genera el
 * PDF con la plantilla de marcadores "Consolidado Base/ConsolidadoBase.html"
 * (32 estudiantes por hoja, con PROM. y PUESTO), en vez del antiguo
 * /api/reportes por concatenación de strings.
 */
@Component({
  selector: 'app-consolidado',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './consolidado.html',
  styleUrl: './consolidado.css',
})
export class Consolidado {
  grades: string[] = ['Grado 1º', 'Grado 2º', 'Grado 3º', 'Grado 4º', 'Grado 5º', 'Grado 6º',
                       'Grado 7º', 'Grado 8º', 'Grado 9º', 'Grado 10º', 'Grado 11º'];
  classrooms: string[] = ['Salon A', 'Salon B'];
  periodos = [1, 2, 3, 4];

  selectedGrade: string | null = null;
  selectedClassroom: string | null = null;
  selectedPeriod = 1;

  isGenerating = false;
  errorMessage = '';
  private rawPdfUrl: string | null = null;
  pdfPreviewUrl: SafeResourceUrl | null = null;

  private readonly API_BASE = 'http://localhost:8080/api/consolidado';

  constructor(private http: HttpClient, private sanitizer: DomSanitizer, private router: Router) {}

  /** Abre el apartado "Consolidados Generados": lista de solo lectura de
   *  todos los consolidados ya generados y guardados en disco. */
  verConsolidadosGenerados() {
    this.router.navigate(['/consolidados-generados']);
  }

  selectGrade(grade: string, classroom: string) {
    this.selectedGrade = grade;
    this.selectedClassroom = classroom;
    this.pdfPreviewUrl = null;
    this.rawPdfUrl = null;
    this.errorMessage = '';
  }

  canGenerate(): boolean {
    return !!(this.selectedGrade && this.selectedClassroom && this.selectedPeriod);
  }

  generarConsolidado() {
    if (!this.canGenerate()) return;

    this.isGenerating = true;
    this.errorMessage = '';
    this.pdfPreviewUrl = null;

    // El backend consulta la tabla de estudiantes con el mismo formato
    // completo con el que se guardan ("Grado 1º", "Salon A"), no la forma
    // abreviada que se usa para nombrar carpetas de boletines.
    const url = `${this.API_BASE}/grado-salon?grade=${encodeURIComponent(this.selectedGrade!)}&classroom=${encodeURIComponent(this.selectedClassroom!)}&period=${this.selectedPeriod}`;

    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        this.rawPdfUrl = URL.createObjectURL(blob);
        this.pdfPreviewUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.rawPdfUrl);
        this.isGenerating = false;
      },
      error: (err) => {
        this.isGenerating = false;
        this.errorMessage = err?.status === 403
          ? 'No tienes permisos para generar este consolidado (solo Administrador o Director de Grupo).'
          : (err?.error?.error || 'No se pudo generar el consolidado. Verifica que haya notas registradas para ese grado, salón y período.');
      }
    });
  }

  descargarPdf() {
    if (!this.rawPdfUrl || !this.selectedGrade || !this.selectedClassroom) return;
    const a = document.createElement('a');
    a.href = this.rawPdfUrl;
    a.download = `Consolidado_${this.selectedGrade}_${this.selectedClassroom}_Periodo${this.selectedPeriod}.pdf`;
    a.click();
  }
}
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ConsolidadosGeneradosService, ConsolidadoGeneradoDto } from '../services/consolidados-generados.service';
import { DialogService } from '../services/dialog.service';

interface ConsolidadoCard extends ConsolidadoGeneradoDto {
  displayName: string;
  gradoLabel: string;
  salonLabel: string;
}

/**
 * "Consolidados Generados": apartado de solo lectura (Administrador /
 * Director de Grupo) que muestra TODOS los consolidados de grado/salón ya
 * generados y guardados en disco (ver ConsolidadoController#/generados),
 * organizados por período y salón, con opción de descargarlos de nuevo sin
 * tener que regenerarlos. Se llega aquí desde el botón "Ver consolidados
 * generados" en el apartado de Consolidado.
 */
@Component({
  selector: 'app-consolidados-generados',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './consolidados-generados.html',
  styleUrls: ['./consolidados-generados.css']
})
export class ConsolidadosGenerados implements OnInit {
  private service = inject(ConsolidadosGeneradosService);
  private dialogService = inject(DialogService);
  private router = inject(Router);

  periodos = [1, 2, 3, 4];
  selectedPeriod: number | null = null; // null = todos los períodos

  consolidados: ConsolidadoCard[] = [];
  isLoading = false;
  errorMessage = '';
  descargando: { [key: string]: boolean } = {};

  ngOnInit(): void {
    this.cargar();
  }

  volver() {
    this.router.navigate(['/reports']);
  }

  onPeriodFilterChange() {
    this.cargar();
  }

  cargar() {
    this.isLoading = true;
    this.errorMessage = '';

    this.service.listar(this.selectedPeriod ?? undefined).subscribe({
      next: (data) => {
        this.consolidados = data.map(c => ({
          ...c,
          displayName: this.formatearNombre(c.fileName),
          gradoLabel: this.gradoDeCarpeta(c.salonCarpeta),
          salonLabel: this.salonDeCarpeta(c.salonCarpeta)
        }));
        this.isLoading = false;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.status === 403
          ? 'No tienes permisos para ver los consolidados generados (solo Administrador o Director de Grupo).'
          : 'No se pudieron cargar los consolidados generados.';
      }
    });
  }

  /** "Consolidado_Grado_5º_Salon_A_Periodo1.pdf" → "Grado 5º Salon A · Periodo1" (legible, sin guiones bajos). */
  private formatearNombre(fileName: string): string {
    return fileName.replace(/\.pdf$/i, '').replace(/^Consolidado_/i, '').replace(/_/g, ' ');
  }

  // "7A" → "Grado 7º"
  private gradoDeCarpeta(salonCarpeta: string): string {
    const m = salonCarpeta.match(/\d+/);
    return m ? `Grado ${m[0]}º` : salonCarpeta;
  }

  // "7A" → "Salón A"
  private salonDeCarpeta(salonCarpeta: string): string {
    const letra = salonCarpeta.replace(/\d+/g, '').trim();
    return letra ? `Salón ${letra}` : '';
  }

  get gruposPorSalon(): { salonCarpeta: string; gradoLabel: string; salonLabel: string; items: ConsolidadoCard[] }[] {
    const grupos = new Map<string, ConsolidadoCard[]>();
    for (const c of this.consolidados) {
      if (!grupos.has(c.salonCarpeta)) grupos.set(c.salonCarpeta, []);
      grupos.get(c.salonCarpeta)!.push(c);
    }
    return Array.from(grupos.entries())
      .sort((a, b) => a[0].localeCompare(b[0], undefined, { numeric: true }))
      .map(([salonCarpeta, items]) => ({
        salonCarpeta,
        gradoLabel: this.gradoDeCarpeta(salonCarpeta),
        salonLabel: this.salonDeCarpeta(salonCarpeta),
        items
      }));
  }

  descargar(c: ConsolidadoGeneradoDto) {
    const key = c.fileName + c.period;
    this.descargando[key] = true;
    this.service.descargarPdf(c).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        this.descargando[key] = false;
      },
      error: () => {
        this.descargando[key] = false;
        this.dialogService.alert('No se pudo descargar el consolidado. Verifica que tu sesión siga activa.', 'Error al descargar');
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

  trackByFileName(index: number, c: ConsolidadoCard): string {
    return c.fileName + c.period;
  }

  trackBySalon(index: number, g: { salonCarpeta: string }): string {
    return g.salonCarpeta;
  }
}

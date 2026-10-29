import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PromocionesService, PromocionDto } from '../services/promociones.service';
import { DialogService } from '../services/dialog.service';

@Component({
  selector: 'app-promociones',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './promociones.html',
  styleUrls: ['./promociones.css']
})
export class Promociones implements OnInit {
  private service = inject(PromocionesService);
  private dialogService = inject(DialogService);

  promociones: PromocionDto[] = [];
  /** Promoción (año) abierta; null = se muestran los recuadros de años. */
  anioSeleccionado: number | null = null;
  busqueda = '';
  isLoading = false;
  errorMessage = '';
  abriendo: { [key: string]: boolean } = {};

  ngOnInit(): void {
    this.cargar();
  }

  cargar() {
    this.isLoading = true;
    this.errorMessage = '';
    this.service.listar().subscribe({
      next: (data) => {
        this.promociones = data || [];
        this.isLoading = false;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.status === 403
          ? 'Solo un administrador puede ver las promociones.'
          : 'No se pudieron cargar las promociones.';
      }
    });
  }

  /** Un recuadro por año con su cantidad de egresados. */
  get anios(): { anio: number; total: number; conBoletines: number }[] {
    const mapa = new Map<number, PromocionDto[]>();
    for (const p of this.promociones) {
      if (!mapa.has(p.academicYear)) mapa.set(p.academicYear, []);
      mapa.get(p.academicYear)!.push(p);
    }
    return Array.from(mapa.entries())
      .map(([anio, lista]) => ({ anio, total: lista.length, conBoletines: lista.filter(x => x.periodos.length > 0).length }))
      .sort((a, b) => b.anio - a.anio);
  }

  abrirAnio(anio: number) {
    this.anioSeleccionado = anio;
    this.busqueda = '';
  }

  volver() {
    this.anioSeleccionado = null;
  }

  get estudiantesDelAnio(): PromocionDto[] {
    const q = this.busqueda.trim().toLowerCase();
    return this.promociones.filter(p =>
      p.academicYear === this.anioSeleccionado &&
      (!q || `${p.name} ${p.surname} ${p.documentNumber || ''}`.toLowerCase().includes(q))
    );
  }

  abrirBoletin(p: PromocionDto, periodo: number) {
    const key = `${p.id}-${periodo}`;
    this.abriendo[key] = true;
    this.service.boletin(p.id, periodo).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        this.abriendo[key] = false;
      },
      error: () => {
        this.abriendo[key] = false;
        this.dialogService.alert('No se pudo abrir el boletín. Verifica que tu sesión siga activa.', 'Error');
      }
    });
  }

  fecha(iso: string | null): string {
    if (!iso) return '-';
    try {
      return new Date(iso + 'T00:00:00').toLocaleDateString('es-CO', { day: 'numeric', month: 'short', year: 'numeric' });
    } catch {
      return iso;
    }
  }

  /** "3 años y 2 meses" / "45 días" que le quedan antes del borrado automático. */
  restante(dias: number | null): string {
    if (dias == null) return '-';
    if (dias < 60) return `${dias} día(s)`;
    const anios = Math.floor(dias / 365);
    const meses = Math.floor((dias % 365) / 30);
    return anios > 0 ? `${anios} año(s)${meses ? ' y ' + meses + ' mes(es)' : ''}` : `${meses} mes(es)`;
  }
}
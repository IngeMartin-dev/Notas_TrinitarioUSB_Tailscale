import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { GlobalRealtimeService } from '../services/global-realtime.service';
import { Subscription } from 'rxjs';

interface Period {
  id?: number;
  periodNumber: number;
  isUnlocked: boolean;
  description?: string;
  unlockDate?: string;
  lockDate?: string;
  isAutomatic?: boolean;
}

@Component({
  selector: 'app-periods',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './periods.html',
  styleUrl: './periods.css',
})
export class Periods implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  private router = inject(Router);
  private authService = inject(AuthService);
  private realtimeService = inject(GlobalRealtimeService);
  private periodsSubscription: Subscription | null = null;

  periods = signal<Period[]>([]);
  loading = signal(true);
  saving = signal(false);
  successMessage = signal('');
  errorMessage = signal('');
  
  editingPeriod = signal<Period | null>(null);
  unlockDateTemp = signal('');
  lockDateTemp = signal('');
  isAutomaticTemp = signal(false);

  private apiUrl = 'http://localhost:8080/api/periods';

  ngOnInit() {
    this.loadPeriods();
    
    // Subscribe to global realtime updates
    this.periodsSubscription = this.realtimeService.periods$.subscribe(periods => {
      if (periods.length > 0) {
        // Eliminar duplicados por periodNumber
        const uniquePeriods = periods.filter((p, index, self) => 
          index === self.findIndex((t) => t.periodNumber === p.periodNumber)
        );
        this.periods.set(uniquePeriods);
      }
    });
  }
  
  ngOnDestroy() {
    if (this.periodsSubscription) {
      this.periodsSubscription.unsubscribe();
    }
  }

  loadPeriods() {
    this.loading.set(true);
    this.http.get<Period[]>(this.apiUrl).subscribe({
      next: (data) => {
        console.log('Periodos recibidos:', data);
        // Eliminar duplicados por periodNumber
        const uniquePeriods = data.filter((p, index, self) => 
          index === self.findIndex((t) => t.periodNumber === p.periodNumber)
        );
        console.log('Periodos únicos:', uniquePeriods);
        
        if (uniquePeriods.length === 0) {
          this.initializePeriods();
        } else {
          this.periods.set(uniquePeriods);
          this.loading.set(false);
        }
      },
      error: (err) => {
        console.error('Error loading periods:', err);
        this.errorMessage.set('Error al cargar los períodos');
        this.loading.set(false);
      }
    });
  }

  initializePeriods() {
    this.http.post<any>(`${this.apiUrl}/initialize`, {}).subscribe({
      next: () => {
        this.loadPeriods();
      },
      error: (err) => {
        console.error('Error initializing periods:', err);
      }
    });
  }

  togglePeriod(periodNumber: number, currentStatus: boolean) {
    this.saving.set(true);
    this.errorMessage.set('');
    this.successMessage.set('');

    const newStatus = !currentStatus;
    
    this.http.put<any>(`${this.apiUrl}/${periodNumber}/unlock`, { unlocked: newStatus })
      .subscribe({
        next: () => {
          this.periods.update(periods => 
            periods.map(p => 
              p.periodNumber === periodNumber 
                ? { ...p, isUnlocked: newStatus, isAutomatic: false }
                : p
            )
          );
          this.saving.set(false);
          this.successMessage.set(newStatus 
            ? `Período ${periodNumber} desbloqueado correctamente`
            : `Período ${periodNumber} bloqueado correctamente`
          );
          setTimeout(() => this.successMessage.set(''), 3000);
          
          // Notify global realtime service
          this.realtimeService.notifyPeriodsChanged();
        },
        error: (err) => {
          console.error('Error updating period:', err);
          this.saving.set(false);
          this.errorMessage.set('Error al actualizar el período');
        }
      });
  }

  openScheduleModal(period: Period) {
    this.editingPeriod.set(period);
    
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const currentDateTime = `${year}-${month}-${day}T${hours}:${minutes}`;
    
    this.unlockDateTemp.set(period.unlockDate ? this.formatDateForInput(period.unlockDate) : currentDateTime);
    this.lockDateTemp.set(period.lockDate ? this.formatDateForInput(period.lockDate) : currentDateTime);
    this.isAutomaticTemp.set(period.isAutomatic || false);
  }

  closeScheduleModal() {
    this.editingPeriod.set(null);
    this.unlockDateTemp.set('');
    this.lockDateTemp.set('');
    this.isAutomaticTemp.set(false);
  }

  formatDateTimeLocal(dateStr: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day}T${hours}:${minutes}`;
  }

  formatDateForInput(dateStr: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day}T${hours}:${minutes}`;
  }

  saveSchedule() {
    const period = this.editingPeriod();
    if (!period) return;

    this.saving.set(true);
    
    let unlockDateStr: string | null = null;
    let lockDateStr: string | null = null;
    
    // Parse datetime-local input directly (format: YYYY-MM-DDTHH:MM)
    if (this.unlockDateTemp()) {
      const value = this.unlockDateTemp();
      unlockDateStr = value.includes(':') ? value + ':00' : value + ':00:00';
    }
    if (this.lockDateTemp()) {
      const value = this.lockDateTemp();
      lockDateStr = value.includes(':') ? value + ':00' : value + ':00:00';
    }

    this.http.put<any>(`${this.apiUrl}/${period.periodNumber}/schedule`, {
      unlockDate: unlockDateStr,
      lockDate: lockDateStr
    }).subscribe({
      next: (response) => {
        this.periods.update(periods =>
          periods.map(p =>
            p.periodNumber === period.periodNumber
              ? { ...p, ...response }
              : p
          )
        );
        this.saving.set(false);
        this.closeScheduleModal();
        this.successMessage.set('Programación guardada correctamente');
        setTimeout(() => this.successMessage.set(''), 3000);
        
        // Notify global realtime service
        this.realtimeService.notifyPeriodsChanged();
      },
      error: (err) => {
        console.error('Error saving schedule:', err);
        this.saving.set(false);
        this.errorMessage.set('Error al guardar la programación');
      }
    });
  }

  isAdmin(): boolean {
    const user = this.authService.getCurrentUserValue();
    const roleName = (user?.role?.name || user?.role || '').toString().toUpperCase();
    // Igual que en app.ts/settings.ts/dashboard.ts: cuenta también el rol
    // "extra" de Admin sumado desde Configuración de Año. getRole() (el
    // string guardado en localStorage al iniciar sesión) nunca se entera
    // de ese rol extra, por eso se usa el usuario completo de /api/auth/me.
    return roleName === 'ADMIN' || !!user?.additionalAdmin;
  }

  getStatusText(isUnlocked: boolean): string {
    return isUnlocked ? 'Desbloqueado' : 'Bloqueado';
  }

  getStatusIcon(isUnlocked: boolean): string {
    return isUnlocked ? 'lock_open' : 'lock';
  }

  formatDisplayDate(dateStr?: string): string {
    if (!dateStr) return 'No establecido';
    const date = new Date(dateStr);
    return date.toLocaleString('es-CO');
  }

  goBack() {
    this.router.navigate(['/dashboard']);
  }
}
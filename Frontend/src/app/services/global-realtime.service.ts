import { Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, interval, Subscription } from 'rxjs';
import { AuthService } from './auth.service';

export interface Period {
  periodNumber: number;
  isUnlocked: boolean;
  unlockDate?: string;
  lockDate?: string;
  isAutomatic?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class GlobalRealtimeService {
  private periodsSubject = new BehaviorSubject<Period[]>([]);
  periods$ = this.periodsSubject.asObservable();
  
  private pollingSubscription: Subscription | null = null;
  private lastPeriods: string = '';
  private notificationPermission: NotificationPermission = 'default';
  private notifiedPeriods: Set<string> = new Set();
  private http: HttpClient;
  private ngZone: NgZone;
  
  constructor(http: HttpClient, ngZone: NgZone, private authService: AuthService) {
    this.http = http;
    this.ngZone = ngZone;
    this.requestNotificationPermission();
  }
  
  private requestNotificationPermission() {
    if ('Notification' in window) {
      Notification.requestPermission().then(permission => {
        this.notificationPermission = permission;
      });
    }
  }
  
  private showPushNotification(title: string, body: string, icon?: string) {
    if ('Notification' in window && Notification.permission === 'granted') {
      const notificationIcon = icon || '/Logo Colegio.png';
      
      console.log('[Notifications] Showing push notification:', title, body);
      
      const notification = new Notification(title, {
        body: body,
        icon: notificationIcon,
        badge: notificationIcon,
        tag: 'period-notification',
        requireInteraction: false,
        silent: false
      });
      
      notification.onclick = () => {
        window.focus();
        notification.close();
      };
      
      setTimeout(() => notification.close(), 10000);
    } else {
      console.log('[Notifications] Permission not granted or Notification API not available');
    }
  }
  
  private checkPeriodDates(periods: Period[]) {
    const now = new Date();
    console.log('[Notifications] Checking periods at', now.toLocaleTimeString(), '| Periods:', JSON.stringify(periods.map(p => ({
      num: p.periodNumber,
      unlocked: p.isUnlocked,
      auto: p.isAutomatic,
      unlock: p.unlockDate,
      lock: p.lockDate
    }))));
    
    for (const period of periods) {
      if (period.isAutomatic && period.unlockDate) {
        const unlockDate = new Date(period.unlockDate);
        const diffMs = unlockDate.getTime() - now.getTime();
        const diffMins = diffMs / 60000;
        const diffDays = diffMs / (1000 * 60 * 60 * 24);
        
        const unlockKey = `unlock-${period.periodNumber}`;
        console.log(`[Notifications] Period ${period.periodNumber} unlock:`, unlockDate.toLocaleString(), '| diffMins:', diffMins.toFixed(1), '| isUnlocked:', period.isUnlocked);

        // Aviso de "faltan 7 días" (independiente del aviso de 5 minutos).
        // Se guarda por fecha exacta (no solo por número de período), así
        // que si el admin reprograma la fecha, el aviso puede repetirse.
        const unlock7dKey = `unlock-${period.periodNumber}-${period.unlockDate}-7d`;
        if (diffDays > 0 && diffDays <= 7 && !this.notifiedPeriods.has(unlock7dKey)) {
          this.notifiedPeriods.add(unlock7dKey);
          this.showPushNotification(
            '📅 Período por abrir',
            `Faltan ${Math.ceil(diffDays)} día${Math.ceil(diffDays) > 1 ? 's' : ''} para que el Período ${period.periodNumber} se abra`
          );
        }
        
        if (diffMins > 0 && diffMins <= 5 && !this.notifiedPeriods.has(unlockKey + '-warning')) {
          console.log('[Notifications] Sending UNLOCK WARNING for period', period.periodNumber);
          this.notifiedPeriods.add(unlockKey + '-warning');
          this.showPushNotification(
            '🔔 Período por comenzar',
            `El Período ${period.periodNumber} se desbloqueará en ${Math.ceil(diffMins)} minuto${Math.ceil(diffMins) > 1 ? 's' : ''}`
          );
        }
        
        if (period.isUnlocked && !this.notifiedPeriods.has(unlockKey + '-started')) {
          console.log('[Notifications] Sending UNLOCKED for period', period.periodNumber);
          this.notifiedPeriods.add(unlockKey + '-started');
          this.showPushNotification(
            '✅ Período iniciado',
            `El Período ${period.periodNumber} ha sido desbloqueado`
          );
        }
      }
      
      if (period.isAutomatic && period.lockDate && period.unlockDate) {
        const unlockDate = new Date(period.unlockDate);
        const lockDate = new Date(period.lockDate);
        const nowMs = now.getTime();
        const unlockMs = unlockDate.getTime();
        const lockMs = lockDate.getTime();
        
        const isActive = nowMs >= unlockMs && nowMs < lockMs;
        const diffToLockMs = lockMs - nowMs;
        const diffMins = diffToLockMs / 60000;
        const diffDays = diffToLockMs / (1000 * 60 * 60 * 24);
        
        const lockKey = `lock-${period.periodNumber}`;
        console.log(`[Notifications] Period ${period.periodNumber} lock:`, lockDate.toLocaleString(), '| diffMins:', diffMins.toFixed(1), '| isUnlocked:', period.isUnlocked, '| isActive:', isActive, '| notified:', this.notifiedPeriods.has(lockKey + '-ended'));

        // Aviso de "faltan 7 días" para el cierre.
        const lock7dKey = `lock-${period.periodNumber}-${period.lockDate}-7d`;
        if (diffDays > 0 && diffDays <= 7 && !this.notifiedPeriods.has(lock7dKey)) {
          this.notifiedPeriods.add(lock7dKey);
          this.showPushNotification(
            '⏳ Período por cerrar',
            `Faltan ${Math.ceil(diffDays)} día${Math.ceil(diffDays) > 1 ? 's' : ''} para que el Período ${period.periodNumber} se cierre`
          );
        }
        
        if (diffMins > 0 && diffMins <= 5 && !this.notifiedPeriods.has(lockKey + '-warning')) {
          console.log('[Notifications] Sending LOCK WARNING for period', period.periodNumber);
          this.notifiedPeriods.add(lockKey + '-warning');
          this.showPushNotification(
            '⚠️ Período por finalizar',
            `El Período ${period.periodNumber} se bloqueará en ${Math.ceil(diffMins)} minuto${Math.ceil(diffMins) > 1 ? 's' : ''}`
          );
        }
        
        const isPastLockTime = nowMs >= lockMs;
        
        if (isPastLockTime && !this.notifiedPeriods.has(lockKey + '-ended')) {
          console.log('[Notifications] Sending LOCKED for period', period.periodNumber, '| pastLock:', isPastLockTime, '| isUnlocked:', period.isUnlocked);
          this.notifiedPeriods.add(lockKey + '-ended');
          this.showPushNotification(
            '🔒 Período finalizado',
            `El Período ${period.periodNumber} ha sido bloqueado`
          );
        }
        
        if (diffMins <= 0 && !period.isUnlocked && !this.notifiedPeriods.has(lockKey + '-ended')) {
          console.log('[Notifications] Sending LOCKED for period (condition 1)', period.periodNumber, '| diffMins:', diffMins, '| isUnlocked:', period.isUnlocked);
          this.notifiedPeriods.add(lockKey + '-ended');
          this.showPushNotification(
            '🔒 Período finalizado',
            `El Período ${period.periodNumber} ha sido bloqueado`
          );
        }
        
        if (!period.isUnlocked && !this.notifiedPeriods.has(lockKey + '-ended') && isActive === false && diffMins <= 0) {
          console.log('[Notifications] Sending LOCKED for period (condition 2)', period.periodNumber);
          this.notifiedPeriods.add(lockKey + '-ended');
          this.showPushNotification(
            '🔒 Período finalizado',
            `El Período ${period.periodNumber} ha sido bloqueado`
          );
        }
      }
    }
  }
  
  startPolling() {
    if (this.pollingSubscription) return;
    
    this.ngZone.runOutsideAngular(() => {
      this.pollingSubscription = interval(10000).subscribe(() => {
        // Igual que en GenerationService: esto arranca al iniciar la app,
        // antes del login. Sin este chequeo, cada 10s se pedía /api/periods
        // sin token y el backend respondía 403.
        if (!this.authService.isAuthenticated()) {
          return;
        }
        this.http.get<Period[]>('http://localhost:8080/api/periods').subscribe({
          next: (periods) => {
            console.log('Periodos desde realtime service:', periods);
            const currentPeriodsStr = JSON.stringify(periods.map(p => ({ 
              periodNumber: p.periodNumber, 
              isUnlocked: p.isUnlocked 
            })));
            
            if (currentPeriodsStr !== this.lastPeriods) {
              this.lastPeriods = currentPeriodsStr;
              this.ngZone.run(() => {
                this.periodsSubject.next(periods);
                this.checkPeriodDates(periods);
              });
            } else {
              this.ngZone.run(() => {
                this.checkPeriodDates(periods);
              });
            }
          },
          error: () => {}
        });
      });
    });
  }
  
  stopPolling() {
    if (this.pollingSubscription) {
      this.pollingSubscription.unsubscribe();
      this.pollingSubscription = null;
    }
  }
  
  notifyPeriodsChanged() {
    this.lastPeriods = '';
    this.http.get<Period[]>('http://localhost:8080/api/periods').subscribe({
      next: (periods) => {
        this.periodsSubject.next(periods);
      }
    });
  }
  
  showTestNotification() {
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification('🔔 Notificaciones activas', {
        body: 'Las notificaciones push funcionan correctamente',
        icon: '/Logo Colegio.png'
      });
    }
  }
  
  requestPermissionOnUserGesture() {
    if ('Notification' in window && this.notificationPermission !== 'granted') {
      Notification.requestPermission().then(permission => {
        this.notificationPermission = permission;
      });
    }
  }
}
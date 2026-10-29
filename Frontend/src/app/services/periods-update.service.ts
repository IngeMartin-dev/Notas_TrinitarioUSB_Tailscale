import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class PeriodsUpdateService {
  private periodsChangedSource = new Subject<void>();
  periodsChanged$ = this.periodsChangedSource.asObservable();

  constructor() {
    // Listen for storage events from other tabs
    window.addEventListener('storage', (event: StorageEvent) => {
      if (event.key === 'periodsChanged') {
        this.periodsChangedSource.next();
      }
    });
  }

  notifyPeriodsChanged() {
    // Set localStorage to trigger storage event in other tabs
    localStorage.setItem('periodsChanged', Date.now().toString());
    localStorage.removeItem('periodsChanged');
    
    // Also notify same-tab subscribers
    this.periodsChangedSource.next();
  }
}

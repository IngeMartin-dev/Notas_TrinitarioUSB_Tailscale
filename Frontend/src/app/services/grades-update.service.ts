import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class GradesUpdateService {
  // Signal to track when grades are updated
  gradesUpdated = signal<number>(0);

  // Method to notify that grades have been updated
  notifyGradeUpdate() {
    this.gradesUpdated.update(v => v + 1);
  }
}

import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

interface Subject {
  id: number;
  name: string;
  code: string;
  level: string;
  gradeMin?: number;
  gradeMax?: number;
  type?: string;
}

@Component({
  selector: 'app-subjects',
  imports: [CommonModule, FormsModule],
  templateUrl: './subjects.html',
  styleUrl: './subjects.css',
})
export class Subjects implements OnInit {
  private http = inject(HttpClient);
selectedTab: 'primaria' | 'bachillerato' | 'media' = 'primaria';
  private subjects: Subject[] = [];
  selectedSubject: Subject | null = null;

  /** Lista de grados disponibles para el filtro */
  readonly gradeOptions: number[] = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11];

  loading = true;

  // Management view
  allSubjects: Subject[] = [];
  showAddModal = false;
  subjectToDelete: Subject | null = null;
  showGradeModal = false;
  subjectToModifyGrades: Subject | null = null;
  modifyGradesData: { gradeMin: number; gradeMax: number } = { gradeMin: 1, gradeMax: 5 };
  isSavingSubject = false;
  isSavingGrades = false;

  newSubject: Partial<Subject> = {
    name: '',
    code: '',
    level: 'primaria',
    gradeMin: 1,
    gradeMax: 5,
    type: 'core'
  };

  // Notification system
  showNotification = false;
  notificationType: 'success' | 'error' | null = null;
  notificationMessage = '';

  ngOnInit() {
    this.loadSubjects();
  }

  loadSubjects() {
    this.http.get<Subject[]>('http://localhost:8080/api/subjects').subscribe({
      next: (data: Subject[]) => {
        this.subjects = data;
        this.allSubjects = [...data].sort((a, b) => a.name.localeCompare(b.name));
        this.loading = false;
      },
      error: (err: any) => {
        console.error('Failed to load subjects', err);
        this.loading = false;
      }
    });
  }

  viewSubjectDetails(subject: Subject) {
    this.selectedSubject = subject;
  }

  closeSubjectDetails() {
    this.selectedSubject = null;
  }

  // Subject Management Methods
  openAddModal() {
    this.newSubject = {
      name: '',
      code: '',
      level: 'primaria',
      gradeMin: 1,
      gradeMax: 5,
      type: 'core'
    };
    this.showAddModal = true;
  }

  onLevelChange() {
    if (this.newSubject.level === 'bachillerato') {
      this.newSubject.gradeMin = 6;
      this.newSubject.gradeMax = 9;
    } else if (this.newSubject.level === 'media') {
      this.newSubject.gradeMin = 10;
      this.newSubject.gradeMax = 11;
    } else {
      this.newSubject.gradeMin = 1;
      this.newSubject.gradeMax = 5;
    }
  }

  closeAddModal() {
    this.showAddModal = false;
  }

  capitalizeFirstLetter(value: string): string {
    if (!value) return value;
    return value.charAt(0).toUpperCase() + value.slice(1);
  }

  onSubjectNameInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const cursorPos = input.selectionStart ?? 0;
    const originalLength = input.value.length;
    const capitalized = this.capitalizeFirstLetter(input.value);
    if (capitalized !== input.value) {
      input.value = capitalized;
      const newPos = cursorPos + (capitalized.length - originalLength);
      input.setSelectionRange(newPos, newPos);
    }
    this.newSubject.name = capitalized;
  }

  addSubject() {
    if (!this.newSubject.name || !this.newSubject.name.trim()) {
      this.showErrorNotification('El nombre de la materia es obligatorio');
      return;
    }

    this.isSavingSubject = true;

    const subjectData: Partial<Subject> = {
      name: this.capitalizeFirstLetter(this.newSubject.name.trim()),
      code: this.newSubject.code?.toUpperCase() || '',
      level: this.newSubject.level || 'primaria',
      gradeMin: this.newSubject.gradeMin || 1,
      gradeMax: this.newSubject.gradeMax || 5,
      type: this.newSubject.type || 'core'
    };

    this.http.post<Subject>('http://localhost:8080/api/subjects', subjectData).subscribe({
      next: (saved: Subject) => {
        this.subjects.push(saved);
        this.allSubjects = [...this.subjects].sort((a, b) => a.name.localeCompare(b.name));
        this.closeAddModal();
        this.isSavingSubject = false;
        this.showSuccessNotification('Materia agregada exitosamente');
      },
      error: (err: any) => {
        console.error('Error creating subject', err);
        this.isSavingSubject = false;
        this.showErrorNotification('Error al agregar la materia');
      }
    });
  }

  confirmDeleteSubject(subject: Subject) {
    this.subjectToDelete = subject;
  }

  closeDeleteModal() {
    this.subjectToDelete = null;
  }

  deleteSubject() {
    if (!this.subjectToDelete) return;

    this.http.delete(`http://localhost:8080/api/subjects/${this.subjectToDelete.id}`).subscribe({
      next: () => {
        this.subjects = this.subjects.filter(s => s.id !== this.subjectToDelete!.id);
        this.allSubjects = [...this.subjects].sort((a, b) => a.name.localeCompare(b.name));
        this.closeDeleteModal();
        this.showSuccessNotification('Materia eliminada exitosamente');
      },
      error: (err: any) => {
        console.error('Error deleting subject', err);
        this.closeDeleteModal();
        this.showErrorNotification('Error al eliminar la materia');
      }
    });
  }

  changeToElective(subject: Subject) {
    const updatedSubject = { ...subject, type: 'elective' };
    this.http.put<Subject>(`http://localhost:8080/api/subjects/${subject.id}`, updatedSubject).subscribe({
      next: (saved: Subject) => {
        const idx = this.subjects.findIndex(s => s.id === saved.id);
        if (idx !== -1) this.subjects[idx] = saved;
        this.allSubjects = [...this.subjects].sort((a, b) => a.name.localeCompare(b.name));
        this.showSuccessNotification('Materia cambiada a electiva');
      },
      error: (err: any) => {
        console.error('Error updating subject', err);
        this.showErrorNotification('Error al cambiar a electiva');
      }
    });
  }

  changeToCore(subject: Subject) {
    const updatedSubject = { ...subject, type: 'core' };
    this.http.put<Subject>(`http://localhost:8080/api/subjects/${subject.id}`, updatedSubject).subscribe({
      next: (saved: Subject) => {
        const idx = this.subjects.findIndex(s => s.id === saved.id);
        if (idx !== -1) this.subjects[idx] = saved;
        this.allSubjects = [...this.subjects].sort((a, b) => a.name.localeCompare(b.name));
        this.showSuccessNotification('Materia cambiada a básica');
      },
      error: (err: any) => {
        console.error('Error updating subject', err);
        this.showErrorNotification('Error al cambiar a básica');
      }
    });
  }

  openModifyGrades(subject: Subject) {
    this.subjectToModifyGrades = subject;
    this.modifyGradesData = { gradeMin: subject.gradeMin ?? 1, gradeMax: subject.gradeMax ?? 5 };
    this.showGradeModal = true;
  }

  closeGradeModal() {
    this.showGradeModal = false;
    this.subjectToModifyGrades = null;
  }

  saveModifiedGrades() {
    if (!this.subjectToModifyGrades) return;

    this.isSavingGrades = true;
    const updatedSubject = {
      ...this.subjectToModifyGrades,
      gradeMin: this.modifyGradesData.gradeMin,
      gradeMax: this.modifyGradesData.gradeMax
    };

    this.http.put<Subject>(`http://localhost:8080/api/subjects/${this.subjectToModifyGrades.id}`, updatedSubject).subscribe({
      next: (saved: Subject) => {
        const idx = this.subjects.findIndex(s => s.id === saved.id);
        if (idx !== -1) this.subjects[idx] = saved;
        this.allSubjects = [...this.subjects].sort((a, b) => a.name.localeCompare(b.name));
        this.closeGradeModal();
        this.isSavingGrades = false;
        this.showSuccessNotification('Grados modificados exitosamente');
      },
      error: (err: any) => {
        console.error('Error updating grades', err);
        this.isSavingGrades = false;
        this.showErrorNotification('Error al modificar grados');
      }
    });
  }

  // Notification methods
  showSuccessNotification(message: string) {
    this.notificationType = 'success';
    this.notificationMessage = message;
    this.showNotification = true;
    setTimeout(() => { this.showNotification = false; }, 2500);
  }

  showErrorNotification(message: string) {
    this.notificationType = 'error';
    this.notificationMessage = message;
    this.showNotification = true;
    setTimeout(() => { this.showNotification = false; }, 2500);
  }

  hideNotification() {
    this.showNotification = false;
  }

  // ---- Filtros ----

  get totalSubjects(): number {
    return this.subjects.length;
  }

  get coreSubjects(): Subject[] {
    return this.subjects.filter((s) => (s.type || 'core').toLowerCase() !== 'elective');
  }

  get electiveSubjects(): Subject[] {
    return this.subjects.filter((s) => (s.type || 'core').toLowerCase() === 'elective');
  }

  get primarySubjectCount(): number {
    return this.subjects.filter((s) => 'primaria' === (s.level || '').toLowerCase()).length;
  }

get highSchoolSubjectCount(): number {
     return this.subjects.filter((s) => 'bachillerato' === (s.level || '').toLowerCase() || 'media' === (s.level || '').toLowerCase()).length;
   }

selectTab(tab: 'primaria' | 'bachillerato' | 'media') {
     this.selectedTab = tab;
     this.selectedSubject = null;
   }

  get tabSubjects(): Subject[] {
     return this.subjects.filter(s => {
       const min = s.gradeMin ?? 1;
       const max = s.gradeMax ?? 11;
       if (this.selectedTab === 'primaria') {
         return max <= 5;
       } else if (this.selectedTab === 'bachillerato') {
         return min >= 6 && min < 10;
       } else {
         return min >= 10;
       }
     });
   }
 }

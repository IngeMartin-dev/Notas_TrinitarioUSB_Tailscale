import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../services/auth.service';
import { DialogService } from '../services/dialog.service';

interface Teacher {
  id: number;
  name: string;
  surname: string;
  email: string;
  username?: string;
  subjects?: Subject[];
}

interface Subject {
  id: number;
  name: string;
  code: string;
  level: string;
  gradeMin?: number;
  gradeMax?: number;
  hoursPerWeek?: number;
  credits?: number;
  type?: string;
  description?: string;
  teacher?: number | null;
  teacherId?: number | null;
}

interface HomeroomAssignment {
  id: number;
  grade: string;
  classroom: string;
  userId: number | null;
  userName: string | null;
}

@Component({
  selector: 'app-directors-group',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './directors-group.html',
  styleUrl: './directors-group.css'
})
export class DirectorsGroup implements OnInit {
  teachers: Teacher[] = [];
  filteredTeachers: Teacher[] = [];
  homeroomAssignments: HomeroomAssignment[] = [];
  allSubjects: Subject[] = [];
  
  selectedTeacher: Teacher | null = null;
  selectedGrade: string | null = null;
  selectedClassroom: string | null = null;
  
  activeTab: 'bachillerato' | 'primaria' = 'bachillerato';
  searchTerm: string = '';
  
  bachilleratoClassrooms: string[] = ['Salon A', 'Salon B'];
  primariaClassrooms: string[] = ['Salon A', 'Salon B'];
  allGrades: string[] = ['Grado 1º', 'Grado 2º', 'Grado 3º', 'Grado 4º', 'Grado 5º', 'Grado 6º', 'Grado 7º', 'Grado 8º', 'Grado 9º', 'Grado 10º', 'Grado 11º'];
  
  isLoading: boolean = false;
  showNotification: boolean = false;
  notificationType: 'success' | 'error' | null = null;
  notificationMessage: string = '';

  constructor(private http: HttpClient, private authService: AuthService, private dialogService: DialogService) {}

  ngOnInit() {
    this.loadTeachers();
    this.loadSubjects();
    this.loadHomeroomAssignments();
  }

  loadTeachers() {
    this.isLoading = true;
    this.http.get<Teacher[]>('http://localhost:8080/api/users/teachers').subscribe({
      next: (teachers) => {
        this.teachers = teachers.map(t => ({ ...t, subjects: [] as Subject[] }));
        this.filteredTeachers = [...this.teachers];
        this.mapSubjectsToTeachers();
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error loading teachers:', err);
        this.teachers = [];
        this.filteredTeachers = [];
        this.isLoading = false;
      }
    });
  }

  loadSubjects() {
    this.http.get<Subject[]>('http://localhost:8080/api/subjects').subscribe({
      next: (subjects) => {
        this.allSubjects = subjects;
        this.mapSubjectsToTeachers();
      },
      error: (err) => {
        console.error('Error loading subjects:', err);
        this.allSubjects = [];
      }
    });
  }

  private mapSubjectsToTeachers() {
    if (this.allSubjects.length === 0 || this.teachers.length === 0) return;
    const subjects = this.allSubjects;
    this.teachers.forEach(teacher => {
      teacher.subjects = subjects.filter(s => s.teacherId === teacher.id || s.teacher === teacher.id);
    });
    this.filteredTeachers.forEach(teacher => {
      teacher.subjects = subjects.filter(s => s.teacherId === teacher.id || s.teacher === teacher.id);
    });
  }

  loadHomeroomAssignments() {
    this.http.get<HomeroomAssignment[]>('http://localhost:8080/api/homeroom-assignments').subscribe({
      next: (assignments) => {
        this.homeroomAssignments = assignments;
      },
      error: (err) => {
        console.error('Error loading homeroom assignments:', err);
        this.homeroomAssignments = [];
      }
    });
  }

  getFilteredTeachers(): Teacher[] {
    const term = this.searchTerm.toLowerCase();
    const filtered = term
      ? this.filteredTeachers.filter(t =>
          t.name.toLowerCase().includes(term) ||
          t.surname.toLowerCase().includes(term) ||
          (t.email && t.email.toLowerCase().includes(term))
        )
      : this.filteredTeachers;

    return [...filtered].sort((a, b) => {
      const aHasClassroom = this.getDirectorAssignment(a) !== undefined;
      const bHasClassroom = this.getDirectorAssignment(b) !== undefined;

      if (aHasClassroom !== bHasClassroom) {
        return aHasClassroom ? 1 : -1;
      }

      return `${a.name} ${a.surname}`.localeCompare(`${b.name} ${b.surname}`, 'es');
    });
  }

  unassignSubjectFromTeacher(subject: Subject) {
    if (!this.selectedTeacher) return;
    this.http.delete(`http://localhost:8080/api/subjects/${subject.id}/teacher`).subscribe({
      next: () => {
        this.showSuccessNotification(`Materia ${subject.name} desasignada de ${this.selectedTeacher!.name} ${this.selectedTeacher!.surname}`);
        this.loadSubjects();
        this.loadTeachers();
      },
      error: (err) => {
        console.error('Error al quitar materia', err);
        this.showErrorNotification('No se pudo quitar la materia. Intenta de nuevo.');
      }
    });
  }

  getTeacherSubjectObjects(teacher: Teacher): Subject[] {
    return teacher.subjects || [];
  }

  getDirectorAssignment(teacher: Teacher): HomeroomAssignment | undefined {
    return this.homeroomAssignments.find(a => a.userId === teacher.id);
  }

  getSubjectAcronym(subject: Subject): string {
    if (subject.code && subject.code.trim().length > 0) {
      return subject.code.trim().toUpperCase();
    }
    const cleaned = (subject.name || '')
      .normalize('NFD')
      .replace(/\p{Diacritic}/gu, '')
      .replace(/[^A-Za-z0-9]+/g, ' ')
      .trim()
      .toUpperCase();
    if (!cleaned) return '';
    return cleaned.replace(/\s+/g, ' ').substring(0, 3);
  }

  getClassroomLetter(classroom: string): string {
    const match = classroom.match(/([A-Za-z])$/);
    return match ? match[1].toUpperCase() : '';
  }

  getClassroomsForTab(): string[] {
    if (this.activeTab === 'bachillerato') {
      return this.allGrades.filter(g => {
        const num = parseInt(g.replace(/\D/g, ''));
        return num >= 6 && num <= 11;
      });
    } else {
      return this.allGrades.filter(g => {
        const num = parseInt(g.replace(/\D/g, ''));
        return num >= 1 && num <= 5;
      });
    }
  }

  getClassroomsForGrade(grade: string): string[] {
    return this.bachilleratoClassrooms;
  }

  getAssignmentForGradeClassroom(grade: string, classroom: string): HomeroomAssignment | undefined {
    return this.homeroomAssignments.find(a => a.grade === grade && a.classroom === classroom);
  }

  isAssigned(grade: string, classroom: string): boolean {
    return this.homeroomAssignments.some(a => a.grade === grade && a.classroom === classroom);
  }

  isAssignedToSelectedTeacher(grade: string, classroom: string): boolean {
    const assignment = this.getAssignmentForGradeClassroom(grade, classroom);
    return assignment !== undefined && this.selectedTeacher !== null && assignment.userId === this.selectedTeacher.id;
  }

  selectTeacher(teacher: Teacher) {
    this.selectedTeacher = teacher;
  }

  selectGrade(grade: string) {
    this.selectedGrade = grade;
    this.selectedClassroom = null;
  }

  selectClassroom(classroom: string) {
    if (!this.selectedGrade) return;
    this.selectedClassroom = classroom;
  }

  async selectGradeAndClassroom(grade: string, classroom: string) {
    if (!this.selectedTeacher) {
      this.showErrorNotification('Selecciona un profesor primero');
      return;
    }

    const existing = this.getAssignmentForGradeClassroom(grade, classroom);
    if (existing && existing.userId !== this.selectedTeacher.id) {
      const ok = await this.dialogService.confirm(
        `El salón ${grade} - ${classroom} ya tiene director asignado (${existing.userName}). ¿Desea reemplazarlo?`,
        'Director ya asignado',
        { confirmText: 'Reemplazar', cancelText: 'Cancelar', danger: true }
      );
      if (!ok) return;
    }

    this.selectedGrade = grade;
    this.selectedClassroom = classroom;
    this.assignDirectorImmediate(grade, classroom);
  }

  private assignDirectorImmediate(grade: string, classroom: string) {
    if (!this.selectedTeacher) return;

    const payload: any = {
      userId: this.selectedTeacher.id,
      grade: grade,
      classroom: classroom
    };

    this.http.post<any>('http://localhost:8080/api/homeroom-assignments/assign', payload).subscribe({
      next: (response) => {
        this.showSuccessNotification(`Director asignado: ${this.selectedTeacher!.name} ${this.selectedTeacher!.surname} - ${grade} ${classroom}`);
        this.loadHomeroomAssignments();
      },
      error: (err) => {
        console.error('Error assigning director:', err);
        this.showErrorNotification('Error al asignar el director de grupo');
      }
    });
  }

  async assignDirector() {
    if (!this.selectedTeacher || !this.selectedGrade || !this.selectedClassroom) {
      this.showErrorNotification('Seleccione un profesor, un grado y un salón');
      return;
    }

    const grade = this.selectedGrade;
    const classroom = this.selectedClassroom;

    const existing = this.getAssignmentForGradeClassroom(grade, classroom);
    if (existing && existing.userId !== this.selectedTeacher.id) {
      const ok = await this.dialogService.confirm(
        `El salón ${grade} - ${classroom} ya tiene director asignado (${existing.userName}). ¿Desea reemplazarlo?`,
        'Director ya asignado',
        { confirmText: 'Reemplazar', cancelText: 'Cancelar', danger: true }
      );
      if (!ok) return;
    }

    const payload: any = {
      userId: this.selectedTeacher.id,
      grade: grade,
      classroom: classroom
    };

    this.http.post<any>('http://localhost:8080/api/homeroom-assignments/assign', payload).subscribe({
      next: (response) => {
        this.showSuccessNotification(`Director asignado: ${this.selectedTeacher!.name} ${this.selectedTeacher!.surname} - ${grade} ${classroom}`);
        this.loadHomeroomAssignments();
        this.selectedGrade = null;
        this.selectedClassroom = null;
      },
      error: (err) => {
        console.error('Error assigning director:', err);
        this.showErrorNotification('Error al asignar el director de grupo');
      }
    });
  }

  removeAssignment(grade: string, classroom: string) {
    this.http.delete(`http://localhost:8080/api/homeroom-assignments/remove`, { body: { grade, classroom } }).subscribe({
      next: () => {
        this.showSuccessNotification('Asignación removida correctamente');
        this.loadHomeroomAssignments();
      },
      error: (err) => {
        console.error('Error removing assignment:', err);
        this.showErrorNotification('Error al remover la asignación');
      }
    });
  }

  onSearch(event: Event) {
    const input = event.target as HTMLInputElement;
    this.searchTerm = input.value;
  }

  clearSelection() {
    this.selectedTeacher = null;
  }

  private showSuccessNotification(message: string) {
    this.notificationType = 'success';
    this.notificationMessage = message;
    this.showNotification = true;
    setTimeout(() => { this.showNotification = false; }, 3000);
  }

  private showErrorNotification(message: string) {
    this.notificationType = 'error';
    this.notificationMessage = message;
    this.showNotification = true;
    setTimeout(() => { this.showNotification = false; }, 4000);
  }
}

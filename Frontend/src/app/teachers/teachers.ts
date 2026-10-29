import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

import { API_BASE_URL } from '../config/api-base';
interface Teacher {
  id: number;
  name: string;
  surname: string;
  username: string;
  email: string;
  role: { name: string } | null;
  enable: boolean;
  profilePicture?: string | null;
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

@Component({
  selector: 'app-teachers',
  imports: [CommonModule, FormsModule],
  templateUrl: './teachers.html',
  styleUrl: './teachers.css',
})
export class Teachers implements OnInit {
  teachers: Teacher[] = [];
  allSubjects: Subject[] = [];
  filteredSubjects: Subject[] = [];
  loading = true;
  saving = false;
  savingEditTeacher = false;
  selectedLevel: string = '';
  selectedSubjectIds: number[] = [];
  searchTerm = '';

  showAddModal = false;
  showConfirmDelete = false;
  teacherToDelete: Teacher | null = null;
  formName = '';
  formSurname = '';
  formEmail = '';
  formUsername = '';
  formPassword = '';
  formConfirmPassword = '';
  showPassword = false;
  showConfirmPassword = false;

  showEditModal = false;
  teacherToEdit: Teacher | null = null;
  editTeacherForm: { name: string; surname: string; username: string; email: string; newPassword: string } = {
    name: '',
    surname: '',
    username: '',
    email: '',
    newPassword: ''
  };

  selectedTeacherForAssignment: Teacher | null = null;

  showNotification = false;
  notificationType: 'success' | 'error' | null = null;
  notificationMessage = '';
  // Inline form field errors
  formEmailError: string | null = null;
  formUsernameError: string | null = null;
  formModalError: string | null = null;
  formPasswordError: string | null = null;

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.loadTeachers();
    this.loadSubjects();
  }

  loadTeachers() {
    this.loading = true;
    this.http.get<Teacher[]>('http://localhost:8080/api/teachers').subscribe({
      next: (data) => {
        this.teachers = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load teachers', err);
        this.loading = false;
      },
    });
  }

  loadSubjects() {
    this.http.get<Subject[]>('http://localhost:8080/api/subjects').subscribe({
      next: (data) => {
        this.allSubjects = data;
        console.log('[Teachers] Materias cargadas:', data.length, data);
      },
      error: (err) => {
        console.error('[Teachers] Failed to load subjects', err);
        this.allSubjects = [];
      },
    });
  }

  onLevelChange(level: string) {
    this.selectedLevel = level;
    if (level === 'Primaria') {
      this.filteredSubjects = this.allSubjects.filter(
        (s) => (s.level || '').toLowerCase() === 'primaria'
      );
    } else if (level === 'Bachillerato') {
      this.filteredSubjects = this.allSubjects.filter(
        (s) => (s.level || '').toLowerCase() === 'bachillerato'
      );
    } else {
      this.filteredSubjects = [];
    }
  }

  toggleSubjectSelection(subjectId: number) {
    const idx = this.selectedSubjectIds.indexOf(subjectId);
    if (idx >= 0) {
      this.selectedSubjectIds.splice(idx, 1);
    } else {
      this.selectedSubjectIds.push(subjectId);
    }
  }

  removeSelectedSubject(subjectId: number) {
    const idx = this.selectedSubjectIds.indexOf(subjectId);
    if (idx >= 0) {
      this.selectedSubjectIds.splice(idx, 1);
    }
  }

  getSelectedSubjects(): Subject[] {
    return this.allSubjects.filter((s) => this.selectedSubjectIds.includes(s.id));
  }

  openAddModal() {
    this.formName = '';
    this.formSurname = '';
    this.formEmail = '';
    this.formUsername = '';
    this.formPassword = '';
    this.formConfirmPassword = '';
    this.selectedLevel = '';
    this.selectedSubjectIds = [];
    this.filteredSubjects = [];
    this.showPassword = false;
    this.showConfirmPassword = false;
    this.formEmailError = null;
    this.formUsernameError = null;
    this.formModalError = null;
    this.formPasswordError = null;
    this.showAddModal = true;
  }

  closeAddModal() {
    this.showAddModal = false;
    this.formPasswordError = null;
    this.formEmailError = null;
    this.formUsernameError = null;
    this.formModalError = null;
  }

  openEditTeacher(teacher: Teacher) {
    this.teacherToEdit = teacher;
    this.editTeacherForm = {
      name: teacher.name,
      surname: teacher.surname,
      username: teacher.username || '',
      email: teacher.email,
      newPassword: ''
    };
    this.showEditModal = true;
  }

  closeEditModal() {
    this.showEditModal = false;
    this.teacherToEdit = null;
    this.savingEditTeacher = false;
    this.editTeacherForm = { name: '', surname: '', username: '', email: '', newPassword: '' };
  }

  saveTeacherEdit() {
    if (!this.teacherToEdit) return;

    const name = this.editTeacherForm.name.trim();
    const surname = this.editTeacherForm.surname.trim();
    const username = this.editTeacherForm.username.trim();
    const email = this.editTeacherForm.email.trim();
    const newPassword = this.editTeacherForm.newPassword.trim();

    if (!name || !surname || !username || !email) {
      this.showError('Completa nombre, apellido, usuario y correo');
      return;
    }

    if (newPassword && newPassword.length < 6) {
      this.showError('La contraseña debe tener al menos 6 caracteres');
      return;
    }

    this.savingEditTeacher = true;
    const updatedUser: any = {
      name,
      surname,
      username,
      email,
      enable: this.teacherToEdit.enable !== false
    };

    this.http.put(`http://localhost:8080/api/users/${this.teacherToEdit.id}`, updatedUser).subscribe({
      next: () => {
        const finishEdit = () => {
          this.showSuccess('Profesor actualizado correctamente');
          this.closeEditModal();
          this.loadTeachers();
        };

        if (newPassword) {
          this.http.post(`http://localhost:8080/api/users/${this.teacherToEdit!.id}/password/reset`, { newPassword }).subscribe({
            next: finishEdit,
            error: (err) => {
              console.error('Failed to update teacher password', err);
              this.showError('Perfil actualizado, pero no se pudo cambiar la contraseña');
              this.closeEditModal();
              this.loadTeachers();
            }
          });
        } else {
          finishEdit();
        }
      },
      error: (err) => {
        console.error('Failed to update teacher', err);
        this.showError(err?.error?.error || err?.message || 'No se pudo actualizar el profesor');
        this.savingEditTeacher = false;
      }
    });
  }

  selectTeacherForAssignment(teacher: Teacher) {
    this.selectedTeacherForAssignment = teacher;
    const assignedSubjects = this.getTeacherSubjectObjects(teacher);
    if (assignedSubjects.length > 0) {
      this.saveTeacherGradeRange(teacher);
    } else {
      this.clearPersistedTeacherGradeRange();
    }
  }

  clearTeacherSelection() {
    this.selectedTeacherForAssignment = null;
  }

  assignSubjectToSelectedTeacher(subject: Subject) {
    if (!this.selectedTeacherForAssignment) return;

    const teacherId = this.selectedTeacherForAssignment.id;

    if (subject.teacherId && subject.teacherId !== teacherId) {
      return;
    }

    this.http.put<any>(`http://localhost:8080/api/subjects/${subject.id}/teacher/${teacherId}`, {}).subscribe({
      next: () => {
        subject.teacherId = teacherId;
        subject.teacher = teacherId;
        this.updateSubjectAssignment(subject, teacherId);
        this.showSuccess(`Materia ${subject.name} asignada a ${this.selectedTeacherForAssignment?.name} ${this.selectedTeacherForAssignment?.surname}`);
        this.loadSubjects();
        this.loadTeachers();
        this.clearTeacherSelection();
      },
      error: (err) => {
        console.error('Error al asignar materia', err);
        this.showError('No se pudo asignar la materia. Intenta nuevamente.');
      }
    });
  }

  updateSubjectAssignment(subject: Subject, teacherId: number) {
    const savedGradeMin = subject.gradeMin;
    const savedGradeMax = subject.gradeMax;
    const assignment: any = {
      subjectId: subject.id,
      teacherId: teacherId,
      subjectName: subject.name,
      level: subject.level,
      gradeMin: savedGradeMin,
      gradeMax: savedGradeMax,
      assignedAt: new Date().toISOString()
    };
    localStorage.setItem(`teacher_assignment_${teacherId}_${subject.id}`, JSON.stringify(assignment));
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

  getSubjectLevelClass(subject: Subject): string {
    return (subject.level || '').toLowerCase() === 'primaria'
      ? 'subject-chip-primaria'
      : 'subject-chip-bachillerato';
  }

  getProfilePictureUrl(teacher: Teacher): string {
    const profilePicture = (teacher.profilePicture || '').trim();
    if (!profilePicture) return '';
    if (profilePicture.startsWith('http://') || profilePicture.startsWith('https://')) {
      return profilePicture;
    }
    return `${API_BASE_URL}${profilePicture.startsWith('/') ? profilePicture : `/${profilePicture}`}`;
  }

  getTeacherFullName(teacher: Teacher): string {
    return `${teacher.name} ${teacher.surname}`;
  }

  getTeacherSubjectObjects(teacher: Teacher): Subject[] {
    return this.allSubjects.filter((s) => (s.teacherId === teacher.id || s.teacher === teacher.id));
  }

  saveTeacherGradeRange(teacher: Teacher) {
    const assignedSubjects = this.getTeacherSubjectObjects(teacher).filter((s) => s.gradeMin != null && s.gradeMax != null);
    if (assignedSubjects.length === 0) {
      this.clearPersistedTeacherGradeRange();
      return;
    }

    const minGrade = Math.min(...assignedSubjects.map((s) => s.gradeMin!));
    const maxGrade = Math.max(...assignedSubjects.map((s) => s.gradeMax!));

    localStorage.setItem('selectedTeacherGradeRange', JSON.stringify({ min: minGrade, max: maxGrade }));
  }

  clearPersistedTeacherGradeRange() {
    localStorage.removeItem('selectedTeacherGradeRange');
  }

  getTeacherSubjects(teacher: Teacher): string[] {
    return this.getTeacherSubjectObjects(teacher).map((s) => `${s.name} (${this.getSubjectAcronym(s)})`);
  }

  unassignSubject(subject: Subject, teacher: Teacher) {
    this.http.delete<any>(`http://localhost:8080/api/subjects/${subject.id}/teacher`).subscribe({
      next: () => {
        subject.teacherId = null;
        subject.teacher = null;
        const remaining = this.getTeacherSubjectObjects(teacher);
        if (remaining.length > 0) {
          this.saveTeacherGradeRange(teacher);
        } else {
          this.clearPersistedTeacherGradeRange();
        }
        this.showSuccess(`Materia ${subject.name} desasignada de ${teacher.name} ${teacher.surname}`);
        this.loadSubjects();
        this.loadTeachers();
      },
      error: (err) => {
        console.error('Error al quitar materia', err);
        this.showError('No se pudo quitar la materia. Intenta de nuevo.');
      },
    });
  }

  createTeacher() {
    this.saving = true;
    const name = this.formName.trim();
    const surname = this.formSurname.trim();
    const email = this.formEmail.trim();
    const username = this.formUsername.trim();
    const password = this.formPassword;
    const confirmPassword = this.formConfirmPassword;

    if (!name || !surname || !email || !password) {
      this.showError('Por favor completa los campos obligatorios (nombre, apellido, correo y contraseña)');
      this.saving = false;
      return;
    }

    if (password !== confirmPassword) {
      this.formPasswordError = 'La contraseña no coincide';
      // focus confirm field
      setTimeout(() => {
        const el = document.getElementById('teacherConfirmPassword') as HTMLInputElement | null;
        if (el) el.focus();
      }, 0);
      this.saving = false;
      return;
    }

    if (password.length < 6) {
      this.showError('La contraseña debe tener al menos 6 caracteres');
      this.saving = false;
      return;
    }

    const payload: any = {
      name,
      surname,
      email,
      username,
      password,
      subjectCodes: this.selectedSubjectIds.map((id) => id.toString()),
    };

    // reset inline errors
    this.formEmailError = null;
    this.formUsernameError = null;

    this.http.post<any>('http://localhost:8080/api/teachers', payload).subscribe({
      next: (res) => {
        this.saving = false;
        this.closeAddModal();
        this.formPasswordError = null;
        this.formEmailError = null;
        this.formUsernameError = null;
        this.formModalError = null;
        this.showSuccess('Profesor y materias asignadas creados correctamente');
        this.loadTeachers();
      },
      error: (err) => {
        console.error('Failed to create teacher', err);
        this.saving = false;
        let backendMsg = err.error?.error || err.error?.message || err.message || 'Error al crear el profesor';
        // map known backend messages to user-friendly Spanish messages
        if (typeof backendMsg === 'string') {
          const lower = backendMsg.toLowerCase();
          if (lower.includes('email already in use') || lower.includes('email already')) {
            backendMsg = 'El correo ya está en uso';
            this.formEmailError = backendMsg;
            // focus email field
            setTimeout(() => {
              const el = document.getElementById('teacherEmail') as HTMLInputElement | null;
              if (el) el.focus();
            }, 0);
          } else if (lower.includes('username already in use') || lower.includes('username already')) {
            backendMsg = 'El nombre de usuario ya está en uso';
            this.formUsernameError = backendMsg;
            setTimeout(() => {
              const el = document.getElementById('teacherUsername') as HTMLInputElement | null;
              if (el) el.focus();
            }, 0);
          }
        }
        // If we set an inline field error, prefer inline; otherwise show error inside modal
        if (!this.formEmailError && !this.formUsernameError) {
          this.formModalError = backendMsg;
          // clear modal error after a short time
          setTimeout(() => (this.formModalError = null), 5000);
        }
      },
    });
  }

  deleteTeacher(teacher: Teacher) {
    this.teacherToDelete = teacher;
    this.showConfirmDelete = true;
  }

  confirmDeleteTeacher() {
    if (!this.teacherToDelete) {
      this.showConfirmDelete = false;
      return;
    }

    const teacherId = this.teacherToDelete.id;
    const teacherName = `${this.teacherToDelete.name} ${this.teacherToDelete.surname}`;

    this.http.delete(`http://localhost:8080/api/teachers/${teacherId}`).subscribe({
      next: () => {
        this.teachers = this.teachers.filter((t) => t.id !== teacherId);
        this.showSuccess(`Profesor ${teacherName} eliminado correctamente`);
        this.showConfirmDelete = false;
        this.teacherToDelete = null;
      },
      error: (err) => {
        const backendMsg = err?.error?.error || err?.error?.message || err?.message || 'Error al eliminar el profesor';
        console.error('[DELETE teacher error]', err);
        this.showError(backendMsg);
        this.showConfirmDelete = false;
        this.teacherToDelete = null;
      },
    });
  }

  cancelDeleteTeacher() {
    this.showConfirmDelete = false;
    this.teacherToDelete = null;
  }

  toggleStatus(teacher: Teacher) {
    const updated = { ...teacher, enable: !teacher.enable };
    this.http.put(`http://localhost:8080/api/users/${teacher.id}/enable`, { enable: updated.enable }).subscribe({
      next: () => {
        teacher.enable = updated.enable;
        this.showSuccess(updated.enable ? 'Profesor activado' : 'Profesor desactivado');
      },
      error: () => this.showError('Error al actualizar el estado'),
    });
  }

  getFilteredTeachers(): Teacher[] {
    const term = this.searchTerm.toLowerCase();
    const filtered = term
      ? this.teachers.filter(
          (t) =>
            t.name.toLowerCase().includes(term) ||
            t.surname.toLowerCase().includes(term) ||
            t.email.toLowerCase().includes(term) ||
            (t.username && t.username.toLowerCase().includes(term))
        )
      : this.teachers;

    return [...filtered].sort((a, b) => {
      const aHasSubject = this.getTeacherSubjectObjects(a).length > 0;
      const bHasSubject = this.getTeacherSubjectObjects(b).length > 0;

      if (aHasSubject !== bHasSubject) {
        return aHasSubject ? 1 : -1;
      }

      return `${a.name} ${a.surname}`.localeCompare(`${b.name} ${b.surname}`, 'es');
    });
  }

  get totalTeachers(): number {
    return this.teachers.length;
  }

  get activeTeachers(): number {
    return this.teachers.filter((t) => t.enable !== false).length;
  }

  get primarySubjects(): Subject[] {
    return this.allSubjects.filter((s) => (s.level || '').toLowerCase() === 'primaria');
  }

  get highSchoolSubjects(): Subject[] {
    return this.allSubjects.filter((s) => (s.level || '').toLowerCase() === 'bachillerato');
  }

  private showSuccess(message: string) {
    this.notificationType = 'success';
    this.notificationMessage = message;
    this.showNotification = true;
    setTimeout(() => (this.showNotification = false), 3500);
  }

  private showError(message: string) {
    this.notificationType = 'error';
    this.notificationMessage = message;
    this.showNotification = true;
    setTimeout(() => (this.showNotification = false), 4000);
  }
}
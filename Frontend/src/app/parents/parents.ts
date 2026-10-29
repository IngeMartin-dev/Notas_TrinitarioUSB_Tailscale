import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../services/auth.service';

interface StudentItem {
  id: number;
  name: string;
  surname: string;
  documentNumber?: string;
  grade: string;
  classGroup: string;
  hasParent: boolean;
  parentId?: number | null;
  parentName?: string | null;
}

interface ParentItem {
  id: number;
  name: string;
  surname: string;
  username?: string;
  email?: string;
  hasStudent: boolean;
  assignedStudentId?: number;
  assignedStudentName?: string;
}

@Component({
  selector: 'app-parents',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './parents.html',
  styleUrl: './parents.css'
})
export class Parents implements OnInit {
  allGrades: string[] = ['Grado 1º', 'Grado 2º', 'Grado 3º', 'Grado 4º', 'Grado 5º', 'Grado 6º', 'Grado 7º', 'Grado 8º', 'Grado 9º', 'Grado 10º', 'Grado 11º'];
  classrooms: string[] = ['Salon A', 'Salon B'];

  selectedGrade: string | null = null;
  selectedClassroom: string | null = null;

  allStudents: StudentItem[] = [];
  allParents: ParentItem[] = [];
  unlinkedParents: ParentItem[] = [];
  linkedParents: ParentItem[] = [];

  selectedStudent: StudentItem | null = null;
  selectedParent: ParentItem | null = null;

  editingParent: ParentItem | null = null;
  showEditParentModal = false;
  editParentForm: { name: string; email: string; surname: string; newPassword: string } = {
    name: '',
    email: '',
    surname: '',
    newPassword: ''
  };

  parentSaving = false;

  isLoading: boolean = false;

  showNotification: boolean = false;
  notificationType: 'success' | 'error' | null = null;
  notificationMessage: string = '';

  showDeleteConfirm = false;
  parentToDelete: ParentItem | null = null;

  showCreateParentModal = false;
  creatingParent = false;
  newParentForm: { name: string; surname: string; email: string; password: string; grade: string; classGroup: string } = {
    name: '',
    surname: '',
    email: '',
    password: '',
    grade: '',
    classGroup: ''
  };

  constructor(private http: HttpClient, private authService: AuthService) {}

  get totalStudentsCount(): number {
    return this.allStudents.length;
  }

  get availableStudentsCount(): number {
    return this.allStudents.filter((student) => !student.hasParent).length;
  }

  get linkedParentsCount(): number {
    return this.allParents.filter((parent) => parent.hasStudent).length;
  }

  get availableParentsCount(): number {
    return this.allParents.filter((parent) => !parent.hasStudent).length;
  }

  ngOnInit() {}

  selectGradeAndClassroom(grade: string, classroom: string) {
    if (this.selectedGrade === grade && this.selectedClassroom === classroom) {
      return;
    }

    this.selectedGrade = grade;
    this.selectedClassroom = classroom;
    this.selectedStudent = null;
    this.selectedParent = null;
    this.loadData();
  }

  private loadData() {
    if (!this.selectedGrade || !this.selectedClassroom) return;

    this.isLoading = true;
    const params: any = { grade: this.selectedGrade };
    if (this.selectedClassroom) {
      params.classroom = this.selectedClassroom;
    }

    this.http.get<any>('http://localhost:8080/api/parents/by-grade-classroom', { params })
      .subscribe({
        next: (response) => {
          console.log('[Parents] API response:', response);
          this.allStudents = (response.students || []).map((s: any) => ({
            id: s.id,
            name: s.name,
            surname: s.surname,
            documentNumber: s.documentNumber,
            grade: s.grade,
            classGroup: s.classGroup,
            hasParent: s.hasParent || false,
            parentId: s.parentId || null,
            parentName: s.parentName || null
          })).sort((a: StudentItem, b: StudentItem) => {
            if (a.hasParent !== b.hasParent) return a.hasParent ? 1 : -1;
            return (a.surname || '').toLowerCase().localeCompare((b.surname || '').toLowerCase(), 'es');
          });

          const mappedParents = (response.parents || []).map((p: any) => ({
            id: p.id,
            name: p.name,
            surname: p.surname,
            username: p.username,
            email: p.email,
            hasStudent: p.hasStudent || false,
            assignedStudentId: p.assignedStudentId || null,
            assignedStudentName: p.assignedStudentName || null
          }));

          this.unlinkedParents = mappedParents
            .filter((p: ParentItem) => !p.hasStudent)
            .sort((a: ParentItem, b: ParentItem) => (a.surname || '').toLowerCase().localeCompare((b.surname || '').toLowerCase(), 'es'));

          this.linkedParents = mappedParents
            .filter((p: ParentItem) => p.hasStudent)
            .sort((a: ParentItem, b: ParentItem) => (a.surname || '').toLowerCase().localeCompare((b.surname || '').toLowerCase(), 'es'));

          this.allParents = [...this.unlinkedParents, ...this.linkedParents];
          console.log('[Parents] allParents count:', this.allParents.length);
          console.log('[Parents] linked:', this.linkedParents.length, 'unlinked:', this.unlinkedParents.length);

          this.isLoading = false;
        },
        error: (err) => {
          console.error('Error loading parents data:', err);
          this.allStudents = [];
          this.allParents = [];
          this.isLoading = false;
          this.showErrorNotification('Error al cargar los datos de padres de familia');
        }
      });
  }

  selectStudent(student: StudentItem) {
    if (student.hasParent) return;
    this.selectedStudent = this.selectedStudent?.id === student.id ? null : student;
    this.selectedParent = null;
  }

   selectParent(parent: ParentItem) {
    if (this.selectedStudent === null) {
      this.showErrorNotification('Selecciona primero un estudiante');
      return;
    }
    if (parent.hasStudent) return;

    this.selectedParent = this.selectedParent?.id === parent.id ? null : parent;

    if (this.selectedStudent && this.selectedParent && this.selectedStudent?.id !== this.selectedParent?.assignedStudentId) {
      this.assignParentToStudent(this.selectedStudent, this.selectedParent);
    }
  }

  startEditParent(parent: ParentItem) {
    this.editingParent = parent;
    this.editParentForm = {
      name: parent.name || '',
      email: parent.email || '',
      surname: parent.surname || '',
      newPassword: ''
    };
    this.showEditParentModal = true;
  }

  saveParentEdit(parent: ParentItem) {
    this.parentSaving = true;
    const name = this.editParentForm.name.trim();
    const surname = this.editParentForm.surname.trim();
    const email = this.editParentForm.email.trim();
    const newPassword = this.editParentForm.newPassword.trim();

    const updatedUser: any = {
      name,
      surname,
      email,
      username: email,
      enable: true
    };

    this.http.put(`http://localhost:8080/api/users/${parent.id}`, updatedUser).subscribe({
      next: () => {
        if (newPassword) {
          this.http.post(`http://localhost:8080/api/users/${parent.id}/password/reset`, { newPassword }).subscribe({
            next: () => {
              this.showSuccessNotification('Padre de familia actualizado correctamente');
              this.closeEditModal();
              this.loadData();
            },
            error: (err: any) => {
              console.error('Failed to update password', err);
              this.showErrorNotification(err?.error?.error || err?.message || 'Perfil actualizado, pero no se pudo cambiar la contraseña');
              this.closeEditModal();
              this.loadData();
              this.parentSaving = false;
            }
          });
        } else {
          this.showSuccessNotification('Padre de familia actualizado correctamente');
          this.closeEditModal();
          this.loadData();
        }
      },
      error: (err: any) => {
        console.error('Failed to update parent', err);
        this.showErrorNotification(err?.error?.error || err?.message || 'No se pudo actualizar el padre de familia');
        this.parentSaving = false;
      }
    });
  }

  closeEditModal() {
    this.showEditParentModal = false;
    this.editingParent = null;
    this.editParentForm = { name: '', email: '', surname: '', newPassword: '' };
    this.parentSaving = false;
  }

  deleteParent(parent: ParentItem) {
    this.parentToDelete = parent;
    this.showDeleteConfirm = true;
  }

  confirmDelete() {
    if (!this.parentToDelete) return;

    const parentId = this.parentToDelete.id;
    this.showDeleteConfirm = false;

    this.http.delete(`http://localhost:8080/api/users/${parentId}`).subscribe({
      next: () => {
        this.showSuccessNotification('Padre de familia eliminado correctamente');
        this.loadData();
        this.parentToDelete = null;
      },
      error: (err: any) => {
        console.error('Delete parent failed', err);
        this.showErrorNotification(err?.error?.error || err?.message || 'No se pudo eliminar el padre de familia');
        this.parentToDelete = null;
      }
    });
  }

  closeDeleteConfirm() {
    this.showDeleteConfirm = false;
    this.parentToDelete = null;
  }

  private assignParentToStudent(student: StudentItem, parent: ParentItem) {
    this.http.post<any>('http://localhost:8080/api/parents/assign', {
      studentId: student.id,
      parentId: parent.id
    }).subscribe({
      next: (response) => {
        this.showSuccessNotification('Padre de familia asignado correctamente');
        this.selectedStudent = null;
        this.selectedParent = null;
        this.loadData();
      },
      error: (err) => {
        console.error('Error assigning parent:', err);
        this.showErrorNotification('Error al asignar el padre de familia');
        this.selectedParent = null;
      }
    });
  }

  unassignParent(student: StudentItem) {
    if (!student.hasParent || !student.parentId) return;

    this.http.delete<any>('http://localhost:8080/api/parents/unassign', {
      body: { studentId: student.id, parentId: student.parentId }
    }).subscribe({
      next: () => {
        this.showSuccessNotification('Asignacion removida correctamente');
        if (this.selectedStudent?.id === student.id) {
          this.selectedStudent = null;
        }
        this.loadData();
      },
      error: (err) => {
        console.error('Error unassigning parent:', err);
        this.showErrorNotification('Error al remover la asignacion');
      }
    });
  }

  clearSelection() {
    this.selectedStudent = null;
    this.selectedParent = null;
  }

  openCreateParentModal() {
    this.newParentForm = {
      name: '',
      surname: '',
      email: '',
      password: '',
      grade: this.selectedGrade || '',
      classGroup: this.selectedClassroom || ''
    };
    this.creatingParent = false;
    this.showCreateParentModal = true;
  }

  closeCreateParentModal() {
    this.showCreateParentModal = false;
    this.creatingParent = false;
  }

  createParent() {
    this.creatingParent = true;
    const name = this.newParentForm.name.trim();
    const surname = this.newParentForm.surname.trim();
    const email = this.newParentForm.email.trim();
    const password = this.newParentForm.password;

    if (!name || !surname || !email || !password) {
      this.showErrorNotification('Completa todos los campos');
      this.creatingParent = false;
      return;
    }

    if (name.length > 100) {
      this.showErrorNotification('El nombre no puede superar 100 caracteres');
      this.creatingParent = false;
      return;
    }

    if (surname.length > 100) {
      this.showErrorNotification('El apellido no puede superar 100 caracteres');
      this.creatingParent = false;
      return;
    }

    if (email.length > 200) {
      this.showErrorNotification('El correo no puede superar 200 caracteres');
      this.creatingParent = false;
      return;
    }

    if (password.length < 6 || password.length > 255) {
      this.showErrorNotification('La contraseña debe tener al menos 6 caracteres y no más de 255 caracteres');
      this.creatingParent = false;
      return;
    }

    this.http.post<any>('http://localhost:8080/api/parents', {
      name,
      surname,
      email,
      password,
      grade: this.newParentForm.grade,
      classroom: this.newParentForm.classGroup
    }).subscribe({
      next: (response) => {
        this.showSuccessNotification('Padre de familia creado correctamente');
        this.closeCreateParentModal();
        this.loadData();
      },
      error: (err: any) => {
        console.error('Failed to create parent', err);
        this.showErrorNotification(err?.error?.error || err?.message || 'No se pudo crear el padre de familia');
        this.creatingParent = false;
      }
    });
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

  hideNotification() {
    this.showNotification = false;
  }
}

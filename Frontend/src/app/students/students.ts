import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

interface Student {
  id: number;
  name: string;
  surname: string;
  grade: string;
  classGroup: string;
  documentNumber: string;
  active: boolean;
  hasParent?: boolean;
  parentName?: string;
}

@Component({
  selector: 'app-students',
  imports: [CommonModule, FormsModule],
  templateUrl: './students.html',
  styleUrl: './students.css',
})
export class Students implements OnInit {
  grades: string[] = ['Grado 1º', 'Grado 2º', 'Grado 3º', 'Grado 4º', 'Grado 5º', 'Grado 6º', 'Grado 7º', 'Grado 8º', 'Grado 9º', 'Grado 10º', 'Grado 11º'];
  classrooms: string[] = ['Salon A', 'Salon B'];
  
  selectedGrade: string | null = null;
  selectedClassroom: string | null = null;
  students: Student[] = [];
  isLoading: boolean = false;
  showPrintButton: boolean = false;
  
  studentToDelete: Student | null = null;
  studentToEdit: Student | null = null;
  
  // Add Student Modal Variables
  showAddStudentModal: boolean = false;
  newStudent: Partial<Student> = {
    name: '',
    surname: '',
    documentNumber: '',
    grade: '',
    classGroup: ''
  };
  isSavingStudent: boolean = false;
  
  // Edit Student Modal Variables
  showEditStudentModal: boolean = false;
  
  // Notification System Variables
  showNotification: boolean = false;
  notificationType: 'success' | 'error' | null = null;
  notificationMessage: string = '';

  constructor(private http: HttpClient) {}

  // ── Estudiantes sin salón (los que acaban de subir al adelantar el año) ──
  /** Estudiantes activos que ya están en su nuevo grado pero aún no tienen salón A/B. */
  sinSalonTodos: Student[] = [];
  /** Grado cuyo listado "Sin salón" se está mostrando (null = vista normal). */
  sinSalonGrade: string | null = null;
  asignando: { [id: number]: boolean } = {};

  ngOnInit() {
    this.loadSinSalon();
  }

  /** Carga todos los estudiantes y se queda con los que no tienen salón. */
  loadSinSalon() {
    this.http.get<Student[]>('http://localhost:8080/api/students').subscribe({
      next: (list) => {
        this.sinSalonTodos = (list || []).filter(st =>
          st.active !== false && !['Salon A', 'Salon B'].includes((st.classGroup || '').trim()) && /^Grado \d+º$/.test(st.grade || ''));
      },
      error: () => { this.sinSalonTodos = []; }
    });
  }

  sinSalonCount(grade: string): number {
    return this.sinSalonTodos.filter(st => st.grade === grade).length;
  }

  get sinSalonDelGrado(): Student[] {
    return this.sinSalonTodos
      .filter(st => st.grade === this.sinSalonGrade)
      .sort((a, b) => (a.surname || '').localeCompare(b.surname || '', 'es'));
  }

  selectSinSalon(grade: string) {
    this.sinSalonGrade = grade;
    this.selectedGrade = null;
    this.selectedClassroom = null;
    this.students = [];
    this.loadSinSalon();
  }

  /** Pulsar A o B: el estudiante pasa al instante a ese salón. */
  asignarSalon(student: Student, letra: 'A' | 'B') {
    if (this.asignando[student.id]) return;
    this.asignando[student.id] = true;
    this.http.post('http://localhost:8080/api/school-year/advance/assign-classrooms',
      { assignments: { [student.id]: letra } }).subscribe({
      next: () => {
        this.asignando[student.id] = false;
        this.sinSalonTodos = this.sinSalonTodos.filter(st => st.id !== student.id);
        this.showSuccessNotification(`${student.surname} ${student.name} pasó al Salon ${letra}`);
        if (this.sinSalonCount(this.sinSalonGrade || '') === 0) {
          this.sinSalonGrade = null;
        }
      },
      error: (err) => {
        this.asignando[student.id] = false;
        this.showErrorNotification(err?.status === 403
          ? 'Solo un administrador puede asignar salones.'
          : 'No se pudo asignar el salón. Intenta de nuevo.');
      }
    });
  }

  selectClassroom(grade: string, classroom: string) {
    this.sinSalonGrade = null;
    this.selectedGrade = grade;
    this.selectedClassroom = classroom;
    console.log('=== STUDENT SELECTION ===');
    console.log('Selected grade:', grade);
    console.log('Selected classroom:', classroom);
    this.fetchStudents(grade, classroom);
  }

  /** "Grado 1º" -> "primero", "Grado 2º" -> "segundo"... (para el mensaje de salón vacío). */
  get selectedGradeWord(): string {
    const nombres = ['', 'primero', 'segundo', 'tercero', 'cuarto', 'quinto', 'sexto',
      'séptimo', 'octavo', 'noveno', 'décimo', 'once'];
    const n = Number((this.selectedGrade || '').match(/\d+/)?.[0]);
    return nombres[n] || (this.selectedGrade || '');
  }

  private showLoading() {
    // Remove fade-out class if it exists to ensure smooth re-appearance
    const loadingElement = document.querySelector('.loading-indicator') as HTMLElement;
    if (loadingElement) {
      loadingElement.classList.remove('fade-out');
    }
    
    // Hide print button immediately when starting new load
    this.hidePrintButtonDelayed();
    
    this.isLoading = true;
  }

  private hideLoading() {
    const loadingElement = document.querySelector('.loading-indicator') as HTMLElement;
    if (loadingElement) {
      loadingElement.classList.add('fade-out');
      setTimeout(() => {
        this.isLoading = false;
        // Show print button with delay after loading disappears
        setTimeout(() => {
          this.showPrintButtonDelayed();
        }, 300); // Delay before showing print button
      }, 600); // Duration of the fade-out animation
    } else {
      this.isLoading = false;
      // Show print button immediately if no loading animation
      setTimeout(() => {
        this.showPrintButtonDelayed();
      }, 300);
    }
  }

  private showPrintButtonDelayed() {
    // Remove slide-out class if it exists to ensure smooth re-appearance
    const printButtonContainer = document.querySelector('.print-button-container') as HTMLElement;
    if (printButtonContainer) {
      printButtonContainer.classList.remove('slide-out');
    }
    this.showPrintButton = true;
  }

  private hidePrintButtonDelayed() {
    const printButtonContainer = document.querySelector('.print-button-container') as HTMLElement;
    if (printButtonContainer) {
      printButtonContainer.classList.add('slide-out');
      setTimeout(() => {
        this.showPrintButton = false;
      }, 500); // Duration of the slide-out animation
    } else {
      this.showPrintButton = false;
    }
  }

  fetchStudents(grade: string, classroom: string) {
    console.log('=== FETCHING STUDENTS FROM DATABASE ===');
    this.showLoading();

    const directUrl = `http://localhost:8080/api/students/grade/${encodeURIComponent(grade)}/class/${encodeURIComponent(classroom)}`;
    console.log('Trying direct URL:', directUrl);

    this.http.get<Student[]>(directUrl)
      .subscribe({
        next: (students) => {
          console.log('Students fetched from database:', students.length, 'students');

          const baseList = students || [];

          if (baseList.length === 0) {
            this.students = [];
            setTimeout(() => this.hideLoading(), 800);
            return;
          }

          const parentEndpoint = `http://localhost:8080/api/parents/by-grade-classroom?grade=${encodeURIComponent(grade)}&classroom=${encodeURIComponent(classroom)}`;

          this.http.get<any>(parentEndpoint)
            .subscribe({
              next: (parentData) => {
                const parentMap = new Map<number, { hasParent: boolean; parentName: string | null }>();
                (parentData.students || []).forEach((s: any) => {
                  parentMap.set(s.id, {
                    hasParent: !!s.hasParent,
                    parentName: s.parentName || null
                  });
                });

                this.students = baseList.map((s: any) => {
                  const info = parentMap.get(s.id);
                  return {
                    ...s,
                    hasParent: info ? info.hasParent : false,
                    parentName: info ? info.parentName : null,
                  };
                })
                  .sort((a, b) => {
                    const surnameA = (a.surname || '').toLowerCase();
                    const surnameB = (b.surname || '').toLowerCase();
                    return surnameA.localeCompare(surnameB);
                  });

                setTimeout(() => this.hideLoading(), 800);
              },
              error: () => {
                this.students = baseList.map((s: any) => ({
                  ...s,
                  hasParent: false,
                  parentName: null,
                }))
                  .sort((a, b) => (a.surname || '').toLowerCase().localeCompare((b.surname || '').toLowerCase()));
                setTimeout(() => this.hideLoading(), 800);
              }
            });
        },
        error: (error) => {
          console.error('Failed to fetch students:', error);
          this.students = [];
          setTimeout(() => this.hideLoading(), 800);
        }
      });
  }

  printStudentsPDF() {
    console.log('=== GENERATING PDF WITH HIDDEN IFRAME (NO NEW TABS) ===');
    
    if (this.students.length === 0) {
      this.showErrorNotification('No hay estudiantes para imprimir');
      return;
    }
    
    // Generate clean HTML with maximum print optimization
    const printContent = this.generateCleanHTML();
    
    // Create hidden iframe for printing (no new tabs/windows)
    const iframe = document.createElement('iframe');
    iframe.style.position = 'absolute';
    iframe.style.width = '0px';
    iframe.style.height = '0px';
    iframe.style.border = 'none';
    
    // Add to DOM (hidden)
    document.body.appendChild(iframe);
    
    // Wait for iframe to load
    iframe.onload = () => {
      setTimeout(() => {
        // Print from iframe
        if (iframe.contentWindow) {
          iframe.contentWindow.print();
          console.log('✅ Print command sent from hidden iframe');
        }
        
        // Clean up after printing
        document.body.removeChild(iframe);
      }, 500);
    };
    
    // Write content to iframe
    if (iframe.contentDocument) {
      iframe.contentDocument.write(printContent);
      iframe.contentDocument.close();
    }
  }
  
  private generateCleanHTML(): string {
    return `
      <!DOCTYPE html>
      <html>
      <head>
        <title>Lista de Estudiantes</title>
        <style>
          @page {
            margin: 15mm;
            size: A4;
          }
          
          * {
            -webkit-print-color-adjust: exact !important;
            color-adjust: exact !important;
          }
          
          body {
            font-family: Arial, sans-serif;
            margin: 0;
            padding: 20px;
            background: white;
            color: #000;
          }
          
          .header {
            text-align: center;
            margin-bottom: 30px;
            padding: 20px;
            border: 2px solid #007bff;
            border-radius: 8px;
            background: #f8f9fa;
          }
          
          .school-name {
            font-size: 24px;
            font-weight: bold;
            color: #007bff;
            margin-bottom: 10px;
            text-transform: uppercase;
          }
          
          .document-title {
            font-size: 18px;
            font-weight: 600;
            color: #333;
            margin-bottom: 5px;
          }
          
          .subtitle {
            font-size: 14px;
            color: #666;
            font-style: italic;
          }
          
          .info-section {
            margin: 20px 0;
            padding: 15px;
            background: #f8f9fa;
            border-radius: 5px;
            border: 1px solid #dee2e6;
          }
          
          .info-row-horizontal {
            display: flex;
            justify-content: space-between;
            font-size: 14px;
            gap: 20px;
          }
          
          .info-item {
            display: flex;
            align-items: center;
            gap: 5px;
            flex: 1;
          }
          
          .info-label {
            font-weight: bold;
            color: #007bff;
            white-space: nowrap;
          }
          
          table {
            width: 100%;
            border-collapse: collapse;
            border: 2px solid #000;
            margin-top: 20px;
          }
          
          th, td {
            border: 1px solid #000;
            padding: 10px;
            text-align: left;
            font-size: 12px;
          }
          
          th {
            background: #007bff;
            color: white;
            font-weight: bold;
            text-align: center;
            text-transform: uppercase;
          }
          
          .number-col {
            text-align: center;
            font-weight: bold;
            width: 8%;
          }
          
          .name-col {
            width: 42%;
          }
          
          .doc-col {
            width: 25%;
          }
          
          .classroom-col {
            width: 15%;
          }
          
          .status-col {
            text-align: center;
            width: 10%;
          }
          
          tr:nth-child(even) {
            background-color: #f8f9fa;
          }
        </style>
      </head>
      <body>
        <div class="header">
          <div class="school-name">COLEGIO TRINITARIO</div>
          <div class="document-title">Lista de Estudiantes</div>
          <div class="subtitle">Sistema de Gestión Académica</div>
        </div>
        
        <div class="info-section">
          <div class="info-row-horizontal">
            <div class="info-item">
              <span class="info-label">Grado:</span>
              <span>${this.selectedGrade}</span>
            </div>
            <div class="info-item">
              <span class="info-label">Salón:</span>
              <span>${this.selectedClassroom}</span>
            </div>
            <div class="info-item">
              <span class="info-label">Total Estudiantes:</span>
              <span>${this.students.length}</span>
            </div>
            <div class="info-item">
              <span class="info-label">Fecha:</span>
              <span>${new Date().toLocaleDateString('es-ES')}</span>
            </div>
          </div>
        </div>
        
        <table>
          <thead>
            <tr>
              <th class="number-col">#</th>
              <th class="name-col">Nombre Completo</th>
              <th class="doc-col">Documento</th>
          <th class="classroom-col">Salón</th>
               <th class="status-col">Estado</th>
             </tr>
           </thead>
           <tbody>
             ${this.students.map((student, index) => `
               <tr>
                 <td class="number-col">${index + 1}</td>
                 <td class="name-col">${student.surname} ${student.name}</td>
                 <td class="doc-col">${student.documentNumber || 'No proporcionado'}</td>
                 <td class="classroom-col">${student.classGroup}</td>
                 <td class="status-col">${student.active ? 'Activo' : 'Inactivo'}</td>
               </tr>
             `).join('')}
          </tbody>
        </table>
      </body>
      </html>
    `;
  }

  // Modal Methods
  openAddStudentModal() {
    if (!this.selectedGrade || !this.selectedClassroom) {
      console.warn('Cannot open modal: No grade or classroom selected');
      return;
    }

    console.log('=== OPENING ADD STUDENT MODAL ===');
    console.log('Selected grade:', this.selectedGrade);
    console.log('Selected classroom:', this.selectedClassroom);
    
    // Reset form data
    this.newStudent = {
      name: '',
      surname: '',
      documentNumber: '',
      grade: this.selectedGrade,
      classGroup: this.selectedClassroom
    };
    
    this.showAddStudentModal = true;
    console.log('Modal opened successfully');
  }

  closeAddStudentModal() {
    console.log('=== CLOSING ADD STUDENT MODAL ===');
    this.showAddStudentModal = false;
    
    // Reset form data
    this.newStudent = {
      name: '',
      surname: '',
      documentNumber: '',
      grade: '',
      classGroup: ''
    };
    
    this.isSavingStudent = false;
    console.log('Modal closed successfully');
  }

  openEditStudentModal(student: Student) {
    console.log('=== OPENING EDIT STUDENT MODAL ===');
    
    this.studentToEdit = { ...student };
    this.showEditStudentModal = true;
    console.log('Edit modal opened for student:', student);
  }

  closeEditStudentModal() {
    console.log('=== CLOSING EDIT STUDENT MODAL ===');
    this.showEditStudentModal = false;
    this.studentToEdit = null;
    this.isSavingStudent = false;
    console.log('Edit modal closed successfully');
  }

  updateStudent() {
    console.log('=== UPDATING STUDENT ===');
    
    if (!this.studentToEdit) return;
    
    if (!this.studentToEdit.name || !this.studentToEdit.surname) {
      this.showErrorNotification('Por favor complete los campos obligatorios (nombres y apellidos)');
      return;
    }

    const updateUrl = `http://localhost:8080/api/students/${this.studentToEdit.id}`;
    
    this.isSavingStudent = true;
    
    const updatedStudentData = {
      name: this.studentToEdit.name?.trim(),
      surname: this.studentToEdit.surname?.trim(),
      documentNumber: this.studentToEdit.documentNumber?.trim() || null,
      grade: this.studentToEdit.grade,
      classGroup: this.studentToEdit.classGroup || null,
      active: this.studentToEdit.active
    };

    this.http.put(updateUrl, updatedStudentData).subscribe({
      next: (response) => {
        console.log('✅ Student updated successfully:', response);
        
        const studentName = `${updatedStudentData.name} ${updatedStudentData.surname}`;
        this.showSuccessNotification(`El estudiante "${studentName}" fue actualizado correctamente`);
        
        this.closeEditStudentModal();
        this.loadSinSalon();
        
        if (this.selectedGrade && this.selectedClassroom) {
          setTimeout(() => {
            console.log('Refreshing student list...');
            this.fetchStudents(this.selectedGrade!, this.selectedClassroom!);
          }, 500);
        }
      },
      error: (error) => {
        console.error('❌ Failed to update student:', error);
        this.showErrorNotification(error?.error?.error || 'Error al actualizar el estudiante. Favor intentarlo más tarde.');
        this.isSavingStudent = false;
      }
    });
  }

  addStudent() {
    console.log('=== ADDING NEW STUDENT ===');
    
    // Validation
    if (!this.newStudent.name || !this.newStudent.surname) {
      this.showErrorNotification('Por favor complete los campos obligatorios (nombres y apellidos)');
      return;
    }
    
    if (!this.selectedGrade || !this.selectedClassroom) {
      this.showErrorNotification('Error: No hay grado o salón seleccionado');
      return;
    }

    // Prepare student data
    const studentData = {
      name: this.newStudent.name?.trim(),
      surname: this.newStudent.surname?.trim(),
      grade: this.selectedGrade,
      classGroup: this.selectedClassroom,
      documentNumber: this.newStudent.documentNumber?.trim() || null
    };

    console.log('Student data to save:', studentData);
    
    // Set saving state
    this.isSavingStudent = true;
    
    // Send to backend
    const saveUrl = 'http://localhost:8080/api/students';
    
    this.http.post(saveUrl, studentData).subscribe({
      next: (response) => {
        console.log('✅ Student added successfully:', response);
        console.log('Response:', response);
        
        // Close modal
        this.closeAddStudentModal();
        
        // Show success notification
        const studentName = `${studentData.name} ${studentData.surname}`;
        this.showSuccessNotification(`El estudiante "${studentName}" fue agregado correctamente`);
        
        // Refresh the student list
        if (this.selectedGrade && this.selectedClassroom) {
          setTimeout(() => {
            console.log('Refreshing student list...');
            this.fetchStudents(this.selectedGrade!, this.selectedClassroom!);
          }, 500);
        }
      },
      error: (error) => {
        console.error('❌ Failed to add student:', error);
        console.error('Error details:', {
          status: error.status,
          message: error.message,
          error: error.error
        });
        
        // Show error message
        let errorMessage = 'Error al agregar el estudiante. ';
        if (error?.status === 409 && error?.error?.error) {
          this.showErrorNotification(error.error.error);
          this.isSavingStudent = false;
          return;
        }
        if (error.status === 0) {
          errorMessage += 'Verifique que el servidor backend esté ejecutándose.';
        } else if (error.status === 400) {
          errorMessage += 'Datos inválidos. Por favor verifique la información.';
        } else {
          errorMessage += `Error del servidor: ${error.status}`;
        }
        
        this.showErrorNotification('Ha ocurrido un error al agregar al estudiante, favor intentarlo más tarde');
        this.isSavingStudent = false;
      }
    });
  }

  // Notification System Methods
  showSuccessNotification(message: string) {
    this.notificationType = 'success';
    this.notificationMessage = message;
    this.showNotification = true;
    
    // Auto-hide after 3 seconds
    setTimeout(() => {
      this.hideNotification();
    }, 3000);
  }

  showErrorNotification(message: string) {
    this.notificationType = 'error';
    this.notificationMessage = message;
    this.showNotification = true;
    
    // Auto-hide after 4 seconds (longer for error messages)
    setTimeout(() => {
      this.hideNotification();
    }, 4000);
  }

  hideNotification() {
    this.showNotification = false;
    setTimeout(() => {
      this.notificationType = null;
      this.notificationMessage = '';
    }, 300); // Wait for fade-out animation
  }

  deleteStudent(student: Student) {
    this.studentToDelete = student;
  }

  confirmDelete() {
    if (!this.studentToDelete) return;

    const student = this.studentToDelete;
    const deleteUrl = `http://localhost:8080/api/students/${student.id}`;

    this.http.delete(deleteUrl).subscribe({
      next: () => {
        this.students = this.students.filter(s => s.id !== student.id);
        this.showSuccessNotification(`El estudiante "${student.surname} ${student.name}" fue eliminado correctamente`);
        this.closeDeleteModal();
      },
      error: (error) => {
        console.error('❌ Failed to delete student:', error);
        this.showErrorNotification('Error al eliminar el estudiante. Favor intentarlo más tarde.');
        this.closeDeleteModal();
      }
    });
  }

  closeDeleteModal() {
    this.studentToDelete = null;
  }

  toggleStudentStatus(student: Student) {
    student.active = !student.active;
    const updateUrl = `http://localhost:8080/api/students/${student.id}`;
    const updatedStudent = { ...student };

    this.http.put(updateUrl, updatedStudent).subscribe({
      next: () => {
        const statusText = updatedStudent.active ? 'activo' : 'inactivo';
        this.showSuccessNotification(`El estudiante "${student.surname} ${student.name}" ahora está ${statusText}`);
      },
      error: (error) => {
        student.active = !student.active;
        console.error('❌ Failed to update student status:', error);
        this.showErrorNotification('Error al actualizar el estado del estudiante. Favor intentarlo más tarde.');
      }
    });
  }
}
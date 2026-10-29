import { Component, OnInit, AfterViewInit, inject, OnDestroy, ChangeDetectionStrategy, ChangeDetectorRef, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { GradesUpdateService } from '../services/grades-update.service';
import { GlobalRealtimeService } from '../services/global-realtime.service';
import { AuthService } from '../services/auth.service';
import { DialogService } from '../services/dialog.service';
import { Subscription } from 'rxjs';

interface Subject {
  id: number;
  name: string;
  code: string;
  level: string;
  gradeMin?: number;
  gradeMax?: number;
  teacherId?: number | null;
  teacher?: number | null;
}

interface Student {
  id: number;
  name: string;
  surname: string;
  grade: string;
  classGroup: string;
}

interface RecoveryPlan {
  id: number;
  studentId: number;
  studentName: string;
  subjectName: string;
  period: number;
  topics: string;
  planContent: string;
  createdAt: Date;
  grade: string;
  classGroup: string;
  durationDays: number;
  day1Content: string;
  day2Content: string;
}

interface StudentWithRecovery {
  student: Student;
  recoveryPlan: RecoveryPlan | null;
  finalGrade: number;
  period: number;
}

@Component({
  selector: 'app-recoveries',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './recoveries.html',
  styleUrl: './recoveries.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Recoveries implements OnInit, AfterViewInit {
  allGrades: string[] = ['Grado 1º', 'Grado 2º', 'Grado 3º', 'Grado 4º', 'Grado 5º', 'Grado 6º', 'Grado 7º', 'Grado 8º', 'Grado 9º', 'Grado 10º', 'Grado 11º'];
  grades: string[] = [...this.allGrades];
  classrooms: string[] = ['Salon A', 'Salon B'];

  selectedGrade: string | null = null;
   teacherGradeRange: { min: number; max: number } | null = null;
hasTeacherGradeRange: boolean | null = null; // null = checking, false = no subjects, true = has subjects
    selectedClassroom: string | null = null;
  selectedPeriod: number = 1;
  availablePeriods: number[] = [1];

  teacherSubjects: Subject[] = [];
  selectedSubject: string | null = null;
  isFadingOut = false;
  isFadingIn = false;

  students: Student[] = [];

  isLoading: boolean = false;
  showLoadingScreen: boolean = false;

  studentsWithRecovery: StudentWithRecovery[] = [];
  allRecoveryPlans: RecoveryPlan[] = [];

  currentYear: number = new Date().getFullYear();

  showPlanModal: boolean = false;
  selectedPlan: RecoveryPlan | null = null;
  safePlanContent: SafeHtml | null = null;

  filterGrade: string | null = null;
  filterClassroom: string | null = null;
  filterPeriod: number | null = null;

  private gradesUpdateService = inject(GradesUpdateService);
  private realtimeService = inject(GlobalRealtimeService);
  private authService = inject(AuthService);
  private dialogService = inject(DialogService);

  // Nota mínima para aprobar, configurable en Configuración de Año (Administrador).
  minPassingGrade = 3.5;

  private loadMinPassingGrade() {
    this.http.get<{ minPassingGrade: number }>('http://localhost:8080/api/school-year/min-passing-grade')
      .subscribe({
        next: (res) => {
          if (typeof res.minPassingGrade === 'number') {
            this.minPassingGrade = res.minPassingGrade;
          }
        },
        error: () => { /* se queda con el valor por defecto 3.5 */ }
      });
  }

  constructor(private http: HttpClient, private cdr: ChangeDetectorRef, private ngZone: NgZone, private sanitizer: DomSanitizer) {}

  private getCurrentUser(): any {
    const current = this.authService.getCurrentUserValue();
    if (current) return current;
    try {
      return JSON.parse(localStorage.getItem('currentUser') || 'null');
    } catch {
      return null;
    }
  }

  private getCurrentTeacherId(): number | null {
    const user = this.getCurrentUser();
    const roleName = user?.role?.name || user?.role;
    return user?.id && roleName === 'TEACHER' ? Number(user.id) : null;
  }

  private teacherIdQuery(): string {
    const teacherId = this.getCurrentTeacherId();
    return teacherId ? `?teacherId=${teacherId}` : '';
  }

  private teacherIdParam(): string {
    const teacherId = this.getCurrentTeacherId();
    return teacherId ? `&teacherId=${teacherId}` : '';
  }

  private periodCheckInterval: any = null;
  private periodsSubscription: Subscription | null = null;
  private previousAvailablePeriods: number[] = [1];

  ngOnInit() {
    this.loadTeacherGradeRange();
    this.loadTeacherSubjectsForGradeRange();
    this.loadUnlockedPeriods();
    this.loadMinPassingGrade();
    this.filterGrade = null;
    this.filterClassroom = null;
    this.filterPeriod = null;
    this.selectedGrade = null;
    this.selectedClassroom = null;
    this.selectedPeriod = 1;
    
    // Subscribe to global realtime updates
    this.periodsSubscription = this.realtimeService.periods$.subscribe(periods => {
      const unlockedPeriods = periods
        .filter(p => p.isUnlocked)
        .map(p => p.periodNumber);
      const newPeriods = unlockedPeriods.length > 0 ? unlockedPeriods : [1];
      
      if (!this.arraysEqual(this.availablePeriods, newPeriods)) {
        this.availablePeriods = newPeriods;
        this.showPeriodChangeNotification();
        
        if (!this.availablePeriods.includes(this.selectedPeriod)) {
          this.selectedPeriod = Math.max(...this.availablePeriods);
          this.loadStudentsWithRecovery();
        }
      }
    });

    this.loadAllRecoveryPlans();

    // Poll for period changes every 10 seconds for real-time updates
    this.ngZone.runOutsideAngular(() => {
      this.periodCheckInterval = setInterval(() => {
        const previousPeriods = [...this.availablePeriods];
        this.http.get<any[]>('http://localhost:8080/api/periods').subscribe({
          next: (periods) => {
            const unlockedPeriods = periods
              .filter(p => p.isUnlocked)
              .map(p => p.periodNumber);
            const newPeriods = unlockedPeriods.length > 0 ? unlockedPeriods : [1];

            if (!this.arraysEqual(previousPeriods, newPeriods)) {
              this.ngZone.run(() => {
                this.availablePeriods = newPeriods;
                this.showPeriodChangeNotification();

                if (!this.availablePeriods.includes(this.selectedPeriod)) {
                  this.selectedPeriod = Math.max(...this.availablePeriods);
                  this.loadStudentsWithRecovery();
                }
              });
            }
          }
        });
      }, 10000);
    });
  }

  private arraysEqual(a: number[], b: number[]): boolean {
    if (a.length !== b.length) return false;
    const sortedA = [...a].sort();
    const sortedB = [...b].sort();
    return sortedA.every((val, idx) => val === sortedB[idx]);
  }

  loadTeacherGradeRange() {
    const json = localStorage.getItem('selectedTeacherGradeRange');
    if (!json) {
      this.teacherGradeRange = null;
      this.grades = [...this.allGrades];
      return;
    }

    try {
      const parsed = JSON.parse(json);
      if (
        parsed &&
        typeof parsed.min === 'number' &&
        typeof parsed.max === 'number' &&
        parsed.min > 0 &&
        parsed.max >= parsed.min
      ) {
        this.teacherGradeRange = { min: parsed.min, max: parsed.max };
        this.grades = this.allGrades.filter((grade) => {
          const parsedGrade = this.parseGradeLabel(grade);
          return parsedGrade !== null && parsedGrade >= parsed.min && parsedGrade <= parsed.max;
        });
        if (this.selectedGrade && !this.grades.includes(this.selectedGrade)) {
          this.selectedGrade = null;
          this.selectedClassroom = null;
        }
        return;
      }
    } catch {
      // ignore invalid payload
    }

    this.teacherGradeRange = null;
    this.grades = [...this.allGrades];
  }

  private parseGradeLabel(grade: string): number | null {
    const match = grade.match(/(\d+)/);
    return match ? parseInt(match[1], 10) : null;
  }

  isTeacher(): boolean {
    const user = this.authService.getCurrentUserValue();
    return !!(user && user.role && (user.role.name || user.role) === 'TEACHER');
  }

  loadTeacherSubjectsForGradeRange() {
    const currentUser = this.authService.getCurrentUserValue();
    if (!currentUser || !currentUser.role) {
      this.hasTeacherGradeRange = false;
      return;
    }

    const roleName = currentUser.role.name || currentUser.role;
    if (roleName !== 'TEACHER' || !currentUser.id) {
      this.hasTeacherGradeRange = false;
      return;
    }

    this.http.get<Subject[]>(`http://localhost:8080/api/subjects/teacher/${currentUser.id}`).subscribe({
      next: (subjects) => {
        this.teacherSubjects = subjects;
        this.selectedSubject = subjects.find(s => s.gradeMin != null && s.gradeMax != null)?.name || subjects[0]?.name || null;
        setTimeout(() => {
          this.applyGradeFilterForSubject(this.selectedSubject || '', false);
        }, 600);
        this.cdr.markForCheck();
      },
      error: () => {
        setTimeout(() => {
          this.teacherGradeRange = null;
          this.grades = [...this.allGrades];
          this.hasTeacherGradeRange = false;
          this.cdr.markForCheck();
        }, 600);
      }
    });
  }

  onSubjectChange(subjectName: string) {
    this.selectedSubject = subjectName;
    this.applyGradeFilterForSubject(subjectName);
    if (this.selectedGrade && this.selectedClassroom) {
      this.loadStudentsWithRecovery();
    }
  }

  private applyGradeFilterForSubject(subjectName: string, animate = true) {
    const subject = this.teacherSubjects.find(s => s.name === subjectName);
    let newGrades: string[] = [];

    if (subject && subject.gradeMin != null && subject.gradeMax != null) {
      const minGrade = subject.gradeMin;
      const maxGrade = subject.gradeMax;
      newGrades = this.allGrades.filter((grade) => {
        const parsedGrade = this.parseGradeLabel(grade);
        return parsedGrade !== null && parsedGrade >= minGrade && parsedGrade <= maxGrade;
      });
      this.teacherGradeRange = { min: minGrade, max: maxGrade };
      this.hasTeacherGradeRange = true;
    } else {
      const withRange = this.teacherSubjects.filter(s => s.gradeMin != null && s.gradeMax != null);
      if (withRange.length > 0) {
        const minGrade = Math.min(...withRange.map(s => s.gradeMin!));
        const maxGrade = Math.max(...withRange.map(s => s.gradeMax!));
        this.teacherGradeRange = { min: minGrade, max: maxGrade };
        newGrades = this.allGrades.filter((grade) => {
          const parsedGrade = this.parseGradeLabel(grade);
          return parsedGrade !== null && parsedGrade >= minGrade && parsedGrade <= maxGrade;
        });
        this.hasTeacherGradeRange = true;
      } else {
        this.teacherGradeRange = null;
        newGrades = [...this.allGrades];
        this.hasTeacherGradeRange = false;
      }
    }

    if (this.selectedGrade && !newGrades.includes(this.selectedGrade)) {
      this.selectedGrade = null;
      this.selectedClassroom = null;
    }

    if (!animate) {
      this.grades = newGrades;
      this.cdr.markForCheck();
      return;
    }

    const changed = !this.stringArraysEqual(this.grades, newGrades);
    if (!changed) return;

    this.isFadingOut = true;
    this.isFadingIn = false;

    setTimeout(() => {
      this.grades = newGrades;
      this.isFadingOut = false;
      this.isFadingIn = true;
      this.cdr.markForCheck();

      setTimeout(() => {
        this.isFadingIn = false;
        this.cdr.markForCheck();
      }, 300);
    }, 300);
  }

  private stringArraysEqual(a: string[], b: string[]): boolean {
    if (a.length !== b.length) return false;
    for (let i = 0; i < a.length; i++) {
      if (a[i] !== b[i]) return false;
    }
    return true;
  }

  private showPeriodChangeNotification() {
    this.cdr.detectChanges();
    
    // Remove any existing toast first
    const existingToast = document.querySelector('.period-change-toast');
    if (existingToast) existingToast.remove();
    
    const toast = document.createElement('div');
    toast.className = 'period-change-toast';
    toast.innerHTML = '<span class="toast-icon">🔔</span> Los períodos se han actualizado';
    toast.style.cssText = `
      position: fixed;
      top: 80px;
      right: 20px;
      background: linear-gradient(135deg, #4CAF50, #45a049);
      color: white;
      padding: 12px 24px;
      border-radius: 8px;
      box-shadow: 0 4px 15px rgba(0,0,0,0.2);
      z-index: 10000;
      animation: slideIn 0.2s ease-out;
      font-family: Arial, sans-serif;
      font-size: 14px;
    `;
    
    const style = document.createElement('style');
    style.textContent = `
      @keyframes slideIn {
        from { transform: translateX(100%); opacity: 0; }
        to { transform: translateX(0); opacity: 1; }
      }
      @keyframes slideOut {
        from { transform: translateX(0); opacity: 1; }
        to { transform: translateX(100%); opacity: 0; }
      }
    `;
    document.head.appendChild(style);
    document.body.appendChild(toast);
    
    setTimeout(() => {
      toast.style.animation = 'slideOut 0.2s ease-in forwards';
      setTimeout(() => toast.remove(), 200);
    }, 500);
  }

  ngOnDestroy() {
    if (this.periodCheckInterval) {
      clearInterval(this.periodCheckInterval);
    }
    if (this.periodsSubscription) {
      this.periodsSubscription.unsubscribe();
    }
  }

  ngAfterViewInit() {}

  loadUnlockedPeriods() {
    this.http.get<any[]>('http://localhost:8080/api/periods').subscribe({
      next: (periods) => {
        const unlockedPeriods = periods
          .filter(p => p.isUnlocked)
          .map(p => p.periodNumber);
        this.availablePeriods = unlockedPeriods.length > 0 ? unlockedPeriods : [1];
        
        // Also save to localStorage
        localStorage.setItem('unlockedPeriods', JSON.stringify(unlockedPeriods.filter(p => p > 1)));
        this.cdr.markForCheck();
      },
      error: () => {
        const stored = localStorage.getItem('unlockedPeriods');
        this.availablePeriods = stored ? [1, ...JSON.parse(stored).filter((p: number) => p > 1)] : [1];
        this.cdr.markForCheck();
      }
    });
  }

  loadAllRecoveryPlans() {
    this.http.get<RecoveryPlan[]>(`http://localhost:8080/api/grades/recovery-plans${this.teacherIdQuery()}`).subscribe({
      next: (plans) => {
        this.allRecoveryPlans = plans;
        this.applyFilters();
        this.cdr.markForCheck();
      },
      error: () => {
        this.allRecoveryPlans = [];
        this.cdr.markForCheck();
      }
    });
  }

  selectGrade(grade: string, classroom: string) {
    this.selectedGrade = grade;
    this.selectedClassroom = classroom;

    this.loadUnlockedPeriods();
    this.selectedPeriod = Math.max(...this.availablePeriods);

    this.showLoadingScreen = true;
    this.isLoading = true;

    setTimeout(() => {
      this.loadData();
    }, 800);
  }

  onPeriodChange(period: number) {
    const newPeriod = parseInt(period.toString());
    
    if (!this.availablePeriods.includes(newPeriod)) {
      this.dialogService.alert('Este período aún no está desbloqueado. Ve a Configuración para desbloquearlo.', 'Período bloqueado');
      return;
    }
    
    const previousPeriod = this.selectedPeriod;
    
    if (newPeriod > previousPeriod) {
      const lastPeriod = Math.max(...this.availablePeriods);
      this.selectedPeriod = lastPeriod;
      this.studentsWithRecovery = [];
      this.loadStudentsWithRecovery();
    } else {
      this.selectedPeriod = newPeriod;
      this.loadStudentsWithRecovery();
    }
  }

  onPeriodManualChange(period: number) {
    this.onPeriodChange(period);
  }

  loadData() {
    if (!this.selectedGrade || !this.selectedClassroom) return;

    const url = `http://localhost:8080/api/students/grade/${encodeURIComponent(this.selectedGrade!)}/class/${encodeURIComponent(this.selectedClassroom!)}`;

    this.http.get<Student[]>(url).subscribe({
      next: (students) => {
        this.students = students.sort((a, b) => (a.surname || '').localeCompare(b.surname || ''));
        this.loadStudentsWithRecovery();
        this.cdr.markForCheck();
      },
      error: () => {
        this.students = [];
        this.isLoading = false;
        this.showLoadingScreen = false;
        this.cdr.markForCheck();
      }
    });
  }

  loadStudentsWithRecovery() {
    if (!this.selectedGrade || !this.selectedClassroom || !this.selectedPeriod) {
      this.applyFilters();
      return;
    }
    
    const url = `http://localhost:8080/api/students/grade/${encodeURIComponent(this.selectedGrade!)}/class/${encodeURIComponent(this.selectedClassroom!)}`;
    
    this.http.get<Student[]>(url).subscribe({
      next: (students) => {
        const sortedStudents = students.sort((a, b) => (a.surname || '').localeCompare(b.surname || ''));
        this.loadGradesForStudents(sortedStudents);
        this.cdr.markForCheck();
      },
      error: () => {
        this.studentsWithRecovery = [];
        this.isLoading = false;
        this.showLoadingScreen = false;
        this.cdr.markForCheck();
      }
    });
  }

  loadGradesForStudents(students: Student[]) {
    const subjectParam = this.selectedSubject ? `&subjectName=${encodeURIComponent(this.selectedSubject)}` : '';
    const url = `http://localhost:8080/api/grades/classroom?grade=${encodeURIComponent(this.selectedGrade!)}&classroom=${encodeURIComponent(this.selectedClassroom!)}&period=${this.selectedPeriod}${this.teacherIdParam()}${subjectParam}`;
    
    this.http.get<any>(url).subscribe({
      next: (response) => {
        const gradesData = response.grades || [];
        
        this.studentsWithRecovery = students.map(student => {
          const studentGrades = gradesData.filter((g: any) => g.student?.id === student.id);
          let finalGrade = this.calculateFinalGrade(studentGrades);
          
          const recoveryPlan = this.allRecoveryPlans.find(p => 
            p.studentId === student.id && p.period === this.selectedPeriod
          );
          
          return {
            student,
            recoveryPlan: recoveryPlan || null,
            finalGrade,
            period: this.selectedPeriod
          };
        }).filter(s => (s.finalGrade > 0 && s.finalGrade < this.minPassingGrade) || s.recoveryPlan !== null);
        
        this.isLoading = false;
        setTimeout(() => {
          this.showLoadingScreen = false;
          this.cdr.markForCheck();
        }, 300);
      },
      error: () => {
        this.studentsWithRecovery = [];
        this.isLoading = false;
        this.showLoadingScreen = false;
        this.cdr.markForCheck();
      }
    });
  }

  calculateFinalGrade(grades: any[]): number {
    const actGrades: number[] = [];
    let evalGrade: number | null = null;
    let autoEvalGrade: number | null = null;
    let promParcGrade: number | null = null;
    let nFinalGrade: number | null = null;

    for (const grade of grades) {
      const gradeName = grade.gradeName?.toLowerCase() || '';

      if (grade.isEvaluation) {
        evalGrade = grade.gradeValue;
      } else if (gradeName.includes('nfinal') || gradeName.includes('n.final')) {
        nFinalGrade = grade.gradeValue;
      } else if (gradeName.includes('act') && !gradeName.includes('auto') && !gradeName.includes('prom')) {
        if (grade.gradeValue !== null) {
          actGrades.push(grade.gradeValue);
        }
      } else if (gradeName.includes('auto')) {
        autoEvalGrade = grade.gradeValue;
      } else if (gradeName.includes('prom')) {
        promParcGrade = grade.gradeValue;
      }
    }

    // Si hay una N.final guardada, esa es la nota definitiva del estudiante
    if (nFinalGrade !== null) {
      return nFinalGrade;
    }
    
    if (autoEvalGrade !== null) actGrades.push(autoEvalGrade);
    if (promParcGrade !== null) actGrades.push(promParcGrade);
    
    let p80 = 0;
    if (actGrades.length > 0) {
      const avgAct = actGrades.reduce((a, b) => a + b, 0) / actGrades.length;
      p80 = avgAct * 0.8;
    }
    
    let p20 = 0;
    if (evalGrade !== null) {
      p20 = evalGrade * 0.2;
    }
    
    return p80 + p20;
  }

  applyFilters() {
    let filtered = [...this.allRecoveryPlans];
    
    if (this.filterGrade) {
      filtered = filtered.filter(p => p.grade === this.filterGrade);
    }
    if (this.filterClassroom) {
      filtered = filtered.filter(p => p.classGroup === this.filterClassroom);
    }
    if (this.filterPeriod) {
      filtered = filtered.filter(p => p.period === this.filterPeriod);
    }
    
    this.studentsWithRecovery = filtered.map(p => ({
      student: {
        id: p.studentId,
        name: p.studentName.split(' ')[1] || '',
        surname: p.studentName.split(' ')[0] || '',
        grade: p.grade,
        classGroup: p.classGroup
      },
      recoveryPlan: p,
      finalGrade: 0,
      period: p.period
    }));
    this.cdr.markForCheck();
  }

  filterByGrade(grade: string | null) {
    this.filterGrade = grade;
    this.applyFilters();
  }

  filterByClassroom(classroom: string | null) {
    this.filterClassroom = classroom;
    this.applyFilters();
  }

  filterByPeriod(period: number | null) {
    this.filterPeriod = period;
    this.applyFilters();
  }

  viewPlan(plan: RecoveryPlan) {
    this.selectedPlan = plan;
    this.showPlanModal = true;
    this.safePlanContent = this.sanitizer.bypassSecurityTrustHtml(plan.planContent || plan.day1Content || '');
    setTimeout(() => this.renderMathInPlan(), 100);
  }

  closePlanModal() {
    this.showPlanModal = false;
    this.selectedPlan = null;
  }

  printPlan() {
    const printContent = document.getElementById('recovery-plan-content');
    if (!printContent) {
      this.dialogService.alert('No hay plan de recuperación para imprimir', 'Nada que imprimir');
      return;
    }

    const printContentHTML = printContent.innerHTML;
    if (!printContentHTML.trim()) {
      this.dialogService.alert('El contenido del plan está vacío', 'Nada que imprimir');
      return;
    }
    
    const printHTML = `
      <!DOCTYPE html>
      <html>
      <head>
        <title>Plan de Recuperación</title>
        <style>
          body { font-family: Arial, sans-serif; padding: 20px; line-height: 1.6; }
          h1, h2, h3 { color: #2c3e50; }
          h2 { border-bottom: 2px solid #3498db; padding-bottom: 8px; }
          h3 { color: #1b6aeb; }
          table { width: 100%; border-collapse: collapse; margin: 15px 0; }
          table th { background: #3498db; color: white; padding: 12px; text-align: left; border: 1px solid #ddd; }
          table td { padding: 10px; border: 1px solid #ddd; }
          table tr:nth-child(even) td { background: #f8f9fa; }
          ul, ol { margin: 10px 0 15px 20px; }
          li { margin: 6px 0; }
          strong { color: #1b6aeb; }
          a { color: #e74c3c; text-decoration: none; }
          @page { margin: 10mm; size: A4; }
          * { -webkit-print-color-adjust: exact !important; print-color-adjust: exact !important; }
        </style>
      </head>
      <body>${printContentHTML}</body>
      </html>
    `;
    
    const iframe = document.createElement('iframe');
    iframe.style.position = 'fixed';
    iframe.style.top = '0';
    iframe.style.left = '0';
    iframe.style.width = '100%';
    iframe.style.height = '100%';
    iframe.style.border = 'none';
    iframe.style.zIndex = '-1';
    
    document.body.appendChild(iframe);
    
    const writeAndPrint = () => {
      if (iframe.contentDocument) {
        iframe.contentDocument.open();
        iframe.contentDocument.write(printHTML);
        iframe.contentDocument.close();
        
        iframe.onload = null;
        
        setTimeout(() => {
          try {
            iframe.contentWindow?.print();
          } catch (e) {
            console.error('Print error:', e);
          } finally {
            setTimeout(() => {
              if (iframe.parentNode) {
                iframe.parentNode.removeChild(iframe);
              }
            }, 1000);
          }
        }, 250);
      }
    };
    
    if (iframe.contentDocument?.readyState === 'complete') {
      writeAndPrint();
    } else {
      iframe.onload = writeAndPrint;
    }
  }

  getGradeColor(grade: number): string {
    if (grade >= 4.0) return 'grade-green';
    if (grade >= this.minPassingGrade) return 'grade-yellow';
    return 'grade-red';
  }

  formatDate(date: Date): string {
    return new Date(date).toLocaleDateString('es-CO', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  }

  getDisplayItems(): StudentWithRecovery[] {
    if (this.selectedGrade && this.selectedClassroom) {
      return this.studentsWithRecovery;
    }
    return [];
  }

  getInitials(studentName: string | undefined): string {
    if (!studentName) return '??';
    const parts = studentName.split(' ');
    const firstInitial = parts.length > 1 ? parts[1].charAt(0) : '';
    const lastInitial = parts.length > 0 ? parts[0].charAt(0) : '';
    return (firstInitial + lastInitial).toUpperCase();
  }

  refresh() {
    this.loadAllRecoveryPlans();
    if (this.selectedGrade && this.selectedClassroom) {
      this.loadStudentsWithRecovery();
    }
    this.gradesUpdateService.notifyGradeUpdate();
  }

  needsRecovery(studentId: number): boolean {
    const item = this.studentsWithRecovery.find(s => s.student.id === studentId);
    return item ? item.finalGrade > 0 && item.finalGrade < this.minPassingGrade : false;
  }

  hasRecoveryPlan(studentId: number, period: number): boolean {
    return this.allRecoveryPlans.some(p => p.studentId === studentId && p.period === period);
  }

  hasExcellentGrade(studentId: number): boolean {
    const item = this.studentsWithRecovery.find(s => s.student.id === studentId);
    return item ? item.finalGrade >= 4.0 : false;
  }

  isLosing(studentId: number): boolean {
    const item = this.studentsWithRecovery.find(s => s.student.id === studentId);
    return item ? item.finalGrade >= this.minPassingGrade && item.finalGrade < 4.0 : false;
  }

  private renderMathInPlan() {
    const planContent = document.getElementById('recovery-plan-content');
    if (!planContent) return;

    const waitForKatex = (callback: () => void, maxAttempts = 30) => {
      let attempts = 0;
      const check = () => {
        attempts++;
        const katexAny = (window as any).katex;
        if (katexAny && typeof katexAny.ParseError !== 'undefined') {
          callback();
        } else if (attempts < maxAttempts) {
          setTimeout(check, 100);
        } else {
          console.warn('KaTeX not fully loaded after maximum attempts');
        }
      };
      check();
    };

    waitForKatex(() => {
      try {
        if (typeof (window as any).renderMathInElement !== 'undefined') {
          (window as any).renderMathInElement(planContent, {
            delimiters: [
              { left: '$$', right: '$$', display: false },
              { left: '$', right: '$', display: false }
            ],
            throwOnError: false,
            trust: true,
            strict: false
          });
        }
      } catch (e) {
        console.warn('KaTeX rendering failed:', e);
      }
    });
  }
}
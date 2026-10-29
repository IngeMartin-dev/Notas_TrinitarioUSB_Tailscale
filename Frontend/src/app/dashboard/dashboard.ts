import { Component, OnInit, signal, inject, effect } from '@angular/core';

import { RouterModule, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';
import { GradesUpdateService } from '../services/grades-update.service';
import { DialogService } from '../services/dialog.service';

import { API_BASE_URL } from '../config/api-base';
interface QuickLink {
  icon: string;
  label: string;
  desc: string;
  color: string;
  route?: string;
  action?: 'addStudent' | 'sendNotification' | 'countdown';
}

interface Activity {
  icon: string;
  description: string;
  timestamp: number; // Unix timestamp in ms
}

@Component({
  selector: 'app-dashboard',
  imports: [RouterModule, FormsModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit {
  // Start with zeros for instant loading
  totalStudents = 0;
  totalGrades = 0;
  totalSubjects = 0;
  totalReports = 0;
  
  // Track individual data loading states
  studentsLoaded = false;
  reportCardsLoaded = false;
  
  currentUser = signal<any>(null);
  private authService = inject(AuthService);
  private http = inject(HttpClient);
  private router = inject(Router);
  private notificationService = inject(NotificationService);
  private gradesUpdateService = inject(GradesUpdateService);
  private dialogService = inject(DialogService);

  // Modal states
  showAddStudentModal = false;
  showAddGradeModal = false;
  showAddSubjectModal = false;
  showViewReportsModal = false;
  showSendNotificationModal = false;

  // Add Student Form Data
  newStudent = {
    name: '',
    surname: '',
    documentNumber: '',
    grade: '',
    classGroup: ''
  };

  // Add Grade Form Data
  newGrade = {
    name: '',
    description: ''
  };

  // Add Subject Form Data
  newSubject = {
    name: '',
    code: '',
    grade: '',
    hoursPerWeek: 1,
    credits: 1,
    type: 'core' as 'core' | 'elective'
  };

  isSavingStudent = false;
  isSavingGrade = false;
  isSavingSubject = false;
  isSendingNotification = false;

  // Notification System Variables
  showNotification = false;
  notificationType: 'success' | 'error' | null = null;
  notificationMessage = '';

  // Countdown Timer Variables
  showCountdownTimer = false;
  countdownName = '';
  countdownTargetDate = '';
  countdownDaysInput = 0;
  countdownEndDate = '';
  countdownDays = '00';
  countdownHours = '00';
  countdownMinutes = '00';
  countdownSeconds = '00';
  showCountdownConfigModal = false;
  countdownInterval: any;
  isAdminUser = false;
  isTeacherUser = false;
  teacherSubjects: string[] = [];
  homeroomAssignment: any | null = null;
  notification12HoursSent = false;
  notificationFinishedSent = false;
  notification7DaysSent = false;

  // Countdown Timer Methods
  loadCountdownConfig() {
    const savedConfig = localStorage.getItem('countdownConfig');
    if (savedConfig) {
      const config = JSON.parse(savedConfig);
      this.countdownName = config.name || '';
      this.countdownTargetDate = config.targetDate || '';
      
      // Load notification flags
      this.notification12HoursSent = localStorage.getItem('countdownNotification12h') === 'true';
      this.notificationFinishedSent = localStorage.getItem('countdownNotificationFinished') === 'true';
      this.notification7DaysSent = localStorage.getItem('countdownNotification7d') === 'true';
      
      if (this.countdownName && this.countdownTargetDate) {
        this.showCountdownTimer = true;
        // Format end date for display
        const endDate = new Date(this.countdownTargetDate);
        this.countdownEndDate = endDate.toLocaleDateString('es-CO', {
          weekday: 'long',
          year: 'numeric',
          month: 'long',
          day: 'numeric'
        });
        this.startCountdown();
      }
    }
  }

  openCountdownConfig() {
    this.showCountdownConfigModal = true;
  }

  closeCountdownConfig() {
    this.showCountdownConfigModal = false;
  }

  saveCountdownConfig() {
    if (!this.countdownName) {
      this.showErrorNotification('Por favor ingrese un título');
      return;
    }
    
    let targetDate: Date;
    
    // Use date if provided, otherwise use days
    if (this.countdownTargetDate) {
      targetDate = new Date(this.countdownTargetDate);
    } else if (this.countdownDaysInput && this.countdownDaysInput > 0) {
      targetDate = new Date();
      targetDate.setDate(targetDate.getDate() + this.countdownDaysInput);
    } else {
      this.showErrorNotification('Por favor ingrese una fecha o días');
      return;
    }
    
    // Reset notification flags for new countdown
    this.notification12HoursSent = false;
    this.notificationFinishedSent = false;
    this.notification7DaysSent = false;
    localStorage.removeItem('countdownNotification12h');
    localStorage.removeItem('countdownNotificationFinished');
    localStorage.removeItem('countdownNotification7d');
    
    const config = {
      name: this.countdownName,
      targetDate: targetDate.toISOString()
    };
    localStorage.setItem('countdownConfig', JSON.stringify(config));
    
    this.showCountdownTimer = true;
    this.closeCountdownConfig();
    this.startCountdown();
    this.showSuccessNotification('Cronómetro configurado correctamente');
    
    // Format detailed time remaining
    const now = new Date();
    const diff = targetDate.getTime() - now.getTime();
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
    
    let timeRemaining = '';
    if (days > 0) {
      timeRemaining = `${days} día${days > 1 ? 's' : ''}, ${hours} hora${hours > 1 ? 's' : ''}`;
    } else if (hours > 0) {
      timeRemaining = `${hours} hora${hours > 1 ? 's' : ''}, ${minutes} minuto${minutes > 1 ? 's' : ''}`;
    } else {
      timeRemaining = `${minutes} minuto${minutes > 1 ? 's' : ''}`;
    }
    
    // Format date for display
    const dateStr = targetDate.toLocaleString('es-CO', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
    
    // Send push notification about new timer with detailed info
    this.notificationService.showNativePushNotification(
      '⏰ ' + this.countdownName,
      `Fecha: ${dateStr}. Tiempo: ${timeRemaining}`
    );
    
    // Track activity
    this.addActivity('timer', `Cronómetro configurado: ${this.countdownName} - ${dateStr}`);
  }

  startCountdown() {
    if (this.countdownInterval) {
      clearInterval(this.countdownInterval);
    }
    
    this.updateCountdown();
    this.countdownInterval = setInterval(() => {
      this.updateCountdown();
    }, 1000);
  }

  updateCountdown() {
    if (!this.countdownTargetDate) return;
    
    const target = new Date(this.countdownTargetDate).getTime();
    const now = new Date().getTime();
    const distance = target - now;
    
    // Check if timer finished
    if (distance < 0) {
      this.countdownDays = '00';
      this.countdownHours = '00';
      this.countdownMinutes = '00';
      this.countdownSeconds = '00';
      
      // Send notification if not already sent
      if (!this.notificationFinishedSent) {
        this.notificationFinishedSent = true;
        localStorage.setItem('countdownNotificationFinished', 'true');
        this.notificationService.showNativePushNotification(
          this.countdownName,
          '¡El tiempo ha terminado!'
        );
        // Track activity - timer finished
        this.addActivity('check_circle', `Tiempo terminado: ${this.countdownName}`);
      }
      
      if (this.countdownInterval) {
        clearInterval(this.countdownInterval);
      }
      return;
    }
    
    const days = Math.floor(distance / (1000 * 60 * 60 * 24));
    const hours = Math.floor((distance % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
    const minutes = Math.floor((distance % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((distance % (1000 * 60)) / 1000);
    
    this.countdownDays = days.toString().padStart(2, '0');
    this.countdownHours = hours.toString().padStart(2, '0');
    this.countdownMinutes = minutes.toString().padStart(2, '0');
    this.countdownSeconds = seconds.toString().padStart(2, '0');
    
    // Check if 7 days remaining
    const sevenDays = 7 * 24 * 60 * 60 * 1000;
    if (distance <= sevenDays && !this.notification7DaysSent) {
      this.notification7DaysSent = true;
      localStorage.setItem('countdownNotification7d', 'true');

      this.notificationService.showNativePushNotification(
        this.countdownName,
        `Faltan 7 días o menos (${days} día${days !== 1 ? 's' : ''})`
      );
      // Track activity - 7 days remaining
      this.addActivity('schedule', `Recordatorio ${this.countdownName}: Faltan 7 días o menos`);
    }

    // Check if 12 hours remaining (43200000 ms = 12 hours)
    const twelveHours = 12 * 60 * 60 * 1000;
    if (distance <= twelveHours && !this.notification12HoursSent) {
      this.notification12HoursSent = true;
      localStorage.setItem('countdownNotification12h', 'true');
      
      let timeText = '';
      if (days > 0) {
        timeText = `${days} día(s) y ${hours} hora(s)`;
      } else if (hours > 0) {
        timeText = `${hours} hora(s) y ${minutes} minuto(s)`;
      } else {
        timeText = `${minutes} minuto(s)`;
      }
      
      this.notificationService.showNativePushNotification(
        this.countdownName,
        `Faltan ${timeText}`
      );
      // Track activity - 12 hours remaining
      this.addActivity('schedule', `Recordatorio ${this.countdownName}: Faltan ${timeText}`);
    }
  }

  // Send Notification Form Data
  newNotification = {
    title: '',
    message: '',
    recipientType: 'ALL' as 'PARENTS' | 'TEACHERS' | 'ADMINISTRATORS' | 'ALL'
  };

  // Notification System Methods
  showSuccessNotification(message: string) {
    this.notificationType = 'success';
    this.notificationMessage = message;
    this.showNotification = true;

    setTimeout(() => {
      this.hideNotification();
    }, 3000);
  }

  showErrorNotification(message: string) {
    this.notificationType = 'error';
    this.notificationMessage = message;
    this.showNotification = true;

    setTimeout(() => {
      this.hideNotification();
    }, 4000);
  }

  hideNotification() {
    this.showNotification = false;
    setTimeout(() => {
      this.notificationType = null;
      this.notificationMessage = '';
    }, 300);
  }

  // Dynamic Recent Activities
  recentActivities: Activity[] = [];
  private readonly ACTIVITIES_KEY = 'recent_activities';
  private readonly MAX_ACTIVITIES = 5;

  constructor() {
    // Subscribe to auth service user changes to stay in sync
    this.authService.currentUser$.subscribe(user => {
      console.log('Dashboard - Auth service user updated:', user);
      this.currentUser.set(user);
    });
    
    // Load recent activities from localStorage
    this.loadRecentActivities();
    
    // Listen for grade updates from the grades component
    effect(() => {
      // Access the signal to create a dependency
      this.gradesUpdateService.gradesUpdated();
      // Show loading state and refresh the grade count when grades are updated
      this.reportCardsLoaded = false;
      this.loadReportCardCount();
    });
  }

  loadRecentActivities() {
    const saved = localStorage.getItem(this.ACTIVITIES_KEY);
    if (saved) {
      this.recentActivities = JSON.parse(saved);
    } else {
      // Default empty
      this.recentActivities = [];
    }
  }

  saveRecentActivities() {
    localStorage.setItem(this.ACTIVITIES_KEY, JSON.stringify(this.recentActivities));
  }

  addActivity(icon: string, description: string) {
    const activity: Activity = {
      icon: icon,
      description: description,
      timestamp: Date.now()
    };
    
    // Add to beginning
    this.recentActivities.unshift(activity);
    
    // Keep only max activities
    if (this.recentActivities.length > this.MAX_ACTIVITIES) {
      this.recentActivities = this.recentActivities.slice(0, this.MAX_ACTIVITIES);
    }
    
    this.saveRecentActivities();
  }
  
  // Format timestamp to readable time
  formatActivityTime(timestamp: number): string {
    const now = Date.now();
    const diff = now - timestamp;
    
    const minutes = Math.floor(diff / (1000 * 60));
    const hours = Math.floor(diff / (1000 * 60 * 60));
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    
    if (minutes < 1) {
      return 'Ahora';
    } else if (minutes < 60) {
      return `Hace ${minutes} min`;
    } else if (hours < 24) {
      return `Hace ${hours} hr`;
    } else if (days === 1) {
      return 'Ayer';
    } else {
      // Return date
      const date = new Date(timestamp);
      return date.toLocaleDateString('es-CO', { month: 'short', day: 'numeric' });
    }
  }
  
  // Saludo de bienvenida: totalmente local (ya no depende de un endpoint de
  // IA en el backend que no existe — antes tiraba 404 en /api/greeting).
  // Elige al azar una entre varias frases según la hora del día.
  greetingText = '';
  greetingLetters: string[] = [];
  private greetingLoaded = false;

  private readonly greetingsManana = [
    'Buenos días',
    '¡Buenos días!',
    'Buenos días, que tengas un excelente día',
    'Arriba ese ánimo, buenos días',
    'Buen día, a por otra jornada productiva',
    '¡Buenos días! Que este día venga cargado de buenas noticias',
    'Buenos días, empecemos con energía',
    'Feliz comienzo de día'
  ];

  private readonly greetingsTarde = [
    'Buenas tardes',
    '¡Buenas tardes!',
    'Buenas tardes, sigue con ese buen ritmo',
    'Buenas tardes, ya vamos por la mitad del día',
    '¡Buenas tardes! Vamos con todo lo que falta',
    'Buenas tardes, gracias por seguir aquí',
    'Feliz tarde, sigamos adelante'
  ];

  private readonly greetingsNoche = [
    'Buenas noches',
    '¡Buenas noches!',
    'Buenas noches, gracias por tu trabajo hoy',
    'Buenas noches, ya casi termina el día',
    'Buenas noches, descansa cuando puedas',
    '¡Buenas noches! Un gusto tenerte por aquí',
    'Feliz noche'
  ];

  private readonly greetingsMadrugada = [
    'Buenas noches',
    'Hola, trasnochando por aquí',
    'Buenas, qué madrugador/a',
    'Hola, un gusto verte a esta hora'
  ];

  generateGreeting() {
    if (this.greetingLoaded) return;

    const user = this.currentUser();
    const userName = user?.name ? user.name.split(' ')[0] : '';

    const hour = new Date().getHours();
    let frases: string[];
    if (hour >= 5 && hour < 12) {
      frases = this.greetingsManana;
    } else if (hour >= 12 && hour < 19) {
      frases = this.greetingsTarde;
    } else if (hour >= 19 || hour < 0) {
      frases = this.greetingsNoche;
    } else {
      frases = this.greetingsMadrugada; // 00:00 - 04:59
    }

    const baseGreeting = frases[Math.floor(Math.random() * frases.length)];
    this.greetingText = userName ? `${baseGreeting}, ${userName}` : baseGreeting;
    this.greetingLoaded = true;
    this.animateGreeting(this.greetingText);
  }
  
  private animateGreeting(text: string) {
    this.greetingLetters = [];
    const chars = text.split('');
    const middle = Math.floor(chars.length / 2);
    
    let delay = 0;
    for (let i = 0; i <= middle; i++) {
      setTimeout(() => {
        if (chars[middle - i]) this.greetingLetters[middle - i] = chars[middle - i];
        if (chars[middle + i] && i > 0) this.greetingLetters[middle + i] = chars[middle + i];
      }, delay);
      delay += 30;
    }
  }

  ngOnInit() {
    console.log('Dashboard - ngOnInit starting...');
    
    // Generate initial greeting
    this.generateGreeting();
    
    // Load dashboard data
    this.loadUserData();
    this.loadDashboardData();
    
    // Load countdown timer configuration
    this.loadCountdownConfig();
  }

  private loadUserData() {
    this.currentUser.set(null);
    this.teacherSubjects = [];
    this.homeroomAssignment = null;

    if (this.authService.isAuthenticated()) {
      this.authService.getCurrentUser().subscribe({
        next: (user) => {
          if (user && user.id) {
            const roleName = user.role?.name;
            // Solo un Director de Grupo puede tener un salón asignado; para
            // el resto de roles ni siquiera vale la pena preguntarle al
            // backend (evita un 404 innecesario en la consola).
            if (roleName === 'DIRECTOR_DE_GRUPO') {
              this.loadHomeroomAssignment(user.id);
            }
            if (roleName === 'TEACHER' || roleName === 'DIRECTOR_DE_GRUPO') {
              this.isTeacherUser = true;
              this.loadTeacherSubjects(user.id);
            }
            if (roleName === 'ADMIN' || user.additionalAdmin) {
              this.checkFechaFinDeAno();
            }
          }
        },
        error: (err) => {
          this.currentUser.set(null);
          this.isTeacherUser = false;
        }
      });
    } else {
      console.log('Usuario no autenticado');
      this.currentUser.set(null);
      this.isTeacherUser = false;
    }
  }

  private loadTeacherSubjects(teacherId: number) {
    this.http.get<any[]>(`http://localhost:8080/api/subjects/teacher/${teacherId}`).subscribe({
      next: (subjects) => {
        this.teacherSubjects = subjects
          .filter(s => s.name && s.name.trim().length > 0)
          .map(s => s.name.trim());
      },
      error: () => {
        this.teacherSubjects = [];
      }
    });
  }

  /** Si el admin entra y ya se alcanzó la fecha de fin de año, lo lleva
   *  directo a Configuración de Año para que decida qué hacer. */
  private checkFechaFinDeAno() {
    this.http.get<{ alcanzada: boolean }>('http://localhost:8080/api/school-year/fecha-alcanzada')
      .subscribe({
        next: (res) => {
          if (res.alcanzada && this.router.url !== '/school-year-config') {
            this.router.navigate(['/school-year-config']);
          }
        },
        error: () => { /* silencioso: no bloquea el resto del panel */ }
      });
  }

  private loadHomeroomAssignment(userId: number) {
    this.homeroomAssignment = null;
    this.http.get<any>(`http://localhost:8080/api/homeroom-assignments/by-user/${userId}`).subscribe({
      next: (assignment) => {
        this.homeroomAssignment = assignment;
      },
      error: () => {
        this.homeroomAssignment = null;
      }
    });
  }

  getUserInitials(): string {
    const user = this.currentUser();
    if (user && user.name && user.surname) {
      return (user.name.charAt(0) + user.surname.charAt(0)).toUpperCase();
    } else if (user && user.name) {
      return user.name.charAt(0).toUpperCase();
    }
    return '';
  }

  getDisplayUsername(): string {
    const user = this.currentUser();
    if (user && user.username) {
      return user.username;
    }
    return '';
  }

  getDisplayName(): string {
    const user = this.currentUser();
    if (user) {
      const fullName = `${user.name || ''} ${user.surname || ''}`.trim();
      return fullName || user.username || 'Usuario sin nombre';
    }
    return 'Usuario sin autenticar';
  }

  getClassroomLetter(classroom: string): string {
    const match = classroom.match(/([A-Za-z])$/);
    return match ? match[1].toUpperCase() : '';
  }

  getHomeroomAssignmentLabel(): string {
    if (!this.homeroomAssignment) return '';
    const grade = this.homeroomAssignment.grade || '';
    const classroomLetter = this.getClassroomLetter(this.homeroomAssignment.classroom || '');
    return `Director ${grade}${classroomLetter ? ' ' + classroomLetter : ''}`.trim();
  }

  getDisplayRole(): string {
    const user = this.currentUser();
    if (user && user.role && user.role.name) {
      return this.translateRole(user.role.name);
    }
    return '';
  }

  private translateRole(roleName: string): string {
    const roleTranslations: { [key: string]: string } = {
      'ADMIN': 'Administrador',
      'TEACHER': 'Profesor',
      'PARENT': 'Padre de Familia',
      'STUDENT': 'Estudiante',
      'DIRECTOR_DE_GRUPO': 'Director de Grupo'
    };
    
    return roleTranslations[roleName] || roleName;
  }

  // Helper method to ensure profile picture URLs are correct (same as settings component)
  getCorrectImageUrl(imagePath: string | null): string | null {
    if (!imagePath) return null;
    
    if (imagePath.startsWith('http')) {
      return imagePath; // Already a full URL
    }
    
    // Construct full URL for relative paths using backend port 8080
    const fullUrl = `${API_BASE_URL}${imagePath}`;
    console.log('🔧 Dashboard - Converted image URL:', imagePath, '->', fullUrl);
    return fullUrl;
  }

  // Enhanced image error handler with fallback (same as settings component)
  onImageError(event: any) {
    console.error('❌ Dashboard - Image failed to load:', event.target.src);
    
    // Check if this is a profile picture that failed to load
    const img = event.target;
    if (img.src.includes('/uploads/profile-pictures/')) {
      console.log('🔄 Dashboard - Profile picture failed to load, falling back to initials');
      
      // Hide the broken image and show initials instead
      img.style.display = 'none';
      
      // Find the parent container and ensure initials are visible
      const parent = img.parentElement;
      if (parent) {
        // Remove any existing initials div and create a new one
        const existingInitials = parent.querySelector('.user-initials-fallback');
        if (existingInitials) {
          existingInitials.remove();
        }
        
        const initialsDiv = document.createElement('div');
        initialsDiv.className = 'profile-initials user-initials-fallback';
        initialsDiv.textContent = this.getUserInitials();
        parent.appendChild(initialsDiv);
      }
    }
  }

  private loadDashboardData() {
    // Start loading - reset flags
    this.studentsLoaded = false;
    this.reportCardsLoaded = false;
    
    console.log('=== LOADING ALL DASHBOARD DATA ===');
    
    // Load all counts in parallel for speed
    this.loadStudentCount();
    this.loadReportCardCount();
    
    // Keep these as static for now until we implement their services
    this.totalSubjects = 8;
    this.totalReports = 45;
  }

  private loadStudentCount() {
    console.log('📊 Loading student count...');
    const url = 'http://localhost:8080/api/students';
    
    this.http.get<any[]>(url).subscribe({
      next: (students) => {
        this.totalStudents = students.length;
        this.studentsLoaded = true;
        console.log('✅ Student count loaded:', this.totalStudents);
      },
      error: (error) => {
        console.error('❌ Failed to load student count:', error);
        this.totalStudents = 0;
        this.studentsLoaded = true;
      }
    });
  }

  private loadReportCardCount() {
    console.log('📊 Loading grade count...');
    // Use teacher-specific endpoint if user is a teacher, otherwise use general count
    const url = this.isTeacherUser ? 'http://localhost:8080/api/grades/count/teacher' : 'http://localhost:8080/api/grades/count';
    
    this.http.get<any>(url).subscribe({
      next: (response) => {
        this.totalGrades = response.count || 0;
        this.reportCardsLoaded = true;
        console.log('✅ Grade count loaded:', this.totalGrades);
      },
      error: (error) => {
        console.error('❌ Failed to load grade count:', error);
        this.totalGrades = 0;
        this.reportCardsLoaded = true;
      }
    });
  }

  // ========== MODAL METHODS ==========
  
  // Add Student Modal Methods
  openAddStudentModal() {
    console.log('=== OPENING ADD STUDENT MODAL ===');
    this.resetStudentForm();
    this.showAddStudentModal = true;
  }

  closeAddStudentModal() {
    console.log('=== CLOSING ADD STUDENT MODAL ===');
    this.showAddStudentModal = false;
    this.resetStudentForm();
  }

  resetStudentForm() {
    this.newStudent = {
      name: '',
      surname: '',
      documentNumber: '',
      grade: 'Grado 1º',
      classGroup: 'Salon A'
    };
    this.isSavingStudent = false;
  }

  addStudent() {
    console.log('=== ADDING STUDENT FROM DASHBOARD ===');
    
    if (!this.newStudent.name || !this.newStudent.surname) {
      this.dialogService.alert('Por favor complete los campos obligatorios (nombres y apellidos)', 'Campos obligatorios');
      return;
    }

    this.isSavingStudent = true;
    
    const studentData = {
      name: this.newStudent.name.trim(),
      surname: this.newStudent.surname.trim(),
      grade: this.newStudent.grade,
      classGroup: this.newStudent.classGroup,
      documentNumber: this.newStudent.documentNumber?.trim() || null
    };

    const saveUrl = 'http://localhost:8080/api/students';
    
    this.http.post(saveUrl, studentData).subscribe({
      next: (response) => {
        console.log('✅ Student added from dashboard:', response);
        this.closeAddStudentModal();
        this.showSuccessNotification(`El estudiante "${studentData.name} ${studentData.surname}" fue agregado correctamente`);
        
        // Format grade for notification
        const gradeText = studentData.grade ? `Grado ${studentData.grade}` : '';
        const classText = studentData.classGroup ? ` - Grupo ${studentData.classGroup}` : '';
        const gradeInfo = gradeText + classText;
        
        // Send push notification about new student
        this.notificationService.showNativePushNotification(
          '👨‍🎓 Nuevo estudiante registrado',
          `${studentData.name} ${studentData.surname}${gradeInfo ? ' - ' + gradeInfo : ''}`
        );
        
        // Track activity
        this.addActivity('person_add', `Nuevo estudiante: ${studentData.name} ${studentData.surname}${gradeInfo ? ' (' + gradeInfo + ')' : ''}`);
        
        // Refresh student count
        this.loadStudentCount();
      },
      error: (error) => {
        console.error('❌ Failed to add student:', error);
        this.showErrorNotification('Error al agregar el estudiante. Verifique que el servidor esté ejecutándose.');
        this.isSavingStudent = false;
      }
    });
  }

  // Add Grade Modal Methods
  openAddGradeModal() {
    console.log('=== OPENING ADD GRADE MODAL ===');
    this.resetGradeForm();
    this.showAddGradeModal = true;
  }

  closeAddGradeModal() {
    console.log('=== CLOSING ADD GRADE MODAL ===');
    this.showAddGradeModal = false;
    this.resetGradeForm();
  }

  resetGradeForm() {
    this.newGrade = {
      name: '',
      description: ''
    };
    this.isSavingGrade = false;
  }

  addGrade() {
    console.log('=== ADDING GRADE FROM DASHBOARD ===');
    
    if (!this.newGrade.name) {
      this.dialogService.alert('Por favor complete el nombre del grado', 'Dato faltante');
      return;
    }

    this.isSavingGrade = true;
    
    // For now, just simulate adding a grade
    // In a real implementation, this would call an API
    setTimeout(() => {
      console.log('✅ Grade added:', this.newGrade);
      this.closeAddGradeModal();
      this.dialogService.alert(`¡Grado "${this.newGrade.name}" agregado exitosamente!`, 'Grado agregado');
      this.isSavingGrade = false;
      
      // Update count
      this.totalSubjects += 1;
    }, 1000);
  }

  // Add Subject Modal Methods
  openAddSubjectModal() {
    console.log('=== OPENING ADD SUBJECT MODAL ===');
    this.resetSubjectForm();
    this.showAddSubjectModal = true;
  }

  closeAddSubjectModal() {
    console.log('=== CLOSING ADD SUBJECT MODAL ===');
    this.showAddSubjectModal = false;
    this.resetSubjectForm();
  }

  resetSubjectForm() {
    this.newSubject = {
      name: '',
      code: '',
      grade: 'Grado 1º',
      hoursPerWeek: 1,
      credits: 1,
      type: 'core'
    };
    this.isSavingSubject = false;
  }

  addSubject() {
    console.log('=== ADDING SUBJECT FROM DASHBOARD ===');
    
    if (!this.newSubject.name || !this.newSubject.code) {
      this.dialogService.alert('Por favor complete el nombre y código de la materia', 'Dato faltante');
      return;
    }

    this.isSavingSubject = true;
    
    // For now, just simulate adding a subject
    // In a real implementation, this would call an API
    setTimeout(() => {
      console.log('✅ Subject added:', this.newSubject);
      this.closeAddSubjectModal();
      this.dialogService.alert(`¡Materia "${this.newSubject.name}" agregada exitosamente!`, 'Materia agregada');
      this.isSavingSubject = false;
      
      // Update count
      this.totalSubjects += 1;
    }, 1000);
  }

  // View Reports Modal Methods
  openViewReportsModal() {
    console.log('=== OPENING VIEW REPORTS MODAL ===');
    this.showViewReportsModal = true;
  }

  closeViewReportsModal() {
    console.log('=== CLOSING VIEW REPORTS MODAL ===');
    this.showViewReportsModal = false;
  }

  generateReport() {
    console.log('=== GENERATING REPORT ===');
    this.dialogService.alert('Funcionalidad de generación de reportes en desarrollo...', 'En desarrollo');
  }




  // ========== ROLES Y ACCESOS RÁPIDOS ==========

  private roleName(): string {
    const user = this.currentUser();
    return ((user?.role?.name || user?.role || '') as string).toString().toUpperCase();
  }

  isParent(): boolean { return this.roleName() === 'PARENT'; }
  isDirector(): boolean { return this.roleName() === 'DIRECTOR_DE_GRUPO'; }
  isTeacherRole(): boolean { return this.roleName() === 'TEACHER' || this.isDirector(); }

  /** Texto de bienvenida corto según el rol (rellena el panel para que no se vea vacío). */
  getRoleTip(): { icon: string; title: string; text: string } {
    if (this.isAdmin()) {
      return { icon: 'admin_panel_settings', title: 'Panel de administración',
        text: 'Gestiona estudiantes, profesores, períodos, boletines y consolidados desde los accesos de abajo.' };
    }
    if (this.isTeacherRole()) {
      return { icon: 'edit_note', title: 'Tu espacio docente',
        text: 'Registra calificaciones, gestiona recuperaciones y comunícate con los padres de familia.' };
    }
    if (this.isParent()) {
      return { icon: 'family_restroom', title: 'Seguimiento académico',
        text: 'Consulta las notas y los boletines de tus hijos y escribe a sus profesores cuando lo necesites.' };
    }
    return { icon: 'waving_hand', title: 'Bienvenido', text: 'Usa los accesos para moverte por la plataforma.' };
  }

  /**
   * Accesos del panel, filtrados por rol: cada rol solo ve lo que realmente puede usar.
   * `route` navega a una pantalla; `action` abre un modal del propio panel.
   */
  getQuickLinks(): QuickLink[] {
    if (this.isAdmin()) {
      return [
        { icon: 'person_add',      label: 'Agregar estudiante',  desc: 'Registro rápido',            action: 'addStudent',        color: '#2563eb' },
        { icon: 'campaign',        label: 'Enviar información',  desc: 'Avisos a la comunidad',      action: 'sendNotification',  color: '#0ea5e9' },
        { icon: 'schedule',        label: 'Cronómetro',          desc: 'Cuenta regresiva',           action: 'countdown',         color: '#8b5cf6' },
        { icon: 'school',          label: 'Estudiantes',         desc: 'Listado y matrícula',        route: '/students',           color: '#2563eb' },
        { icon: 'groups',          label: 'Profesores',          desc: 'Cuentas y materias',         route: '/teachers',           color: '#0d9488' },
        { icon: 'family_restroom', label: 'Padres de familia',   desc: 'Cuentas y vínculos',         route: '/parents',            color: '#f59e0b' },
        { icon: 'menu_book',       label: 'Materias',            desc: 'Áreas y asignaturas',        route: '/subjects',           color: '#6366f1' },
        { icon: 'event',           label: 'Períodos',            desc: 'Abrir y cerrar períodos',    route: '/periods',            color: '#ec4899' },
        { icon: 'assignment',      label: 'Generar boletines',   desc: 'Por grado y salón',          route: '/boletines',          color: '#16a34a' },
        { icon: 'folder_open',     label: 'Boletines generados', desc: 'Consulta y descarga',        route: '/boletines-generados', color: '#15803d' },
        { icon: 'assessment',      label: 'Consolidados',        desc: 'Resumen por salón',          route: '/consolidados-generados', color: '#ea580c' },
        { icon: 'workspace_premium', label: 'Promociones',       desc: 'Egresados Grado 11º',        route: '/promociones',        color: '#a16207' },
        { icon: 'supervisor_account', label: 'Directores de grupo', desc: 'Asignación por salón',    route: '/directors-group',    color: '#7c3aed' },
        { icon: 'event_repeat',    label: 'Año escolar',         desc: 'Cierre y adelantar año',     route: '/school-year-config', color: '#dc2626' },
        { icon: 'chat',            label: 'Mensajes',            desc: 'Chats con la comunidad',     route: '/chats',              color: '#0284c7' },
        { icon: 'settings',        label: 'Ajustes',             desc: 'Perfil y seguridad',         route: '/settings',           color: '#64748b' },
      ];
    }
    if (this.isTeacherRole()) {
      const links: QuickLink[] = [
        { icon: 'grade',           label: 'Calificaciones',      desc: 'Registrar y editar notas',   route: '/grades',             color: '#2563eb' },
        { icon: 'published_with_changes', label: 'Recuperaciones', desc: 'Planes y notas de recuperación', route: '/recoveries',   color: '#16a34a' },
      ];
      if (this.isDirector()) {
        links.push(
          { icon: 'assessment',  label: 'Consolidado',         desc: 'Resumen de tu salón',        route: '/reports',            color: '#ea580c' },
          { icon: 'folder_open', label: 'Consolidados generados', desc: 'Consulta y descarga',     route: '/consolidados-generados', color: '#15803d' },
        );
      }
      links.push(
        { icon: 'chat',          label: 'Mensajes',            desc: 'Habla con padres y colegas', route: '/chats',              color: '#0284c7' },
        { icon: 'settings',      label: 'Ajustes',             desc: 'Perfil y seguridad',         route: '/settings',           color: '#64748b' },
      );
      return links;
    }
    if (this.isParent()) {
      return [
        { icon: 'grade',           label: 'Mis notas',           desc: 'Calificaciones de tus hijos', route: '/mis-notas',         color: '#2563eb' },
        { icon: 'assignment',      label: 'Mis boletines',       desc: 'Descarga y consulta',         route: '/mis-boletines',     color: '#16a34a' },
        { icon: 'bar_chart',       label: 'Ver reportes',        desc: 'Resumen por período',         route: '/mis-boletines',     color: '#ea580c' },
        { icon: 'chat',            label: 'Mensajes',            desc: 'Escribe a los profesores',    route: '/chats',             color: '#0284c7' },
        { icon: 'settings',        label: 'Mi perfil',           desc: 'Datos y seguridad',           route: '/settings',          color: '#64748b' },
      ];
    }
    return [
      { icon: 'chat',     label: 'Mensajes', desc: 'Chats',            route: '/chats',    color: '#0284c7' },
      { icon: 'settings', label: 'Ajustes',  desc: 'Perfil y seguridad', route: '/settings', color: '#64748b' },
    ];
  }

  onQuickLink(link: QuickLink) {
    if (link.route) {
      this.router.navigate([link.route]);
      return;
    }
    switch (link.action) {
      case 'addStudent':       this.openAddStudentModal(); break;
      case 'sendNotification': this.openSendNotificationModal(); break;
      case 'countdown':        this.openCountdownConfig(); break;
    }
  }

  /** Escala valorativa (la misma que usan los boletines) — se muestra a los padres. */
  readonly escalaValorativa = [
    { letra: 'E', nombre: 'Excelente', rango: '4.6 – 5.0', color: '#16a34a' },
    { letra: 'S', nombre: 'Sobresaliente', rango: '4.0 – 4.5', color: '#2563eb' },
    { letra: 'B', nombre: 'Bueno', rango: '3.5 – 3.9', color: '#f59e0b' },
    { letra: 'A', nombre: 'Aceptable', rango: '3.0 – 3.4', color: '#ea580c' },
    { letra: 'I', nombre: 'Insuficiente', rango: '1.0 – 2.9', color: '#dc2626' },
  ];

  /** Iniciales del remitente para la vista previa (usa el mismo cálculo del perfil). */
  getPreviewRecipientLabel(): string {
    switch (this.newNotification.recipientType) {
      case 'PARENTS': return 'Padres de familia';
      case 'TEACHERS': return 'Profesores';
      case 'ADMINISTRATORS': return 'Administradores';
      default: return 'Toda la comunidad';
    }
  }

  // ========== ADMIN METHODS ==========

  isAdmin(): boolean {
    const user = this.currentUser();
    if (!user) return false;
    const roleName = (user.role?.name || user.role || '').toString().toUpperCase();
    // Igual que en app.ts/settings.ts: cuenta también el rol "extra" de
    // Admin sumado desde Configuración de Año (User.additionalAdmin).
    return roleName === 'ADMIN' || !!user.additionalAdmin;
  }

  // Send Notification Modal Methods
  openSendNotificationModal() {
    console.log('=== OPENING SEND NOTIFICATION MODAL ===');
    this.resetNotificationForm();
    this.showSendNotificationModal = true;
  }

  closeSendNotificationModal() {
    console.log('=== CLOSING SEND NOTIFICATION MODAL ===');
    this.showSendNotificationModal = false;
    this.resetNotificationForm();
  }

  resetNotificationForm() {
    this.newNotification = {
      title: '',
      message: '',
      recipientType: 'ALL'
    };
    this.isSendingNotification = false;
  }

  sendNotification() {
    console.log('=== SENDING NOTIFICATION ===');
    
    if (!this.newNotification.title || !this.newNotification.message) {
      this.dialogService.alert('Por favor complete el título y mensaje de la notificación', 'Dato faltante');
      return;
    }

    this.isSendingNotification = true;

    // Crear notificaciones para los destinatarios seleccionados usando el backend optimizado

    const sendNotificationByRole = (): Promise<any> => {
      const notificationData = {
        title: this.newNotification.title,
        message: this.newNotification.message,
        type: 'ADMIN_MESSAGE',
        recipientType: this.newNotification.recipientType
      };

      return new Promise((resolve, reject) => {
        this.http.post('http://localhost:8080/api/notifications/send', notificationData).subscribe({
          next: (response) => {
            console.log('✅ Notifications sent by role:', this.newNotification.recipientType);
            resolve(response);
          },
          error: (error) => {
            console.error('❌ Failed to send notifications by role:', error);
            reject(error);
          }
        });
      });
    };

    // Enviar notificaciones por rol usando el backend optimizado
    sendNotificationByRole().then(() => {
      console.log('✅ All notifications sent successfully by role');
      this.closeSendNotificationModal();
      
      // Mostrar animación de éxito
      this.showSuccessAnimation();
      
      this.isSendingNotification = false;
    }).catch(error => {
      console.error('❌ Failed to send notifications by role:', error);
      this.dialogService.alert('Error al enviar las notificaciones. Inténtelo de nuevo.', 'Error');
      this.isSendingNotification = false;
    });
  }

  private showSuccessAnimation() {
    // Crear elemento de animación de éxito mejorado
    const successDiv = document.createElement('div');
    successDiv.style.cssText = `
      position: fixed;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      background: linear-gradient(135deg, #10b981, #059669);
      color: white;
      padding: 30px 50px;
      border-radius: 16px;
      font-size: 20px;
      font-weight: 600;
      z-index: 10000;
      box-shadow: 0 20px 60px rgba(16, 185, 129, 0.4);
      animation: successPulse 0.8s ease-out;
      min-width: 300px;
      text-align: center;
    `;
    
    successDiv.innerHTML = `
      <div style="display: flex; flex-direction: column; align-items: center; gap: 16px;">
        <div class="checkmark-container" style="
          width: 60px;
          height: 60px;
          border-radius: 50%;
          background: rgba(255, 255, 255, 0.2);
          display: flex;
          align-items: center;
          justify-content: center;
          animation: checkmarkBounce 0.6s ease-out 0.3s both;
        ">
          <svg class="checkmark" width="32" height="32" viewBox="0 0 52 52" style="
            animation: checkmarkStroke 0.6s ease-out 0.5s both;
          ">
            <path class="checkmark__check" fill="none" d="m9 16 11 11 25-25" stroke="white" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <div style="font-size: 18px; font-weight: 600;">¡Notificación Enviada!</div>
        <div style="font-size: 14px; opacity: 0.9;">Todos los destinatarios recibieron el mensaje</div>
      </div>
    `;
    
    // Agregar estilos de animación mejorados
    const style = document.createElement('style');
    style.textContent = `
      @keyframes successPulse {
        0% {
          opacity: 0;
          transform: translate(-50%, -50%) scale(0.3) rotate(-10deg);
        }
        50% {
          opacity: 1;
          transform: translate(-50%, -50%) scale(1.1) rotate(2deg);
        }
        100% {
          opacity: 1;
          transform: translate(-50%, -50%) scale(1) rotate(0deg);
        }
      }
      
      @keyframes checkmarkBounce {
        0% {
          transform: scale(0);
          opacity: 0;
        }
        50% {
          transform: scale(1.2);
          opacity: 1;
        }
        100% {
          transform: scale(1);
          opacity: 1;
        }
      }
      
      @keyframes checkmarkStroke {
        0% {
          stroke-dasharray: 166;
          stroke-dashoffset: 166;
        }
        100% {
          stroke-dasharray: 166;
          stroke-dashoffset: 0;
        }
      }
    `;
    document.head.appendChild(style);
    
    document.body.appendChild(successDiv);
    
    // Remover después de 3 segundos
    setTimeout(() => {
      successDiv.style.animation = 'successPulse 0.4s ease-out reverse';
      setTimeout(() => {
        successDiv.remove();
        style.remove();
      }, 400);
    }, 3000);
  }
}
import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../services/auth.service';
import { API_BASE_URL } from '../config/api-base';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { ThemeService } from '../services/theme.service';
import { DialogService } from '../services/dialog.service';
import { TermsContentComponent } from '../legal/terms-content/terms-content';
import { PrivacyContentComponent } from '../legal/privacy-content/privacy-content';
import { CookiesContentComponent } from '../legal/cookies-content/cookies-content';
import { RefundContentComponent } from '../legal/refund-content/refund-content';

@Component({
  selector: 'app-settings',
  imports: [FormsModule, TermsContentComponent, PrivacyContentComponent, CookiesContentComponent, RefundContentComponent],
  templateUrl: './settings.html',
  styleUrl: './settings.css',
})
export class Settings implements OnInit, OnDestroy {
  private authService = inject(AuthService);
  private http = inject(HttpClient);
  private router = inject(Router);
  private themeService = inject(ThemeService);
  private dialogService = inject(DialogService);

  // Tema (modo claro/oscuro) — estado compartido con el resto de la app
  isDarkTheme = this.themeService.isDark;

  currentUser = signal<any>(null);
  selectedFile: File | null = null;
  previewUrl = signal<string | null>(null);
  showSuccessNotification = signal(false);
  showLogoutConfirmation = signal(false);
  
  // Current year (dynamic)
  currentYear: number = new Date().getFullYear();
  
  // Settings - Notificaciones
  pushNotificationsEnabled = signal(true);
  emailNotificationsEnabled = signal(true);
  messageNotificationsEnabled = signal(true);
  
  // Settings - Idioma
  selectedLanguage = signal('es');
  
  // Settings - Security
  showChangePasswordModal = signal(false);
  currentPassword = signal('');
  newPassword = signal('');
  confirmPassword = signal('');
  passwordChangeError = signal<string | null>(null);
  passwordChangeSuccess = signal(false);
  isChangingPassword = signal(false);
  
  // Settings - Ayuda (FAQ / Soporte)
  showFaqModal = signal(false);
  showSupportModal = signal(false);
  expandedFaqIndex = signal<number | null>(null);
  faqItems: { question: string; answer: string }[] = [
    {
      question: '¿Cómo veo las calificaciones de mi hijo(a)?',
      answer: 'Ingresa a la sección "Calificaciones" desde el menú principal. Allí verás las notas por materia y periodo tal como las registraron los profesores.'
    },
    {
      question: '¿Cómo descargo el boletín de calificaciones?',
      answer: 'En la sección de Boletines/Consolidados, selecciona el periodo y el estudiante, y usa el botón de descarga para obtener el PDF en tamaño carta.'
    },
    {
      question: '¿Olvidé mi contraseña, qué hago?',
      answer: 'Desde la pantalla de inicio de sesión selecciona "¿Olvidaste tu contraseña?". Si no tienes esa opción disponible, contacta a soporte para que un administrador te ayude a restablecerla.'
    },
    {
      question: '¿Cómo cambio mi contraseña?',
      answer: 'Ve a Ajustes > Seguridad > Cambiar Contraseña, ingresa tu contraseña actual y la nueva contraseña dos veces para confirmarla.'
    },
    {
      question: '¿Cómo hablo con el profesor o el director de grupo de mi hijo(a)?',
      answer: 'Usa la sección de Chat/Mensajes: allí encontrarás a los profesores del grado correspondiente y a los administradores del colegio para escribirles directamente.'
    },
    {
      question: '¿Por qué no veo algunas materias en el boletín?',
      answer: 'El boletín muestra las materias asignadas al grado del estudiante para el periodo activo. Si crees que falta alguna, contacta a soporte o al director de grupo.'
    },
    {
      question: '¿Puedo usar la aplicación desde el celular?',
      answer: 'Sí, la plataforma es responsiva y puedes usarla desde el navegador de tu celular sin instalar nada adicional.'
    },
    {
      question: '¿Cómo cierro sesión en otro dispositivo que ya no uso?',
      answer: 'Ve a Ajustes > Seguridad > Sesiones Activas. Allí puedes ver todos los dispositivos donde tu cuenta tiene sesión iniciada y cerrar la que quieras.'
    },
  ];

  // Settings - Legal
  showTermsModal = signal(false);
  showPrivacyModal = signal(false);

  // Settings - Autenticación de Dos Factores (2FA)
  twoFactorEnabled = signal(false);
  showTwoFactorEnableModal = signal(false); // paso 1: pide activar -> ya se mandó el código
  showTwoFactorDisableModal = signal(false); // pide contraseña para desactivar
  twoFactorCodeInput = signal('');
  twoFactorPasswordInput = signal('');
  twoFactorEmailHint = signal('');
  twoFactorError = signal<string | null>(null);
  twoFactorBusy = signal(false);

  // Settings - Sesiones Activas
  showSessionsModal = signal(false);
  loadingSessions = signal(false);
  sessionsError = signal<string | null>(null);
  activeSessions = signal<Array<{
    id: number; device: string; ipAddress: string | null;
    createdAt: string; lastUsedAt: string; current: boolean;
  }>>([]);
  revokingSessionId = signal<number | null>(null);
  
  // Edit profile fields
  isEditingProfile = signal(false);
  editName = signal('');
  editSurname = signal('');
  editEmail = signal('');
  isUpdatingProfile = signal(false);
  profileUpdateError = signal<string | null>(null);
  
  // Animation states for profile edit
  isFormHiding = signal(false);
  
  private successNotificationTimer: any = null;
  private canShowSuccessNotification = false;
  private isComponentActive = false;
  private notificationLockCounter = 0;
  private notificationGuardian: any = null;

  constructor() {
    // Complete reset and lockdown when component loads
    this.isComponentActive = false;
    this.notificationLockCounter = 0;
    this.lockdownNotifications();
    this.forceHideNotification();
    
    // Subscribe to auth service user changes to stay in sync
    this.authService.currentUser$.subscribe(user => {
      console.log('Settings - Auth service user updated:', user);
      this.currentUser.set(user);
      // Aggressive lockdown and force hide on any user data change
      this.lockdownNotifications();
      this.forceHideNotification();
    });
    
    this.loadCurrentUser();
  }

  ngOnInit() {
    // Aggressive lockdown of notification system on component init
    this.notificationLockCounter = 0;
    this.lockdownNotifications();
    this.forceHideNotification();
    this.isComponentActive = true;
    
    // Start notification guardian - continuously monitor and force hide
    this.startNotificationGuardian();
    
    // Load user settings
    this.loadNotificationSettings();
    this.loadLanguageSettings();
    
    // Additional force hide after a short delay to catch any delayed triggers
    setTimeout(() => {
      this.forceHideNotification();
      this.lockdownNotifications();
    }, 50);
  }

  private startNotificationGuardian() {
    // Guardian that continuously ensures notification stays hidden
    this.notificationGuardian = setInterval(() => {
      if (this.showSuccessNotification() && !this.canShowSuccessNotification) {
        console.log('🛡️ Settings - Guardian detected unauthorized notification, forcing hide');
        this.forceHideNotification();
        this.lockdownNotifications();
      }
    }, 2000); // Check every 2s
  }

  private lockdownNotifications() {
    this.canShowSuccessNotification = false;
    this.notificationLockCounter++;
    this.hideSuccessNotification();
    console.log(`🔒 Settings - Notifications locked down (counter: ${this.notificationLockCounter})`);
  }

  private unlockNotificationsForAction() {
    this.canShowSuccessNotification = true;
    console.log(`🔓 Settings - Notifications unlocked for action (counter: ${this.notificationLockCounter})`);
  }

  private forceHideNotification() {
    this.showSuccessNotification.set(false);
    console.log('🚫 Settings - Notification force hidden');
  }

  ngOnDestroy() {
    // Complete shutdown of notification system
    this.isComponentActive = false;
    this.lockdownNotifications();
    this.forceHideNotification();
    
    // Clean up timer when component is destroyed
    if (this.successNotificationTimer) {
      clearTimeout(this.successNotificationTimer);
      this.successNotificationTimer = null;
    }
    
    // Stop notification guardian
    if (this.notificationGuardian) {
      clearInterval(this.notificationGuardian);
      this.notificationGuardian = null;
      console.log('🛡️ Settings - Notification guardian stopped');
    }
  }

  openChangePasswordModal() {
    this.showChangePasswordModal.set(true);
    this.currentPassword.set('');
    this.newPassword.set('');
    this.confirmPassword.set('');
    this.passwordChangeError.set(null);
    this.passwordChangeSuccess.set(false);
  }

  closeChangePasswordModal() {
    this.showChangePasswordModal.set(false);
  }

  changePassword() {
    if (!this.currentPassword() || !this.newPassword() || !this.confirmPassword()) {
      this.passwordChangeError.set('Por favor completa todos los campos');
      return;
    }

    if (this.newPassword() !== this.confirmPassword()) {
      this.passwordChangeError.set('Las contraseñas no coinciden');
      return;
    }

    if (this.newPassword().length < 6) {
      this.passwordChangeError.set('La contraseña debe tener al menos 6 caracteres');
      return;
    }

    this.isChangingPassword.set(true);
    this.passwordChangeError.set(null);
    this.passwordChangeSuccess.set(false);

    const userId = this.currentUser()?.id;
    if (!userId) {
      this.isChangingPassword.set(false);
      this.passwordChangeError.set('No se pudo identificar al usuario actual');
      return;
    }

    this.http.put(
      `${this.authService.API_BASE_URL}/users/${userId}/password`,
      {
        currentPassword: this.currentPassword(),
        newPassword: this.newPassword()
      }
    ).subscribe({
      next: () => {
        this.passwordChangeSuccess.set(true);
        this.currentPassword.set('');
        this.newPassword.set('');
        this.confirmPassword.set('');
        setTimeout(() => {
          this.isChangingPassword.set(false);
          this.closeChangePasswordModal();
        }, 1500);
      },
      error: (error) => {
        console.error('Password change failed:', error);
        this.isChangingPassword.set(false);
        if (error.status === 401) {
          this.passwordChangeError.set('La contraseña actual es incorrecta');
        } else if (error.status === 0) {
          this.passwordChangeError.set('No se pudo conectar con el servidor');
        } else {
          this.passwordChangeError.set(error.error?.error || 'Error al cambiar la contraseña');
        }
      }
    });
  }

  // ========== NOTIFICATION SETTINGS ==========
  onPushNotificationChange(enabled: boolean) {
    this.pushNotificationsEnabled.set(enabled);
    localStorage.setItem('pushNotifications', JSON.stringify(enabled));
    console.log('Push notifications:', enabled ? 'enabled' : 'disabled');
  }

  onEmailNotificationChange(enabled: boolean) {
    this.emailNotificationsEnabled.set(enabled);
    localStorage.setItem('emailNotifications', JSON.stringify(enabled));
    console.log('Email notifications:', enabled ? 'enabled' : 'disabled');
  }

  onMessageNotificationChange(enabled: boolean) {
    this.messageNotificationsEnabled.set(enabled);
    localStorage.setItem('messageNotifications', JSON.stringify(enabled));
    console.log('Message notifications:', enabled ? 'enabled' : 'disabled');
  }

  loadNotificationSettings() {
    const push = localStorage.getItem('pushNotifications');
    const email = localStorage.getItem('emailNotifications');
    const messages = localStorage.getItem('messageNotifications');

    if (push !== null) this.pushNotificationsEnabled.set(JSON.parse(push));
    if (email !== null) this.emailNotificationsEnabled.set(JSON.parse(email));
    if (messages !== null) this.messageNotificationsEnabled.set(JSON.parse(messages));
  }

  // ========== LANGUAGE SETTINGS ==========
  onLanguageChange(event: Event) {
    const select = event.target as HTMLSelectElement;
    const lang = select.value;
    this.selectedLanguage.set(lang);
    localStorage.setItem('language', lang);
    console.log('Language changed to:', lang);
    // In a real app, you would use a translation service here
  }

  loadLanguageSettings() {
    const lang = localStorage.getItem('language');
    if (lang) {
      this.selectedLanguage.set(lang);
    }
    
    // Load notification settings
    this.loadNotificationSettings();
  }

  // ========== THEME (Modo claro/oscuro) ==========
  // El estado vive en ThemeService; aquí solo delegamos.
  onThemeChange(enabled: boolean) {
    this.themeService.setDark(enabled);
    console.log('Tema:', enabled ? 'oscuro' : 'claro');
  }
    
  isAdmin(): boolean {
    const user = this.currentUser();
    if (!user) return false;
    const roleName = (user.role?.name || user.role || '').toString().toUpperCase();
    // Cuenta como admin tanto el que tiene ADMIN como rol principal, como
    // el que se le sumó el rol "extra" de Admin desde Configuración de Año
    // (ver User.additionalAdmin en el backend / AuthController#/me).
    return roleName === 'ADMIN' || !!user.additionalAdmin;
  }

  // ========== PRIVACY SETTINGS ==========
  downloadingData = signal(false);

  downloadMyData() {
    this.downloadingData.set(true);
    // Antes esto solo exportaba el perfil y las preferencias locales
    // ("solo nombres"). Ahora se pide al backend la exportación COMPLETA
    // según el rol (notas y materias de los hijos si es padre, materias
    // asignadas si es profesor/director de grupo).
    this.http.get<any>(`${this.authService.API_BASE_URL}/export/my-data`).subscribe({
      next: (fullData) => {
        this.triggerDataDownload({
          ...fullData,
          notificationSettings: {
            push: this.pushNotificationsEnabled(),
            email: this.emailNotificationsEnabled(),
            messages: this.messageNotificationsEnabled()
          },
          language: this.selectedLanguage()
        });
        this.downloadingData.set(false);
      },
      error: (err) => {
        console.error('Error exportando datos completos, se descarga solo el perfil:', err);
        // Si por algún motivo el backend falla, no dejamos al usuario sin
        // nada: al menos exportamos lo que ya teníamos disponible localmente.
        this.triggerDataDownload({
          profile: this.currentUser(),
          notificationSettings: {
            push: this.pushNotificationsEnabled(),
            email: this.emailNotificationsEnabled(),
            messages: this.messageNotificationsEnabled()
          },
          language: this.selectedLanguage(),
          exportDate: new Date().toISOString(),
          warning: 'No se pudo obtener la exportación completa del servidor; este archivo solo contiene el perfil.'
        });
        this.downloadingData.set(false);
      }
    });
  }

  private triggerDataDownload(userData: any) {
    const dataStr = JSON.stringify(userData, null, 2);
    const blob = new Blob([dataStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `mis-datos-notas-trinitario-${new Date().toISOString().split('T')[0]}.json`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  // ========== AUTENTICACIÓN DE DOS FACTORES (2FA) ==========
  /** Se llama cuando el usuario mueve el switch. */
  onToggleTwoFactor() {
    if (this.twoFactorEnabled()) {
      // Ya estaba activado y lo quiere apagar: pedir contraseña.
      this.twoFactorEnabled.set(true); // revertir visualmente hasta confirmar
      this.twoFactorError.set(null);
      this.twoFactorPasswordInput.set('');
      this.showTwoFactorDisableModal.set(true);
    } else {
      // Lo quiere activar: mandar el código y pedirlo.
      this.twoFactorError.set(null);
      this.twoFactorBusy.set(true);
      // Se abre el recuadro de inmediato; el correo se envía en segundo plano.
      this.twoFactorEmailHint.set('tu correo registrado');
      this.twoFactorCodeInput.set('');
      this.showTwoFactorEnableModal.set(true);
      this.http.post<any>(`${this.authService.API_BASE_URL}/auth/2fa/enable`, {}).subscribe({
        next: (res) => {
          this.twoFactorBusy.set(false);
          this.twoFactorEmailHint.set(res.emailHint || 'tu correo registrado');
        },
        error: (err) => {
          this.twoFactorBusy.set(false);
          console.error('Error iniciando activación de 2FA:', err);
          this.twoFactorError.set(err.error?.error || 'No se pudo enviar el código');
        }
      });
    }
  }

  confirmEnableTwoFactor() {
    if (!this.twoFactorCodeInput().trim()) {
      this.twoFactorError.set('Ingresa el código que llegó a tu correo');
      return;
    }
    this.twoFactorBusy.set(true);
    this.twoFactorError.set(null);
    this.http.post<any>(`${this.authService.API_BASE_URL}/auth/2fa/enable/confirm`, {
      code: this.twoFactorCodeInput().trim()
    }).subscribe({
      next: () => {
        this.twoFactorBusy.set(false);
        this.twoFactorEnabled.set(true);
        this.showTwoFactorEnableModal.set(false);
      },
      error: (err) => {
        this.twoFactorBusy.set(false);
        this.twoFactorError.set(err.error?.error || 'Código incorrecto o vencido');
      }
    });
  }

  cancelEnableTwoFactor() {
    this.showTwoFactorEnableModal.set(false);
    this.twoFactorEnabled.set(false);
  }

  confirmDisableTwoFactor() {
    if (!this.twoFactorPasswordInput()) {
      this.twoFactorError.set('Ingresa tu contraseña actual');
      return;
    }
    this.twoFactorBusy.set(true);
    this.twoFactorError.set(null);
    this.http.post<any>(`${this.authService.API_BASE_URL}/auth/2fa/disable`, {
      password: this.twoFactorPasswordInput()
    }).subscribe({
      next: () => {
        this.twoFactorBusy.set(false);
        this.twoFactorEnabled.set(false);
        this.showTwoFactorDisableModal.set(false);
      },
      error: (err) => {
        this.twoFactorBusy.set(false);
        this.twoFactorError.set(err.error?.error || 'Contraseña incorrecta');
      }
    });
  }

  cancelDisableTwoFactor() {
    this.showTwoFactorDisableModal.set(false);
    this.twoFactorEnabled.set(true);
  }

  // ========== SESIONES ACTIVAS ==========
  openActiveSessionsModal() {
    this.showSessionsModal.set(true);
    this.loadActiveSessions();
  }

  closeActiveSessionsModal() {
    this.showSessionsModal.set(false);
  }

  loadActiveSessions() {
    this.loadingSessions.set(true);
    this.sessionsError.set(null);
    const myRefreshToken = localStorage.getItem('refreshToken') || '';
    const params = myRefreshToken ? `?currentRefreshToken=${encodeURIComponent(myRefreshToken)}` : '';
    this.http.get<any[]>(`${this.authService.API_BASE_URL}/auth/sessions${params}`).subscribe({
      next: (sessions) => {
        this.activeSessions.set(sessions || []);
        this.loadingSessions.set(false);
      },
      error: (err) => {
        console.error('Error loading active sessions:', err);
        this.sessionsError.set('No se pudieron cargar las sesiones activas. Intenta de nuevo.');
        this.loadingSessions.set(false);
      }
    });
  }

  async revokeSession(sessionId: number, isCurrent: boolean) {
    if (isCurrent) {
      const confirmed = await this.dialogService.confirm(
        'Esta es tu sesión actual. Si la cierras, se cerrará tu sesión en este dispositivo. ¿Deseas continuar?',
        'Cerrar sesión actual'
      );
      if (!confirmed) return;
    }
    this.revokingSessionId.set(sessionId);
    this.http.delete(`${this.authService.API_BASE_URL}/auth/sessions/${sessionId}`).subscribe({
      next: () => {
        this.revokingSessionId.set(null);
        if (isCurrent) {
          this.closeActiveSessionsModal();
          this.logout();
          return;
        }
        this.activeSessions.update(list => list.filter(s => s.id !== sessionId));
      },
      error: (err) => {
        console.error('Error revoking session:', err);
        this.revokingSessionId.set(null);
        this.sessionsError.set('No se pudo cerrar esa sesión. Intenta de nuevo.');
      }
    });
  }

  // ========== HELP & SUPPORT ==========
  openFaqModal() {
    this.expandedFaqIndex.set(null);
    this.showFaqModal.set(true);
  }

  closeFaqModal() {
    this.showFaqModal.set(false);
  }

  toggleFaq(index: number) {
    this.expandedFaqIndex.set(this.expandedFaqIndex() === index ? null : index);
  }

  openSupportModal() {
    this.showSupportModal.set(true);
  }

  closeSupportModal() {
    this.showSupportModal.set(false);
  }

  contactSupport() {
    const subject = encodeURIComponent('Contacto desde Notas Trinitario');
    const body = encodeURIComponent(`\n\n---\nUsuario: ${this.currentUser()?.name || 'No identificado'}\nEmail: ${this.currentUser()?.email || 'No registrado'}`);
    window.open(`mailto:secretaria.trinitario@gmail.com?subject=${subject}&body=${body}`);
  }

  async sendFeedback() {
    const feedback = await this.dialogService.input(
      '¿Tienes comentarios o sugerencias? Cuéntanos:',
      'Comentarios',
      'Escribe aquí tus comentarios...'
    );
    if (!feedback || !feedback.trim()) return;

    this.http.post(`${this.authService.API_BASE_URL}/feedback`, { message: feedback.trim() }).subscribe({
      next: async () => {
        await this.dialogService.alert('¡Gracias por tus comentarios! Los hemos recibido.', 'Comentarios enviados');
      },
      error: async (err) => {
        console.error('Error enviando comentarios:', err);
        await this.dialogService.alert(
          'No pudimos enviar tu comentario en este momento. Por favor intenta de nuevo más tarde.',
          'Error'
        );
      }
    });
  }

  openTerms() {
    this.showTermsModal.set(true);
  }

  closeTermsModal() {
    this.showTermsModal.set(false);
  }

  /** Botón "Aceptar" del modal de Términos: además de cerrar, deja
   *  constancia (con fecha) de que el usuario los aceptó. */
  acceptTerms() {
    this.http.post(`${this.authService.API_BASE_URL}/auth/accept-legal`, { type: 'terms' }).subscribe({
      next: () => this.closeTermsModal(),
      error: (err) => {
        console.error('Error registrando aceptación de términos:', err);
        this.closeTermsModal(); // no bloqueamos al usuario si falla el registro
      }
    });
  }

  openPrivacyPolicy() {
    this.showPrivacyModal.set(true);
  }

  closePrivacyModal() {
    this.showPrivacyModal.set(false);
  }

  /** Botón "Aceptar" del modal de Política de Privacidad: idem acceptTerms(). */
  acceptPrivacy() {
    this.http.post(`${this.authService.API_BASE_URL}/auth/accept-legal`, { type: 'privacy' }).subscribe({
      next: () => this.closePrivacyModal(),
      error: (err) => {
        console.error('Error registrando aceptación de privacidad:', err);
        this.closePrivacyModal();
      }
    });
  }

  // --- Política de Cookies / Almacenamiento local (solo informativa, no requiere aceptación) ---
  showCookiesModal = signal(false);
  openCookiesPolicy() { this.showCookiesModal.set(true); }
  closeCookiesModal() { this.showCookiesModal.set(false); }

  // --- Política de Reembolsos (solo informativa, no requiere aceptación) ---
  showRefundModal = signal(false);
  openRefundPolicy() { this.showRefundModal.set(true); }
  closeRefundModal() { this.showRefundModal.set(false); }

  loadCurrentUser() {
    if (this.authService.isAuthenticated()) {
      this.authService.getCurrentUser().subscribe({
        next: (user) => {
          console.log('Settings - User loaded:', user);
          this.currentUser.set(user);
          this.twoFactorEnabled.set(!!user?.twoFactorEnabled);
        },
        error: (err) => {
          console.error('Settings - Failed to load user:', err);
          this.currentUser.set(null);
        }
      });
    } else {
      this.currentUser.set(null);
    }
  }

  getUserInitials(): string {
    const user = this.currentUser();
    if (user && user.name && user.surname) {
      return (user.name.charAt(0) + user.surname.charAt(0)).toUpperCase();
    }
    return 'U';
  }

  onFileSelected(event: any) {
    console.log('📁 File selection event:', event);
    const file = event.target.files[0];
    console.log('📁 Selected file:', file);
    
    this.selectedFile = file;
    
    if (file) {
      console.log('📁 Creating preview for file:', file.name, file.type, file.size);
      
      // Create preview URL with better error handling
      const reader = new FileReader();
      reader.onload = (e) => {
        const result = e.target?.result as string;
        console.log('📁 Preview created successfully:', result ? 'Success' : 'Failed');
        this.previewUrl.set(result);
        console.log('📁 Preview URL set:', this.previewUrl());
      };
      reader.onerror = (error) => {
        console.error('❌ FileReader error:', error);
        this.dialogService.alert('Error al leer el archivo. Por favor intenta con otra imagen.', 'Error de archivo');
        this.selectedFile = null;
      };
      reader.readAsDataURL(file);
    } else {
      console.log('📁 No file selected, clearing preview');
      this.previewUrl.set(null);
    }
  }

  clearPreview() {
    this.previewUrl.set(null);
    this.selectedFile = null;
    // Reset file input
    const fileInput = document.getElementById('profile-picture') as HTMLInputElement;
    if (fileInput) {
      fileInput.value = '';
    }
  }

  displaySuccessNotification() {
    // Ultra-restrictive check: must be active, authorized, AND have recent unlock
    if (!this.isComponentActive || !this.canShowSuccessNotification || this.notificationLockCounter > 0) {
      console.log(`🚫 Settings - Success notification blocked (active: ${this.isComponentActive}, authorized: ${this.canShowSuccessNotification}, locks: ${this.notificationLockCounter})`);
      return;
    }
    
    // Clear any existing timer
    if (this.successNotificationTimer) {
      clearTimeout(this.successNotificationTimer);
      this.successNotificationTimer = null;
    }
    
    console.log(`✅ Settings - Showing success notification (UNLOCKED) - Counter: ${this.notificationLockCounter}`);
    this.showSuccessNotification.set(true);
    
    // Auto-hide after 4 seconds and re-lockdown immediately
    this.successNotificationTimer = setTimeout(() => {
      this.hideSuccessNotification();
      this.lockdownNotifications();
      this.forceHideNotification();
    }, 4000);
  }

  hideSuccessNotification() {
    // Clear any existing timer
    if (this.successNotificationTimer) {
      clearTimeout(this.successNotificationTimer);
      this.successNotificationTimer = null;
    }
    
    this.showSuccessNotification.set(false);
  }

  updateProfilePictureImmediately(profilePicturePath: string) {
    console.log('🖼️ Updating profile picture immediately to:', profilePicturePath);
    
    // Construct full URL if it's a relative path - use backend port 8080
    let fullImageUrl = profilePicturePath;
    if (profilePicturePath && !profilePicturePath.startsWith('http')) {
      fullImageUrl = `${API_BASE_URL}${profilePicturePath}`;
      console.log('🖼️ Constructed full image URL with backend port:', fullImageUrl);
    }
    
    // Update the current user with the new profile picture immediately
    const currentUserData = this.currentUser();
    if (currentUserData) {
      const updatedUser = {
        ...currentUserData,
        profilePicture: fullImageUrl
      };
      this.currentUser.set(updatedUser);
      console.log('🖼️ Profile picture updated. New user data:', updatedUser);
    } else {
      console.log('⚠️ No current user data available for update');
    }
  }

  // Helper method to ensure profile picture URLs are correct
  getCorrectImageUrl(imagePath: string | null): string | null {
    if (!imagePath) return null;
    
    if (imagePath.startsWith('http')) {
      return imagePath; // Already a full URL
    }
    
    // Construct full URL for relative paths using backend port 8080
    const fullUrl = `${API_BASE_URL}${imagePath}`;
    console.log('🔧 Converted image URL:', imagePath, '->', fullUrl);
    return fullUrl;
  }

  // Enhanced image error handler with fallback
  onImageError(event: any) {
    console.error('❌ Image failed to load:', event.target.src);
    
    // Check if this is a profile picture that failed to load
    const img = event.target;
    if (img.src.includes('/uploads/profile-pictures/')) {
      console.log('🔄 Profile picture failed to load, falling back to initials');
      
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
        initialsDiv.className = 'user-initials user-initials-fallback';
        initialsDiv.textContent = this.getUserInitials();
        parent.appendChild(initialsDiv);
      }
    }
  }



  uploadProfilePicture() {
    console.log('🔄 Upload button clicked!');
    console.log('Selected file:', this.selectedFile);
    console.log('Current user:', this.currentUser());
    
    if (!this.selectedFile) {
      console.error('❌ No file selected');
      this.dialogService.alert('Por favor selecciona una imagen primero', 'Sin imagen');
      return;
    }
    
    if (!this.currentUser()) {
      console.error('❌ No user data available');
      this.dialogService.alert('No se pudo obtener la información del usuario', 'Error');
      return;
    }

    console.log('📤 Starting upload...');
    const formData = new FormData();
    formData.append('file', this.selectedFile);

    const userId = this.currentUser().id;
    const uploadUrl = `http://localhost:8080/api/users/${userId}/profile-picture`;
    console.log('🌐 Upload URL:', uploadUrl);
    
    // Log token for debugging
    const token = this.authService.getToken();
    console.log('🔑 Auth token available:', !!token);
    console.log('🔑 Token length:', token ? token.length : 0);

    this.http.post(uploadUrl, formData)
      .subscribe({
        next: (response: any) => {
          console.log('✅ Upload successful:', response);
          
          // Update profile picture immediately
          if (response && response.profilePicture) {
            this.updateProfilePictureImmediately(response.profilePicture);
          }
          
          // Reload user data from server (in background)
          this.authService.getCurrentUser().subscribe();
          
          // Clear preview after successful upload
          this.clearPreview();
          
          // Temporarily stop guardian for successful action
          if (this.notificationGuardian) {
            clearInterval(this.notificationGuardian);
            this.notificationGuardian = null;
          }
          
          // Unlock notifications specifically for this successful action
          this.unlockNotificationsForAction();
          this.displaySuccessNotification();
          
          // Restart guardian after showing notification
          setTimeout(() => {
            this.startNotificationGuardian();
          }, 4500);
        },
        error: (error) => {
          console.error('❌ Upload failed:', error);
          console.error('❌ Error status:', error.status);
          console.error('❌ Error message:', error.message);
          console.error('❌ Error URL:', error.url);
          console.error('❌ Full error object:', JSON.stringify(error, null, 2));
          
          let errorMsg = 'Error al subir la imagen. ';
          const backendMessage = error?.error?.error || error?.error?.message;
          if (error.status === 0) {
            errorMsg += 'No se puede conectar con el servidor. Verifica que el servidor esté ejecutándose.';
          } else if (error.status === 404) {
            errorMsg += 'El endpoint de la API no existe.';
          } else if (error.status === 401) {
            errorMsg += 'No tienes permisos para realizar esta acción (revisa si tu sesión sigue activa).';
          } else if (backendMessage) {
            // Usa el motivo real que ahora devuelve el backend en vez de un
            // mensaje genérico (ej. "Usuario no encontrado", "El archivo debe
            // ser una imagen", detalles de validación, etc.)
            errorMsg += backendMessage;
          } else if (error.status === 500) {
            errorMsg += 'Error interno del servidor.';
          } else {
            errorMsg += `Código de error: ${error.status}`;
          }
          
          this.dialogService.alert(errorMsg + ' Por favor intenta de nuevo.', 'Error al subir');
          // Keep preview on error so user can retry
        }
      });
  }

  showLogoutDialog() {
    this.showLogoutConfirmation.set(true);
  }

  confirmLogout() {
    console.log('🗑️ Settings - User confirmed logout');
    this.showLogoutConfirmation.set(false);
    this.currentUser.set(null);
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  cancelLogout() {
    console.log('❌ Settings - User cancelled logout');
    this.showLogoutConfirmation.set(false);
  }

  logout() {
    this.showLogoutDialog();
  }

  // Edit profile methods
  startEditProfile() {
    const user = this.currentUser();
    if (user) {
      this.editName.set(user.name || '');
      this.editSurname.set(user.surname || '');
      this.editEmail.set(user.email || '');
      this.isEditingProfile.set(true);
      this.profileUpdateError.set(null);
    }
  }

  cancelEditProfile() {
    // Trigger hide animation before closing
    this.isFormHiding.set(true);
    
    // Wait for animation to complete before hiding the form
    setTimeout(() => {
      this.isEditingProfile.set(false);
      this.isFormHiding.set(false);
      this.editName.set('');
      this.editSurname.set('');
      this.editEmail.set('');
      this.profileUpdateError.set(null);
    }, 250); // Match CSS animation duration
  }

  saveProfile() {
    const user = this.currentUser();
    if (!user || !user.id) {
      this.profileUpdateError.set('No se pudo obtener la información del usuario');
      return;
    }

    const name = this.editName().trim();
    const surname = this.editSurname().trim();

    if (!name) {
      this.profileUpdateError.set('El nombre es obligatorio');
      return;
    }

    this.isUpdatingProfile.set(true);
    this.profileUpdateError.set(null);

    const updateData = {
      name: name,
      surname: surname,
      email: this.editEmail(),
      username: user.username,
      enable: user.enable,
      profilePicture: user.profilePicture
    };

    const updateUrl = `http://localhost:8080/api/users/${user.id}`;

    this.http.put(updateUrl, updateData).subscribe({
      next: (updatedUser: any) => {
        console.log('✅ Profile updated successfully:', updatedUser);
        
        // Trigger success animation before closing the form
        this.isFormHiding.set(true);
        
        // Wait for animation to complete before hiding the form
        setTimeout(() => {
          // Update local user data
          const newUserData = {
            ...user,
            name: updatedUser.name,
            surname: updatedUser.surname,
            email: updatedUser.email,
            profilePicture: user.profilePicture
          };
          this.currentUser.set(newUserData);
          
          
          
          this.isEditingProfile.set(false);
          this.isFormHiding.set(false);
          this.isUpdatingProfile.set(false);
          
          // Show success notification
          this.unlockNotificationsForAction();
          this.displayNameUpdateNotification();
        }, 250); // Match CSS animation duration
      },
      error: (error) => {
        console.error('❌ Failed to update profile:', error);
        this.isUpdatingProfile.set(false);
        
        let errorMsg = 'Error al actualizar el perfil. ';
        // Si el backend mandó un mensaje concreto (ej: "correo ya en uso",
        // "nombre de usuario ya en uso"), mostrarlo tal cual en vez del
        // mensaje genérico por código de estado.
        if (error.error && typeof error.error === 'object' && error.error.error) {
          errorMsg = error.error.error;
        } else if (error.status === 0) {
          errorMsg += 'No se puede conectar con el servidor.';
        } else if (error.status === 401) {
          errorMsg += 'No tienes permisos para realizar esta acción.';
        } else if (error.status === 409) {
          errorMsg += 'El correo o el usuario ya están en uso.';
        } else if (error.status === 500) {
          errorMsg += 'Error interno del servidor.';
        } else {
          errorMsg += `Código de error: ${error.status}`;
        }
        this.profileUpdateError.set(errorMsg);
      }
    });
  }

  displayNameUpdateNotification() {
    // Temporarily stop guardian
    if (this.notificationGuardian) {
      clearInterval(this.notificationGuardian);
      this.notificationGuardian = null;
    }
    
    // Show notification using existing showSuccessNotification signal
    this.showSuccessNotification.set(true);
    
    // Hide after 4 seconds and re-lockdown
    setTimeout(() => {
      this.showSuccessNotification.set(false);
      this.lockdownNotifications();
      
      // Restart guardian
      setTimeout(() => {
        this.startNotificationGuardian();
      }, 500);
    }, 4000);
  }
}
import { Component, Input, Output, EventEmitter, inject, signal } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { NotificationService, Notification } from '../services/notification.service';
import { AuthService } from '../services/auth.service';

export interface NotificationReply {
  originalNotificationId: number;
  message: string;
  senderId: number;
}

@Component({
  selector: 'app-notification-details',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './notification-details.component.html',
  styleUrls: ['./notification-details.component.css']
})
export class NotificationDetailsComponent {
  @Input() notification: Notification | null = null;
  @Input() isOpen = false;
  /** true cuando se abre una RESPUESTA recibida: solo se lee (sin responder ni marcar). */
  @Input() readOnly = false;
  @Output() close = new EventEmitter<void>();
  @Output() reply = new EventEmitter<NotificationReply>();

  private notificationService = inject(NotificationService);
  private authService = inject(AuthService);

  replyMessage = '';
  isReplying = false;
  
  // Success notification signal
  showSuccessNotification = signal(false);

  closeModal() {
    this.isOpen = false;
    this.resetForm();
    this.close.emit();
  }

  resetForm() {
    this.replyMessage = '';
    this.isReplying = false;
  }

  markAsRead() {
    if (this.notification && !this.notification.isRead) {
      this.notificationService.markAsRead(this.notification.id).subscribe();
    }
  }

  sendReply() {
    if (!this.replyMessage.trim() || !this.notification || this.isReplying) {
      return;
    }

    this.isReplying = true;

    const replyData = {
      originalNotificationId: this.notification.id,
      message: this.replyMessage.trim(),
      senderId: this.getCurrentUserId()
    };

    this.reply.emit(replyData);
    
    // Show success notification
    this.showSuccessNotification.set(true);
    
    // Hide after 2 seconds
    setTimeout(() => {
      this.showSuccessNotification.set(false);
      this.resetForm();
    }, 2000);
  }

  getCurrentUserId(): number {
    const user = this.authService.getCurrentUserValue();
    return user && user.id ? user.id : 0;
  }

  formatDateTime(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleString('es-ES', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  getNotificationTypeIcon(type: string): string {
    switch (type) {
      case 'ADMIN_MESSAGE':
        return 'admin_panel_settings';
      case 'REPORT_CARD_SENT':
        return 'assignment';
      case 'REPORT_CARD_SIGNED':
        return 'verified';
      case 'REPLY':
        return 'reply';
      default:
        return 'notifications';
    }
  }

  getNotificationTypeColor(type: string): string {
    switch (type) {
      case 'ADMIN_MESSAGE':
        return '#2196F3';
      case 'REPORT_CARD_SENT':
        return '#FF9800';
      case 'REPORT_CARD_SIGNED':
        return '#4CAF50';
      case 'REPLY':
        return '#0ea5e9';
      default:
        return '#757575';
    }
  }

  isOwnNotification(): boolean {
    const userData = localStorage.getItem('currentUser');
    if (userData) {
      const currentUser = JSON.parse(userData);
      return this.notification ? this.notification.user.id === currentUser.id : false;
    }
    return false;
  }

  getInitials(name?: string, surname?: string): string {
    if (!name && !surname) return '';
    
    const firstName = name ? name.charAt(0).toUpperCase() : '';
    const lastName = surname ? surname.charAt(0).toUpperCase() : '';
    
    return firstName + lastName;
  }
}
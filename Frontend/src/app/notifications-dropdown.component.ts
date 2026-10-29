import { Component, Input, Output, EventEmitter, inject, OnInit } from '@angular/core';

import { NotificationService, Notification } from './services/notification.service';

export interface NotificationClickEvent {
  notification: Notification;
  action: 'view' | 'reply';
}

@Component({
  selector: 'app-notifications-dropdown',
  standalone: true,
  imports: [],
  template: `
    @if (isOpen) {
      <div class="notifications-dropdown" [class.dropdown-open]="isOpen">
        <div class="dropdown-header">
          <h3>Notificaciones</h3>
          <button class="close-btn" (click)="closeDropdown()">
            <span class="material-icons">close</span>
          </button>
        </div>
        @if (notifications.length > 0) {
          <div class="notifications-list">
            @for (notification of notifications; track trackByNotificationId($index, notification)) {
              <div
                class="notification-item"
                [class.unread]="isNotificationUnread(notification)"
                (click)="markAsReadAndClose(notification)">
                <div class="notification-content">
                  <div class="notification-header">
                    <div class="notification-title">{{ notification.title }}</div>
                    <div class="notification-time">{{ formatTime(notification.createdAt) }}</div>
                  </div>
                  <div class="notification-message">{{ notification.message }}</div>
                </div>
                @if (!notification.isRead) {
                  <div class="notification-status">
                    <span class="unread-dot"></span>
                  </div>
                }
              </div>
            }
          </div>
        } @else {
          <div class="empty-state">
            <span class="material-icons">notifications_none</span>
            <p>No tienes notificaciones</p>
          </div>
        }
        @if (notifications.length > 0) {
          <div class="dropdown-footer">
            <button
              class="mark-all-read-btn"
              [disabled]="!hasUnreadNotifications"
              [class.disabled]="!hasUnreadNotifications"
              (click)="markAllAsRead()">
              Marcar todas como leídas
            </button>
          </div>
        }
      </div>
    }
    `,
  styles: [`
    .notifications-dropdown {
      position: absolute;
      top: 100%;
      right: 0;
      width: 380px;
      max-height: min(500px, 70vh);
      background: var(--surface);
      border-radius: var(--r-md);
      box-shadow: var(--shadow-md);
      border: 1px solid var(--border);
      z-index: 1000;
      animation: dropdownSlide 0.2s ease-out;
      overflow: hidden;
    }

    .dropdown-open {
      transform: translateY(0);
    }

    @keyframes dropdownSlide {
      from {
        opacity: 0;
        transform: translateY(-10px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }

    .dropdown-header {
      padding: 20px 20px 16px;
      border-bottom: 1px solid var(--border);
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .dropdown-header h3 {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      color: var(--text-1);
    }

    .close-btn {
      background: none;
      border: none;
      padding: 4px;
      border-radius: 50%;
      cursor: pointer;
      color: var(--text-3);
      transition: all 0.18s ease;
    }

    .close-btn:hover {
      background: var(--surface-2);
      color: var(--text-1);
    }

    .close-btn:focus-visible {
      outline: 2px solid var(--brand);
      outline-offset: 2px;
    }

    .notifications-list {
      max-height: 350px;
      overflow-y: auto;
    }

    .notification-item {
      padding: 16px 20px;
      border-bottom: 1px solid var(--border);
      cursor: pointer;
      transition: background-color 0.15s ease;
      display: flex;
      align-items: flex-start;
      gap: 12px;
      position: relative;
    }

    .notification-item:hover {
      background: var(--surface-2);
    }

    .notification-item.unread {
      background: var(--success-bg);
      border-left: 3px solid var(--success);
    }

    .notification-item.unread:hover {
      background: #d1fae5;
    }

    .notification-icon {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      overflow: hidden;
      background: var(--surface-2);
    }

    .notification-icon .material-icons {
      font-size: 18px;
      color: white;
    }

    .notification-icon.info { background: var(--brand); }
    .notification-icon.success { background: var(--success); }
    .notification-icon.warning { background: var(--warning); }
    .notification-icon.error { background: var(--danger); }

    .profile-picture {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      object-fit: cover;
      object-position: center;
    }

    .notification-content {
      flex: 1;
      min-width: 0;
    }

    .notification-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 8px;
    }

    .notification-title {
      font-weight: 600;
      font-size: 14px;
      color: var(--text-1);
      line-height: 1.3;
      flex: 1;
      margin-right: 8px;
    }

    .notification-time {
      font-size: 11px;
      color: var(--text-4);
      white-space: nowrap;
      margin-top: 2px;
    }

    .notification-message {
      font-size: 13px;
      color: var(--text-2);
      line-height: 1.4;
      margin-bottom: 6px;
      word-wrap: break-word;
      overflow-wrap: break-word;
      overflow: hidden;
      display: -webkit-box;
      display: box;
      line-clamp: 5;
      -webkit-line-clamp: 5;
      -webkit-box-orient: vertical;
      box-orient: vertical;
    }

    .notification-time {
      font-size: 12px;
      color: var(--text-4);
    }

    .notification-status {
      flex-shrink: 0;
      padding-top: 2px;
    }

    .unread-dot {
      width: 8px;
      height: 8px;
      background: var(--success);
      border-radius: 50%;
    }

    .empty-state {
      padding: 40px 20px;
      text-align: center;
      color: var(--text-3);
    }

    .empty-state .material-icons {
      font-size: 48px;
      margin-bottom: 12px;
      opacity: 0.5;
    }

    .empty-state p {
      margin: 0;
      font-size: 14px;
    }

    .dropdown-footer {
      padding: 12px 20px;
      border-top: 1px solid var(--border);
      background: var(--surface-2);
    }

    .mark-all-read-btn {
      width: 100%;
      padding: 8px 12px;
      background: var(--success);
      color: white;
      border: none;
      border-radius: var(--r-sm);
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
      transition: background-color 0.18s ease;
    }

    .mark-all-read-btn:hover {
      background: #15803d;
    }

    .mark-all-read-btn:focus-visible {
      outline: 2px solid var(--success);
      outline-offset: 2px;
    }

    .mark-all-read-btn:disabled,
    .mark-all-read-btn.disabled {
      background: var(--border-strong);
      color: var(--text-3);
      cursor: not-allowed;
      opacity: 0.6;
    }

    .mark-all-read-btn:disabled:hover,
    .mark-all-read-btn.disabled:hover {
      background: var(--border-strong);
      color: var(--text-3);
    }

    /* Scrollbar styling */
    .notifications-list::-webkit-scrollbar {
      width: 6px;
    }

    .notifications-list::-webkit-scrollbar-track {
      background: transparent;
    }

    .notifications-list::-webkit-scrollbar-thumb {
      background: var(--border-strong);
      border-radius: 3px;
    }

    .notifications-list::-webkit-scrollbar-thumb:hover {
      background: var(--text-4);
    }

    @media (max-width: 480px) {
      .notifications-dropdown {
        width: calc(100vw - 24px);
        max-width: none;
        right: 12px;
      }
    }
  `]
})
export class NotificationsDropdownComponent implements OnInit {
  @Input() isOpen = false;
  @Output() close = new EventEmitter<void>();
  @Output() notificationClick = new EventEmitter<NotificationClickEvent>();

  private notificationService = inject(NotificationService);
  
  notifications: Notification[] = [];

  ngOnInit() {
    // Subscribe to notifications changes
    this.notificationService.getNotifications().subscribe(notifications => {
      this.notifications = notifications;
    });
  }

  closeDropdown() {
    this.isOpen = false;
    this.notificationService.clearSeenNotifications(); // Clear seen notifications when dropdown is closed
    this.close.emit();
  }

  markAsReadAndClose(notification: Notification) {
    // Mark as seen when notification is clicked (before opening)
    this.markAsSeen(notification);
    
    if (!notification.isRead) {
      this.notificationService.markAsRead(notification.id).subscribe();
    }
    this.notificationClick.emit({
      notification,
      action: 'view'
    });
    this.closeDropdown();
  }

  markAllAsRead() {
    const unreadNotifications = this.notifications.filter(n => !n.isRead);
    if (unreadNotifications.length === 0) {
      return; // No unread notifications to mark as read
    }
    
    unreadNotifications.forEach(notification => {
      this.notificationService.markAsRead(notification.id).subscribe();
    });
  }

  get hasUnreadNotifications(): boolean {
    return this.notifications.some(n => !n.isRead);
  }

  formatTime(createdAt: string): string {
    return this.notificationService.formatTimeAgo(createdAt);
  }

  getNotificationIcon(type: string): string {
    switch (type) {
      case 'REPORT_CARD_SENT':
        return 'assignment';
      case 'REPORT_CARD_SIGNED':
        return 'verified';
      case 'INFO':
      default:
        return 'info';
    }
  }

  trackByNotificationId(index: number, notification: Notification): number {
    return notification.id;
  }

  isNotificationUnread(notification: Notification): boolean {
    return this.notificationService.isNewlyArrived(notification);
  }

  markAsSeen(notification: Notification): void {
    this.notificationService.markAsSeen(notification.id);
  }
}
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, tap } from 'rxjs';
import { FirebasePushService } from './firebase-push.service';

export interface Notification {
  id: number;
  user: {
    id: number;
    name: string;
    surname: string;
    profilePicture?: string;
  };
  title: string;
  message: string;
  createdAt: string;
  isRead: boolean;
  notificationType: string;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly firebasePush = inject(FirebasePushService);
  private readonly API_BASE = 'http://localhost:8080/api/notifications';

  private notificationsSubject = new BehaviorSubject<Notification[]>([]);
  private unreadCountSubject = new BehaviorSubject<number>(0);
  private seenNotificationIds = new Set<number>();
  private readNotificationIds = new Set<number>();
  private previousNotifications: Notification[] = [];
  private readonly STORAGE_KEY = 'seen_notifications';
  private readonly READ_STORAGE_KEY = 'read_notifications';
  private readonly PUSHED_NOTIFICATIONS_KEY = 'pushed_notifications';

  public notifications$ = this.notificationsSubject.asObservable();
  public unreadCount$ = this.unreadCountSubject.asObservable();

  constructor() {
    this.loadSeenNotifications();
    this.loadReadNotifications();
    
    // Auto-refresh notifications every 3 seconds
    setInterval(() => {
      this.refreshNotifications();
    }, 3000);
    
    // Request notification permission on initialization
    this.requestNotificationPermission();
  }

  /**
   * Request browser notification permission
   */
  async requestNotificationPermission(): Promise<boolean> {
    if (!('Notification' in window)) {
      console.log('This browser does not support notifications');
      return false;
    }

    if (Notification.permission === 'granted') {
      return true;
    }

    if (Notification.permission !== 'denied') {
      const permission = await Notification.requestPermission();
      return permission === 'granted';
    }

    return false;
  }

  /**
   * Register FCM token for the current user
   * Call this after successful login
   */
  async registerPushToken(userId: number): Promise<void> {
    try {
      const token = await this.firebasePush.requestPermissionAndGetToken(userId);
      if (token) {
        console.log('FCM Token registered for user:', userId);
      }
    } catch (error) {
      console.error('Error registering push token:', error);
    }
  }

  /**
   * Show notification using FirebasePushService
   */
  showNativePushNotification(title: string, body: string, data?: any): void {
    this.firebasePush.showPushNotification(title, body, data);
  }

  /**
   * Show a push notification with app logo
   */
  private showPushNotification(title: string, body: string, icon?: string): void {
    if (Notification.permission !== 'granted') {
      return;
    }

    // Use app logo or default icon
    const notificationIcon = icon || '/Logo Colegio.png';

    const notification = new Notification(title, {
      body: body,
      icon: notificationIcon,
      badge: notificationIcon,
      tag: 'notas-trinitario',
      requireInteraction: false,
      silent: false
    });

    notification.onclick = () => {
      window.focus();
      notification.close();
    };

    // Auto close after 5 seconds
    setTimeout(() => {
      notification.close();
    }, 5000);
  }

  /**
   * Check for new notifications and show push notification
   */
  private checkForNewNotifications(newNotifications: Notification[]): void {
    // Get IDs of notifications that have already shown a push notification
    const pushedNotificationIds = this.getPushedNotificationIds();
    
    // Find notifications that weren't in the previous list AND haven't shown a push yet
    const newOnes = newNotifications.filter(newNotif => 
      !this.previousNotifications.some(prevNotif => prevNotif.id === newNotif.id) &&
      !pushedNotificationIds.has(newNotif.id)
    );

    // Show push notification for each new notification
    newOnes.forEach(notification => {
      this.showPushNotification(
        notification.title,
        notification.message
      );
      // Mark as pushed so we don't show it again
      this.markNotificationAsPushed(notification.id);
    });

    // Update previous notifications
    this.previousNotifications = [...newNotifications];
  }

  /**
   * Get set of notification IDs that have already shown a push
   */
  private getPushedNotificationIds(): Set<number> {
    try {
      const stored = localStorage.getItem(this.PUSHED_NOTIFICATIONS_KEY);
      if (stored) {
        return new Set(JSON.parse(stored));
      }
    } catch (error) {
      console.error('Error loading pushed notifications:', error);
    }
    return new Set();
  }

  /**
   * Mark a notification as having shown a push
   */
  private markNotificationAsPushed(notificationId: number): void {
    try {
      const pushedIds = this.getPushedNotificationIds();
      pushedIds.add(notificationId);
      localStorage.setItem(this.PUSHED_NOTIFICATIONS_KEY, JSON.stringify(Array.from(pushedIds)));
    } catch (error) {
      console.error('Error saving pushed notification:', error);
    }
  }

  /**
   * Load notifications for the current user
   */
  loadNotifications(userId: number): Observable<Notification[]> {
    return this.http.get<Notification[]>(`${this.API_BASE}/user/${userId}`).pipe(
      tap(notifications => {
        console.log('Loaded notifications:', notifications);
        
        // Check for new notifications and show push
        this.checkForNewNotifications(notifications);
        
        const notificationsWithPreservedReadStatus = notifications.map(notification => {
          if (this.readNotificationIds.has(notification.id)) {
            return { ...notification, isRead: true };
          }
          return notification;
        });
        
        this.loadSeenNotifications();
        this.notificationsSubject.next(notificationsWithPreservedReadStatus);
        this.updateUnreadCount(notificationsWithPreservedReadStatus);
      })
    );
  }

  /**
   * Load read notification IDs from localStorage
   */
  private loadReadNotifications(): void {
    try {
      const stored = localStorage.getItem(this.READ_STORAGE_KEY);
      if (stored) {
        const readIds = JSON.parse(stored);
        this.readNotificationIds = new Set(readIds);
        console.log('Loaded read notifications from storage:', this.readNotificationIds.size);
      }
    } catch (error) {
      console.error('Error loading read notifications:', error);
      this.readNotificationIds.clear();
    }
  }

  /**
   * Save read notification IDs to localStorage
   */
  private saveReadNotifications(): void {
    try {
      const readIds = Array.from(this.readNotificationIds);
      localStorage.setItem(this.READ_STORAGE_KEY, JSON.stringify(readIds));
      console.log('Saved read notifications to storage:', readIds.length);
    } catch (error) {
      console.error('Error saving read notifications:', error);
    }
  }

  /**
   * Load seen notification IDs from localStorage
   */
  private loadSeenNotifications(): void {
    try {
      const stored = localStorage.getItem(this.STORAGE_KEY);
      if (stored) {
        const seenIds = JSON.parse(stored);
        this.seenNotificationIds = new Set(seenIds);
        console.log('Loaded seen notifications from storage:', this.seenNotificationIds.size);
      }
    } catch (error) {
      console.error('Error loading seen notifications:', error);
      this.seenNotificationIds.clear();
    }
  }

  /**
   * Save seen notification IDs to localStorage
   */
  private saveSeenNotifications(): void {
    try {
      const seenIds = Array.from(this.seenNotificationIds);
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(seenIds));
      console.log('Saved seen notifications to storage:', seenIds.length);
    } catch (error) {
      console.error('Error saving seen notifications:', error);
    }
  }

  /**
   * Refresh notifications (without loading initially)
   */
  refreshNotifications(): void {
    const user = JSON.parse(localStorage.getItem('currentUser') || '{}');
    if (user && user.id) {
      this.http.get<Notification[]>(`${this.API_BASE}/user/${user.id}`).subscribe({
        next: (notifications) => {
          console.log('Refreshed notifications:', notifications);
          
          // Preserve local read status for notifications marked as read locally
          const notificationsWithPreservedReadStatus = notifications.map(notification => {
            // If notification was marked as read locally, keep it as read even if server says unread
            if (this.readNotificationIds.has(notification.id)) {
              return { ...notification, isRead: true };
            }
            return notification;
          });
          
          // Show all notifications to authenticated users
          this.notificationsSubject.next(notificationsWithPreservedReadStatus);
          this.updateUnreadCount(notificationsWithPreservedReadStatus);
        },
        error: (error) => {
          console.error('Error refreshing notifications:', error);
        }
      });
    }
  }

  /**
   * Mark a notification as read
   */
  markAsRead(notificationId: number): Observable<any> {
    // Add to read notifications set immediately for local state
    this.readNotificationIds.add(notificationId);
    this.saveReadNotifications();
    
    return this.http.post(`${this.API_BASE}/${notificationId}/read`, {}).pipe(
      tap(() => {
        // Update the notification in our local list
        const notifications = this.notificationsSubject.value;
        const updatedNotifications = notifications.map(n => 
          n.id === notificationId ? { ...n, isRead: true } : n
        );
        this.notificationsSubject.next(updatedNotifications);
        this.updateUnreadCount(updatedNotifications);
      })
    );
  }

  /**
   * Get unread notifications count
   */
  getUnreadCount(): Observable<number> {
    return this.unreadCount$;
  }

  /**
   * Get all notifications
   */
  getNotifications(): Observable<Notification[]> {
    return this.notifications$;
  }

  /**
   * Check if a notification is newly arrived (unread and not seen before)
   */
  isNewlyArrived(notification: Notification): boolean {
    // Consider as newly arrived if:
    // 1. It's not marked as read on the server AND
    // 2. It's not in our local read notifications list AND  
    // 3. It hasn't been seen before
    const isReadLocally = this.readNotificationIds.has(notification.id);
    return !notification.isRead && !isReadLocally && !this.seenNotificationIds.has(notification.id);
  }

  /**
   * Mark a notification as seen (when it appears in the dropdown)
   */
  markAsSeen(notificationId: number): void {
    console.log('Marking notification as seen:', notificationId);
    this.seenNotificationIds.add(notificationId);
    this.saveSeenNotifications();
  }

  /**
   * Clear seen notifications (when user opens the dropdown)
   */
  clearSeenNotifications(): void {
    console.log('Clearing seen notifications from current session');
    this.seenNotificationIds.clear();
    // Don't clear from localStorage, as user wants to remember seen notifications
  }

  /**
   * Clear seen notifications from localStorage (for testing or reset)
   */
  clearSeenNotificationsStorage(): void {
    console.log('Clearing seen notifications from localStorage');
    this.seenNotificationIds.clear();
    localStorage.removeItem(this.STORAGE_KEY);
  }

  /**
   * Clear read notifications (when user opens the dropdown)
   */
  clearReadNotifications(): void {
    console.log('Clearing read notifications from current session');
    this.readNotificationIds.clear();
    // Don't clear from localStorage, as user wants to remember read notifications
  }

  /**
   * Clear read notifications from localStorage (for testing or reset)
   */
  clearReadNotificationsStorage(): void {
    console.log('Clearing read notifications from localStorage');
    this.readNotificationIds.clear();
    localStorage.removeItem(this.READ_STORAGE_KEY);
  }

  /**
   * Get count of seen notifications
   */
  getSeenNotificationsCount(): number {
    return this.seenNotificationIds.size;
  }

  /**
   * Get count of read notifications
   */
  getReadNotificationsCount(): number {
    return this.readNotificationIds.size;
  }

  /**
   * Mark all notifications as read locally (for bulk operations)
   */
  markAllAsReadLocally(notifications: Notification[]): void {
    notifications.forEach(notification => {
      this.readNotificationIds.add(notification.id);
    });
    this.saveReadNotifications();

    // Update the notifications array
    const updatedNotifications = notifications.map(n => ({ ...n, isRead: true }));
    this.notificationsSubject.next(updatedNotifications);
    this.updateUnreadCount(updatedNotifications);
  }

  /**
   * Delete all notifications for the current user
   */
  deleteAllNotifications(): Observable<any> {
    const user = JSON.parse(localStorage.getItem('currentUser') || '{}');
    if (!user || !user.id) {
      return new Observable(observer => observer.error('No user found'));
    }

    return this.http.delete(`${this.API_BASE}/user/${user.id}`).pipe(
      tap(() => {
        // Clear all notifications locally
        this.notificationsSubject.next([]);
        this.updateUnreadCount([]);
        // Clear local storage
        this.readNotificationIds.clear();
        this.seenNotificationIds.clear();
        this.saveReadNotifications();
        this.saveSeenNotifications();
      })
    );
  }

  /**
   * Check if current user can see notifications
   */
  canUserSeeNotifications(): boolean {
    // Always show notifications to all authenticated users
    const isAuthenticated = localStorage.getItem('token') !== null;
    
    console.log('canUserSeeNotifications - isAuthenticated:', isAuthenticated);
    
    // Show notifications if user is authenticated
    return isAuthenticated;
  }

  /**
   * Format notification time for display
   */
  formatTimeAgo(createdAt: string): string {
    const now = new Date();
    const created = new Date(createdAt);
    const diffInMinutes = Math.floor((now.getTime() - created.getTime()) / (1000 * 60));

    if (diffInMinutes < 1) return 'Ahora mismo';
    if (diffInMinutes < 60) return `Hace ${diffInMinutes} min`;
    
    const diffInHours = Math.floor(diffInMinutes / 60);
    if (diffInHours < 24) return `Hace ${diffInHours} h`;
    
    const diffInDays = Math.floor(diffInHours / 24);
    if (diffInDays < 7) return `Hace ${diffInDays} d`;
    
    return created.toLocaleDateString('es-ES', { 
      day: 'numeric', 
      month: 'short',
      year: created.getFullYear() !== now.getFullYear() ? 'numeric' : undefined
    });
  }

  /**
   * Update unread count
   */
  private updateUnreadCount(notifications: Notification[]): void {
    const unreadCount = notifications.filter(n => !n.isRead).length;
    this.unreadCountSubject.next(unreadCount);
  }

  /**
   * Filter notifications by user role - show to all authenticated users including administrators
   */
  private filterNotificationsByRole(notifications: Notification[]): Notification[] {
    const currentUser = JSON.parse(localStorage.getItem('currentUser') || '{}');
    
    // If user is authenticated, show all notifications
    if (currentUser && currentUser.id) {
      return notifications;
    }
    
    // If no user, return empty array
    return [];
  }
}
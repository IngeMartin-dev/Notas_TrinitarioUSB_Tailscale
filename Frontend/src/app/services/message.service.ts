import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, tap } from 'rxjs';

export interface Message {
  id: number;
  senderId: number;
  senderName: string;
  senderSurname: string;
  recipientId: number;
  recipientName: string;
  recipientSurname: string;
  originalNotificationId: number;
  originalNotificationTitle: string;
  replyMessage: string;
  createdAt: string;
  isRead: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class MessageService {
  private readonly http = inject(HttpClient);
  private readonly API_BASE = 'http://localhost:8080/api/messages';

  private messagesSubject = new BehaviorSubject<Message[]>([]);
  private unreadCountSubject = new BehaviorSubject<number>(0);
  private readMessageIds = new Set<number>(); // Track messages that have been marked as read
  private readonly READ_STORAGE_KEY = 'read_messages';

  public messages$ = this.messagesSubject.asObservable();
  public unreadCount$ = this.unreadCountSubject.asObservable();

  constructor() {
    // Load read messages from localStorage on service initialization
    this.loadReadMessages();
    
    // Auto-refresh messages every 5 seconds
    setInterval(() => {
      this.refreshMessages();
    }, 5000);
  }

  /**
   * Load messages for the current user
   */
  loadMessages(userId: number): Observable<Message[]> {
    return this.http.get<Message[]>(`${this.API_BASE}/user/${userId}`).pipe(
      tap(messages => {
        console.log('Loaded messages:', messages);
        
        // Preserve local read status for messages marked as read locally
        const messagesWithPreservedReadStatus = messages.map(message => {
          // If message was marked as read locally, keep it as read even if server says unread
          if (this.readMessageIds.has(message.id)) {
            return { ...message, isRead: true };
          }
          return message;
        });
        
        this.messagesSubject.next(messagesWithPreservedReadStatus);
        this.updateUnreadCount(messagesWithPreservedReadStatus);
      })
    );
  }

  /**
   * Load read message IDs from localStorage
   */
  private loadReadMessages(): void {
    try {
      const stored = localStorage.getItem(this.READ_STORAGE_KEY);
      if (stored) {
        const readIds = JSON.parse(stored);
        this.readMessageIds = new Set(readIds);
        console.log('Loaded read messages from storage:', this.readMessageIds.size);
      }
    } catch (error) {
      console.error('Error loading read messages:', error);
      this.readMessageIds.clear();
    }
  }

  /**
   * Save read message IDs to localStorage
   */
  private saveReadMessages(): void {
    try {
      const readIds = Array.from(this.readMessageIds);
      localStorage.setItem(this.READ_STORAGE_KEY, JSON.stringify(readIds));
      console.log('Saved read messages to storage:', readIds.length);
    } catch (error) {
      console.error('Error saving read messages:', error);
    }
  }

  /**
   * Refresh messages (without loading initially)
   */
  refreshMessages(): void {
    const user = JSON.parse(localStorage.getItem('currentUser') || '{}');
    if (user && user.id) {
      this.http.get<Message[]>(`${this.API_BASE}/user/${user.id}`).subscribe({
        next: (messages) => {
          console.log('Refreshed messages:', messages);
          
          // Preserve local read status for messages marked as read locally
          const messagesWithPreservedReadStatus = messages.map(message => {
            if (this.readMessageIds.has(message.id)) {
              return { ...message, isRead: true };
            }
            return message;
          });
          
          this.messagesSubject.next(messagesWithPreservedReadStatus);
          this.updateUnreadCount(messagesWithPreservedReadStatus);
        },
        error: (error) => {
          console.error('Error refreshing messages:', error);
        }
      });
    }
  }

  /**
   * Mark a message as read
   */
  markAsRead(messageId: number): Observable<any> {
    // Add to read messages set immediately for local state
    this.readMessageIds.add(messageId);
    this.saveReadMessages();
    
    return this.http.post(`${this.API_BASE}/${messageId}/read`, {}).pipe(
      tap(() => {
        // Update the message in our local list
        const messages = this.messagesSubject.value;
        const updatedMessages = messages.map(m => 
          m.id === messageId ? { ...m, isRead: true } : m
        );
        this.messagesSubject.next(updatedMessages);
        this.updateUnreadCount(updatedMessages);
      })
    );
  }

  /**
   * Get unread messages count
   */
  getUnreadCount(): Observable<number> {
    return this.unreadCount$;
  }

  /**
   * Get all messages
   */
  getMessages(): Observable<Message[]> {
    return this.messages$;
  }

  /**
   * Update unread count
   */
  private updateUnreadCount(messages: Message[]): void {
    const unreadCount = messages.filter(m => !m.isRead).length;
    this.unreadCountSubject.next(unreadCount);
  }

  /**
   * Format message time for display
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
}
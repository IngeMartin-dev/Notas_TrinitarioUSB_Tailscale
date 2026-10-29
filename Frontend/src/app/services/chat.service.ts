import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type ChatMessageType = 'TEXT' | 'IMAGE' | 'FILE' | 'GIF' | 'STICKER';

export interface ChatContactDto {
  id: number;
  name: string;
  surname: string;
  profilePicture: string | null;
  role: string;
  lastMessage: string | null;
  lastMessageAt: string | null;
  unreadCount: number;
}

export interface ChatMessageDto {
  id: number;
  senderId: number;
  senderName: string;
  receiverId: number;
  type: ChatMessageType;
  content: string;
  fileUrl: string | null;
  fileName: string | null;
  createdAt: string;
  read: boolean;
}

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly API_BASE = 'http://localhost:8080/api/chats';

  getContacts(currentUserId: number): Observable<ChatContactDto[]> {
    return this.http.get<ChatContactDto[]>(`${this.API_BASE}/contacts`, {
      params: { currentUserId: String(currentUserId) }
    });
  }

  getConversation(currentUserId: number, otherUserId: number): Observable<ChatMessageDto[]> {
    return this.http.get<ChatMessageDto[]>(`${this.API_BASE}/conversation/${otherUserId}`, {
      params: { currentUserId: String(currentUserId) }
    });
  }

  getNewMessages(currentUserId: number, otherUserId: number, since: string): Observable<ChatMessageDto[]> {
    return this.http.get<ChatMessageDto[]>(`${this.API_BASE}/conversation/${otherUserId}/nuevos`, {
      params: { currentUserId: String(currentUserId), since }
    });
  }

  sendMessage(senderId: number, receiverId: number, content: string, type: ChatMessageType = 'TEXT'): Observable<ChatMessageDto> {
    return this.http.post<ChatMessageDto>(`${this.API_BASE}/messages`, {
      senderId, receiverId, content, type
    });
  }

  sendAttachment(senderId: number, receiverId: number, type: ChatMessageType, file: File): Observable<ChatMessageDto> {
    const formData = new FormData();
    formData.append('senderId', String(senderId));
    formData.append('receiverId', String(receiverId));
    formData.append('type', type);
    formData.append('file', file);
    return this.http.post<ChatMessageDto>(`${this.API_BASE}/messages/adjunto`, formData);
  }

  markAsRead(currentUserId: number, otherUserId: number): Observable<any> {
    return this.http.put(`${this.API_BASE}/conversation/${otherUserId}/leido`, {}, {
      params: { currentUserId: String(currentUserId) }
    });
  }

  getUnreadTotal(currentUserId: number): Observable<{ total: number }> {
    return this.http.get<{ total: number }>(`${this.API_BASE}/no-leidos`, {
      params: { currentUserId: String(currentUserId) }
    });
  }
}
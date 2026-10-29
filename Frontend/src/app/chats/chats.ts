import { Component, OnInit, OnDestroy, inject, ViewChild, ElementRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChatService, ChatContactDto, ChatMessageDto, ChatMessageType } from '../services/chat.service';
import { FirebasePushService } from '../services/firebase-push.service';

import { API_BASE_URL } from '../config/api-base';
interface ChatContact extends ChatContactDto {
  lastMessageTime: string;
  isOnline: boolean;
}

interface DisplayMessage extends ChatMessageDto {
  isFromMe: boolean;
}

// Pack de stickers integrado (emoji grandes). No depende de ningún
// servicio externo, así que funciona siempre, sin necesidad de API key.
const STICKER_PACK: string[] = [
  '😀', '😂', '😍', '🥳', '😎', '🤔', '😴', '😭', '😡', '🥺',
  '👍', '👏', '🙌', '🙏', '💪', '✌️', '🤝', '👋', '🤞', '👌',
  '❤️', '🔥', '⭐', '🎉', '✅', '❌', '💯', '📚', '🎓', '🏆'
];

@Component({
  selector: 'app-chats',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './chats.html',
  styleUrls: ['./chats.css']
})
export class Chats implements OnInit, OnDestroy {
  @ViewChild('messagesContainer') messagesContainer!: ElementRef;
  @ViewChild('fileInput') fileInputRef!: ElementRef<HTMLInputElement>;

  private chatService = inject(ChatService);
  private firebasePushService = inject(FirebasePushService);

  currentUserId = 0;

  chats: ChatContact[] = [];
  filteredChats: ChatContact[] = [];
  searchTerm = '';
  isLoading = true;

  selectedChat: ChatContact | null = null;
  selectedChatMessages: DisplayMessage[] = [];
  newMessage = '';

  showStickerPicker = false;
  showGifPicker = false;
  stickerPack = STICKER_PACK;

  // Buscador de GIFs (Tenor). Requiere una API key propia del colegio,
  // gratuita en https://tenor.com/developer/keyregistration. Sin la key
  // configurada, el buscador queda deshabilitado y solo se puede adjuntar
  // un GIF ya descargado (como archivo) con el clip 📎.
  private readonly TENOR_API_KEY = ''; // ← pega aquí tu API key de Tenor
  gifSearchTerm = '';
  gifResults: { id: string; preview: string; url: string }[] = [];
  gifLoading = false;

  uploadingAttachment = false;

  private pollContactsHandle: any = null;
  private pollMessagesHandle: any = null;
  private lastMessageTimestamp: string | null = null;

  ngOnInit() {
    const currentUser = JSON.parse(localStorage.getItem('currentUser') || '{}');
    this.currentUserId = currentUser.id ?? 0;

    this.loadContacts();
    // Refresca la lista de contactos (últimos mensajes / no leídos) cada 4s.
    this.pollContactsHandle = setInterval(() => this.loadContacts(true), 4000);
  }

  ngOnDestroy() {
    if (this.pollContactsHandle) clearInterval(this.pollContactsHandle);
    if (this.pollMessagesHandle) clearInterval(this.pollMessagesHandle);
  }

  loadContacts(silent = false) {
    if (!silent) this.isLoading = true;
    this.chatService.getContacts(this.currentUserId).subscribe({
      next: (contacts) => {
        this.chats = contacts.map(c => ({
          ...c,
          lastMessageTime: c.lastMessageAt ? this.formatTimeAgo(c.lastMessageAt) : '',
          isOnline: false
        }));
        this.applyFilter();
        this.isLoading = false;

        if (this.selectedChat) {
          const updated = this.chats.find(c => c.id === this.selectedChat!.id);
          if (updated) this.selectedChat = updated;
        }
      },
      error: () => { this.isLoading = false; }
    });
  }

  applyFilter() {
    const term = this.searchTerm.trim().toLowerCase();
    this.filteredChats = !term
      ? this.chats
      : this.chats.filter(c => `${c.name} ${c.surname}`.toLowerCase().includes(term));
  }

  onSearchChange() {
    this.applyFilter();
  }

  selectChat(chat: ChatContact) {
    this.selectedChat = chat;
    this.selectedChatMessages = [];
    // Se reinicia a null: mientras esté en null, pollNewMessages() no hace
    // nada (ver guard más abajo). Evita que un "poll" dispare con el
    // timestamp de la conversación ANTERIOR justo antes de que termine de
    // cargar la nueva, lo que duplicaba mensajes (y producía el error
    // NG0955 de claves repetidas en la lista).
    this.lastMessageTimestamp = null;
    this.showStickerPicker = false;
    this.showGifPicker = false;
    this.loadConversation();

    if (this.pollMessagesHandle) clearInterval(this.pollMessagesHandle);
    // Chat "casi instantáneo": revisa mensajes nuevos cada segundo mientras
    // la conversación está abierta.
    this.pollMessagesHandle = setInterval(() => this.pollNewMessages(), 1000);

    this.chatService.markAsRead(this.currentUserId, chat.id).subscribe(() => {
      chat.unreadCount = 0;
    });
  }

  closeChat() {
    this.selectedChat = null;
    this.selectedChatMessages = [];
    if (this.pollMessagesHandle) clearInterval(this.pollMessagesHandle);
  }

  loadConversation() {
    if (!this.selectedChat) return;
    this.chatService.getConversation(this.currentUserId, this.selectedChat.id).subscribe({
      next: (messages) => {
        this.selectedChatMessages = messages.map(m => this.toDisplayMessage(m));
        if (messages.length > 0) {
          this.lastMessageTimestamp = messages[messages.length - 1].createdAt;
        }
        setTimeout(() => this.scrollToBottom(), 50);
      }
    });
  }

  private pollNewMessages() {
    if (!this.selectedChat || !this.lastMessageTimestamp) return;
    const chatIdAlPedir = this.selectedChat.id; // por si cambia de chat mientras la petición está en vuelo
    this.chatService.getNewMessages(this.currentUserId, this.selectedChat.id, this.lastMessageTimestamp)
      .subscribe(nuevos => {
        if (nuevos.length === 0) return;
        if (!this.selectedChat || this.selectedChat.id !== chatIdAlPedir) return; // ya cambió de chat, se descarta

        const idsExistentes = new Set(this.selectedChatMessages.map(m => m.id));
        const realmenteNuevos = nuevos.filter(m => !idsExistentes.has(m.id));
        if (realmenteNuevos.length === 0) return;

        realmenteNuevos.forEach(m => this.selectedChatMessages.push(this.toDisplayMessage(m)));
        this.lastMessageTimestamp = nuevos[nuevos.length - 1].createdAt;
        setTimeout(() => this.scrollToBottom(), 50);

        const deOtro = realmenteNuevos.some(m => m.senderId !== this.currentUserId);
        if (deOtro && this.selectedChat) {
          this.chatService.markAsRead(this.currentUserId, this.selectedChat.id).subscribe();
        }
      });
  }

  private toDisplayMessage(m: ChatMessageDto): DisplayMessage {
    return { ...m, isFromMe: m.senderId === this.currentUserId };
  }

  sendMessage() {
    if (!this.newMessage.trim() || !this.selectedChat) return;
    const texto = this.newMessage.trim();
    this.newMessage = '';

    this.chatService.sendMessage(this.currentUserId, this.selectedChat.id, texto, 'TEXT').subscribe({
      next: (msg) => {
        this.selectedChatMessages.push(this.toDisplayMessage(msg));
        this.lastMessageTimestamp = msg.createdAt;
        this.selectedChat!.lastMessage = texto;
        this.selectedChat!.lastMessageTime = 'Ahora';
        setTimeout(() => this.scrollToBottom(), 50);
      },
      error: () => { this.newMessage = texto; }
    });
  }

  openFilePicker() {
    this.fileInputRef?.nativeElement.click();
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || !this.selectedChat) return;

    const esImagen = file.type.startsWith('image/');
    const tipo: ChatMessageType = esImagen ? 'IMAGE' : 'FILE';

    this.uploadingAttachment = true;
    this.chatService.sendAttachment(this.currentUserId, this.selectedChat.id, tipo, file).subscribe({
      next: (msg) => {
        this.selectedChatMessages.push(this.toDisplayMessage(msg));
        this.lastMessageTimestamp = msg.createdAt;
        this.selectedChat!.lastMessage = tipo === 'IMAGE' ? '📷 Foto' : '📎 ' + file.name;
        this.selectedChat!.lastMessageTime = 'Ahora';
        this.uploadingAttachment = false;
        setTimeout(() => this.scrollToBottom(), 50);
      },
      error: () => { this.uploadingAttachment = false; }
    });

    input.value = '';
  }

  toggleStickerPicker() {
    this.showStickerPicker = !this.showStickerPicker;
    this.showGifPicker = false;
  }

  sendSticker(emoji: string) {
    if (!this.selectedChat) return;
    this.showStickerPicker = false;
    this.chatService.sendMessage(this.currentUserId, this.selectedChat.id, emoji, 'STICKER').subscribe(msg => {
      this.selectedChatMessages.push(this.toDisplayMessage(msg));
      this.lastMessageTimestamp = msg.createdAt;
      this.selectedChat!.lastMessage = 'Sticker';
      this.selectedChat!.lastMessageTime = 'Ahora';
      setTimeout(() => this.scrollToBottom(), 50);
    });
  }

  toggleGifPicker() {
    this.showGifPicker = !this.showGifPicker;
    this.showStickerPicker = false;
  }

  get tenorConfigured(): boolean {
    return !!this.TENOR_API_KEY;
  }

  searchGifs() {
    if (!this.tenorConfigured || !this.gifSearchTerm.trim()) return;
    this.gifLoading = true;
    const query = encodeURIComponent(this.gifSearchTerm.trim());
    fetch(`https://tenor.googleapis.com/v2/search?q=${query}&key=${this.TENOR_API_KEY}&limit=12&media_filter=gif`)
      .then(res => res.json())
      .then(data => {
        this.gifResults = (data.results || []).map((r: any) => ({
          id: r.id,
          preview: r.media_formats?.tinygif?.url || r.media_formats?.gif?.url,
          url: r.media_formats?.gif?.url
        }));
        this.gifLoading = false;
      })
      .catch(() => { this.gifLoading = false; });
  }

  sendGif(gifUrl: string) {
    if (!this.selectedChat) return;
    this.showGifPicker = false;
    this.chatService.sendMessage(this.currentUserId, this.selectedChat.id, gifUrl, 'GIF').subscribe(msg => {
      this.selectedChatMessages.push(this.toDisplayMessage(msg));
      this.lastMessageTimestamp = msg.createdAt;
      this.selectedChat!.lastMessage = 'GIF';
      this.selectedChat!.lastMessageTime = 'Ahora';
      setTimeout(() => this.scrollToBottom(), 50);
    });
  }

  scrollToBottom() {
    if (this.messagesContainer) {
      const container = this.messagesContainer.nativeElement;
      container.scrollTop = container.scrollHeight;
    }
  }

  formatTimeAgo(createdAt: string): string {
    const now = new Date();
    const created = new Date(createdAt);
    const diffInMinutes = Math.floor((now.getTime() - created.getTime()) / (1000 * 60));

    if (diffInMinutes < 1) return 'Ahora';
    if (diffInMinutes < 60) return `${diffInMinutes}m`;

    const diffInHours = Math.floor(diffInMinutes / 60);
    if (diffInHours < 24) return `${diffInHours}h`;

    const diffInDays = Math.floor(diffInHours / 24);
    if (diffInDays === 1) return 'Ayer';
    if (diffInDays < 7) return `${diffInDays}d`;

    return created.toLocaleDateString('es-ES', { day: 'numeric', month: 'short' });
  }

  formatMessageTime(createdAt: string): string {
    const date = new Date(createdAt);
    return date.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
  }

  getMessageDate(createdAt: string): string {
    const date = new Date(createdAt);
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);

    if (date.toDateString() === today.toDateString()) return 'Hoy';
    if (date.toDateString() === yesterday.toDateString()) return 'Ayer';
    return date.toLocaleDateString('es-ES', { day: 'numeric', month: 'long', year: 'numeric' });
  }

  shouldShowDateDivider(index: number): boolean {
    if (index === 0) return true;
    const prev = new Date(this.selectedChatMessages[index - 1].createdAt).toDateString();
    const curr = new Date(this.selectedChatMessages[index].createdAt).toDateString();
    return prev !== curr;
  }

  getInitials(name: string, surname: string): string {
    return ((name?.charAt(0) || '') + (surname?.charAt(0) || '')).toUpperCase();
  }

  getCorrectImageUrl(imagePath: string | null | undefined): string {
    if (!imagePath) return '';
    if (imagePath.startsWith('http')) return imagePath;
    return `${API_BASE_URL}${imagePath}`;
  }

  trackByChatId(index: number, chat: ChatContact): number {
    return chat.id;
  }

  trackByMessageId(index: number, message: DisplayMessage): number {
    return message.id;
  }
}